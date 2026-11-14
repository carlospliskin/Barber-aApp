import 'package:freezed_annotation/freezed_annotation.dart';
import '../../domain/entities/client_entities.dart';
import '../../domain/failures/client_failure.dart';

part 'home_client_state.freezed.dart';

@freezed
class HomeClientState with _$HomeClientState {
  const HomeClientState._();

  const factory HomeClientState({
    // Barberías
    @Default([]) List<BarberiaInfo> barberías,
    @Default(false) bool barberíasLoading,

    // Favoritas
    @Default([]) List<FavoriteBarberia> favorites,
    @Default(false) bool favoritesLoading,

    // Mis citas
    @Default([]) List<ClientAppointment> appointments,
    @Default(false) bool appointmentsLoading,

    // Detalle de barbería seleccionada (para el booking)
    BarberiaInfo? selectedBarberia,
    @Default(false) bool detailLoading,

    // Lealtad en la barbería seleccionada
    ClientLoyaltyStatus? loyaltyStatus,

    // Booking en curso
    PublicService? selectedService,
    PublicBarber? selectedBarber,
    DateTime? selectedDate,
    AvailableSlot? selectedSlot,
    @Default(false) bool isBooking,

    // Cancelación
    @Default(false) bool isCancelling,

    // Vista activa (0=Inicio, 1=Mis citas, 2=Favoritas)
    @Default(0) int activeTab,

    // Error no bloqueante
    ClientFailure? failure,
  }) = _HomeClientState;

  // ─── Computed ──────────────────────────────────────────────────────────────

  List<ClientAppointment> get upcomingAppointments =>
      appointments.where((a) => a.status.isActive).toList()
        ..sort((a, b) => a.startTime.compareTo(b.startTime));

  List<ClientAppointment> get pastAppointments =>
      appointments.where((a) => !a.status.isActive).toList()
        ..sort((a, b) => b.startTime.compareTo(a.startTime));

  bool get canBook =>
      selectedService != null &&
      selectedBarber != null &&
      selectedDate != null &&
      selectedSlot != null;

  List<AvailableSlot> get slotsForSelectedBarber =>
      selectedBarber?.availableSlots
          .where((s) => s.isAvailable)
          .toList() ?? [];

  bool get isLoadingAny =>
      barberíasLoading || favoritesLoading || appointmentsLoading;
}
