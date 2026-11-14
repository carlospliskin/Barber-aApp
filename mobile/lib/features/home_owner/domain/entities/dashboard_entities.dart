import 'package:freezed_annotation/freezed_annotation.dart';

part 'dashboard_entities.freezed.dart';

// ─── Appointment ──────────────────────────────────────────────────────────────

enum AppointmentStatus {
  pendingConfirmation,
  confirmed,
  completed,
  cancelled,
  noShow,
}

extension AppointmentStatusX on AppointmentStatus {
  String get label => switch (this) {
    AppointmentStatus.pendingConfirmation => 'Pendiente',
    AppointmentStatus.confirmed          => 'Confirmada',
    AppointmentStatus.completed          => 'Completada',
    AppointmentStatus.cancelled          => 'Cancelada',
    AppointmentStatus.noShow             => 'No show',
  };

  static AppointmentStatus fromString(String raw) => switch (raw.toUpperCase()) {
    'PENDING_CONFIRMATION' => AppointmentStatus.pendingConfirmation,
    'CONFIRMED'            => AppointmentStatus.confirmed,
    'COMPLETED'            => AppointmentStatus.completed,
    'CANCELLED'            => AppointmentStatus.cancelled,
    'NO_SHOW'              => AppointmentStatus.noShow,
    _                      => AppointmentStatus.pendingConfirmation,
  };
}

@freezed
class AppointmentClient with _$AppointmentClient {
  const factory AppointmentClient({
    required String id,
    required String name,
    required String phone,
    String? email,
    String? notes,
  }) = _AppointmentClient;
}

@freezed
class AppointmentService with _$AppointmentService {
  const factory AppointmentService({
    required String id,
    required String name,
    required String category,
    required int durationMinutes,
    required String basePrice,
    required String depositAmount,
  }) = _AppointmentService;
}

@freezed
class Appointment with _$Appointment {
  const Appointment._();

  const factory Appointment({
    required String id,
    required String confirmationCode,
    required AppointmentClient client,
    required AppointmentService service,
    required String barberId,
    required String barberName,
    required DateTime startTime,
    required DateTime endTime,
    required AppointmentStatus status,
    required String depositStatus,
    String? depositAmount,
    @Default(0) int rescheduleCount,
    required DateTime createdAt,
  }) = _Appointment;

  // ─── Computed ────────────────────────────────────────────────────────────────

  String get timeRange {
    final start = _formatTime(startTime);
    final end   = _formatTime(endTime);
    return '$start – $end';
  }

  String get startTimeFormatted => _formatTime(startTime);

  bool get isPending  => status == AppointmentStatus.pendingConfirmation;
  bool get isConfirmed => status == AppointmentStatus.confirmed;
  bool get isActive   => status == AppointmentStatus.confirmed ||
                         status == AppointmentStatus.pendingConfirmation;

  bool get requiresDeposit =>
      depositStatus == 'PENDING' || depositStatus == 'NONE';

  String _formatTime(DateTime dt) {
    final h = dt.hour.toString().padLeft(2, '0');
    final m = dt.minute.toString().padLeft(2, '0');
    return '$h:$m';
  }
}

// ─── DashboardMetrics ─────────────────────────────────────────────────────────

@freezed
class DashboardMetrics with _$DashboardMetrics {
  const DashboardMetrics._();

  const factory DashboardMetrics({
    required int appointmentsToday,
    required int appointmentsWeek,
    required String revenueWeek,
    required double attendanceRate,
    required int pendingConfirmations,
    required int completedToday,
  }) = _DashboardMetrics;

  static DashboardMetrics empty() => const DashboardMetrics(
    appointmentsToday: 0,
    appointmentsWeek: 0,
    revenueWeek: '0.00',
    attendanceRate: 0.0,
    pendingConfirmations: 0,
    completedToday: 0,
  );

  String get attendancePercent =>
      '${(attendanceRate * 100).toStringAsFixed(0)}%';
}

// ─── CreateAppointmentResult ──────────────────────────────────────────────────

@freezed
class CreateAppointmentResult with _$CreateAppointmentResult {
  const factory CreateAppointmentResult({
    required String appointmentId,
    required String confirmationCode,
    required String depositStatus,
    String? paymentUrl,           // URL de MercadoPago si requiere depósito
  }) = _CreateAppointmentResult;
}

// ─── DayScheduleInput ────────────────────────────────────────────────────────

@freezed
class DayScheduleInput with _$DayScheduleInput {
  const factory DayScheduleInput({
    required int dayOfWeek,       // 0=Lunes ... 6=Domingo
    required bool isWorking,
    @Default('10:00') String openTime,
    @Default('20:00') String closeTime,
    @Default(1) int capacity,
  }) = _DayScheduleInput;
}

// ─── Service mutations ────────────────────────────────────────────────────────

@freezed
class CreateServiceInput with _$CreateServiceInput {
  const factory CreateServiceInput({
    required String name,
    required String category,
    required int durationMinutes,
    required String basePrice,
    @Default('NONE') String depositType,
    @Default('0') String depositAmount,
    @Default(1) int maxConcurrent,
  }) = _CreateServiceInput;
}
