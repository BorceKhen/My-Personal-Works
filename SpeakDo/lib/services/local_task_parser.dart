import 'package:uuid/uuid.dart';
import '../models/task_model.dart';

/// Parsed result returned by LocalTaskParser
class ParsedTranscriptResult {
  final String title;
  final String summary;
  final List<TaskItem> tasks;

  const ParsedTranscriptResult({
    required this.title,
    required this.summary,
    required this.tasks,
  });
}

/// Zero-latency, deterministic offline regex NLP parser.
/// Extracts actionable tasks, deadlines, note titles, and summaries without cloud LLMs.
class LocalTaskParser {
  static const _uuid = Uuid();

  /// Parse raw transcript text into structured title, summary, and action items.
  static Map<String, dynamic> parseTranscript(String transcript, {String noteId = ''}) {
    List<Map<String, dynamic>> rawTasks = [];
    final sentences = transcript
        .split(RegExp(r'(?<=[.!?])\s+'))
        .map((s) => s.trim())
        .where((s) => s.isNotEmpty)
        .toList();

    if (sentences.isEmpty) {
      return {
        'title': 'Quick Voice Note',
        'summary': 'No speech content recorded.',
        'tasks': <Map<String, dynamic>>[],
      };
    }

    // Trigger phrase matchers for actionable items
    final actionPattern = RegExp(
      r'\b(need to|must|remind me to|submit|send|prepare|call|schedule|finish|follow up|complete|buy|review|email|draft)\b\s+(.*)',
      caseSensitive: false,
    );

    // Relative date matchers
    final datePattern = RegExp(
      r'\b(today|tomorrow|next monday|next tuesday|next wednesday|next thursday|next friday|next saturday|next sunday|by \d{1,2}(?::\d{2})?\s*[ap]m|at \d{1,2}(?::\d{2})?\s*[ap]m|in \d+ days?)\b',
      caseSensitive: false,
    );

    int taskIndex = 0;
    for (var sentence in sentences) {
      if (actionPattern.hasMatch(sentence)) {
        final match = actionPattern.firstMatch(sentence);
        String taskText = match?.group(0) ?? sentence;

        // Clean trailing punctuation
        taskText = taskText.replaceAll(RegExp(r'[.!?]+$'), '').trim();
        // Capitalize first letter
        if (taskText.isNotEmpty) {
          taskText = taskText[0].toUpperCase() + taskText.substring(1);
        }

        DateTime? dueDate;
        final dateMatch = datePattern.firstMatch(sentence);
        if (dateMatch != null) {
          dueDate = _resolveRelativeDate(dateMatch.group(0), sentence);
        }

        rawTasks.add({
          'id': '${_uuid.v4()}-$taskIndex',
          'note_id': noteId,
          'task_text': taskText,
          'due_date': dueDate?.toIso8601String(),
          'is_completed': 0,
          'synced_to_calendar': 0,
        });
        taskIndex++;
      }
    }

    final firstSentence = sentences.first;
    final title = firstSentence.length > 40
        ? '${firstSentence.substring(0, 37)}...'
        : firstSentence.replaceAll(RegExp(r'[.!?]+$'), '');

    final summary = sentences.take(2).join(' ');

    return {
      'title': title,
      'summary': summary,
      'tasks': rawTasks,
    };
  }

  /// High-level typed parsing helper returning model entities
  static ParsedTranscriptResult parse(String transcript, {required String noteId}) {
    final rawMap = parseTranscript(transcript, noteId: noteId);
    final rawTasks = rawMap['tasks'] as List<dynamic>;

    final tasks = rawTasks.map((t) {
      return TaskItem.fromMap(t as Map<String, dynamic>);
    }).toList();

    return ParsedTranscriptResult(
      title: rawMap['title'] as String,
      summary: rawMap['summary'] as String,
      tasks: tasks,
    );
  }

  /// Deterministic relative date resolver
  static DateTime? _resolveRelativeDate(String? dateStr, String fullSentence) {
    if (dateStr == null) return null;
    final lower = dateStr.toLowerCase();
    final now = DateTime.now();

    // Parse specific hour if mentioned e.g. "by 5 pm" or "at 2 pm" or "10:30 am"
    int targetHour = 17; // default 5:00 PM
    int targetMinute = 0;

    final timeRegex = RegExp(r'(\d{1,2})(?::(\d{2}))?\s*([ap]m)', caseSensitive: false);
    final timeMatch = timeRegex.firstMatch(fullSentence.toLowerCase());
    if (timeMatch != null) {
      int parsedH = int.tryParse(timeMatch.group(1) ?? '17') ?? 17;
      final parsedM = int.tryParse(timeMatch.group(2) ?? '0') ?? 0;
      final amPm = timeMatch.group(3) ?? 'pm';

      if (amPm == 'pm' && parsedH < 12) parsedH += 12;
      if (amPm == 'am' && parsedH == 12) parsedH = 0;
      targetHour = parsedH;
      targetMinute = parsedM;
    }

    if (lower.contains('today')) {
      return DateTime(now.year, now.month, now.day, targetHour, targetMinute);
    } else if (lower.contains('tomorrow')) {
      final tom = now.add(const Duration(days: 1));
      return DateTime(tom.year, tom.month, tom.day, targetHour, targetMinute);
    } else if (lower.contains('next monday')) {
      return _nextWeekday(DateTime.monday, targetHour, targetMinute);
    } else if (lower.contains('next tuesday')) {
      return _nextWeekday(DateTime.tuesday, targetHour, targetMinute);
    } else if (lower.contains('next wednesday')) {
      return _nextWeekday(DateTime.wednesday, targetHour, targetMinute);
    } else if (lower.contains('next thursday')) {
      return _nextWeekday(DateTime.thursday, targetHour, targetMinute);
    } else if (lower.contains('next friday')) {
      return _nextWeekday(DateTime.friday, targetHour, targetMinute);
    } else if (lower.contains('next saturday')) {
      return _nextWeekday(DateTime.saturday, targetHour, targetMinute);
    } else if (lower.contains('next sunday')) {
      return _nextWeekday(DateTime.sunday, targetHour, targetMinute);
    } else if (lower.contains('in ') && lower.contains('day')) {
      final numMatch = RegExp(r'in (\d+) day').firstMatch(lower);
      final days = int.tryParse(numMatch?.group(1) ?? '2') ?? 2;
      final targetDate = now.add(Duration(days: days));
      return DateTime(targetDate.year, targetDate.month, targetDate.day, targetHour, targetMinute);
    }

    // Default fallback to 2 days ahead at 5:00 PM
    final fallbackDate = now.add(const Duration(days: 2));
    return DateTime(fallbackDate.year, fallbackDate.month, fallbackDate.day, targetHour, targetMinute);
  }

  static DateTime _nextWeekday(int targetWeekday, int hour, int minute) {
    final now = DateTime.now();
    int daysToAdd = (targetWeekday - now.weekday) % 7;
    if (daysToAdd <= 0) {
      daysToAdd += 7;
    }
    final nextDate = now.add(Duration(days: daysToAdd));
    return DateTime(nextDate.year, nextDate.month, nextDate.day, hour, minute);
  }
}
