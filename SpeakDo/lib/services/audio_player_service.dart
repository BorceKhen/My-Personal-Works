import 'dart:async';
import 'package:flutter/foundation.dart';

/// Interactive Audio Player Controller supporting playback, scrubbing,
/// timestamp jumping, and variable speed multipliers.
class AudioPlayerService extends ChangeNotifier {
  Duration _duration = const Duration(seconds: 30);
  Duration _position = Duration.zero;
  bool _isPlaying = false;
  double _speed = 1.0;
  Timer? _playbackTimer;

  Duration get duration => _duration;
  Duration get position => _position;
  bool get isPlaying => _isPlaying;
  double get speed => _speed;

  /// Load audio for a given duration
  void loadAudio({required Duration duration}) {
    _playbackTimer?.cancel();
    _duration = duration.inSeconds > 0 ? duration : const Duration(seconds: 15);
    _position = Duration.zero;
    _isPlaying = false;
    _speed = 1.0;
    notifyListeners();
  }

  /// Start or resume playback
  void play() {
    if (_isPlaying) return;
    _isPlaying = true;
    _startTimer();
    notifyListeners();
  }

  /// Pause playback
  void pause() {
    if (!_isPlaying) return;
    _isPlaying = false;
    _playbackTimer?.cancel();
    notifyListeners();
  }

  /// Toggle play / pause state
  void togglePlayPause() {
    if (_isPlaying) {
      pause();
    } else {
      play();
    }
  }

  /// Seek to a specific timestamp/duration (e.g. from transcript [00:15])
  void seek(Duration newPosition) {
    if (newPosition < Duration.zero) {
      _position = Duration.zero;
    } else if (newPosition > _duration) {
      _position = _duration;
    } else {
      _position = newPosition;
    }
    notifyListeners();
  }

  /// Skip backward by [seconds]
  void skipBackward([int seconds = 5]) {
    seek(_position - Duration(seconds: seconds));
  }

  /// Skip forward by [seconds]
  void skipForward([int seconds = 5]) {
    seek(_position + Duration(seconds: seconds));
  }

  /// Cycle or set playback speed (1.0x, 1.25x, 1.5x, 2.0x)
  void setSpeed(double newSpeed) {
    _speed = newSpeed;
    if (_isPlaying) {
      _startTimer(); // Restart timer with new tick rate
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

  void _startTimer() {
    _playbackTimer?.cancel();
    // 100ms standard tick, adjusted by speed
    final intervalMs = (100 / _speed).round();
    _playbackTimer = Timer.periodic(Duration(milliseconds: intervalMs), (timer) {
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

  /// Human formatted position e.g. "01:23"
  String get formattedPosition => _formatDuration(_position);

  /// Human formatted duration e.g. "03:45"
  String get formattedDuration => _formatDuration(_duration);

  static String _formatDuration(Duration d) {
    final minutes = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final seconds = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }

  @override
  void dispose() {
    _playbackTimer?.cancel();
    super.dispose();
  }
}
