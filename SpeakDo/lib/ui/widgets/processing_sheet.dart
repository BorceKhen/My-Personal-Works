import 'dart:async';
import 'package:flutter/material.dart';
import '../../models/note_model.dart';
import '../../services/offline_pipeline_orchestrator.dart';
import '../../theme/app_theme.dart';
import 'live_waveform_painter.dart';

enum ProcessingModalMode {
  recording,
  pipeline,
}

/// Active Processing Sheet Component (/processing-modal).
/// Handles both Live Recording Mode and Offline Pipeline Progress Mode.
class ProcessingSheet extends StatefulWidget {
  final ProcessingModalMode initialMode;
  final String? importedFilePath;
  final String? customSimulatedTranscript;
  final void Function(Note note) onComplete;

  const ProcessingSheet({
    super.key,
    required this.initialMode,
    this.importedFilePath,
    this.customSimulatedTranscript,
    required this.onComplete,
  });

  @override
  State<ProcessingSheet> createState() => _ProcessingSheetState();
}

class _ProcessingSheetState extends State<ProcessingSheet> {
  late ProcessingModalMode _currentMode;
  Timer? _recordTimer;
  int _secondsRecorded = 0;
  bool _isPaused = false;

  StreamSubscription<PipelineStatus>? _pipelineSubscription;
  PipelineStatus _pipelineStatus = const PipelineStatus(
    stage: ProcessingStage.idle,
    progress: 0.0,
    statusText: 'Initializing offline engine...',
    logs: [],
  );

  bool _showLogs = false;

  @override
  void initState() {
    super.initState();
    _currentMode = widget.initialMode;

    if (_currentMode == ProcessingModalMode.recording) {
      _startRecordingTimer();
    } else {
      _startPipeline(
        filePath: widget.importedFilePath ?? 'imported_media.mp4',
        durationSec: 32,
      );
    }
  }

  void _startRecordingTimer() {
    _recordTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!_isPaused) {
        setState(() {
          _secondsRecorded++;
        });
      }
    });
  }

  void _stopRecordingAndProcess() {
    _recordTimer?.cancel();
    setState(() {
      _currentMode = ProcessingModalMode.pipeline;
    });

    final recordedDuration = _secondsRecorded > 0 ? _secondsRecorded : 18;
    _startPipeline(
      filePath: 'microphone://live_pcm_stream_${DateTime.now().millisecondsSinceEpoch}.wav',
      durationSec: recordedDuration,
    );
  }

  void _startPipeline({required String filePath, required int durationSec}) {
    _pipelineSubscription?.cancel();
    _pipelineSubscription = OfflinePipelineOrchestrator.processMedia(
      inputFilePath: filePath,
      recordedDurationSeconds: durationSec,
      customSimulatedTranscript: widget.customSimulatedTranscript,
    ).listen((status) {
      if (mounted) {
        setState(() {
          _pipelineStatus = status;
        });

        if (status.stage == ProcessingStage.completed && status.createdNote != null) {
          // Provide short auto-advance or button click
        }
      }
    });
  }

  @override
  void dispose() {
    _recordTimer?.cancel();
    _pipelineSubscription?.cancel();
    super.dispose();
  }

  String _formatTimer(int seconds) {
    final h = (seconds ~/ 3600).toString().padLeft(2, '0');
    final m = ((seconds % 3600) ~/ 60).toString().padLeft(2, '0');
    final s = (seconds % 60).toString().padLeft(2, '0');
    return '$h:$m:$s';
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: AppTheme.slateBackground,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      padding: EdgeInsets.only(
        top: 20,
        left: 20,
        right: 20,
        bottom: MediaQuery.of(context).viewInsets.bottom + 28,
      ),
      child: AnimatedSwitcher(
        duration: const Duration(milliseconds: 300),
        child: _currentMode == ProcessingModalMode.recording
            ? _buildRecordingView()
            : _buildPipelineView(),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 1. Live Recording View
  // ---------------------------------------------------------------------------
  Widget _buildRecordingView() {
    return Column(
      key: const ValueKey('recording_view'),
      mainAxisSize: MainAxisSize.min,
      children: [
        // Top drag handle
        Center(
          child: Container(
            width: 44,
            height: 4,
            decoration: BoxDecoration(
              color: AppTheme.subtleBorder,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
        ),
        const SizedBox(height: 20),

        // Air-gapped security badge
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color: AppTheme.surfaceCard,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: AppTheme.subtleBorder),
          ),
          child: const Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.shield_outlined, size: 14, color: AppTheme.accentAmber),
              SizedBox(width: 6),
              Text(
                '100% On-Device Air-Gapped Recording',
                style: TextStyle(
                  color: AppTheme.accentAmber,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),

        // Live Timer Counter
        Text(
          _formatTimer(_secondsRecorded),
          style: const TextStyle(
            fontSize: 42,
            fontWeight: FontWeight.w800,
            color: AppTheme.primaryText,
            letterSpacing: 2,
            fontFeatures: [FontFeature.tabularFigures()],
          ),
        ),
        const SizedBox(height: 6),
        Text(
          _isPaused ? 'Recording Paused' : 'Listening & Capturing PCM Audio...',
          style: TextStyle(
            fontSize: 14,
            color: _isPaused ? AppTheme.accentAmber : AppTheme.mutedText,
          ),
        ),
        const SizedBox(height: 28),

        // Live Waveform Visualizer
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          decoration: BoxDecoration(
            color: AppTheme.surfaceCard,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppTheme.subtleBorder),
          ),
          child: LiveWaveformWidget(
            isRecording: !_isPaused,
            height: 70,
            barCount: 38,
            barColor: AppTheme.accentAmber,
          ),
        ),
        const SizedBox(height: 24),

        // Format info
        const Text(
          'Sampling Rate: 16kHz Mono 16-bit PCM • Whisper.cpp Ready',
          style: TextStyle(fontSize: 12, color: AppTheme.mutedText),
        ),
        const SizedBox(height: 28),

        // Action controls
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            // Cancel Button
            IconButton(
              iconSize: 28,
              onPressed: () {
                _recordTimer?.cancel();
                Navigator.of(context).pop();
              },
              icon: const Icon(Icons.close_rounded, color: AppTheme.mutedText),
              tooltip: 'Cancel Recording',
            ),

            // Pause/Resume Button
            InkWell(
              onTap: () {
                setState(() {
                  _isPaused = !_isPaused;
                });
              },
              borderRadius: BorderRadius.circular(50),
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppTheme.surfaceCard,
                  shape: BoxShape.circle,
                  border: Border.all(color: AppTheme.subtleBorder),
                ),
                child: Icon(
                  _isPaused ? Icons.play_arrow_rounded : Icons.pause_rounded,
                  size: 28,
                  color: AppTheme.primaryText,
                ),
              ),
            ),

            // Stop and Process CTA (Amber button)
            InkWell(
              onTap: _stopRecordingAndProcess,
              borderRadius: BorderRadius.circular(50),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    colors: [AppTheme.accentAmber, AppTheme.accentAmberDark],
                  ),
                  borderRadius: BorderRadius.circular(32),
                  boxShadow: [
                    BoxShadow(
                      color: AppTheme.accentAmber.withValues(alpha: 0.35),
                      blurRadius: 16,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: const Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.stop_rounded, size: 24, color: Colors.black),
                    SizedBox(width: 8),
                    Text(
                      'Stop & Transcribe',
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                        color: Colors.black,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // 2. Offline Pipeline Progress View
  // ---------------------------------------------------------------------------
  Widget _buildPipelineView() {
    final progress = _pipelineStatus.progress;
    final percentInt = (progress * 100).toInt();
    final isDone = _pipelineStatus.stage == ProcessingStage.completed;
    final isError = _pipelineStatus.stage == ProcessingStage.error;

    return Column(
      key: const ValueKey('pipeline_view'),
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Center(
          child: Container(
            width: 44,
            height: 4,
            decoration: BoxDecoration(
              color: AppTheme.subtleBorder,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
        ),
        const SizedBox(height: 18),

        // Title and percent
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: isDone
                        ? AppTheme.successEmerald.withValues(alpha: 0.2)
                        : AppTheme.accentAmber.withValues(alpha: 0.2),
                    shape: BoxShape.circle,
                  ),
                  child: Icon(
                    isDone
                        ? Icons.check_circle_rounded
                        : isError
                            ? Icons.error_outline_rounded
                            : Icons.memory_rounded,
                    size: 20,
                    color: isDone
                        ? AppTheme.successEmerald
                        : isError
                            ? AppTheme.errorRed
                            : AppTheme.accentAmber,
                  ),
                ),
                const SizedBox(width: 12),
                Text(
                  isDone
                      ? 'Processing Complete!'
                      : isError
                          ? 'Processing Error'
                          : 'Offline Engine Running',
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                    color: AppTheme.primaryText,
                  ),
                ),
              ],
            ),
            Text(
              '$percentInt%',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.w800,
                color: isDone ? AppTheme.successEmerald : AppTheme.accentAmber,
              ),
            ),
          ],
        ),
        const SizedBox(height: 18),

        // Animated Percentage Bar
        ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: SizedBox(
            height: 8,
            child: LinearProgressIndicator(
              value: progress,
              backgroundColor: AppTheme.surfaceCard,
              valueColor: AlwaysStoppedAnimation<Color>(
                isDone
                    ? AppTheme.successEmerald
                    : isError
                        ? AppTheme.errorRed
                        : AppTheme.accentAmber,
              ),
            ),
          ),
        ),
        const SizedBox(height: 12),
        Text(
          _pipelineStatus.statusText,
          style: const TextStyle(
            fontSize: 13,
            color: AppTheme.mutedText,
          ),
        ),
        const SizedBox(height: 20),

        // Multi-stage step indicators
        _buildStageIndicator(
          stepNumber: 1,
          title: 'Step 1: Converting Audio (FFmpeg)',
          subtitle: 'Strip video & resample to 16kHz mono PCM WAV',
          isActive: _pipelineStatus.stage == ProcessingStage.convertingAudio,
          isCompleted: progress >= 0.40,
        ),
        const SizedBox(height: 10),
        _buildStageIndicator(
          stepNumber: 2,
          title: 'Step 2: Transcribing Offline (whisper.cpp)',
          subtitle: 'Quantized GGML model on-device speech inference',
          isActive: _pipelineStatus.stage == ProcessingStage.transcribingOffline,
          isCompleted: progress >= 0.80,
        ),
        const SizedBox(height: 10),
        _buildStageIndicator(
          stepNumber: 3,
          title: 'Step 3: Parsing Action Items & Deadlines',
          subtitle: 'Deterministic zero-latency offline regex NLP extractor',
          isActive: _pipelineStatus.stage == ProcessingStage.parsingActionItems ||
              _pipelineStatus.stage == ProcessingStage.persisting,
          isCompleted: isDone,
        ),
        const SizedBox(height: 18),

        // Telemetry Logs toggle
        InkWell(
          onTap: () {
            setState(() {
              _showLogs = !_showLogs;
            });
          },
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Native Execution Telemetry (${_pipelineStatus.logs.length} events)',
                style: const TextStyle(fontSize: 12, color: AppTheme.mutedText),
              ),
              Icon(
                _showLogs ? Icons.expand_less_rounded : Icons.expand_more_rounded,
                size: 18,
                color: AppTheme.mutedText,
              ),
            ],
          ),
        ),
        if (_showLogs) ...[
          const SizedBox(height: 8),
          Container(
            height: 110,
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: Colors.black.withValues(alpha: 0.5),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: AppTheme.subtleBorder),
            ),
            child: ListView.builder(
              reverse: true,
              itemCount: _pipelineStatus.logs.length,
              itemBuilder: (context, index) {
                final revIndex = _pipelineStatus.logs.length - 1 - index;
                return Text(
                  _pipelineStatus.logs[revIndex],
                  style: const TextStyle(
                    fontSize: 11,
                    fontFamily: 'monospace',
                    color: Color(0xFF68D391),
                  ),
                );
              },
            ),
          ),
        ],
        const SizedBox(height: 24),

        // Completion Action
        if (isDone && _pipelineStatus.createdNote != null)
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTheme.successEmerald,
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(14),
              ),
            ),
            onPressed: () {
              Navigator.of(context).pop();
              widget.onComplete(_pipelineStatus.createdNote!);
            },
            child: const Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  'View Note & Extracted Tasks',
                  style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
                ),
                SizedBox(width: 8),
                Icon(Icons.arrow_forward_rounded, size: 18),
              ],
            ),
          )
        else if (isError)
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTheme.surfaceCard,
              foregroundColor: AppTheme.primaryText,
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(14),
                side: const BorderSide(color: AppTheme.subtleBorder),
              ),
            ),
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Dismiss'),
          ),
      ],
    );
  }

  Widget _buildStageIndicator({
    required int stepNumber,
    required String title,
    required String subtitle,
    required bool isActive,
    required bool isCompleted,
  }) {
    Color iconColor;
    IconData icon;

    if (isCompleted) {
      iconColor = AppTheme.successEmerald;
      icon = Icons.check_circle_rounded;
    } else if (isActive) {
      iconColor = AppTheme.accentAmber;
      icon = Icons.hourglass_top_rounded;
    } else {
      iconColor = AppTheme.mutedText.withValues(alpha: 0.5);
      icon = Icons.circle_outlined;
    }

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isActive
            ? AppTheme.surfaceCardHover
            : AppTheme.surfaceCard,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: isActive ? AppTheme.accentAmber : AppTheme.subtleBorder,
          width: isActive ? 1.5 : 1,
        ),
      ),
      child: Row(
        children: [
          Icon(icon, color: iconColor, size: 22),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: TextStyle(
                    fontSize: 13.5,
                    fontWeight: FontWeight.w600,
                    color: isCompleted || isActive
                        ? AppTheme.primaryText
                        : AppTheme.mutedText,
                  ),
                ),
                Text(
                  subtitle,
                  style: const TextStyle(
                    fontSize: 11,
                    color: AppTheme.mutedText,
                  ),
                ),
              ],
            ),
          ),
          if (isActive)
            const SizedBox(
              width: 14,
              height: 14,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                valueColor: AlwaysStoppedAnimation<Color>(AppTheme.accentAmber),
              ),
            ),
        ],
      ),
    );
  }
}
