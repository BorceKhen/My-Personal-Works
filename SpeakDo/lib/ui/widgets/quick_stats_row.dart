import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

/// Quick statistics pill row for the main dashboard
class QuickStatsRow extends StatelessWidget {
  final int totalNotes;
  final int pendingTasks;
  final int completedTasks;
  final int syncedTasks;

  const QuickStatsRow({
    super.key,
    required this.totalNotes,
    required this.pendingTasks,
    required this.completedTasks,
    required this.syncedTasks,
  });

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      physics: const BouncingScrollPhysics(),
      child: Row(
        children: [
          _buildStatPill(
            icon: Icons.mic_rounded,
            label: 'Total Notes',
            value: '$totalNotes',
            color: AppTheme.accentAmber,
          ),
          const SizedBox(width: 10),
          _buildStatPill(
            icon: Icons.pending_actions_rounded,
            label: 'Pending Tasks',
            value: '$pendingTasks',
            color: pendingTasks > 0 ? const Color(0xFFF97316) : AppTheme.mutedText,
          ),
          const SizedBox(width: 10),
          _buildStatPill(
            icon: Icons.task_alt_rounded,
            label: 'Completed',
            value: '$completedTasks',
            color: AppTheme.successEmerald,
          ),
          const SizedBox(width: 10),
          _buildStatPill(
            icon: Icons.calendar_month_rounded,
            label: 'Calendar Synced',
            value: '$syncedTasks',
            color: AppTheme.infoIndigo,
          ),
        ],
      ),
    );
  }

  Widget _buildStatPill({
    required IconData icon,
    required String label,
    required String value,
    required Color color,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: AppTheme.surfaceCard,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppTheme.subtleBorder),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            padding: const EdgeInsets.all(6),
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.15),
              shape: BoxShape.circle,
            ),
            child: Icon(icon, size: 16, color: color),
          ),
          const SizedBox(width: 10),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                value,
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                  color: AppTheme.primaryText,
                ),
              ),
              Text(
                label,
                style: const TextStyle(
                  fontSize: 11,
                  color: AppTheme.mutedText,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
