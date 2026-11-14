import 'package:freezed_annotation/freezed_annotation.dart';

part 'client_entities.freezed.dart';

// ─── Barbería pública ─────────────────────────────────────────────────────────

@freezed
class BarberiaInfo with _$BarberiaInfo {
  const BarberiaInfo._();

  const factory BarberiaInfo({
    required String id,
    required String name,
    required bool isOpen,
    required double avgRating,
    required int reviewCount,
    @Default(false) bool isFavorite,
    @Default([]) List<PublicService> services,
    @Default([]) List<PublicBarber> barbers,
  }) = _BarberiaInfo;

  bool get hasAvailability =>
      barbers.any((b) => b.availableSlots.isNotEmpty);

  String get ratingLabel => avgRating > 0
      ? '★ ${avgRating.toStringAsFixed(1)}'
      : 'Sin calificaciones';
}

@freezed
class PublicService with _$PublicService {
  const PublicService._();

  const factory PublicService({
    required String id,
    required String name,
    required String category,
    required int durationMinutes,
    required String basePrice,
    required String depositAmount,
    required String depositType,
  }) = _PublicService;

  bool get requiresDeposit => depositType != 'NONE';
  String get priceLabel     => '\$$basePrice MXN';
  String get durationLabel  => '${durationMinutes}min';
}

@freezed
class PublicBarber with _$PublicBarber {
  const factory PublicBarber({
    required String id,
    required String name,
    required int capacity,
    required double avgRating,
    @Default([]) List<AvailableSlot> availableSlots,
  }) = _PublicBarber;
}

@freezed
class AvailableSlot with _$AvailableSlot {
  const AvailableSlot._();

  const factory AvailableSlot({
    required String time,      // "10:00"
    required int slotNumber,
    required bool isAvailable,
  }) = _AvailableSlot;

  DateTime toDateTime(DateTime date) {
    final parts = time.split(':');
    return DateTime(date.year, date.month, date.day,
        int.parse(parts[0]), int.parse(parts[1]));
  }
}

// ─── Barbería favorita ────────────────────────────────────────────────────────

@freezed
class FavoriteBarberia with _$FavoriteBarberia {
  const factory FavoriteBarberia({
    required String id,
    required String name,
    required double avgRating,
    required int reviewCount,
    required bool isOpen,
  }) = _FavoriteBarberia;
}

// ─── Cita del cliente ─────────────────────────────────────────────────────────

enum ClientAppointmentStatus {
  pendingConfirmation,
  confirmed,
  completed,
  cancelled,
  noShow,
}

extension ClientAppointmentStatusX on ClientAppointmentStatus {
  String get label => switch (this) {
    ClientAppointmentStatus.pendingConfirmation => 'Pendiente',
    ClientAppointmentStatus.confirmed           => 'Confirmada',
    ClientAppointmentStatus.completed           => 'Completada',
    ClientAppointmentStatus.cancelled           => 'Cancelada',
    ClientAppointmentStatus.noShow              => 'No show',
  };

  bool get isActive => this == ClientAppointmentStatus.confirmed ||
      this == ClientAppointmentStatus.pendingConfirmation;

  bool get canCancel => this == ClientAppointmentStatus.confirmed ||
      this == ClientAppointmentStatus.pendingConfirmation;

  bool get canReschedule => this == ClientAppointmentStatus.confirmed;

  static ClientAppointmentStatus fromString(String raw) =>
      switch (raw.toUpperCase()) {
        'PENDING_CONFIRMATION' => ClientAppointmentStatus.pendingConfirmation,
        'CONFIRMED'            => ClientAppointmentStatus.confirmed,
        'COMPLETED'            => ClientAppointmentStatus.completed,
        'CANCELLED'            => ClientAppointmentStatus.cancelled,
        'NO_SHOW'              => ClientAppointmentStatus.noShow,
        _                      => ClientAppointmentStatus.pendingConfirmation,
      };
}

@freezed
class ClientAppointment with _$ClientAppointment {
  const ClientAppointment._();

  const factory ClientAppointment({
    required String id,
    required String confirmationCode,
    required String barberiaId,
    required String barberiaName,
    required String barberName,
    required String serviceName,
    required String serviceCategory,
    required int durationMinutes,
    required String basePrice,
    required DateTime startTime,
    required DateTime endTime,
    required ClientAppointmentStatus status,
    required String depositStatus,
    String? depositAmount,
    String? paymentUrl,
    @Default(0) int rescheduleCount,
  }) = _ClientAppointment;

  String get timeRange {
    final h1 = startTime.hour.toString().padLeft(2, '0');
    final m1 = startTime.minute.toString().padLeft(2, '0');
    final h2 = endTime.hour.toString().padLeft(2, '0');
    final m2 = endTime.minute.toString().padLeft(2, '0');
    return '$h1:$m1 – $h2:$m2';
  }

  String get startTimeFormatted {
    final h = startTime.hour.toString().padLeft(2, '0');
    final m = startTime.minute.toString().padLeft(2, '0');
    return '$h:$m';
  }

  bool get requiresPayment =>
      depositStatus == 'PENDING' && paymentUrl != null;

  bool get isPenaltyRisk => rescheduleCount >= 2;
}

// ─── Resultado de crear reserva ───────────────────────────────────────────────

@freezed
class BookingResult with _$BookingResult {
  const BookingResult._();

  const factory BookingResult({
    required String appointmentId,
    required String confirmationCode,
    required String depositStatus,
    String? paymentUrl,
  }) = _BookingResult;

  bool get requiresPayment =>
      depositStatus == 'PENDING' && paymentUrl != null;
}

// ─── Progreso de lealtad del cliente ─────────────────────────────────────────

@freezed
class ClientLoyaltyStatus with _$ClientLoyaltyStatus {
  const ClientLoyaltyStatus._();

  const factory ClientLoyaltyStatus({
    required String id,
    required String programId,
    required String programName,
    required int visitsCount,
    required int totalVisitsEver,
    DateTime? lastVisitAt,
    @Default([]) List<LoyaltyRuleProgress> rulesProgress,
  }) = _ClientLoyaltyStatus;

  bool get hasProgress => visitsCount > 0;
}

@freezed
class LoyaltyRuleProgress with _$LoyaltyRuleProgress {
  const LoyaltyRuleProgress._();

  const factory LoyaltyRuleProgress({
    required String ruleId,
    required String ruleName,
    required String description,
    required int visitsCount,
    required int visitsRequired,
    required String rewardType,
    double? rewardValue,
  }) = _LoyaltyRuleProgress;

  double get progressPercent =>
      visitsRequired > 0 ? (visitsCount / visitsRequired).clamp(0.0, 1.0) : 0.0;

  int get visitsRemaining =>
      (visitsRequired - visitsCount).clamp(0, visitsRequired);

  bool get isCompleted => visitsCount >= visitsRequired;

  String get rewardLabel => switch (rewardType) {
    'FREE_SERVICE'   => 'Servicio gratis',
    'DISCOUNT_PCT'   => '${rewardValue?.toInt() ?? 0}% descuento',
    'DISCOUNT_FIXED' => '\$${rewardValue?.toStringAsFixed(0) ?? 0} MXN',
    _                => 'Beneficio',
  };
}
