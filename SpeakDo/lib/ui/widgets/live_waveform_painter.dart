import 'dart:math';
import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

/// Animated live audio waveform visualizer
class LiveWaveformWidget extends StatefulWidget {
  final bool isRecording;
  final bool isPlaying;
  final double height;
  final int barCount;
  final Color? barColor;

  const LiveWaveformWidget({
    super.key,
    this.isRecording = false,
    this.isPlaying = false,
    this.height = 64,
    this.barCount = 36,
    this.barColor,
  });

  @override
  State<LiveWaveformWidget> createState() => _LiveWaveformWidgetState();
}

class _LiveWaveformWidgetState extends State<LiveWaveformWidget> with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  final List<double> _baseHeights = [];
  final Random _random = Random(42);

  @override
  void initState() {
    super.initState();
    for (int i = 0; i < widget.barCount; i++) {
      _baseHeights.add(0.2 + _random.nextDouble() * 0.8);
    }

    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    );

    if (widget.isRecording || widget.isPlaying) {
      _controller.repeat();
    }
  }

  @override
  void didUpdateWidget(covariant LiveWaveformWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    if ((widget.isRecording || widget.isPlaying) && !_controller.isAnimating) {
      _controller.repeat();
    } else if (!widget.isRecording && !widget.isPlaying && _controller.isAnimating) {
      _controller.stop();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final activeColor = widget.barColor ??
        (widget.isRecording ? AppTheme.accentAmber : AppTheme.infoIndigo);

    return SizedBox(
      height: widget.height,
      width: double.infinity,
      child: AnimatedBuilder(
        animation: _controller,
        builder: (context, child) {
          return CustomPaint(
            painter: _WaveformPainter(
              animationValue: _controller.value,
              baseHeights: _baseHeights,
              color: activeColor,
              isActive: widget.isRecording || widget.isPlaying,
            ),
          );
        },
      ),
    );
  }
}

class _WaveformPainter extends CustomPainter {
  final double animationValue;
  final List<double> baseHeights;
  final Color color;
  final bool isActive;

  _WaveformPainter({
    required this.animationValue,
    required this.baseHeights,
    required this.color,
    required this.isActive,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final barCount = baseHeights.length;
    final totalSpacing = size.width * 0.35;
    final barWidth = (size.width - totalSpacing) / barCount;
    final spacing = totalSpacing / (barCount - 1);

    final paint = Paint()
      ..color = color
      ..strokeCap = StrokeCap.round
      ..style = PaintingStyle.fill;

    for (int i = 0; i < barCount; i++) {
      double scale = baseHeights[i];

      if (isActive) {
        // Continuous wave modulation
        final phase = (animationValue * 2 * pi) + (i * 0.25);
        final modulation = 0.5 + 0.5 * sin(phase);
        scale = 0.2 + (baseHeights[i] * 0.8 * modulation);
      } else {
        scale = baseHeights[i] * 0.35;
      }

      final barH = max(4.0, size.height * scale);
      final left = i * (barWidth + spacing);
      final top = (size.height - barH) / 2;

      final rect = RRect.fromRectAndRadius(
        Rect.fromLTWH(left, top, barWidth, barH),
        const Radius.circular(3),
      );

      // Subtle gradient effect for center bars
      if (isActive) {
        final distFromCenter = (i - (barCount / 2)).abs() / (barCount / 2);
        paint.color = color.withValues(alpha: 1.0 - (distFromCenter * 0.45));
      } else {
        paint.color = color.withValues(alpha: 0.35);
      }

      canvas.drawRRect(rect, paint);
    }
  }

  @override
  bool shouldRepaint(covariant _WaveformPainter oldDelegate) {
    return oldDelegate.animationValue != animationValue ||
        oldDelegate.isActive != isActive ||
        oldDelegate.color != color;
  }
}
