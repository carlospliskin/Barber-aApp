import 'package:freezed_annotation/freezed_annotation.dart';

part 'loyalty_entities.freezed.dart';

// ─── Estado de apertura ───────────────────────────────────────────────────────

enum OpenStatusSource { manualOverride, schedule, noSchedule }

@freezed
class TenantOpenStatus with _$TenantOpenStatus {
  const TenantOpenStatus._();

  const factory TenantOpenStatus({
    required bool isOpen,
    required OpenStatusSource source,
    String? opensAt,
    String? closesAt,
  }) = _TenantOpenStatus;

  /// El dueño tiene control manual activo
  bool get isManualOverride => source == OpenStatusSource.manualOverride;

  /// Texto para mostrar en UI
  String get label => isOpen ? 'ABIERTO' : 'CERRADO';

  /// El dueño puede cambiar el estado
  bool get canToggle => true;
}

// ─── Tipos de regla ───────────────────────────────────────────────────────────

enum LoyaltyRuleType { visitCount, combo, custom }

enum LoyaltyRewardType { freeService, discountPct, discountFixed }

extension LoyaltyRewardTypeX on LoyaltyRewardType {
  String label(double value) => switch (this) {
    LoyaltyRewardType.freeService   => 'Servicio gratis',
    LoyaltyRewardType.discountPct   => '${value.toInt()}% descuento',
    LoyaltyRewardType.discountFixed => '\$${value.toStringAsFixed(0)} MXN descuento',
  };
}

// ─── Regla de fidelización ────────────────────────────────────────────────────

@freezed
class LoyaltyRule with _$LoyaltyRule {
  const LoyaltyRule._();

  const factory LoyaltyRule({
    required String id,
    required String programId,
    required String name,
    required String description,
    required LoyaltyRuleType type,
    int? triggerValue,
    required LoyaltyRewardType rewardType,
    double? rewardValue,
    @Default(true) bool isActive,
    @Default(0) int sortOrder,
  }) = _LoyaltyRule;

  /// Texto corto para mostrar en card (ej. "5 visitas → Corte gratis")
  String get shortDescription {
    final trigger = triggerValue != null ? 'Cada $triggerValue visitas' : '';
    final reward  = rewardValue != null
        ? rewardType.label(rewardValue!)
        : 'Beneficio';
    return '$trigger → $reward';
  }
}

// ─── Programa de fidelización ─────────────────────────────────────────────────

@freezed
class LoyaltyProgram with _$LoyaltyProgram {
  const LoyaltyProgram._();

  const factory LoyaltyProgram({
    required String id,
    required String tenantId,
    required String name,
    String? description,
    required List<LoyaltyRule> rules,
    @Default(true) bool isActive,
    @Default(0) int totalMembers,
  }) = _LoyaltyProgram;

  List<LoyaltyRule> get activeRules =>
      rules.where((r) => r.isActive).toList();

  bool get hasRules => rules.isNotEmpty;
}

// ─── Progreso de un cliente ───────────────────────────────────────────────────

@freezed
class ClientLoyaltyProgress with _$ClientLoyaltyProgress {
  const ClientLoyaltyProgress._();

  const factory ClientLoyaltyProgress({
    required String id,
    required String clientUserId,
    required String programId,
    required int visitsCount,
    required int totalVisitsEver,
    DateTime? lastVisitAt,
  }) = _ClientLoyaltyProgress;

  /// Visitas hasta el próximo beneficio dado una regla
  int visitsUntilReward(LoyaltyRule rule) {
    if (rule.triggerValue == null) return 0;
    final mod = visitsCount % rule.triggerValue!;
    return mod == 0 ? 0 : rule.triggerValue! - mod;
  }

  /// Porcentaje de progreso hacia el próximo beneficio
  double progressPercent(LoyaltyRule rule) {
    if (rule.triggerValue == null || rule.triggerValue! == 0) return 0;
    return (visitsCount % rule.triggerValue!) / rule.triggerValue!;
  }
}

// ─── Servicio del Owner (para el grid) ───────────────────────────────────────

@freezed
class OwnerService with _$OwnerService {
  const OwnerService._();

  const factory OwnerService({
    required String id,
    required String name,
    required String category,
    required int durationMinutes,
    required String basePrice,
    required String depositType,
    required String depositAmount,
    required int maxConcurrent,
    @Default(true) bool isActive,
  }) = _OwnerService;

  String get durationLabel => '${durationMinutes}min';
  String get priceLabel    => '\$$basePrice';
  bool get hasDeposit      => depositType != 'NONE';
}

// ─── Barbero con horario de hoy ───────────────────────────────────────────────

@freezed
class BarberWithSchedule with _$BarberWithSchedule {
  const BarberWithSchedule._();

  const factory BarberWithSchedule({
    required String memberId,
    required String name,
    required bool isWorkingToday,
    String? openTime,
    String? closeTime,
    @Default(1) int capacity,
  }) = _BarberWithSchedule;

  String get scheduleLabel {
    if (!isWorkingToday) return 'Libre hoy';
    if (openTime != null && closeTime != null) return '$openTime – $closeTime';
    return 'Sin horario';
  }
}
