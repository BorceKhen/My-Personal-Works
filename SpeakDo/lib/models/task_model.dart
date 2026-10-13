import 'package:intl/intl.dart';

/// Represents an extracted actionable item or deadline.
class TaskItem {
  final String id;
  final String noteId;
  final String taskText;
  final DateTime? dueDate;
  final bool isCompleted;
  final bool syncedToCalendar;

  const TaskItem({
    required this.id,
    required this.noteId,
    required this.taskText,
    this.dueDate,
    this.isCompleted = false,
    this.syncedToCalendar = false,
  });

  /// Factory from SQLite map
  factory TaskItem.fromMap(Map<String, dynamic> map) {
    DateTime? parsedDueDate;
    if (map['due_date'] != null && map['due_date'].toString().isNotEmpty) {
      parsedDueDate = DateTime.tryParse(map['due_date'].toString());
    }

    return TaskItem(
      id: map['id'] as String,
      noteId: map['note_id'] as String,
      taskText: map['task_text'] as String,
      dueDate: parsedDueDate,
      isCompleted: (map['is_completed'] as int? ?? 0) == 1,
      syncedToCalendar: (map['synced_to_calendar'] as int? ?? 0) == 1,
    );
  }

  /// Convert to SQLite map
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'note_id': noteId,
      'task_text': taskText,
      'due_date': dueDate?.toIso8601String(),
      'is_completed': isCompleted ? 1 : 0,
      'synced_to_calendar': syncedToCalendar ? 1 : 0,
    };
  }

  TaskItem copyWith({
    String? id,
    String? noteId,
    String? taskText,
    DateTime? dueDate,
    bool? isCompleted,
    bool? syncedToCalendar,
  }) {
    return TaskItem(
      id: id ?? this.id,
      noteId: noteId ?? this.noteId,
      taskText: taskText ?? this.taskText,
      dueDate: dueDate ?? this.dueDate,
      isCompleted: isCompleted ?? this.isCompleted,
      syncedToCalendar: syncedToCalendar ?? this.syncedToCalendar,
    );
  }

  /// Formatted due date label (e.g. "Due Today, 5:00 PM")
  String get formattedDueDate {
    if (dueDate == null) return 'No deadline specified';
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final dueDay = DateTime(dueDate!.year, dueDate!.month, dueDate!.day);

    final timeStr = DateFormat('h:mm a').format(dueDate!);

    if (dueDay.isAtSameMomentAs(today)) {
      return 'Due Today, $timeStr';
    } else if (dueDay.isAtSameMomentAs(today.add(const Duration(days: 1)))) {
      return 'Due Tomorrow, $timeStr';
    } else {
      return 'Due ${DateFormat('MMM d, h:mm a').format(dueDate!)}';
    }
  }
}
