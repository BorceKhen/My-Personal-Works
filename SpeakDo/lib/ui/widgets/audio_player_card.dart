import 'package:flutter/material.dart';
import '../../services/audio_player_service.dart';
import '../../theme/app_theme.dart';
import 'live_waveform_painter.dart';

/// Interactive Audio Player Card widget with scrubbing, speed toggling, and skip buttons.
class AudioPlayerCard extends StatelessWidget {
  final AudioPlayerService player;

  const AudioPlayerCard({
    super.key,
    required this.player,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: player,
      builder: (context, _) {
        final totalMs = player.duration.inMilliseconds;
        final currentMs = player.position.inMilliseconds.clamp(0, totalMs > 0 ? totalMs : 1);
        final sliderValue = totalMs > 0 ? (currentMs / totalMs) : 0.0;

        return Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            color: AppTheme.surfaceCard,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: AppTheme.subtleBorder),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.25),
                blurRadius: 16,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Waveform representation while playing
              Container(
                height: 48,
                padding: const EdgeInsets.symmetric(horizontal: 8),
                decoration: BoxDecoration(
                  color: AppTheme.slateBackground.withValues(alpha: 0.6),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: LiveWaveformWidget(
                  isPlaying: player.isPlaying,
                  height: 44,
                  barCount: 32,
                  barColor: AppTheme.accentAmber,
                ),
              ),
              const SizedBox(height: 12),

              // Slider scrub bar
              SliderTheme(
                data: SliderTheme.of(context).copyWith(
                  thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 7),
                  trackHeight: 4,
                  overlayShape: const RoundSliderOverlayShape(overlayRadius: 14),
                ),
                child: Slider(
                  value: sliderValue.clamp(0.0, 1.0),
                  onChanged: (val) {
                    final targetMs = (val * totalMs).round();
                    player.seek(Duration(milliseconds: targetMs));
                  },
                ),
              ),

              // Timestamps: Current time & Total duration
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      player.formattedPosition,
                      style: const TextStyle(
                        fontSize: 12,
                        fontFeatures: [FontFeature.tabularFigures()],
                        color: AppTheme.primaryText,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    Text(
                      player.formattedDuration,
                      style: const TextStyle(
                        fontSize: 12,
                        fontFeatures: [FontFeature.tabularFigures()],
                        color: AppTheme.mutedText,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),

              // Control buttons: Skip 5s, Play/Pause, Skip 5s, Speed toggle
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  // Rewind 5s
                  IconButton(
                    onPressed: () => player.skipBackward(5),
                    icon: const Icon(Icons.replay_5_rounded),
                    color: AppTheme.mutedText,
                    iconSize: 26,
                    tooltip: 'Rewind 5s',
                  ),

                  // Main Play / Pause Circle Button
                  InkWell(
                    onTap: () => player.togglePlayPause(),
                    borderRadius: BorderRadius.circular(50),
                    child: Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(
                          colors: [AppTheme.accentAmber, AppTheme.accentAmberDark],
                        ),
                        shape: BoxShape.circle,
                        boxShadow: [
                          BoxShadow(
                            color: AppTheme.accentAmber.withValues(alpha: 0.35),
                            blurRadius: 12,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                      child: Icon(
                        player.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded,
                        color: Colors.black,
                        size: 30,
                      ),
                    ),
                  ),

                  // Fast forward 5s
                  IconButton(
                    onPressed: () => player.skipForward(5),
                    icon: const Icon(Icons.forward_5_rounded),
                    color: AppTheme.mutedText,
                    iconSize: 26,
                    tooltip: 'Forward 5s',
                  ),

                  // Speed multiplier toggle
                  InkWell(
                    onTap: () => player.cycleSpeed(),
                    borderRadius: BorderRadius.circular(10),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                      decoration: BoxDecoration(
                        color: AppTheme.slateBackground,
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: AppTheme.subtleBorder),
                      ),
                      child: Text(
                        '${player.speed}x',
                        style: const TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.bold,
                          color: AppTheme.accentAmber,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        );
      },
    );
  }
}
