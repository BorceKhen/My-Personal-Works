import 'dart:async';
import 'dart:io';
import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/foundation.dart';

/// Real Hardware Audio Player Controller using audioplayers.
/// Plays real recorded voice audio through the device speaker with scrubbing and variable speeds.
class AudioPlayerService extends ChangeNotifier {
  final AudioPlayer _player = AudioPlayer();

  Duration _duration = const Duration(seconds: 15);
  Duration _position = Duration.zero;
  bool _isPlaying = false;
  double _speed = 1.0;
  String _currentAudioPath = '';
  bool _isRealAudio = false;

  StreamSubscription? _posSub;
  StreamSubscription? _durSub;
  StreamSubscription? _stateSub;
  StreamSubscription? _completeSub;
  Timer? _fallbackTimer;

  Duration get duration => _duration;
  Duration get position => _position;
  bool get isPlaying => _isPlaying;
  double get speed => _speed;
  String get audioPath => _currentAudioPath;
  bool get isRealAudio => _isRealAudio;

  AudioPlayerService() {
    _initPlayerListeners();
  }

  void _initPlayerListeners() {
    _posSub = _player.onPositionChanged.listen((pos) {
      _position = pos;
      notifyListeners();
    });

    _durSub = _player.onDurationChanged.listen((dur) {
      if (dur.inMilliseconds > 0) {
        _duration = dur;
        notifyListeners();
      }
    });

    _stateSub = _player.onPlayerStateChanged.listen((state) {
      _isPlaying = (state == PlayerState.playing);
      notifyListeners();
    });

    _completeSub = _player.onPlayerComplete.listen((_) {
      _position = Duration.zero;
      _isPlaying = false;
      notifyListeners();
    });
  }

  /// Load audio from file path with speaker output routing
  Future<void> loadAudio({
    required String audioPath,
    required Duration duration,
  }) async {
    _fallbackTimer?.cancel();
    _currentAudioPath = audioPath;
    _position = Duration.zero;
    _isPlaying = false;
    _speed = 1.0;
    _duration = duration.inSeconds > 0 ? duration : const Duration(seconds: 15);

    try {
      if (!kIsWeb && audioPath.isNotEmpty && File(audioPath).existsSync()) {
        _isRealAudio = true;

        // Route audio explicitly to loudspeaker
        await _player.setAudioContext(AudioContext(
          android: const AudioContextAndroid(
            isSpeakerphoneOn: true,
            stayAwake: true,
            contentType: AndroidContentType.speech,
            usageType: AndroidUsageType.media,
            audioFocus: AndroidAudioFocus.gain,
          ),
          iOS: AudioContextIOS(
            category: AVAudioSessionCategory.playback,
            options: const {
              AVAudioSessionOptions.defaultToSpeaker,
            },
          ),
        ));

        await _player.setSource(DeviceFileSource(audioPath));
      } else {
        _isRealAudio = false;
      }
    } catch (e) {
      debugPrint('[AudioPlayerService] Error setting audio source: $e');
      _isRealAudio = false;
    }

    notifyListeners();
  }

  /// Start or resume real audio playback
  Future<void> play() async {
    if (_isPlaying) return;

    if (_isRealAudio) {
      try {
        await _player.setPlaybackRate(_speed);
        await _player.resume();
        _isPlaying = true;
        notifyListeners();
        return;
      } catch (e) {
        debugPrint('[AudioPlayerService] Play error: $e');
      }
    }

    // Fallback simulation timer for web or virtual test audio
    _isPlaying = true;
    _startFallbackTimer();
    notifyListeners();
  }

  /// Pause audio playback
  Future<void> pause() async {
    if (!_isPlaying) return;

    if (_isRealAudio) {
      try {
        await _player.pause();
        _isPlaying = false;
        notifyListeners();
        return;
      } catch (e) {
        debugPrint('[AudioPlayerService] Pause error: $e');
      }
    }

    _isPlaying = false;
    _fallbackTimer?.cancel();
    notifyListeners();
  }

  /// Toggle play / pause
  Future<void> togglePlayPause() async {
    if (_isPlaying) {
      await pause();
    } else {
      await play();
    }
  }

  /// Seek to duration
  Future<void> seek(Duration newPosition) async {
    final clamped = newPosition < Duration.zero
        ? Duration.zero
        : newPosition > _duration
            ? _duration
            : newPosition;

    _position = clamped;
    if (_isRealAudio) {
      try {
        await _player.seek(clamped);
      } catch (e) {
        debugPrint('[AudioPlayerService] Seek error: $e');
      }
    }
    notifyListeners();
  }

  void skipBackward([int seconds = 5]) {
    seek(_position - Duration(seconds: seconds));
  }

  void skipForward([int seconds = 5]) {
    seek(_position + Duration(seconds: seconds));
  }

  Future<void> setSpeed(double newSpeed) async {
    _speed = newSpeed;
    if (_isRealAudio) {
      try {
        await _player.setPlaybackRate(newSpeed);
      } catch (e) {
        debugPrint('[AudioPlayerService] Speed error: $e');
      }
    } else if (_isPlaying) {
      _startFallbackTimer();
    }
    notifyListeners();
  }

  void cycleSpeed() {
    if (_speed == 1.0) {
      setSpeed(1.25);
    } else if (_speed == 1.25) {
      setSpeed(1.5);
    } else if (_speed == 1.5) {
      setSpeed(2.0);
    } else {
      setSpeed(1.0);
    }
  }

  void _startFallbackTimer() {
    _fallbackTimer?.cancel();
    final intervalMs = (100 / _speed).round();
    _fallbackTimer = Timer.periodic(Duration(milliseconds: intervalMs), (timer) {
      final nextMs = _position.inMilliseconds + 100;
      if (nextMs >= _duration.inMilliseconds) {
        _position = _duration;
        _isPlaying = false;
        timer.cancel();
        notifyListeners();
      } else {
        _position = Duration(milliseconds: nextMs);
        notifyListeners();
      }
    });
  }

  String get formattedPosition => _formatDuration(_position);
  String get formattedDuration => _formatDuration(_duration);

  static String _formatDuration(Duration d) {
    final minutes = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final seconds = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }

  @override
  void dispose() {
    _posSub?.cancel();
    _durSub?.cancel();
    _stateSub?.cancel();
    _completeSub?.cancel();
    _fallbackTimer?.cancel();
    _player.dispose();
    super.dispose();
  }
}
