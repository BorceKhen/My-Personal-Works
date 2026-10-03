package com.example.imagecompressor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class CompressionMode {
    VISUALLY_LOSSLESS,
    PURE_LOSSLESS
}

sealed interface CompressionUiState {
    data object Idle : CompressionUiState

    data class Loading(
        val message: String = "Developing your photo in the booth..."
    ) : CompressionUiState

    data class Success(
        val result: ProcessResult,
        val mode: CompressionMode
    ) : CompressionUiState

    data class Error(
        val message: String
    ) : CompressionUiState
}

sealed interface UiEvent {
    data class ShowToast(val message: String) : UiEvent
    data class RequestWritePermission(val fileToSave: File, val format: OutputImageFormat) : UiEvent
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<CompressionUiState>(CompressionUiState.Idle)
    val uiState: StateFlow<CompressionUiState> = _uiState.asStateFlow()

    private val _selectedMode = MutableStateFlow(CompressionMode.VISUALLY_LOSSLESS)
    val selectedMode: StateFlow<CompressionMode> = _selectedMode.asStateFlow()

    // Explicit format selector: Keep Original, JPEG, PNG, or WebP
    private val _targetFormat = MutableStateFlow(TargetFormat.ORIGINAL)
    val targetFormat: StateFlow<TargetFormat> = _targetFormat.asStateFlow()

    // Default 60% quality (Recommended for 40-70% savings while remaining visually sharp)
    private val _targetQuality = MutableStateFlow(60)
    val targetQuality: StateFlow<Int> = _targetQuality.asStateFlow()

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow: SharedFlow<UiEvent> = _eventFlow.asSharedFlow()

    fun setCompressionMode(mode: CompressionMode) {
        _selectedMode.value = mode
    }

    fun setTargetFormat(format: TargetFormat) {
        _targetFormat.value = format
    }

    fun setTargetQuality(quality: Int) {
        _targetQuality.value = quality.coerceIn(30, 95)
    }

    /**
     * Executes unified image conversion and dynamic compression pipeline.
     */
    fun processImageUri(uri: Uri) {
        val context = getApplication<Application>()
        val mode = _selectedMode.value
        val formatSelection = _targetFormat.value
        val quality = _targetQuality.value
        val isPureLossless = (mode == CompressionMode.PURE_LOSSLESS)

        viewModelScope.launch {
            _uiState.value = CompressionUiState.Loading("Reading and preparing photo...")

            withContext(Dispatchers.IO) {
                try {
                    _uiState.value = CompressionUiState.Loading("Developing & applying color quantization...")

                    val result = ImageProcessor.processAndConvertImage(
                        context = context,
                        inputUri = uri,
                        targetFormatSelection = formatSelection,
                        targetQuality = quality,
                        isPureLossless = isPureLossless
                    )

                    if (result != null) {
                        _uiState.value = CompressionUiState.Success(
                            result = result,
                            mode = mode
                        )
                    } else {
                        _uiState.value = CompressionUiState.Error(
                            "Booth development failed. Please try a different photo."
                        )
                    }
                } catch (e: Exception) {
                    _uiState.value = CompressionUiState.Error(
                        e.localizedMessage ?: "Unexpected error during image processing."
                    )
                }
            }
        }
    }

    fun saveToGallery(file: File, format: OutputImageFormat) {
        val context = getApplication<Application>()

        if (!PermissionUtil.hasWritePermission(context)) {
            viewModelScope.launch {
                _eventFlow.emit(UiEvent.RequestWritePermission(file, format))
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val savedUri = FileUtil.saveImageToPictures(context, file, format)
            if (savedUri != null) {
                _eventFlow.emit(UiEvent.ShowToast("Print collected! Saved to Pictures/PhotoBooth"))
            } else {
                _eventFlow.emit(UiEvent.ShowToast("Failed to save image to Pictures."))
            }
        }
    }

    fun reset() {
        _uiState.value = CompressionUiState.Idle
    }
}
