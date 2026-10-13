import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';
import 'package:speech_to_text/speech_to_text.dart';

class RecordingSessionResult {
  final String audioFilePath;
  final String pureTranscript;
  final int durationSeconds;

  const RecordingSessionResult({
    required this.audioFilePath,
    required this.pureTranscript,
    required this.durationSeconds,
  });
}

/// Real hardware audio recorder & live pure speech-to-text service.
/// Records real audio to disk and transcribes actual spoken words.
class RealAudioRecorderService {
  static final RealAudioRecorderService instance = RealAudioRecorderService._internal();
  RealAudioRecorderService._internal();

  final AudioRecorder _audioRecorder = AudioRecorder();
  final SpeechToText _speechToText = SpeechToText();

  bool _isSttAvailable = false;
  bool _isRecording = false;
  String _currentLiveWords = '';
  String? _currentAudioPath;
  DateTime? _recordingStartTime;

  bool get isRecording => _isRecording;
  String get currentLiveWords => _currentLiveWords;

  /// Request permissions and initialize hardware mic & speech engine
  Future<bool> initialize() async {
    try {
      final hasMic = await _audioRecorder.hasPermission();
      if (!hasMic) {
        debugPrint('[RealAudioRecorder] Microphone permission not granted');
      }

      _isSttAvailable = await _speechToText.initialize(
        onError: (val) => debugPrint('[RealAudioRecorder STT Error] $val'),
        onStatus: (val) => debugPrint('[RealAudioRecorder STT Status] $val'),
      );

      return true;
    } catch (e) {
      debugPrint('[RealAudioRecorder] Init error: $e');
      return false;
    }
  }

  /// Start recording real audio to disk and listening for speech words
  Future<bool> startRecording({void Function(String liveWords)? onLiveSpeech}) async {
    try {
      await initialize();

      _currentLiveWords = '';
      _recordingStartTime = DateTime.now();

      String targetPath;
      if (!kIsWeb) {
        final docDir = await getApplicationDocumentsDirectory();
        final recDir = Directory(p.join(docDir.path, 'SpeakDoRecordings'));
        if (!recDir.existsSync()) {
          recDir.createSync(recursive: true);
        }
        final timestamp = DateTime.now().millisecondsSinceEpoch;
        targetPath = p.join(recDir.path, 'voice_$timestamp.m4a');
      } else {
        targetPath = 'virtual://live_voice.m4a';
      }

      _currentAudioPath = targetPath;

      // Start hardware audio recorder
      if (await _audioRecorder.hasPermission()) {
        await _audioRecorder.start(
          const RecordConfig(
            encoder: AudioEncoder.aacLc,
            bitRate: 128000,
            sampleRate: 44100,
            numChannels: 1,
          ),
          path: targetPath,
        );
      }

      // Start live Speech-To-Text if engine is available
      if (_isSttAvailable) {
        await _speechToText.listen(
          onResult: (result) {
            _currentLiveWords = result.recognizedWords;
            onLiveSpeech?.call(_currentLiveWords);
          },
          listenOptions: SpeechListenOptions(
            partialResults: true,
            cancelOnError: false,
            listenMode: ListenMode.dictation,
            listenFor: const Duration(minutes: 30),
            pauseFor: const Duration(seconds: 10),
          ),
        );
      }

      _isRecording = true;
      return true;
    } catch (e) {
      debugPrint('[RealAudioRecorder] startRecording error: $e');
      _isRecording = false;
      return false;
    }
  }

  /// Stop recording, flush audio to disk, and return real audio path & pure transcription
  Future<RecordingSessionResult> stopRecording() async {
    int durationSec = 1;
    if (_recordingStartTime != null) {
      durationSec = DateTime.now().difference(_recordingStartTime!).inSeconds;
      if (durationSec < 1) durationSec = 1;
    }

    String audioPath = _currentAudioPath ?? '';
    try {
      if (_isRecording) {
        final stoppedPath = await _audioRecorder.stop();
        if (stoppedPath != null && stoppedPath.isNotEmpty) {
          audioPath = stoppedPath;
        }
      }

      if (_speechToText.isListening) {
        await _speechToText.stop();
      }
    } catch (e) {
      debugPrint('[RealAudioRecorder] stop error: $e');
    } finally {
      _isRecording = false;
    }

    // Verify audio file exists on disk
    if (!kIsWeb && audioPath.isNotEmpty) {
      final file = File(audioPath);
      if (file.existsSync()) {
        debugPrint('[RealAudioRecorder] Real audio saved at $audioPath (${file.lengthSync()} bytes)');
      }
    }

    final pureText = _currentLiveWords.trim();

    return RecordingSessionResult(
      audioFilePath: audioPath,
      pureTranscript: pureText,
      durationSeconds: durationSec,
    );
  }

  void dispose() {
    _audioRecorder.dispose();
    _speechToText.stop();
  }
}
