import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/providers/infrastructure_providers.dart';
import '../../../../features/auth/presentation/notifiers/auth_notifier.dart';
import '../../data/datasources/client_remote_datasource.dart';
import '../../data/repositories/client_repository_impl.dart';
import '../../domain/entities/client_entities.dart';
import '../../domain/repositories/i_client_repository.dart';
import '../../domain/usecases/client_use_cases.dart';
import 'home_client_state.dart';

// ─── Providers de infraestructura ─────────────────────────────────────────────

final _clientDatasourceProvider = Provider<ClientRemoteDatasource>((ref) =>
  ClientRemoteDatasource(client: ref.read(httpClientProvider)),
);

final _barberiaRepoProvider = Provider<IBarberiaRepository>((ref) =>
  BarberiaRepositoryImpl(remote: ref.read(_clientDatasourceProvider)),
);

final _bookingRepoProvider = Provider<IBookingRepository>((ref) =>
  BookingRepositoryImpl(remote: ref.read(_clientDatasourceProvider)),
);

final _clientLoyaltyRepoProvider = Provider<IClientLoyaltyRepository>((ref) =>
  ClientLoyaltyRepositoryImpl(remote: ref.read(_clientDatasourceProvider)),
);

// ─── Providers de UseCases ────────────────────────────────────────────────────

final _searchUC   = Provider<SearchBarberiasUseCase>((ref) =>
  SearchBarberiasUseCase(ref.read(_barberiaRepoProvider)));
final _detailUC   = Provider<GetBarberiaDetailUseCase>((ref) =>
  GetBarberiaDetailUseCase(ref.read(_barberiaRepoProvider)));
final _favoritesUC= Provider<GetFavoritesUseCase>((ref) =>
  GetFavoritesUseCase(ref.read(_barberiaRepoProvider)));
final _addFavUC   = Provider<AddFavoriteUseCase>((ref) =>
  AddFavoriteUseCase(ref.read(_barberiaRepoProvider)));
final _removeFavUC= Provider<RemoveFavoriteUseCase>((ref) =>
  RemoveFavoriteUseCase(ref.read(_barberiaRepoProvider)));
final _myAptsUC   = Provider<GetMyAppointmentsUseCase>((ref) =>
  GetMyAppointmentsUseCase(ref.read(_bookingRepoProvider)));
final _createBookUC= Provider<CreateBookingUseCase>((ref) =>
  CreateBookingUseCase(ref.read(_bookingRepoProvider)));
final _cancelUC   = Provider<CancelAppointmentUseCase>((ref) =>
  CancelAppointmentUseCase(ref.read(_bookingRepoProvider)));
final _loyaltyUC  = Provider<GetMyLoyaltyUseCase>((ref) =>
  GetMyLoyaltyUseCase(ref.read(_clientLoyaltyRepoProvider)));

// ─── HomeClientNotifier ───────────────────────────────────────────────────────

class HomeClientNotifier extends StateNotifier<HomeClientState> {
  final SearchBarberiasUseCase _search;
  final GetBarberiaDetailUseCase _detail;
  final GetFavoritesUseCase _getFavorites;
  final AddFavoriteUseCase _addFav;
  final RemoveFavoriteUseCase _removeFav;
  final GetMyAppointmentsUseCase _getMyApts;
  final CreateBookingUseCase _createBook;
  final CancelAppointmentUseCase _cancel;
  final GetMyLoyaltyUseCase _getLoyalty;
  final String _token;

  HomeClientNotifier({
    required SearchBarberiasUseCase search,
    required GetBarberiaDetailUseCase detail,
    required GetFavoritesUseCase getFavorites,
    required AddFavoriteUseCase addFav,
    required RemoveFavoriteUseCase removeFav,
    required GetMyAppointmentsUseCase getMyApts,
    required CreateBookingUseCase createBook,
    required CancelAppointmentUseCase cancel,
    required GetMyLoyaltyUseCase getLoyalty,
    required String token,
  })  : _search     = search,
        _detail     = detail,
        _getFavorites = getFavorites,
        _addFav     = addFav,
        _removeFav  = removeFav,
        _getMyApts  = getMyApts,
        _createBook = createBook,
        _cancel     = cancel,
        _getLoyalty = getLoyalty,
        _token      = token,
        super(const HomeClientState()) {
    _init();
  }

  // ─── Init ──────────────────────────────────────────────────────────────────

  Future<void> _init() async {
    await Future.wait([
      _loadBarberías(),
      _loadFavorites(),
      _loadAppointments(),
    ]);
  }

  // ─── Barberías ─────────────────────────────────────────────────────────────

  Future<void> _loadBarberías({String? date}) async {
    state = state.copyWith(barberíasLoading: true, failure: null);
    final result = await _search(token: _token, date: date);
    state = result.fold(
      (f) => state.copyWith(barberíasLoading: false, failure: f),
      (bs) => state.copyWith(barberíasLoading: false, barberías: bs),
    );
  }

  Future<void> refresh() => _init();

  Future<void> searchForDate(DateTime date) {
    final dateStr = '${date.year}-${date.month.toString().padLeft(2,'0')}-${date.day.toString().padLeft(2,'0')}';
    return _loadBarberías(date: dateStr);
  }

  // ─── Detalle de barbería ──────────────────────────────────────────────────

  Future<void> selectBarberia(String barberiaId, {String? date}) async {
    state = state.copyWith(
      detailLoading: true,
      selectedBarberia: null,
      selectedService: null,
      selectedBarber: null,
      selectedSlot: null,
    );
    final dateStr = date ?? _todayStr();
    final result = await _detail(
      barberiaId: barberiaId, token: _token, date: dateStr);
    state = result.fold(
      (f) => state.copyWith(detailLoading: false, failure: f),
      (b) => state.copyWith(detailLoading: false, selectedBarberia: b),
    );
    // Cargar lealtad si tiene el programa
    if (result.isRight()) _loadLoyalty(barberiaId);
  }

  Future<void> _loadLoyalty(String barberiaId) async {
    final result = await _getLoyalty(
      barberiaId: barberiaId, token: _token);
    result.fold(
      (_) => null,
      (l) => state = state.copyWith(loyaltyStatus: l),
    );
  }

  // ─── Selección del flujo de booking ───────────────────────────────────────

  void selectService(PublicService service) {
    state = state.copyWith(
      selectedService: service,
      selectedBarber:  null,   // resetear barbero al cambiar servicio
      selectedSlot:    null,
    );
  }

  void selectBarber(PublicBarber barber) {
    state = state.copyWith(
      selectedBarber: barber,
      selectedSlot:   null,    // resetear slot al cambiar barbero
    );
  }

  void selectDate(DateTime date) {
    state = state.copyWith(selectedDate: date, selectedSlot: null);
    // Recargar disponibilidad para la fecha seleccionada
    if (state.selectedBarberia != null) {
      selectBarberia(state.selectedBarberia!.id, date: _dateStr(date));
    }
  }

  void selectSlot(AvailableSlot slot) {
    state = state.copyWith(selectedSlot: slot);
  }

  void clearBookingSelection() {
    state = state.copyWith(
      selectedService: null,
      selectedBarber:  null,
      selectedDate:    null,
      selectedSlot:    null,
    );
  }

  // ─── Confirmar reserva ────────────────────────────────────────────────────

  Future<BookingResult?> confirmBooking() async {
    if (!state.canBook) return null;

    state = state.copyWith(isBooking: true);

    final slot     = state.selectedSlot!;
    final date     = state.selectedDate!;
    final startTime = slot.toDateTime(date);

    final result = await _createBook(
      barberiaId: state.selectedBarberia!.id,
      serviceId:  state.selectedService!.id,
      barberId:   state.selectedBarber!.id,
      startTime:  startTime,
      token:      _token,
    );

    state = state.copyWith(isBooking: false);

    return result.fold(
      (f) {
        state = state.copyWith(failure: f);
        return null;
      },
      (booking) {
        // Limpiar selección y recargar citas
        clearBookingSelection();
        _loadAppointments();
        return booking;
      },
    );
  }

  // ─── Mis citas ────────────────────────────────────────────────────────────

  Future<void> _loadAppointments() async {
    state = state.copyWith(appointmentsLoading: true);
    final result = await _getMyApts(token: _token);
    state = result.fold(
      (f) => state.copyWith(appointmentsLoading: false),
      (apts) => state.copyWith(
        appointmentsLoading: false, appointments: apts),
    );
  }

  Future<bool> cancelAppointment(String appointmentId) async {
    state = state.copyWith(isCancelling: true);
    final result = await _cancel(
      appointmentId: appointmentId, token: _token);
    state = state.copyWith(isCancelling: false);

    return result.fold(
      (f) {
        state = state.copyWith(failure: f);
        return false;
      },
      (_) {
        // Actualizar localmente
        state = state.copyWith(
          appointments: state.appointments.map((a) =>
            a.id == appointmentId
                ? a.copyWith(status: ClientAppointmentStatus.cancelled)
                : a,
          ).toList(),
        );
        return true;
      },
    );
  }

  // ─── Favoritos ────────────────────────────────────────────────────────────

  Future<void> _loadFavorites() async {
    state = state.copyWith(favoritesLoading: true);
    final result = await _getFavorites(token: _token);
    state = result.fold(
      (_) => state.copyWith(favoritesLoading: false),
      (favs) => state.copyWith(
        favoritesLoading: false, favorites: favs),
    );
  }

  Future<void> toggleFavorite(String barberiaId) async {
    final isFav = state.favorites.any((f) => f.id == barberiaId);

    if (isFav) {
      await _removeFav(barberiaId: barberiaId, token: _token);
      state = state.copyWith(
        favorites: state.favorites.where((f) => f.id != barberiaId).toList(),
      );
    } else {
      await _addFav(barberiaId: barberiaId, token: _token);
      // Recargar para obtener datos completos
      await _loadFavorites();
    }

    // Actualizar isFavorite en la lista de barberías
    state = state.copyWith(
      barberías: state.barberías.map((b) =>
        b.id == barberiaId ? b.copyWith(isFavorite: !isFav) : b,
      ).toList(),
      selectedBarberia: state.selectedBarberia?.id == barberiaId
          ? state.selectedBarberia?.copyWith(isFavorite: !isFav)
          : state.selectedBarberia,
    );
  }

  // ─── Navegación entre tabs ────────────────────────────────────────────────

  void setTab(int index) => state = state.copyWith(activeTab: index);

  void clearError() => state = state.copyWith(failure: null);

  // ─── Helpers ──────────────────────────────────────────────────────────────

  String _todayStr() => _dateStr(DateTime.now());

  String _dateStr(DateTime d) =>
      '${d.year}-${d.month.toString().padLeft(2,'0')}-${d.day.toString().padLeft(2,'0')}';
}

// ─── Provider público ─────────────────────────────────────────────────────────

final homeClientNotifierProvider =
    StateNotifierProvider<HomeClientNotifier, HomeClientState>((ref) {
  final user = ref.watch(currentUserProvider);
  return HomeClientNotifier(
    search:       ref.read(_searchUC),
    detail:       ref.read(_detailUC),
    getFavorites: ref.read(_favoritesUC),
    addFav:       ref.read(_addFavUC),
    removeFav:    ref.read(_removeFavUC),
    getMyApts:    ref.read(_myAptsUC),
    createBook:   ref.read(_createBookUC),
    cancel:       ref.read(_cancelUC),
    getLoyalty:   ref.read(_loyaltyUC),
    token:        user?.token ?? '',
  );
});
