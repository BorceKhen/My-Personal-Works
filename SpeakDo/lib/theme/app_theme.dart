import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Design System for SpeakDo - 100% Offline Air-Gapped Voice Logger
class AppTheme {
  // Brand Palette specification
  static const Color slateBackground = Color(0xFF0F172A);
  static const Color surfaceCard = Color(0xFF1E293B);
  static const Color surfaceCardHover = Color(0xFF243247);
  static const Color subtleBorder = Color(0xFF334155);
  static const Color accentAmber = Color(0xFFF59E0B);
  static const Color accentAmberDark = Color(0xFFD97706);
  static const Color successEmerald = Color(0xFF10B981);
  static const Color primaryText = Color(0xFFF8FAFC);
  static const Color mutedText = Color(0xFF94A3B8);
  static const Color errorRed = Color(0xFFEF4444);
  static const Color infoIndigo = Color(0xFF6366F1);

  static ThemeData get darkTheme {
    final textTheme = GoogleFonts.interTextTheme(
      ThemeData(brightness: Brightness.dark).textTheme,
    ).copyWith(
      displayLarge: const TextStyle(color: primaryText, fontWeight: FontWeight.bold, fontSize: 32),
      displayMedium: const TextStyle(color: primaryText, fontWeight: FontWeight.bold, fontSize: 24),
      titleLarge: const TextStyle(color: primaryText, fontWeight: FontWeight.w700, fontSize: 20),
      titleMedium: const TextStyle(color: primaryText, fontWeight: FontWeight.w600, fontSize: 16),
      bodyLarge: const TextStyle(color: primaryText, fontSize: 15),
      bodyMedium: const TextStyle(color: mutedText, fontSize: 13.5),
      bodySmall: const TextStyle(color: mutedText, fontSize: 12),
    );

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: slateBackground,
      primaryColor: accentAmber,
      colorScheme: const ColorScheme.dark(
        primary: accentAmber,
        secondary: successEmerald,
        surface: surfaceCard,
        error: errorRed,
        onPrimary: Colors.black,
        onSecondary: Colors.white,
        onSurface: primaryText,
      ),
      textTheme: textTheme,
      appBarTheme: const AppBarTheme(
        backgroundColor: slateBackground,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        iconTheme: IconThemeData(color: primaryText),
      ),
      cardTheme: CardThemeData(
        color: surfaceCard,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: subtleBorder, width: 1),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surfaceCard,
        hintStyle: const TextStyle(color: mutedText, fontSize: 14),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: subtleBorder, width: 1),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: subtleBorder, width: 1),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: accentAmber, width: 1.5),
        ),
      ),
      dividerTheme: const DividerThemeData(
        color: subtleBorder,
        thickness: 1,
        space: 24,
      ),
      sliderTheme: SliderThemeData(
        activeTrackColor: accentAmber,
        inactiveTrackColor: subtleBorder,
        thumbColor: accentAmber,
        overlayColor: accentAmber.withValues(alpha: 0.2),
        trackHeight: 4,
        thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
      ),
    );
  }
}
