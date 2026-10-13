/// Represents a timestamped segment of the transcribed speech.
class TranscriptSegment {
  final Duration timestamp;
  final String text;
  final bool isActionable;

  const TranscriptSegment({
    required this.timestamp,
    required this.text,
    this.isActionable = false,
  });

  /// Format as [MM:SS]
  String get formattedTime {
    final minutes = timestamp.inMinutes.remainder(60).toString().padLeft(2, '0');
    final seconds = timestamp.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '[$minutes:$seconds]';
  }
}
