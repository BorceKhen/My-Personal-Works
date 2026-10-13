import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import '../models/note_model.dart';
import '../models/task_model.dart';

/// Local-First SQLite Database Service for SpeakDo.
/// 100% offline, privacy-first, migration-ready local persistence.
class AppDatabase {
  static final AppDatabase instance = AppDatabase._internal();
  static Database? _database;

  // In-memory web fallback for zero-dependency web execution
  final List<Note> _webNotes = [];
  final List<TaskItem> _webTasks = [];

  AppDatabase._internal();

  /// Retrieve the active SQLite database instance
  Future<Database?> get database async {
    if (kIsWeb) return null; // Web uses local in-memory fallback
    if (_database != null) return _database!;
    _database = await _initDatabase();
    return _database!;
  }

  /// Initialize SQLite with platform-specific adapters (FFI for Desktop, Native for Mobile)
  Future<Database> _initDatabase() async {
    if (!kIsWeb && (Platform.isWindows || Platform.isLinux || Platform.isMacOS)) {
      sqfliteFfiInit();
      databaseFactory = databaseFactoryFfi;
    }

    String dbPath;
    if (!kIsWeb && (Platform.isWindows || Platform.isLinux || Platform.isMacOS)) {
      final docDir = await getApplicationDocumentsDirectory();
      final speakDoDir = Directory(p.join(docDir.path, 'SpeakDo'));
      if (!speakDoDir.existsSync()) {
        speakDoDir.createSync(recursive: true);
      }
      dbPath = p.join(speakDoDir.path, 'speakdo.db');
    } else {
      final databasesPath = await getDatabasesPath();
      dbPath = p.join(databasesPath, 'speakdo.db');
    }

    return await openDatabase(
      dbPath,
      version: 1,
      onConfigure: (db) async {
        // Enforce foreign key constraints
        await db.execute('PRAGMA foreign_keys = ON');
      },
      onCreate: _onCreate,
      onUpgrade: _onUpgrade,
    );
  }

  /// Initial SQLite Schema Creation
  Future<void> _onCreate(Database db, int version) async {
    // 1. Notes Table
    await db.execute('''
      CREATE TABLE notes (
        id TEXT PRIMARY KEY NOT NULL,
        title TEXT NOT NULL,
        summary TEXT,
        full_transcript TEXT NOT NULL,
        audio_file_path TEXT NOT NULL,
        duration_seconds INTEGER NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );
    ''');

    // 2. Tasks / Action Items Table
    await db.execute('''
      CREATE TABLE tasks (
        id TEXT PRIMARY KEY NOT NULL,
        note_id TEXT NOT NULL,
        task_text TEXT NOT NULL,
        due_date TIMESTAMP,
        is_completed INTEGER DEFAULT 0,
        synced_to_calendar INTEGER DEFAULT 0,
        FOREIGN KEY (note_id) REFERENCES notes(id) ON DELETE CASCADE
      );
    ''');

    // Create helpful indices for performance
    await db.execute('CREATE INDEX idx_tasks_note_id ON tasks(note_id);');
    await db.execute('CREATE INDEX idx_tasks_is_completed ON tasks(is_completed);');
  }

  /// Migration handler for future schema expansions
  Future<void> _onUpgrade(Database db, int oldVersion, int newVersion) async {
    // Migration hooks ready for schema version upgrades
    if (oldVersion < 2) {
      // Future version 2 migrations
    }
  }

  // ---------------------------------------------------------------------------
  // CRUD Operations
  // ---------------------------------------------------------------------------

  /// Insert a newly transcribed Note along with its extracted action items.
  Future<void> insertNote(Note note, List<TaskItem> tasks) async {
    if (kIsWeb) {
      _webNotes.removeWhere((n) => n.id == note.id);
      _webTasks.removeWhere((t) => t.noteId == note.id);
      _webNotes.insert(0, note.copyWith(tasks: tasks));
      _webTasks.addAll(tasks);
      return;
    }

    final db = await database;
    if (db == null) return;

    await db.transaction((txn) async {
      await txn.insert(
        'notes',
        note.toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace,
      );

      final batch = txn.batch();
      for (final task in tasks) {
        batch.insert(
          'tasks',
          task.toMap(),
          conflictAlgorithm: ConflictAlgorithm.replace,
        );
      }
      await batch.commit(noResult: true);
    });
  }

  /// Fetch all notes, optionally filtered by search text and task completion status.
  Future<List<Note>> getAllNotes({String? searchQuery, String? filter}) async {
    if (kIsWeb) {
      var notes = List<Note>.from(_webNotes);
      if (searchQuery != null && searchQuery.trim().isNotEmpty) {
        final query = searchQuery.toLowerCase();
        notes = notes.where((n) {
          final titleMatch = n.title.toLowerCase().contains(query);
          final transcriptMatch = n.fullTranscript.toLowerCase().contains(query);
          final summaryMatch = (n.summary ?? '').toLowerCase().contains(query);
          return titleMatch || transcriptMatch || summaryMatch;
        }).toList();
      }

      if (filter == 'pending') {
        notes = notes.where((n) => n.pendingTasksCount > 0).toList();
      } else if (filter == 'completed') {
        notes = notes.where((n) => n.tasks.isNotEmpty && n.pendingTasksCount == 0).toList();
      } else if (filter == 'has_tasks') {
        notes = notes.where((n) => n.tasks.isNotEmpty).toList();
      }

      notes.sort((a, b) => b.createdAt.compareTo(a.createdAt));
      return notes;
    }

    final db = await database;
    if (db == null) return [];

    String? whereClause;
    List<dynamic>? whereArgs;

    if (searchQuery != null && searchQuery.trim().isNotEmpty) {
      final queryParam = '%${searchQuery.trim()}%';
      whereClause = 'title LIKE ? OR full_transcript LIKE ? OR summary LIKE ?';
      whereArgs = [queryParam, queryParam, queryParam];
    }

    final noteMaps = await db.query(
      'notes',
      where: whereClause,
      whereArgs: whereArgs,
      orderBy: 'created_at DESC',
    );

    final List<Note> results = [];
    for (final map in noteMaps) {
      final noteId = map['id'] as String;
      final taskMaps = await db.query(
        'tasks',
        where: 'note_id = ?',
        whereArgs: [noteId],
        orderBy: 'due_date ASC, id ASC',
      );
      final tasks = taskMaps.map((tm) => TaskItem.fromMap(tm)).toList();
      final note = Note.fromMap(map, tasks: tasks);

      // Apply in-memory secondary filters
      if (filter == 'pending' && note.pendingTasksCount == 0) continue;
      if (filter == 'completed' && (note.tasks.isEmpty || note.pendingTasksCount > 0)) continue;
      if (filter == 'has_tasks' && note.tasks.isEmpty) continue;

      results.add(note);
    }

    return results;
  }

  /// Retrieve a specific note with its associated tasks
  Future<Note?> getNoteById(String noteId) async {
    if (kIsWeb) {
      try {
        final note = _webNotes.firstWhere((n) => n.id == noteId);
        final noteTasks = _webTasks.where((t) => t.noteId == noteId).toList();
        return note.copyWith(tasks: noteTasks);
      } catch (_) {
        return null;
      }
    }

    final db = await database;
    if (db == null) return null;

    final noteMaps = await db.query(
      'notes',
      where: 'id = ?',
      whereArgs: [noteId],
      limit: 1,
    );

    if (noteMaps.isEmpty) return null;

    final taskMaps = await db.query(
      'tasks',
      where: 'note_id = ?',
      whereArgs: [noteId],
      orderBy: 'due_date ASC, id ASC',
    );

    final tasks = taskMaps.map((tm) => TaskItem.fromMap(tm)).toList();
    return Note.fromMap(noteMaps.first, tasks: tasks);
  }

  /// Toggle task completion in SQLite
  Future<void> updateTaskCompletion(String taskId, bool isCompleted) async {
    if (kIsWeb) {
      final index = _webTasks.indexWhere((t) => t.id == taskId);
      if (index != -1) {
        _webTasks[index] = _webTasks[index].copyWith(isCompleted: isCompleted);
        _syncWebNoteTasks(_webTasks[index].noteId);
      }
      return;
    }

    final db = await database;
    if (db == null) return;

    await db.update(
      'tasks',
      {'is_completed': isCompleted ? 1 : 0},
      where: 'id = ?',
      whereArgs: [taskId],
    );
  }

  /// Update calendar sync status for a task
  Future<void> updateTaskCalendarSync(String taskId, bool synced) async {
    if (kIsWeb) {
      final index = _webTasks.indexWhere((t) => t.id == taskId);
      if (index != -1) {
        _webTasks[index] = _webTasks[index].copyWith(syncedToCalendar: synced);
        _syncWebNoteTasks(_webTasks[index].noteId);
      }
      return;
    }

    final db = await database;
    if (db == null) return;

    await db.update(
      'tasks',
      {'synced_to_calendar': synced ? 1 : 0},
      where: 'id = ?',
      whereArgs: [taskId],
    );
  }

  /// Insert an individual manually added task
  Future<void> insertTask(TaskItem task) async {
    if (kIsWeb) {
      _webTasks.add(task);
      _syncWebNoteTasks(task.noteId);
      return;
    }

    final db = await database;
    if (db == null) return;

    await db.insert(
      'tasks',
      task.toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  /// Delete a single task
  Future<void> deleteTask(String taskId) async {
    if (kIsWeb) {
      final task = _webTasks.firstWhere((t) => t.id == taskId, orElse: () => TaskItem(id: '', noteId: '', taskText: ''));
      _webTasks.removeWhere((t) => t.id == taskId);
      if (task.noteId.isNotEmpty) {
        _syncWebNoteTasks(task.noteId);
      }
      return;
    }

    final db = await database;
    if (db == null) return;

    await db.delete(
      'tasks',
      where: 'id = ?',
      whereArgs: [taskId],
    );
  }

  /// Delete note and cascade delete associated tasks
  Future<void> deleteNote(String noteId) async {
    if (kIsWeb) {
      _webNotes.removeWhere((n) => n.id == noteId);
      _webTasks.removeWhere((t) => t.noteId == noteId);
      return;
    }

    final db = await database;
    if (db == null) return;

    await db.delete(
      'notes',
      where: 'id = ?',
      whereArgs: [noteId],
    );
  }

  /// Retrieve summary statistics for the dashboard
  Future<Map<String, int>> getStats() async {
    if (kIsWeb) {
      final totalNotes = _webNotes.length;
      final pendingTasks = _webTasks.where((t) => !t.isCompleted).length;
      final completedTasks = _webTasks.where((t) => t.isCompleted).length;
      final syncedTasks = _webTasks.where((t) => t.syncedToCalendar).length;
      return {
        'totalNotes': totalNotes,
        'pendingTasks': pendingTasks,
        'completedTasks': completedTasks,
        'syncedTasks': syncedTasks,
      };
    }

    final db = await database;
    if (db == null) {
      return {'totalNotes': 0, 'pendingTasks': 0, 'completedTasks': 0, 'syncedTasks': 0};
    }

    final totalNotesCount = Sqflite.firstIntValue(
      await db.rawQuery('SELECT COUNT(*) FROM notes'),
    ) ?? 0;

    final pendingTasksCount = Sqflite.firstIntValue(
      await db.rawQuery('SELECT COUNT(*) FROM tasks WHERE is_completed = 0'),
    ) ?? 0;

    final completedTasksCount = Sqflite.firstIntValue(
      await db.rawQuery('SELECT COUNT(*) FROM tasks WHERE is_completed = 1'),
    ) ?? 0;

    final syncedTasksCount = Sqflite.firstIntValue(
      await db.rawQuery('SELECT COUNT(*) FROM tasks WHERE synced_to_calendar = 1'),
    ) ?? 0;

    return {
      'totalNotes': totalNotesCount,
      'pendingTasks': pendingTasksCount,
      'completedTasks': completedTasksCount,
      'syncedTasks': syncedTasksCount,
    };
  }

  void _syncWebNoteTasks(String noteId) {
    final noteIndex = _webNotes.indexWhere((n) => n.id == noteId);
    if (noteIndex != -1) {
      final tasks = _webTasks.where((t) => t.noteId == noteId).toList();
      _webNotes[noteIndex] = _webNotes[noteIndex].copyWith(tasks: tasks);
    }
  }

  /// Prepopulate with initial demo note if database is completely empty
  Future<void> seedInitialDemoDataIfEmpty() async {
    final stats = await getStats();
    if (stats['totalNotes'] == 0) {
      final now = DateTime.now();
      final demoNote = Note(
        id: 'demo-welcome-note-001',
        title: 'Project Kickoff & Action Items',
        summary: 'Met with team to outline offline voice processing architecture. Need to submit architecture review by tomorrow.',
        fullTranscript:
            'Welcome to SpeakDo! This is an on-device, 100% offline voice note logger. '
            'We need to submit the architecture review tomorrow at 5 pm. '
            'Also make sure to call David by 2 pm to confirm the design specifications. '
            'Finally, schedule a follow-up meeting next monday with stakeholders to demo on-device whisper transcription.',
        audioFilePath: 'local_assets/audio/demo_note.wav',
        durationSeconds: 24,
        createdAt: now.subtract(const Duration(hours: 3)),
      );

      final demoTasks = [
        TaskItem(
          id: 'task-demo-1',
          noteId: demoNote.id,
          taskText: 'Submit the architecture review',
          dueDate: DateTime(now.year, now.month, now.day + 1, 17, 0),
          isCompleted: false,
          syncedToCalendar: false,
        ),
        TaskItem(
          id: 'task-demo-2',
          noteId: demoNote.id,
          taskText: 'Call David to confirm the design specifications',
          dueDate: DateTime(now.year, now.month, now.day, 14, 0),
          isCompleted: false,
          syncedToCalendar: true,
        ),
        TaskItem(
          id: 'task-demo-3',
          noteId: demoNote.id,
          taskText: 'Schedule a follow-up meeting with stakeholders to demo on-device whisper transcription',
          dueDate: now.add(const Duration(days: 4)),
          isCompleted: true,
          syncedToCalendar: true,
        ),
      ];

      await insertNote(demoNote, demoTasks);
    }
  }
}
