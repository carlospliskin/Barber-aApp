import 'package:flutter/material.dart';

/// Paleta cromática de Barbería SaaS
/// Concepto: Obsidian base + Gold accent
/// Cada color tiene un propósito semántico claro

abstract class AppColors {
  // ─── Base — Escala de negro ───────────────────────────────────────────────
  /// Fondo principal de toda la app
  static const Color obsidian = Color(0xFF0A0A0B);

  /// Fondo de pantallas secundarias
  static const Color carbon = Color(0xFF111114);

  /// Cards, contenedores elevados
  static const Color graphite = Color(0xFF1A1A1F);

  /// Items dentro de cards, inputs
  static const Color slate = Color(0xFF242429);

  /// Separadores, bordes visibles
  static const Color smoke = Color(0xFF2E2E35);

  /// Bordes sutiles, divisores
  static const Color mist = Color(0xFF3D3D47);

  /// Texto deshabilitado, placeholders
  static const Color ash = Color(0xFF6B6B78);

  /// Texto secundario, labels
  static const Color silver = Color(0xFF9898A8);

  /// Texto secundario fuerte
  static const Color pearl = Color(0xFFC8C8D8);

  /// Texto principal ligero
  static const Color ivory = Color(0xFFF0F0F5);

  /// Texto principal máximo contraste
  static const Color white = Color(0xFFFAFAFA);

  // ─── Gold — Acento principal ──────────────────────────────────────────────
  /// Acento primario — botones, highlights, iconos activos
  static const Color gold = Color(0xFFC9A84C);

  /// Gold más claro — hover states, backgrounds suaves
  static const Color goldLight = Color(0xFFE8C97A);

  /// Gold más oscuro — pressed states, gradientes
  static const Color goldDeep = Color(0xFFA07832);

  /// Gold con opacidad — backgrounds decorativos
  static const Color goldSubtle = Color(0x26C9A84C); // 15% opacity
  static const Color goldGlow = Color(0x4DC9A84C);   // 30% opacity

  // ─── Gradientes gold ──────────────────────────────────────────────────────
  static const LinearGradient goldGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [goldDeep, gold],
  );

  static const LinearGradient goldGradientH = LinearGradient(
    begin: Alignment.centerLeft,
    end: Alignment.centerRight,
    colors: [goldDeep, goldLight],
  );

  // ─── Status ───────────────────────────────────────────────────────────────
  /// Cita confirmada, operación exitosa
  static const Color confirmed = Color(0xFF3A9B6F);
  static const Color confirmedSubtle = Color(0x263A9B6F);

  /// Pendiente de pago o confirmación
  static const Color pending = Color(0xFFC9A84C); // = gold
  static const Color pendingSubtle = Color(0x26C9A84C);

  /// Error, cancelación, penalización
  static const Color error = Color(0xFFC44B3A);
  static const Color errorSubtle = Color(0x26C44B3A);

  /// No-show
  static const Color noShow = Color(0xFF7B4FB5);
  static const Color noShowSubtle = Color(0x267B4FB5);

  // ─── Fondo de pantalla ────────────────────────────────────────────────────
  static const RadialGradient loginBg = RadialGradient(
    center: Alignment(0.0, -0.3),
    radius: 1.2,
    colors: [
      Color(0xFF1A1410), // warm dark
      Color(0xFF0A0A0B), // pure obsidian
    ],
  );

  // ─── Sombras ──────────────────────────────────────────────────────────────
  static List<BoxShadow> get shadowSm => [
    BoxShadow(
      color: Colors.black.withOpacity(0.4),
      blurRadius: 8,
      offset: const Offset(0, 2),
    ),
  ];

  static List<BoxShadow> get shadowMd => [
    BoxShadow(
      color: Colors.black.withOpacity(0.5),
      blurRadius: 32,
      offset: const Offset(0, 8),
    ),
  ];

  static List<BoxShadow> get shadowLg => [
    BoxShadow(
      color: Colors.black.withOpacity(0.6),
      blurRadius: 64,
      offset: const Offset(0, 24),
    ),
  ];

  static List<BoxShadow> get shadowGold => [
    BoxShadow(
      color: gold.withOpacity(0.25),
      blurRadius: 24,
      spreadRadius: 0,
    ),
  ];

  static List<BoxShadow> get shadowGoldBtn => [
    BoxShadow(
      color: gold.withOpacity(0.35),
      blurRadius: 16,
      offset: const Offset(0, 4),
    ),
  ];
}
