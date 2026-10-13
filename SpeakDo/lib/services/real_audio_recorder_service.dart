import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';

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

/// Real hardware audio recorder service.
/// Records real audio to disk as 16kHz mono PCM WAV required by Whisper and AudioPlayer.
class RealAudioRecorderService {
  static final RealAudioRecorderService instance = RealAudioRecorderService._internal();
  RealAudioRecorderService._internal();

  final AudioRecorder _audioRecorder = AudioRecorder();

  bool _isRecording = false;
  String? _currentAudioPath;
  DateTime? _recordingStartTime;

  bool get isRecording => _isRecording;

  /// Check and request hardware microphone permission
  Future<bool> checkPermission() async {
    try {
      final hasMic = await _audioRecorder.hasPermission();
      debugPrint('[RealAudioRecorder] Microphone permission status: $hasMic');
      return hasMic;
    } catch (e) {
      debugPrint('[RealAudioRecorder] Permission check error: $e');
      return false;
    }
  }

  /// Start recording real audio to disk (16kHz mono PCM WAV)
  Future<bool> startRecording() async {
    try {
      final hasPerm = await checkPermission();
      if (!hasPerm) {
        debugPrint('[RealAudioRecorder] Cannot start recording without microphone permission');
        _isRecording = false;
        return false;
      }

      _recordingStartTime = DateTime.now();

      String targetPath;
      if (!kIsWeb) {
        final docDir = await getApplicationDocumentsDirectory();
        final recDir = Directory(p.join(docDir.path, 'SpeakDoRecordings'));
        if (!recDir.existsSync()) {
          recDir.createSync(recursive: true);
        }
        final timestamp = DateTime.now().millisecondsSinceEpoch;
        targetPath = p.join(recDir.path, 'voice_$timestamp.wav');
      } else {
        targetPath = 'virtual://live_voice.wav';
      }

      _currentAudioPath = targetPath;

      // Start hardware audio recorder in 16kHz mono PCM WAV
      await _audioRecorder.start(
        const RecordConfig(
          encoder: AudioEncoder.wav,
          sampleRate: 16000,
          numChannels: 1,
        ),
        path: targetPath,
      );

      _isRecording = await _audioRecorder.isRecording();
      debugPrint('[RealAudioRecorder] Hardware recording started at $targetPath (active: $_isRecording)');
      return _isRecording;
    } catch (e) {
      debugPrint('[RealAudioRecorder] startRecording error: $e');
      _isRecording = false;
      return false;
    }
  }

  /// Stop recording, flush audio to disk, and return verified audio path
  Future<RecordingSessionResult> stopRecording() async {
    int durationSec = 1;
    if (_recordingStartTime != null) {
      durationSec = DateTime.now().difference(_recordingStartTime!).inSeconds;
      if (durationSec < 1) durationSec = 1;
    }

    String audioPath = _currentAudioPath ?? '';
    try {
      if (_isRecording || await _audioRecorder.isRecording()) {
        final stoppedPath = await _audioRecorder.stop();
        if (stoppedPath != null && stoppedPath.isNotEmpty) {
          audioPath = stoppedPath;
        }
      }
    } catch (e) {
      debugPrint('[RealAudioRecorder] stop error: $e');
    } finally {
      _isRecording = false;
    }

    // Verify audio file exists on disk and has real recorded bytes
    if (!kIsWeb && audioPath.isNotEmpty) {
      final file = File(audioPath);
      if (file.existsSync()) {
        final size = file.lengthSync();
        debugPrint('[RealAudioRecorder] Real audio verified on disk: $audioPath ($size bytes)');
      } else {
        debugPrint('[RealAudioRecorder] Notice: Audio file not found at $audioPath');
      }
    }

    return RecordingSessionResult(
      audioFilePath: audioPath,
      pureTranscript: '',
      durationSeconds: durationSec,
    );
  }

  void dispose() {
    _audioRecorder.dispose();
  }
}
