import 'package:freezed_annotation/freezed_annotation.dart';
import '../../domain/entities/dashboard_entities.dart';
import '../../domain/entities/loyalty_entities.dart';
import '../../domain/failures/dashboard_failure.dart';

part 'home_owner_state.freezed.dart';

@freezed
class HomeOwnerState with _$HomeOwnerState {
  const HomeOwnerState._();

  const factory HomeOwnerState({
    // Citas del día
    @Default([]) List<Appointment> appointments,
    DashboardMetrics? metrics,
    @Default(false) bool appointmentsLoading,

    // Estado de apertura
    TenantOpenStatus? openStatus,
    @Default(false) bool togglingStatus,

    // Servicios (grid)
    @Default([]) List<OwnerService> services,
    @Default(false) bool servicesLoading,

    // Barberos con horario hoy
    @Default([]) List<BarberWithSchedule> barbers,

    // Fidelización
    LoyaltyProgram? loyaltyProgram,
    @Default(false) bool loyaltyLoading,

    // Fecha seleccionada
    required DateTime selectedDate,

    // Error global (no bloquea toda la UI)
    DashboardFailure? failure,
  }) = _HomeOwnerState;

  // ─── Computed ─────────────────────────────────────────────────────────────

  bool get isOpen => openStatus?.isOpen ?? false;

  bool get isLoadingAny =>
      appointmentsLoading || servicesLoading || loyaltyLoading;

  List<Appointment> get todayAppointments =>
      appointments.where((a) => a.status != AppointmentStatus.cancelled).toList();

  int get pendingCount =>
      appointments.where((a) => a.isPending).length;

  bool get hasBarbersWorkingToday =>
      barbers.any((b) => b.isWorkingToday);
}
