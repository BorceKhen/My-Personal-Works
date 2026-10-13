import 'package:flutter_test/flutter_test.dart';
import 'package:speak_do/services/local_task_parser.dart';

void main() {
  group('LocalTaskParser Deterministic Offline NLP Tests', () {
    test('extracts tasks and relative dates accurately', () {
      const transcript =
          'Welcome to the kickoff. We need to submit the architecture review tomorrow at 5 pm. '
          'Also call David by 2 pm to confirm the design specifications. '
          'Finally, schedule a follow-up meeting next monday with stakeholders.';

      final result = LocalTaskParser.parse(transcript, noteId: 'test-note-1');

      expect(result.title.isNotEmpty, isTrue);
      expect(result.tasks.length, 3);

      // Verify task 1
      final task1 = result.tasks[0];
      expect(task1.taskText.toLowerCase(), contains('need to submit'));
      expect(task1.dueDate, isNotNull);

      // Verify task 2
      final task2 = result.tasks[1];
      expect(task2.taskText.toLowerCase(), contains('call david'));

      // Verify task 3
      final task3 = result.tasks[2];
      expect(task3.taskText.toLowerCase(), contains('schedule a follow-up meeting'));
      expect(task3.dueDate, isNotNull);
    });

    test('handles empty or non-actionable transcripts gracefully', () {
      const transcript = 'Just regular conversation with no tasks or deadlines.';
      final result = LocalTaskParser.parse(transcript, noteId: 'test-note-2');

      expect(result.tasks.isEmpty, isTrue);
      expect(result.title, contains('Just regular conversation'));
    });
  });
}
