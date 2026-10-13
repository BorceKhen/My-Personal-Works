import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:uuid/uuid.dart';
import '../../database/app_database.dart';
import '../../models/note_model.dart';
import '../../models/task_model.dart';
import '../../services/audio_player_service.dart';
import '../../services/native_calendar_service.dart';
import '../../theme/app_theme.dart';
import '../widgets/audio_player_card.dart';
import '../widgets/task_item_tile.dart';

/// Note Details & Task Screen (/note-details).
/// Features interactive audio player, extracted action items with calendar sync,
/// and interactive timestamped transcript with direct scrubbing jumps.
class NoteDetailsScreen extends StatefulWidget {
  final String noteId;

  const NoteDetailsScreen({super.key, required this.noteId});

  @override
  State<NoteDetailsScreen> createState() => _NoteDetailsScreenState();
}

class _NoteDetailsScreenState extends State<NoteDetailsScreen> {
  Note? _note;
  bool _isLoading = true;
  final AudioPlayerService _audioPlayer = AudioPlayerService();

  @override
  void initState() {
    super.initState();
    _loadNote();
  }

  Future<void> _loadNote() async {
    final note = await AppDatabase.instance.getNoteById(widget.noteId);
    if (mounted) {
      setState(() {
        _note = note;
        _isLoading = false;
      });

      if (note != null) {
        _audioPlayer.loadAudio(
          audioPath: note.audioFilePath,
          duration: Duration(seconds: note.durationSeconds),
        );
      }
    }
  }

  @override
  void dispose() {
    _audioPlayer.dispose();
    super.dispose();
  }

  // Toggle task completion
  Future<void> _toggleTaskComplete(TaskItem task, bool isCompleted) async {
    await AppDatabase.instance.updateTaskCompletion(task.id, isCompleted);
    _loadNote();
  }

  // Sync individual task to system calendar
  Future<void> _syncTaskToCalendar(TaskItem task) async {
    final success = await NativeCalendarService.exportTaskToSystemCalendar(
      title: task.taskText,
      dueDate: task.dueDate,
      taskId: task.id,
    );

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Row(
            children: [
              Icon(
                success ? Icons.event_available_rounded : Icons.error_outline_rounded,
                color: success ? AppTheme.successEmerald : AppTheme.errorRed,
                size: 20,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  success
                      ? 'Task synced to native calendar (Alert set 15m prior)'
                      : 'Calendar sync failed',
                ),
              ),
            ],
          ),
          backgroundColor: AppTheme.surfaceCard,
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
    _loadNote();
  }

  // Batch sync all unsynced tasks
  Future<void> _syncAllTasks() async {
    if (_note == null || _note!.tasks.isEmpty) return;

    final syncedCount = await NativeCalendarService.syncAllTasks(_note!.tasks);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('$syncedCount task(s) synced to phone calendar!'),
          backgroundColor: AppTheme.infoIndigo,
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
    _loadNote();
  }

  // Add manual task dialog
  Future<void> _showAddTaskDialog() async {
    final textController = TextEditingController();
    DateTime? selectedDate = DateTime.now().add(const Duration(days: 1));

    await showDialog(
      context: context,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              backgroundColor: AppTheme.surfaceCard,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(18),
                side: const BorderSide(color: AppTheme.subtleBorder),
              ),
              title: const Text('Add Action Item', style: TextStyle(fontWeight: FontWeight.bold)),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(
                    controller: textController,
                    autofocus: true,
                    decoration: const InputDecoration(
                      hintText: 'e.g. Follow up with design team',
                    ),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text('Due Date:', style: TextStyle(color: AppTheme.mutedText)),
                      TextButton.icon(
                        icon: const Icon(Icons.calendar_today_rounded, size: 16),
                        label: Text(
                          selectedDate != null
                              ? '${selectedDate!.month}/${selectedDate!.day} 5:00 PM'
                              : 'Set Date',
                          style: const TextStyle(color: AppTheme.accentAmber),
                        ),
                        onPressed: () async {
                          final picked = await showDatePicker(
                            context: context,
                            initialDate: selectedDate ?? DateTime.now(),
                            firstDate: DateTime.now().subtract(const Duration(days: 1)),
                            lastDate: DateTime.now().add(const Duration(days: 365)),
                          );
                          if (picked != null) {
                            setDialogState(() {
                              selectedDate = DateTime(picked.year, picked.month, picked.day, 17, 0);
                            });
                          }
                        },
                      ),
                    ],
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('Cancel', style: TextStyle(color: AppTheme.mutedText)),
                ),
                ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.accentAmber,
                    foregroundColor: Colors.black,
                  ),
                  onPressed: () async {
                    if (textController.text.trim().isNotEmpty && _note != null) {
                      final newTask = TaskItem(
                        id: const Uuid().v4(),
                        noteId: _note!.id,
                        taskText: textController.text.trim(),
                        dueDate: selectedDate,
                        isCompleted: false,
                        syncedToCalendar: false,
                      );
                      await AppDatabase.instance.insertTask(newTask);
                      if (context.mounted) Navigator.of(context).pop();
                      _loadNote();
                    }
                  },
                  child: const Text('Add Task', style: TextStyle(fontWeight: FontWeight.bold)),
                ),
              ],
            );
          },
        );
      },
    );
  }

  // Copy transcript to clipboard
  void _copyTranscript() {
    if (_note == null) return;
    Clipboard.setData(ClipboardData(text: _note!.fullTranscript));
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Transcript copied to clipboard!'),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        body: Center(
          child: CircularProgressIndicator(color: AppTheme.accentAmber),
        ),
      );
    }

    if (_note == null) {
      return Scaffold(
        appBar: AppBar(),
        body: const Center(child: Text('Note not found.')),
      );
    }

    final note = _note!;
    final segments = note.segments;

    return Scaffold(
      appBar: AppBar(
        title: Text(
          note.title,
          style: const TextStyle(fontSize: 17, fontWeight: FontWeight.bold),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.copy_rounded, size: 20),
            tooltip: 'Copy Transcript',
            onPressed: _copyTranscript,
          ),
          IconButton(
            icon: const Icon(Icons.sync_rounded, size: 22, color: AppTheme.infoIndigo),
            tooltip: 'Sync All Tasks to Calendar',
            onPressed: _syncAllTasks,
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(20, 10, 20, 32),
        physics: const BouncingScrollPhysics(),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Note Metadata Header
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: AppTheme.surfaceCard,
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: AppTheme.subtleBorder),
                  ),
                  child: Text(
                    note.formattedCreatedAt,
                    style: const TextStyle(fontSize: 12, color: AppTheme.mutedText),
                  ),
                ),
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: AppTheme.surfaceCard,
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: AppTheme.subtleBorder),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.audiotrack_rounded, size: 13, color: AppTheme.accentAmber),
                      const SizedBox(width: 4),
                      Text(
                        note.formattedDuration,
                        style: const TextStyle(
                          fontSize: 12,
                          color: AppTheme.accentAmber,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                ),
                const Spacer(),
                const Row(
                  children: [
                    Icon(Icons.lock_outline_rounded, size: 14, color: AppTheme.successEmerald),
                    SizedBox(width: 4),
                    Text(
                      'Air-Gapped',
                      style: TextStyle(
                        fontSize: 12,
                        color: AppTheme.successEmerald,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 18),

            // 1. Interactive Audio Player Card
            AudioPlayerCard(player: _audioPlayer),
            const SizedBox(height: 24),

            // 2. Pure Transcription Header & Card
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(6),
                      decoration: BoxDecoration(
                        color: AppTheme.accentAmber.withValues(alpha: 0.15),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Icon(
                        Icons.text_snippet_rounded,
                        size: 18,
                        color: AppTheme.accentAmber,
                      ),
                    ),
                    const SizedBox(width: 10),
                    const Text(
                      'Pure Transcription',
                      style: TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.bold,
                        color: AppTheme.primaryText,
                      ),
                    ),
                  ],
                ),
                TextButton.icon(
                  onPressed: _copyTranscript,
                  icon: const Icon(Icons.copy_rounded, size: 14),
                  label: const Text('Copy', style: TextStyle(fontSize: 12)),
                  style: TextButton.styleFrom(
                    foregroundColor: AppTheme.accentAmber,
                    visualDensity: VisualDensity.compact,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            const Text(
              'Verbatim speech transcription from recording. Tap any segment to seek audio:',
              style: TextStyle(fontSize: 12, color: AppTheme.mutedText),
            ),
            const SizedBox(height: 12),

            // Interactive Timestamped Transcript Card
            AnimatedBuilder(
              animation: _audioPlayer,
              builder: (context, _) {
                final currentSeconds = _audioPlayer.position.inSeconds;

                return Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AppTheme.surfaceCard,
                    borderRadius: BorderRadius.circular(18),
                    border: Border.all(color: AppTheme.subtleBorder),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // Full text view
                      SelectableText(
                        note.fullTranscript,
                        style: const TextStyle(
                          fontSize: 15,
                          height: 1.5,
                          color: AppTheme.primaryText,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      const SizedBox(height: 16),
                      const Divider(height: 1),
                      const SizedBox(height: 12),
                      const Text(
                        'Timestamp Segments:',
                        style: TextStyle(
                          fontSize: 12,
                          color: AppTheme.mutedText,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 8),
                      ...segments.map((seg) {
                        final segSec = seg.timestamp.inSeconds;
                        final isCurrent = currentSeconds >= segSec &&
                            (segments.indexOf(seg) == segments.length - 1 ||
                                currentSeconds < segments[segments.indexOf(seg) + 1].timestamp.inSeconds);

                        return Padding(
                          padding: const EdgeInsets.only(bottom: 8),
                          child: InkWell(
                            onTap: () {
                              _audioPlayer.seek(seg.timestamp);
                              if (!_audioPlayer.isPlaying) {
                                _audioPlayer.play();
                              }
                            },
                            borderRadius: BorderRadius.circular(8),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                              decoration: BoxDecoration(
                                color: isCurrent
                                    ? AppTheme.accentAmber.withValues(alpha: 0.12)
                                    : Colors.transparent,
                                borderRadius: BorderRadius.circular(8),
                                border: isCurrent
                                    ? Border.all(color: AppTheme.accentAmber.withValues(alpha: 0.4))
                                    : null,
                              ),
                              child: Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                    decoration: BoxDecoration(
                                      color: AppTheme.slateBackground,
                                      borderRadius: BorderRadius.circular(6),
                                      border: Border.all(
                                        color: isCurrent ? AppTheme.accentAmber : AppTheme.subtleBorder,
                                      ),
                                    ),
                                    child: Text(
                                      seg.formattedTime,
                                      style: TextStyle(
                                        fontSize: 11,
                                        fontWeight: FontWeight.bold,
                                        color: isCurrent ? AppTheme.accentAmber : AppTheme.mutedText,
                                        fontFeatures: const [FontFeature.tabularFigures()],
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 10),
                                  Expanded(
                                    child: Text(
                                      seg.text,
                                      style: TextStyle(
                                        fontSize: 13,
                                        height: 1.4,
                                        color: isCurrent
                                            ? AppTheme.primaryText
                                            : const Color(0xFFCBD5E1),
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        );
                      }),
                    ],
                  ),
                );
              },
            ),
            const SizedBox(height: 24),

            // 3. Action Items (if any)
            if (note.tasks.isNotEmpty) ...[
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(6),
                        decoration: BoxDecoration(
                          color: AppTheme.infoIndigo.withValues(alpha: 0.15),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: const Icon(
                          Icons.checklist_rounded,
                          size: 18,
                          color: AppTheme.infoIndigo,
                        ),
                      ),
                      const SizedBox(width: 10),
                      const Text(
                        'Extracted Action Items',
                        style: TextStyle(
                          fontSize: 17,
                          fontWeight: FontWeight.bold,
                          color: AppTheme.primaryText,
                        ),
                      ),
                    ],
                  ),
                  IconButton(
                    onPressed: _showAddTaskDialog,
                    icon: const Icon(Icons.add_circle_outline_rounded, color: AppTheme.accentAmber),
                    tooltip: 'Add Task',
                  ),
                ],
              ),
              const SizedBox(height: 12),
              ...note.tasks.map((task) {
                return TaskItemTile(
                  task: task,
                  onToggleComplete: (val) => _toggleTaskComplete(task, val),
                  onSyncCalendar: () => _syncTaskToCalendar(task),
                  onDelete: () async {
                    await AppDatabase.instance.deleteTask(task.id);
                    _loadNote();
                  },
                );
              }),
            ],
          ],
        ),
      ),
    );
  }
}
