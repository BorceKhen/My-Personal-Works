import 'package:intl/intl.dart';
import 'task_model.dart';
import 'transcript_segment.dart';

/// Represents a voice note record in the offline SQLite database.
class Note {
  final String id;
  final String title;
  final String? summary;
  final String fullTranscript;
  final String audioFilePath;
  final int durationSeconds;
  final DateTime createdAt;
  final List<TaskItem> tasks;

  Note({
    required this.id,
    required this.title,
    this.summary,
    required this.fullTranscript,
    required this.audioFilePath,
    required this.durationSeconds,
    required this.createdAt,
    this.tasks = const [],
  });

  /// Factory constructor for SQLite row map
  factory Note.fromMap(Map<String, dynamic> map, {List<TaskItem> tasks = const []}) {
    DateTime parsedDate;
    final createdRaw = map['created_at'];
    if (createdRaw is String) {
      parsedDate = DateTime.tryParse(createdRaw) ?? DateTime.now();
    } else {
      parsedDate = DateTime.now();
    }

    return Note(
      id: map['id'] as String,
      title: map['title'] as String,
      summary: map['summary'] as String?,
      fullTranscript: map['full_transcript'] as String,
      audioFilePath: map['audio_file_path'] as String,
      durationSeconds: map['duration_seconds'] as int? ?? 0,
      createdAt: parsedDate,
      tasks: tasks,
    );
  }

  /// Serialize to SQLite row map
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'title': title,
      'summary': summary,
      'full_transcript': fullTranscript,
      'audio_file_path': audioFilePath,
      'duration_seconds': durationSeconds,
      'created_at': createdAt.toIso8601String(),
    };
  }

  Note copyWith({
    String? id,
    String? title,
    String? summary,
    String? fullTranscript,
    String? audioFilePath,
    int? durationSeconds,
    DateTime? createdAt,
    List<TaskItem>? tasks,
  }) {
    return Note(
      id: id ?? this.id,
      title: title ?? this.title,
      summary: summary ?? this.summary,
      fullTranscript: fullTranscript ?? this.fullTranscript,
      audioFilePath: audioFilePath ?? this.audioFilePath,
      durationSeconds: durationSeconds ?? this.durationSeconds,
      createdAt: createdAt ?? this.createdAt,
      tasks: tasks ?? this.tasks,
    );
  }

  /// Human-friendly duration tag (e.g. "03:45" or "00:15")
  String get formattedDuration {
    final minutes = (durationSeconds ~/ 60).toString().padLeft(2, '0');
    final seconds = (durationSeconds % 60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }

  /// Formatted creation time (e.g. "Oct 14, 4:20 PM")
  String get formattedCreatedAt {
    return DateFormat('MMM d, h:mm a').format(createdAt);
  }

  /// Total count of pending (uncompleted) action items
  int get pendingTasksCount {
    return tasks.where((t) => !t.isCompleted).length;
  }

  /// Total count of tasks synced to system calendar
  int get syncedTasksCount {
    return tasks.where((t) => t.syncedToCalendar).length;
  }

  /// Parse the full transcript into timestamped segments for interactive scrubbing
  List<TranscriptSegment> get segments {
    if (fullTranscript.trim().isEmpty) return [];

    final sentences = fullTranscript
        .split(RegExp(r'(?<=[.!?])\s+'))
        .where((s) => s.trim().isNotEmpty)
        .toList();

    if (sentences.isEmpty) {
      return [
        TranscriptSegment(
          timestamp: Duration.zero,
          text: fullTranscript,
          isActionable: false,
        )
      ];
    }

    final double stepSeconds = durationSeconds > 0
        ? (durationSeconds / sentences.length)
        : 3.5;

    final actionVerbs = RegExp(
      r'\b(need to|must|remind me to|submit|send|prepare|call|schedule|finish|follow up|complete|buy|review|email|draft)\b',
      caseSensitive: false,
    );

    return List.generate(sentences.length, (index) {
      final text = sentences[index].trim();
      final secOffset = (index * stepSeconds).floor();
      return TranscriptSegment(
        timestamp: Duration(seconds: secOffset),
        text: text,
        isActionable: actionVerbs.hasMatch(text),
      );
    });
  }
}
