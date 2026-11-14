import 'package:freezed_annotation/freezed_annotation.dart';

part 'loyalty_entities.freezed.dart';

// ─── Enums ────────────────────────────────────────────────────────────────────

enum LoyaltyProgramType { visitCount, combo, birthday, custom }

enum LoyaltyRewardType { freeService, discountPercent, discountFixed }

extension LoyaltyRewardTypeX on LoyaltyRewardType {
  static LoyaltyRewardType fromString(String raw) => switch (raw.toUpperCase()) {
    'FREE_SERVICE'      => LoyaltyRewardType.freeService,
    'DISCOUNT_PERCENT'  => LoyaltyRewardType.discountPercent,
    'DISCOUNT_FIXED'    => LoyaltyRewardType.discountFixed,
    _                   => LoyaltyRewardType.discountPercent,
  };
}

extension LoyaltyProgramTypeX on LoyaltyProgramType {
  static LoyaltyProgramType fromString(String raw) => switch (raw.toUpperCase()) {
    'VISIT_COUNT' => LoyaltyProgramType.visitCount,
    'COMBO'       => LoyaltyProgramType.combo,
    'BIRTHDAY'    => LoyaltyProgramType.birthday,
    _             => LoyaltyProgramType.custom,
  };
}

// ─── LoyaltyProgram ───────────────────────────────────────────────────────────

@freezed
class LoyaltyProgram with _$LoyaltyProgram {
  const LoyaltyProgram._();

  const factory LoyaltyProgram({
    required String id,
    required LoyaltyProgramType programType,
    required String title,
    String? description,
    required int requiredVisits,
    required LoyaltyRewardType rewardType,
    required String rewardValue,
    @Default(true) bool isActive,
  }) = _LoyaltyProgram;

  String get rewardDescription => switch (rewardType) {
    LoyaltyRewardType.freeService     => 'Servicio gratis',
    LoyaltyRewardType.discountPercent => '$rewardValue% de descuento',
    LoyaltyRewardType.discountFixed   => '\$$rewardValue MXN de descuento',
  };

  String get conditionText => 'Cada $requiredVisits visitas';

  // Icono semántico para UI
  String get emoji => switch (rewardType) {
    LoyaltyRewardType.freeService     => '🎁',
    LoyaltyRewardType.discountPercent => '🏷️',
    LoyaltyRewardType.discountFixed   => '💰',
  };
}

// ─── ClientLoyalty ────────────────────────────────────────────────────────────

@freezed
class ClientLoyalty with _$ClientLoyalty {
  const ClientLoyalty._();

  const factory ClientLoyalty({
    required int totalVisits,
    required int currentCycleVisits,
    required int pendingRewardsCount,
    DateTime? lastVisitAt,
  }) = _ClientLoyalty;

  /// Progreso en el ciclo actual (0.0 a 1.0) para un programa dado
  double progressFor(LoyaltyProgram program) {
    if (program.requiredVisits == 0) return 1.0;
    return (currentCycleVisits / program.requiredVisits).clamp(0.0, 1.0);
  }

  bool hasReachedGoal(LoyaltyProgram program) =>
      currentCycleVisits >= program.requiredVisits;
}

// ─── TenantOpenStatus ─────────────────────────────────────────────────────────

@freezed
class TenantOpenStatus with _$TenantOpenStatus {
  const TenantOpenStatus._();

  const factory TenantOpenStatus({
    required bool isOpen,
    @Default(false) bool isManualOverride,
    String? overrideReason,
    DateTime? overrideUntil,
  }) = _TenantOpenStatus;

  String get statusLabel => isOpen ? 'ABIERTO' : 'CERRADO';

  String get sourceLabel => isManualOverride
      ? (isOpen ? 'Abierto manualmente' : 'Cerrado manualmente')
      : (isOpen ? 'Abierto por horario' : 'Fuera de horario');
}

// ─── Predefined program templates (para el dueño al crear rápido) ─────────────

class LoyaltyProgramTemplate {
  final String title;
  final String description;
  final int requiredVisits;
  final LoyaltyRewardType rewardType;
  final String rewardValue;
  final String emoji;

  const LoyaltyProgramTemplate({
    required this.title,
    required this.description,
    required this.requiredVisits,
    required this.rewardType,
    required this.rewardValue,
    required this.emoji,
  });

  static const List<LoyaltyProgramTemplate> defaults = [
    LoyaltyProgramTemplate(
      title: 'Cada 5 cortes',
      description: 'Tu 5to corte es completamente gratis',
      requiredVisits: 5,
      rewardType: LoyaltyRewardType.freeService,
      rewardValue: '100',
      emoji: '✂️',
    ),
    LoyaltyProgramTemplate(
      title: 'Corte + Barba',
      description: '20% de descuento al combinar corte y barba',
      requiredVisits: 3,
      rewardType: LoyaltyRewardType.discountPercent,
      rewardValue: '20',
      emoji: '💈',
    ),
    LoyaltyProgramTemplate(
      title: 'Cliente frecuente',
      description: '\$50 de descuento en tu 10ma visita',
      requiredVisits: 10,
      rewardType: LoyaltyRewardType.discountFixed,
      rewardValue: '50',
      emoji: '⭐',
    ),
  ];
}
