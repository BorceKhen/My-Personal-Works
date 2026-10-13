import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';
import '../database/app_database.dart';
import '../models/note_model.dart';
import '../models/task_model.dart';
import 'ffmpeg_service.dart';
import 'local_task_parser.dart';
import 'whisper_service.dart';

enum ProcessingStage {
  idle,
  convertingAudio,       // Step 1: Converting Audio (FFmpeg)
  transcribingOffline,   // Step 2: Transcribing Offline (whisper.cpp)
  parsingActionItems,    // Step 3: Parsing Action Items & Deadlines
  persisting,            // Step 4: Local SQLite Save
  completed,
  error,
}

class PipelineStatus {
  final ProcessingStage stage;
  final double progress; // 0.0 to 1.0
  final String statusText;
  final List<String> logs;
  final Note? createdNote;
  final List<TaskItem> createdTasks;
  final String? errorMessage;

  const PipelineStatus({
    required this.stage,
    required this.progress,
    required this.statusText,
    required this.logs,
    this.createdNote,
    this.createdTasks = const [],
    this.errorMessage,
  });

  PipelineStatus copyWith({
    ProcessingStage? stage,
    double? progress,
    String? statusText,
    List<String>? logs,
    Note? createdNote,
    List<TaskItem>? createdTasks,
    String? errorMessage,
  }) {
    return PipelineStatus(
      stage: stage ?? this.stage,
      progress: progress ?? this.progress,
      statusText: statusText ?? this.statusText,
      logs: logs ?? this.logs,
      createdNote: createdNote ?? this.createdNote,
      createdTasks: createdTasks ?? this.createdTasks,
      errorMessage: errorMessage ?? this.errorMessage,
    );
  }
}

/// Offline Media Processing Pipeline Coordinator.
/// Wires: FFmpeg conversion -> whisper.cpp transcription -> LocalTaskParser extraction -> SQLite save.
class OfflinePipelineOrchestrator {
  static const _uuid = Uuid();

  /// Execute the entire offline pipeline on an audio/video file.
  static Stream<PipelineStatus> processMedia({
    required String inputFilePath,
    required int recordedDurationSeconds,
    String? customSimulatedTranscript,
  }) async* {
    final List<String> logs = [];

    void addLog(String message) {
      final time = DateTime.now().toIso8601String().substring(11, 19);
      logs.add('[$time] $message');
    }

    // Initial state
    yield PipelineStatus(
      stage: ProcessingStage.convertingAudio,
      progress: 0.05,
      statusText: 'Step 1 of 3: Initializing FFmpeg conversion session...',
      logs: logs,
    );

    String? wavPath;
    try {
      // -----------------------------------------------------------------------
      // STEP 1: FFmpeg Audio Extraction & Resampling (16kHz mono pcm_s16le)
      // -----------------------------------------------------------------------
      addLog('FFmpeg: Stripping video (-vn) and resampling to 16kHz mono PCM WAV');
      yield PipelineStatus(
        stage: ProcessingStage.convertingAudio,
        progress: 0.15,
        statusText: 'Step 1 of 3: Converting audio via FFmpeg (-vn -ar 16000 -ac 1)...',
        logs: logs,
      );

      wavPath = await FFmpegService.instance.extractToWav(
        inputFilePath,
        onProgress: (subProgress, log) {
          addLog('FFmpeg: $log');
        },
      );

      if (wavPath == null || wavPath.isEmpty) {
        throw Exception('FFmpeg failed to extract 16kHz WAV from source media.');
      }

      yield PipelineStatus(
        stage: ProcessingStage.convertingAudio,
        progress: 0.35,
        statusText: 'Step 1 complete: 16kHz WAV ready for Whisper engine.',
        logs: logs,
      );

      // -----------------------------------------------------------------------
      // STEP 2: On-Device whisper.cpp STT Inference (100% Offline)
      // -----------------------------------------------------------------------
      yield PipelineStatus(
        stage: ProcessingStage.transcribingOffline,
        progress: 0.40,
        statusText: 'Step 2 of 3: Feeding WAV into on-device whisper.cpp engine...',
        logs: logs,
      );

      addLog('Whisper: Loading quantized GGML model (ggml-base.en.bin)');
      addLog('Whisper: Air-gapped STT inference initiated on-device');

      final transcript = await WhisperService.instance.transcribeOfflineAudio(
        wavPath,
        customSimulatedText: customSimulatedTranscript,
        onProgress: (subProgress, log) {
          addLog('Whisper: $log');
        },
      );

      yield PipelineStatus(
        stage: ProcessingStage.transcribingOffline,
        progress: 0.75,
        statusText: 'Step 2 complete: Full transcript generated without network calls.',
        logs: logs,
      );

      // -----------------------------------------------------------------------
      // STEP 3: Deterministic Offline NLP Task & Deadline Extraction
      // -----------------------------------------------------------------------
      yield PipelineStatus(
        stage: ProcessingStage.parsingActionItems,
        progress: 0.80,
        statusText: 'Step 3 of 3: Parsing action items and deadlines with zero-latency regex NLP...',
        logs: logs,
      );

      addLog('NLP: Running deterministic regex matchers for trigger phrases & dates');

      final noteId = _uuid.v4();
      final parseResult = LocalTaskParser.parse(transcript, noteId: noteId);

      addLog('NLP: Discovered ${parseResult.tasks.length} actionable item(s) in transcript');
      for (var t in parseResult.tasks) {
        addLog('NLP Task: "${t.taskText}" (${t.formattedDueDate})');
      }

      yield PipelineStatus(
        stage: ProcessingStage.parsingActionItems,
        progress: 0.90,
        statusText: 'Step 3 complete: Extracted ${parseResult.tasks.length} task(s).',
        logs: logs,
      );

      // -----------------------------------------------------------------------
      // STEP 4: Local SQLite Persistence
      // -----------------------------------------------------------------------
      yield PipelineStatus(
        stage: ProcessingStage.persisting,
        progress: 0.95,
        statusText: 'Saving note and action items to encrypted local SQLite...',
        logs: logs,
      );

      final newNote = Note(
        id: noteId,
        title: parseResult.title,
        summary: parseResult.summary,
        fullTranscript: transcript,
        audioFilePath: wavPath,
        durationSeconds: recordedDurationSeconds > 0 ? recordedDurationSeconds : 15,
        createdAt: DateTime.now(),
        tasks: parseResult.tasks,
      );

      await AppDatabase.instance.insertNote(newNote, parseResult.tasks);
      addLog('SQLite: Successfully committed Note [id=$noteId] & ${parseResult.tasks.length} tasks');

      // Final Completed state
      yield PipelineStatus(
        stage: ProcessingStage.completed,
        progress: 1.0,
        statusText: 'Processing Complete! Ready for review.',
        logs: logs,
        createdNote: newNote,
        createdTasks: parseResult.tasks,
      );
    } catch (e, stack) {
      debugPrint('Pipeline execution failure: $e\n$stack');
      addLog('ERROR: ${e.toString()}');
      yield PipelineStatus(
        stage: ProcessingStage.error,
        progress: 1.0,
        statusText: 'Processing Failed: ${e.toString()}',
        logs: logs,
        errorMessage: e.toString(),
      );
    }
  }
}
