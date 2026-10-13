import 'package:flutter/material.dart';
import '../../models/task_model.dart';
import '../../theme/app_theme.dart';

/// Task Item Tile with interactive checkbox, due date badge, and calendar sync button.
class TaskItemTile extends StatelessWidget {
  final TaskItem task;
  final ValueChanged<bool> onToggleComplete;
  final VoidCallback onSyncCalendar;
  final VoidCallback? onDelete;

  const TaskItemTile({
    super.key,
    required this.task,
    required this.onToggleComplete,
    required this.onSyncCalendar,
    this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppTheme.surfaceCard,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: task.isCompleted
              ? AppTheme.successEmerald.withValues(alpha: 0.3)
              : AppTheme.subtleBorder,
          width: 1,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Custom styled interactive checkbox
              InkWell(
                onTap: () => onToggleComplete(!task.isCompleted),
                borderRadius: BorderRadius.circular(8),
                child: Container(
                  width: 24,
                  height: 24,
                  margin: const EdgeInsets.only(top: 2, right: 12),
                  decoration: BoxDecoration(
                    color: task.isCompleted
                        ? AppTheme.successEmerald
                        : Colors.transparent,
                    borderRadius: BorderRadius.circular(7),
                    border: Border.all(
                      color: task.isCompleted
                          ? AppTheme.successEmerald
                          : AppTheme.mutedText,
                      width: 2,
                    ),
                  ),
                  child: task.isCompleted
                      ? const Icon(Icons.check, size: 16, color: Colors.white)
                      : null,
                ),
              ),

              // Task text
              Expanded(
                child: Text(
                  task.taskText,
                  style: TextStyle(
                    fontSize: 14.5,
                    fontWeight: FontWeight.w500,
                    color: task.isCompleted
                        ? AppTheme.mutedText
                        : AppTheme.primaryText,
                    decoration: task.isCompleted
                        ? TextDecoration.lineThrough
                        : TextDecoration.none,
                    decorationColor: AppTheme.mutedText,
                  ),
                ),
              ),

              if (onDelete != null)
                IconButton(
                  visualDensity: VisualDensity.compact,
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                  icon: const Icon(Icons.close_rounded, size: 16, color: AppTheme.mutedText),
                  onPressed: onDelete,
                  tooltip: 'Delete Task',
                ),
            ],
          ),
          const SizedBox(height: 10),

          // Bottom metadata: Due date badge & Sync to Calendar button
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              // Due date badge
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: task.dueDate != null
                      ? AppTheme.accentAmber.withValues(alpha: 0.12)
                      : AppTheme.slateBackground,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(
                    color: task.dueDate != null
                        ? AppTheme.accentAmber.withValues(alpha: 0.3)
                        : AppTheme.subtleBorder,
                  ),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.schedule_rounded,
                      size: 13,
                      color: task.dueDate != null
                          ? AppTheme.accentAmber
                          : AppTheme.mutedText,
                    ),
                    const SizedBox(width: 5),
                    Text(
                      task.formattedDueDate,
                      style: TextStyle(
                        fontSize: 11.5,
                        fontWeight: FontWeight.w600,
                        color: task.dueDate != null
                            ? AppTheme.accentAmber
                            : AppTheme.mutedText,
                      ),
                    ),
                  ],
                ),
              ),

              // Sync to Calendar CTA
              InkWell(
                onTap: task.syncedToCalendar ? null : onSyncCalendar,
                borderRadius: BorderRadius.circular(8),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                  decoration: BoxDecoration(
                    color: task.syncedToCalendar
                        ? AppTheme.infoIndigo.withValues(alpha: 0.15)
                        : AppTheme.slateBackground,
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                      color: task.syncedToCalendar
                          ? AppTheme.infoIndigo.withValues(alpha: 0.5)
                          : AppTheme.subtleBorder,
                    ),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        task.syncedToCalendar
                            ? Icons.event_available_rounded
                            : Icons.event_rounded,
                        size: 14,
                        color: task.syncedToCalendar
                            ? AppTheme.infoIndigo
                            : AppTheme.mutedText,
                      ),
                      const SizedBox(width: 5),
                      Text(
                        task.syncedToCalendar ? 'Synced' : 'Sync to Calendar',
                        style: TextStyle(
                          fontSize: 11.5,
                          fontWeight: FontWeight.w600,
                          color: task.syncedToCalendar
                              ? AppTheme.infoIndigo
                              : AppTheme.primaryText,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
