import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'app_colors.dart';
import 'app_typography.dart';
import 'app_spacing.dart';

class AppTheme {
  AppTheme._();

  static ThemeData get dark => ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        scaffoldBackgroundColor: AppColors.obsidian,
        colorScheme: const ColorScheme.dark(
          primary: AppColors.gold,
          onPrimary: AppColors.obsidian,
          secondary: AppColors.goldLight,
          onSecondary: AppColors.obsidian,
          surface: AppColors.graphite,
          onSurface: AppColors.white,
          background: AppColors.obsidian,
          onBackground: AppColors.white,
          error: AppColors.error,
          onError: AppColors.white,
          outline: AppColors.smoke,
          outlineVariant: AppColors.mist,
          surfaceVariant: AppColors.slate,
        ),
        fontFamily: AppTypography.dmSans,
        textTheme: const TextTheme(
          displayLarge: AppTypography.displayXl,
          displayMedium: AppTypography.displayLg,
          displaySmall: AppTypography.displayMd,
          headlineLarge: AppTypography.headingLg,
          headlineMedium: AppTypography.headingMd,
          headlineSmall: AppTypography.headingSm,
          bodyLarge: AppTypography.bodyLg,
          bodyMedium: AppTypography.bodyMd,
          bodySmall: AppTypography.bodySm,
          labelLarge: AppTypography.labelLg,
          labelMedium: AppTypography.labelMd,
          labelSmall: AppTypography.labelSm,
        ),

        // ── AppBar ──────────────────────────────────────────────────────────
        appBarTheme: AppBarTheme(
          backgroundColor: AppColors.obsidian,
          foregroundColor: AppColors.white,
          elevation: 0,
          scrolledUnderElevation: 0,
          centerTitle: false,
          systemOverlayStyle: SystemUiOverlayStyle.light.copyWith(
            statusBarColor: Colors.transparent,
          ),
          titleTextStyle: AppTypography.headingMd,
        ),

        // ── Bottom Navigation ────────────────────────────────────────────────
        bottomNavigationBarTheme: const BottomNavigationBarThemeData(
          backgroundColor: AppColors.carbon,
          selectedItemColor: AppColors.gold,
          unselectedItemColor: AppColors.ash,
          showSelectedLabels: false,
          showUnselectedLabels: false,
          type: BottomNavigationBarType.fixed,
          elevation: 0,
        ),

        // ── Input ────────────────────────────────────────────────────────────
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: AppColors.graphite,
          contentPadding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.base,
            vertical: AppSpacing.md,
          ),
          border: OutlineInputBorder(
            borderRadius: AppRadius.rInput,
            borderSide: const BorderSide(color: AppColors.smoke),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: AppRadius.rInput,
            borderSide: const BorderSide(color: AppColors.smoke),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: AppRadius.rInput,
            borderSide: const BorderSide(color: AppColors.gold, width: 1.5),
          ),
          errorBorder: OutlineInputBorder(
            borderRadius: AppRadius.rInput,
            borderSide: const BorderSide(color: AppColors.error),
          ),
          hintStyle: AppTypography.bodyMd.copyWith(color: AppColors.ash),
          labelStyle: AppTypography.labelMd.copyWith(color: AppColors.ash),
          floatingLabelStyle: AppTypography.labelSm.copyWith(
            color: AppColors.gold,
            letterSpacing: 0.2,
          ),
        ),

        // ── ElevatedButton ───────────────────────────────────────────────────
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            backgroundColor: AppColors.gold,
            foregroundColor: AppColors.obsidian,
            elevation: 0,
            shadowColor: Colors.transparent,
            shape: RoundedRectangleBorder(
              borderRadius: AppRadius.rButton,
            ),
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.xl,
              vertical: AppSpacing.base,
            ),
            textStyle: AppTypography.labelLg.copyWith(
              fontSize: 14,
              fontWeight: FontWeight.w700,
              letterSpacing: 0.3,
            ),
            minimumSize: const Size(double.infinity, AppSpacing.touchTarget),
          ),
        ),

        // ── OutlinedButton ───────────────────────────────────────────────────
        outlinedButtonTheme: OutlinedButtonThemeData(
          style: OutlinedButton.styleFrom(
            foregroundColor: AppColors.gold,
            side: const BorderSide(color: AppColors.gold),
            shape: RoundedRectangleBorder(
              borderRadius: AppRadius.rButton,
            ),
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.xl,
              vertical: AppSpacing.base,
            ),
            textStyle: AppTypography.labelLg.copyWith(
              fontSize: 14,
              fontWeight: FontWeight.w600,
            ),
            minimumSize: const Size(double.infinity, AppSpacing.touchTarget),
          ),
        ),

        // ── TextButton ───────────────────────────────────────────────────────
        textButtonTheme: TextButtonThemeData(
          style: TextButton.styleFrom(
            foregroundColor: AppColors.gold,
            textStyle: AppTypography.labelMd.copyWith(
              color: AppColors.gold,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),

        // ── Card ─────────────────────────────────────────────────────────────
        cardTheme: CardTheme(
          color: AppColors.graphite,
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: AppRadius.rCard,
            side: const BorderSide(color: AppColors.smoke),
          ),
          margin: EdgeInsets.zero,
        ),

        // ── Divider ──────────────────────────────────────────────────────────
        dividerTheme: const DividerThemeData(
          color: AppColors.smoke,
          thickness: 1,
          space: 0,
        ),

        // ── Dialog ───────────────────────────────────────────────────────────
        dialogTheme: DialogTheme(
          backgroundColor: AppColors.graphite,
          surfaceTintColor: Colors.transparent,
          shape: RoundedRectangleBorder(borderRadius: AppRadius.rModal),
          titleTextStyle: AppTypography.headingMd,
          contentTextStyle: AppTypography.bodyMd,
        ),

        // ── Bottom Sheet ─────────────────────────────────────────────────────
        bottomSheetTheme: const BottomSheetThemeData(
          backgroundColor: AppColors.graphite,
          surfaceTintColor: Colors.transparent,
          modalBackgroundColor: AppColors.graphite,
          dragHandleColor: AppColors.smoke,
          dragHandleSize: Size(40, 4),
          showDragHandle: true,
        ),

        // ── Chip ─────────────────────────────────────────────────────────────
        chipTheme: ChipThemeData(
          backgroundColor: AppColors.graphite,
          selectedColor: AppColors.goldSubtle,
          side: const BorderSide(color: AppColors.smoke),
          shape: RoundedRectangleBorder(
            borderRadius: AppRadius.rPill,
          ),
          labelStyle: AppTypography.labelMd,
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.md,
            vertical: AppSpacing.xs,
          ),
        ),

        // ── Snackbar ─────────────────────────────────────────────────────────
        snackBarTheme: SnackBarThemeData(
          backgroundColor: AppColors.slate,
          contentTextStyle: AppTypography.bodyMd.copyWith(color: AppColors.white),
          shape: RoundedRectangleBorder(borderRadius: AppRadius.rMd),
          behavior: SnackBarBehavior.floating,
        ),
      );
}
