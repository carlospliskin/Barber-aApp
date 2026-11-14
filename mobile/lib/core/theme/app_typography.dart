import 'package:flutter/material.dart';
import 'app_colors.dart';

/// Sistema tipográfico de Barbería SaaS
/// Playfair Display → Display / Headings (carácter, exclusividad)
/// DM Sans         → Body / UI (legibilidad, limpieza)
/// DM Mono         → Datos, precios, códigos (precisión)

abstract class AppTypography {
  // ─── Font families ────────────────────────────────────────────────────────
  static const String playfair = 'PlayfairDisplay';
  static const String dmSans = 'DMSans';
  static const String dmMono = 'DMMono';

  // ─── Display — Playfair ───────────────────────────────────────────────────
  /// Títulos hero, pantallas de bienvenida
  static const TextStyle displayXl = TextStyle(
    fontFamily: playfair,
    fontSize: 40,
    fontWeight: FontWeight.w900,
    color: AppColors.white,
    height: 1.05,
    letterSpacing: -0.5,
  );

  /// Títulos principales de pantalla
  static const TextStyle displayLg = TextStyle(
    fontFamily: playfair,
    fontSize: 32,
    fontWeight: FontWeight.w900,
    color: AppColors.white,
    height: 1.1,
    letterSpacing: -0.3,
  );

  /// Títulos de sección importantes
  static const TextStyle displayMd = TextStyle(
    fontFamily: playfair,
    fontSize: 24,
    fontWeight: FontWeight.w700,
    color: AppColors.white,
    height: 1.15,
  );

  // ─── Headings — DM Sans ───────────────────────────────────────────────────
  /// Títulos de cards destacadas
  static const TextStyle headingLg = TextStyle(
    fontFamily: dmSans,
    fontSize: 20,
    fontWeight: FontWeight.w600,
    color: AppColors.white,
    height: 1.3,
  );

  /// Títulos de sección
  static const TextStyle headingMd = TextStyle(
    fontFamily: dmSans,
    fontSize: 17,
    fontWeight: FontWeight.w600,
    color: AppColors.white,
    height: 1.35,
  );

  /// Subtítulos
  static const TextStyle headingSm = TextStyle(
    fontFamily: dmSans,
    fontSize: 15,
    fontWeight: FontWeight.w600,
    color: AppColors.white,
    height: 1.4,
  );

  // ─── Body — DM Sans ───────────────────────────────────────────────────────
  /// Texto de cuerpo principal
  static const TextStyle bodyLg = TextStyle(
    fontFamily: dmSans,
    fontSize: 15,
    fontWeight: FontWeight.w400,
    color: AppColors.pearl,
    height: 1.6,
  );

  /// Texto de cuerpo estándar
  static const TextStyle bodyMd = TextStyle(
    fontFamily: dmSans,
    fontSize: 13,
    fontWeight: FontWeight.w400,
    color: AppColors.pearl,
    height: 1.55,
  );

  /// Texto secundario, metadatos
  static const TextStyle bodySm = TextStyle(
    fontFamily: dmSans,
    fontSize: 12,
    fontWeight: FontWeight.w300,
    color: AppColors.silver,
    height: 1.5,
  );

  // ─── Labels — DM Sans ─────────────────────────────────────────────────────
  /// Labels de inputs, etiquetas de sección
  static const TextStyle labelLg = TextStyle(
    fontFamily: dmSans,
    fontSize: 12,
    fontWeight: FontWeight.w600,
    color: AppColors.pearl,
    height: 1.4,
    letterSpacing: 0.2,
  );

  /// Labels pequeños, navegación
  static const TextStyle labelMd = TextStyle(
    fontFamily: dmSans,
    fontSize: 11,
    fontWeight: FontWeight.w500,
    color: AppColors.silver,
    height: 1.4,
    letterSpacing: 0.1,
  );

  /// Micro labels, tags
  static const TextStyle labelSm = TextStyle(
    fontFamily: dmSans,
    fontSize: 10,
    fontWeight: FontWeight.w600,
    color: AppColors.ash,
    height: 1.4,
    letterSpacing: 0.15,
  );

  /// Labels de sección tipo "HORARIOS" — uppercase, tracking
  static const TextStyle overline = TextStyle(
    fontFamily: dmSans,
    fontSize: 10,
    fontWeight: FontWeight.w600,
    color: AppColors.ash,
    height: 1.4,
    letterSpacing: 0.25,
  );

  // ─── Mono — DM Mono ───────────────────────────────────────────────────────
  /// Precios grandes
  static const TextStyle monoXl = TextStyle(
    fontFamily: dmMono,
    fontSize: 28,
    fontWeight: FontWeight.w500,
    color: AppColors.gold,
    height: 1.1,
  );

  /// Precios estándar, tiempos
  static const TextStyle monoLg = TextStyle(
    fontFamily: dmMono,
    fontSize: 18,
    fontWeight: FontWeight.w500,
    color: AppColors.gold,
    height: 1.2,
  );

  /// Precios pequeños, horas, códigos
  static const TextStyle monoMd = TextStyle(
    fontFamily: dmMono,
    fontSize: 13,
    fontWeight: FontWeight.w500,
    color: AppColors.gold,
    height: 1.3,
  );

  /// Micro datos, tags de tiempo
  static const TextStyle monoSm = TextStyle(
    fontFamily: dmMono,
    fontSize: 11,
    fontWeight: FontWeight.w400,
    color: AppColors.silver,
    height: 1.3,
  );

  // ─── Helpers ──────────────────────────────────────────────────────────────
  static TextStyle withColor(TextStyle style, Color color) =>
      style.copyWith(color: color);

  static TextStyle withGold(TextStyle style) =>
      style.copyWith(color: AppColors.gold);

  static TextStyle withOpacity(TextStyle style, double opacity) =>
      style.copyWith(color: style.color?.withOpacity(opacity));
}
