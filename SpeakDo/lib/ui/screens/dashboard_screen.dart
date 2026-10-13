import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import '../../database/app_database.dart';
import '../../models/note_model.dart';
import '../../theme/app_theme.dart';
import '../widgets/processing_sheet.dart';
import '../widgets/quick_stats_row.dart';
import 'note_details_screen.dart';

/// Main Dashboard Screen (/dashboard)
/// Full-text search, live recording trigger, media file import, and notes feed.
class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  final TextEditingController _searchController = TextEditingController();
  List<Note> _notes = [];
  Map<String, int> _stats = {
    'totalNotes': 0,
    'pendingTasks': 0,
    'completedTasks': 0,
    'syncedTasks': 0,
  };
  bool _isLoading = true;
  String _activeFilter = 'all'; // 'all', 'has_tasks', 'pending', 'completed'

  @override
  void initState() {
    super.initState();
    _initAndLoad();
  }

  Future<void> _initAndLoad() async {
    setState(() => _isLoading = true);
    await AppDatabase.instance.seedInitialDemoDataIfEmpty();
    await _loadData();
  }

  Future<void> _loadData() async {
    final query = _searchController.text.trim();
    final filter = _activeFilter == 'all' ? null : _activeFilter;

    final notes = await AppDatabase.instance.getAllNotes(
      searchQuery: query.isEmpty ? null : query,
      filter: filter,
    );
    final stats = await AppDatabase.instance.getStats();

    if (mounted) {
      setState(() {
        _notes = notes;
        _stats = stats;
        _isLoading = false;
      });
    }
  }

  void _openRecordingSheet() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) {
        return ProcessingSheet(
          initialMode: ProcessingModalMode.recording,
          onComplete: (createdNote) {
            _loadData();
            _navigateToNoteDetails(createdNote);
          },
        );
      },
    );
  }

  Future<void> _importMediaFile() async {
    try {
      final result = await FilePicker.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['mp3', 'm4a', 'wav', 'mp4', 'mov', 'aac', 'ogg'],
      );

      if (result != null && result.files.isNotEmpty) {
        final pickedFile = result.files.first;
        final filePath = pickedFile.path ?? pickedFile.name;

        if (!mounted) return;

        showModalBottomSheet(
          context: context,
          isScrollControlled: true,
          backgroundColor: Colors.transparent,
          builder: (context) {
            return ProcessingSheet(
              initialMode: ProcessingModalMode.pipeline,
              importedFilePath: filePath,
              onComplete: (createdNote) {
                _loadData();
                _navigateToNoteDetails(createdNote);
              },
            );
          },
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('File picker notice: $e'),
            backgroundColor: AppTheme.surfaceCard,
          ),
        );
      }
    }
  }

  void _navigateToNoteDetails(Note note) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (context) => NoteDetailsScreen(noteId: note.id),
      ),
    );
    _loadData();
  }

  Future<void> _deleteNoteConfirm(Note note) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppTheme.surfaceCard,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: AppTheme.subtleBorder),
        ),
        title: const Text('Delete Note?'),
        content: Text(
          'Are you sure you want to delete "${note.title}" and its ${note.tasks.length} associated action items?',
          style: const TextStyle(color: AppTheme.mutedText),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel', style: TextStyle(color: AppTheme.mutedText)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTheme.errorRed,
              foregroundColor: Colors.white,
            ),
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );

    if (confirm == true) {
      await AppDatabase.instance.deleteNote(note.id);
      _loadData();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: RefreshIndicator(
          color: AppTheme.accentAmber,
          backgroundColor: AppTheme.surfaceCard,
          onRefresh: _loadData,
          child: CustomScrollView(
            physics: const AlwaysScrollableScrollPhysics(parent: BouncingScrollPhysics()),
            slivers: [
              // Header Sliver
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 16, 20, 12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // Top Row: Logo, Offline Badge
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Row(
                            children: [
                              Container(
                                padding: const EdgeInsets.all(8),
                                decoration: BoxDecoration(
                                  gradient: const LinearGradient(
                                    colors: [AppTheme.accentAmber, AppTheme.accentAmberDark],
                                  ),
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: const Icon(
                                  Icons.graphic_eq_rounded,
                                  color: Colors.black,
                                  size: 24,
                                ),
                              ),
                              const SizedBox(width: 12),
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  RichText(
                                    text: const TextSpan(
                                      text: 'Speak',
                                      style: TextStyle(
                                        fontSize: 22,
                                        fontWeight: FontWeight.w800,
                                        color: AppTheme.primaryText,
                                        letterSpacing: -0.5,
                                      ),
                                      children: [
                                        TextSpan(
                                          text: 'Do',
                                          style: TextStyle(color: AppTheme.accentAmber),
                                        ),
                                      ],
                                    ),
                                  ),
                                  const Text(
                                    'On-Device Voice Logger',
                                    style: TextStyle(
                                      fontSize: 11,
                                      color: AppTheme.mutedText,
                                      fontWeight: FontWeight.w500,
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),

                          // Air-gapped privacy tag
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                            decoration: BoxDecoration(
                              color: AppTheme.surfaceCard,
                              borderRadius: BorderRadius.circular(20),
                              border: Border.all(
                                color: AppTheme.successEmerald.withValues(alpha: 0.4),
                              ),
                            ),
                            child: const Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  Icons.shield_rounded,
                                  size: 13,
                                  color: AppTheme.successEmerald,
                                ),
                                SizedBox(width: 5),
                                Text(
                                  '100% Offline',
                                  style: TextStyle(
                                    fontSize: 11,
                                    fontWeight: FontWeight.bold,
                                    color: AppTheme.successEmerald,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 20),

                      // Quick Stats Row
                      QuickStatsRow(
                        totalNotes: _stats['totalNotes'] ?? 0,
                        pendingTasks: _stats['pendingTasks'] ?? 0,
                        completedTasks: _stats['completedTasks'] ?? 0,
                        syncedTasks: _stats['syncedTasks'] ?? 0,
                      ),
                      const SizedBox(height: 20),

                      // Hero Actions Area
                      _buildHeroActionsArea(),
                      const SizedBox(height: 20),

                      // Search Bar
                      _buildSearchBar(),
                      const SizedBox(height: 14),

                      // Filter chips
                      _buildFilterChips(),
                      const SizedBox(height: 14),

                      // Section Title
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            'Recorded Notes (${_notes.length})',
                            style: const TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w700,
                              color: AppTheme.primaryText,
                            ),
                          ),
                          if (_searchController.text.isNotEmpty || _activeFilter != 'all')
                            TextButton(
                              onPressed: () {
                                _searchController.clear();
                                setState(() => _activeFilter = 'all');
                                _loadData();
                              },
                              child: const Text('Reset filter', style: TextStyle(fontSize: 12)),
                            ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),

              // Notes List Feed
              if (_isLoading)
                const SliverFillRemaining(
                  hasScrollBody: false,
                  child: Center(
                    child: CircularProgressIndicator(color: AppTheme.accentAmber),
                  ),
                )
              else if (_notes.isEmpty)
                SliverFillRemaining(
                  hasScrollBody: false,
                  child: _buildEmptyState(),
                )
              else
                SliverPadding(
                  padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
                  sliver: SliverList(
                    delegate: SliverChildBuilderDelegate(
                      (context, index) {
                        final note = _notes[index];
                        return _buildNoteFeedCard(note);
                      },
                      childCount: _notes.length,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Hero Actions Area
  // ---------------------------------------------------------------------------
  Widget _buildHeroActionsArea() {
    return Row(
      children: [
        // "Record Voice Note" CTA (Primary Amber)
        Expanded(
          flex: 3,
          child: InkWell(
            onTap: _openRecordingSheet,
            borderRadius: BorderRadius.circular(16),
            child: Container(
              padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 16),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [AppTheme.accentAmber, AppTheme.accentAmberDark],
                ),
                borderRadius: BorderRadius.circular(16),
                boxShadow: [
                  BoxShadow(
                    color: AppTheme.accentAmber.withValues(alpha: 0.28),
                    blurRadius: 14,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: const Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.mic_rounded, color: Colors.black, size: 22),
                  SizedBox(width: 8),
                  Text(
                    'Record Voice Note',
                    style: TextStyle(
                      color: Colors.black,
                      fontWeight: FontWeight.w800,
                      fontSize: 14.5,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(width: 10),

        // "Import Media File" CTA (Secondary Surface Card)
        Expanded(
          flex: 2,
          child: InkWell(
            onTap: _importMediaFile,
            borderRadius: BorderRadius.circular(16),
            child: Container(
              padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 12),
              decoration: BoxDecoration(
                color: AppTheme.surfaceCard,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppTheme.subtleBorder),
              ),
              child: const Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.file_upload_outlined, color: AppTheme.primaryText, size: 20),
                  SizedBox(width: 6),
                  Text(
                    'Import File',
                    style: TextStyle(
                      color: AppTheme.primaryText,
                      fontWeight: FontWeight.w600,
                      fontSize: 13.5,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // Search Bar
  // ---------------------------------------------------------------------------
  Widget _buildSearchBar() {
    return TextField(
      controller: _searchController,
      onChanged: (_) => _loadData(),
      decoration: InputDecoration(
        hintText: 'Search transcripts, titles, or tasks...',
        prefixIcon: const Icon(Icons.search_rounded, color: AppTheme.mutedText, size: 20),
        suffixIcon: _searchController.text.isNotEmpty
            ? IconButton(
                icon: const Icon(Icons.clear_rounded, size: 18),
                onPressed: () {
                  _searchController.clear();
                  _loadData();
                },
              )
            : null,
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Filter Chips
  // ---------------------------------------------------------------------------
  Widget _buildFilterChips() {
    final filters = [
      {'key': 'all', 'label': 'All Notes'},
      {'key': 'has_tasks', 'label': 'Has Tasks'},
      {'key': 'pending', 'label': 'Pending'},
      {'key': 'completed', 'label': 'Completed'},
    ];

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      physics: const BouncingScrollPhysics(),
      child: Row(
        children: filters.map((f) {
          final isSelected = _activeFilter == f['key'];
          return Padding(
            padding: const EdgeInsets.only(right: 8),
            child: FilterChip(
              label: Text(
                f['label']!,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                  color: isSelected ? Colors.black : AppTheme.primaryText,
                ),
              ),
              selected: isSelected,
              showCheckmark: false,
              selectedColor: AppTheme.accentAmber,
              backgroundColor: AppTheme.surfaceCard,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(20),
                side: BorderSide(
                  color: isSelected ? AppTheme.accentAmber : AppTheme.subtleBorder,
                ),
              ),
              onSelected: (_) {
                setState(() => _activeFilter = f['key']!);
                _loadData();
              },
            ),
          );
        }).toList(),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Note Card
  // ---------------------------------------------------------------------------
  Widget _buildNoteFeedCard(Note note) {
    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      decoration: BoxDecoration(
        color: AppTheme.surfaceCard,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AppTheme.subtleBorder),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.15),
            blurRadius: 10,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      child: InkWell(
        onTap: () => _navigateToNoteDetails(note),
        borderRadius: BorderRadius.circular(18),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Top Header: Note Title, Duration Tag, Delete
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Text(
                      note.title,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: AppTheme.primaryText,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  const SizedBox(width: 8),

                  // Duration Tag (e.g. 03:45)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: AppTheme.slateBackground,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: AppTheme.subtleBorder),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.timer_outlined, size: 12, color: AppTheme.accentAmber),
                        const SizedBox(width: 4),
                        Text(
                          note.formattedDuration,
                          style: const TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                            color: AppTheme.accentAmber,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 4),

                  // Delete option
                  IconButton(
                    visualDensity: VisualDensity.compact,
                    padding: EdgeInsets.zero,
                    constraints: const BoxConstraints(),
                    icon: const Icon(Icons.more_vert_rounded, size: 18, color: AppTheme.mutedText),
                    onPressed: () => _deleteNoteConfirm(note),
                  ),
                ],
              ),
              const SizedBox(height: 6),

              // Timestamp
              Text(
                note.formattedCreatedAt,
                style: const TextStyle(fontSize: 11.5, color: AppTheme.mutedText),
              ),
              const SizedBox(height: 10),

              // Summary excerpt
              Text(
                note.summary ?? note.fullTranscript,
                style: const TextStyle(
                  fontSize: 13,
                  color: Color(0xFFCBD5E1),
                  height: 1.35,
                ),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 14),

              // Badges row: Pending Tasks, Calendar Synced status
              Row(
                children: [
                  // Pending Task badge
                  if (note.tasks.isNotEmpty) ...[
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: note.pendingTasksCount > 0
                            ? AppTheme.accentAmber.withValues(alpha: 0.15)
                            : AppTheme.successEmerald.withValues(alpha: 0.15),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: note.pendingTasksCount > 0
                              ? AppTheme.accentAmber.withValues(alpha: 0.4)
                              : AppTheme.successEmerald.withValues(alpha: 0.4),
                        ),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            note.pendingTasksCount > 0
                                ? Icons.pending_actions_rounded
                                : Icons.check_circle_outline_rounded,
                            size: 13,
                            color: note.pendingTasksCount > 0
                                ? AppTheme.accentAmber
                                : AppTheme.successEmerald,
                          ),
                          const SizedBox(width: 5),
                          Text(
                            note.pendingTasksCount > 0
                                ? '${note.pendingTasksCount} Pending Tasks'
                                : 'All Tasks Completed',
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                              color: note.pendingTasksCount > 0
                                  ? AppTheme.accentAmber
                                  : AppTheme.successEmerald,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                  ],

                  // Calendar Sync Status Icon
                  if (note.syncedTasksCount > 0)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: AppTheme.infoIndigo.withValues(alpha: 0.15),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: AppTheme.infoIndigo.withValues(alpha: 0.4),
                        ),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.event_available_rounded, size: 13, color: AppTheme.infoIndigo),
                          const SizedBox(width: 4),
                          Text(
                            '${note.syncedTasksCount} Synced',
                            style: const TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                              color: AppTheme.infoIndigo,
                            ),
                          ),
                        ],
                      ),
                    ),

                  const Spacer(),
                  const Icon(Icons.arrow_forward_ios_rounded, size: 13, color: AppTheme.mutedText),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Empty State
  // ---------------------------------------------------------------------------
  Widget _buildEmptyState() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppTheme.surfaceCard,
                shape: BoxShape.circle,
                border: Border.all(color: AppTheme.subtleBorder),
              ),
              child: const Icon(
                Icons.mic_none_rounded,
                size: 48,
                color: AppTheme.accentAmber,
              ),
            ),
            const SizedBox(height: 18),
            const Text(
              'No Voice Notes Found',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: AppTheme.primaryText,
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'Record a live voice note or import an audio/video file. All processing is 100% on-device and privacy-first.',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 13,
                color: AppTheme.mutedText,
                height: 1.4,
              ),
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              style: ElevatedButton.styleFrom(
                backgroundColor: AppTheme.accentAmber,
                foregroundColor: Colors.black,
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
              onPressed: _openRecordingSheet,
              icon: const Icon(Icons.mic_rounded, size: 18),
              label: const Text(
                'Record Voice Note',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
