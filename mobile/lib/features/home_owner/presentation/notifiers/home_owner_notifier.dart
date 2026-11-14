import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/providers/infrastructure_providers.dart';
import '../../../../features/auth/presentation/notifiers/auth_notifier.dart';
import '../../data/datasources/dashboard_remote_datasource.dart';
import '../../data/datasources/loyalty_remote_datasource.dart';
import '../../data/repositories/dashboard_repository_impl.dart';
import '../../data/repositories/loyalty_repository_impl.dart';
import '../../domain/entities/loyalty_entities.dart';
import '../../domain/repositories/i_dashboard_repository.dart';
import '../../domain/repositories/i_loyalty_repository.dart';
import '../../domain/usecases/dashboard_use_cases.dart';
import '../../domain/usecases/loyalty_use_cases.dart';
import 'home_owner_state.dart';

// ─── Providers de infraestructura ─────────────────────────────────────────────

final _dashboardDatasourceProvider = Provider<DashboardRemoteDatasource>((ref) =>
  DashboardRemoteDatasource(client: ref.read(httpClientProvider)),
);

final _loyaltyDatasourceProvider = Provider<LoyaltyRemoteDatasource>((ref) =>
  LoyaltyRemoteDatasource(client: ref.read(httpClientProvider)),
);

final _dashboardRepoProvider = Provider<IDashboardRepository>((ref) =>
  DashboardRepositoryImpl(remote: ref.read(_dashboardDatasourceProvider)),
);

final _tenantStatusRepoProvider = Provider<ITenantStatusRepository>((ref) =>
  TenantStatusRepositoryImpl(remote: ref.read(_loyaltyDatasourceProvider)),
);

final _loyaltyRepoProvider = Provider<ILoyaltyRepository>((ref) =>
  LoyaltyRepositoryImpl(remote: ref.read(_loyaltyDatasourceProvider)),
);

// ─── Providers de UseCases ────────────────────────────────────────────────────

final _getAppointmentsUC = Provider<GetAppointmentsByDateUseCase>((ref) =>
  GetAppointmentsByDateUseCase(ref.read(_dashboardRepoProvider)),
);
final _getMetricsUC = Provider<GetDashboardMetricsUseCase>((ref) =>
  GetDashboardMetricsUseCase(ref.read(_dashboardRepoProvider)),
);
final _markNoShowUC = Provider<MarkNoShowUseCase>((ref) =>
  MarkNoShowUseCase(ref.read(_dashboardRepoProvider)),
);
final _getTenantStatusUC = Provider<GetTenantStatusUseCase>((ref) =>
  GetTenantStatusUseCase(ref.read(_tenantStatusRepoProvider)),
);
final _toggleStatusUC = Provider<ToggleTenantOpenUseCase>((ref) =>
  ToggleTenantOpenUseCase(ref.read(_tenantStatusRepoProvider)),
);
final _getLoyaltyUC = Provider<GetLoyaltyProgramUseCase>((ref) =>
  GetLoyaltyProgramUseCase(ref.read(_loyaltyRepoProvider)),
);
final _addRuleUC = Provider<AddLoyaltyRuleUseCase>((ref) =>
  AddLoyaltyRuleUseCase(ref.read(_loyaltyRepoProvider)),
);
final _toggleRuleUC = Provider<ToggleLoyaltyRuleUseCase>((ref) =>
  ToggleLoyaltyRuleUseCase(ref.read(_loyaltyRepoProvider)),
);
final _getServicesUC = Provider<GetOwnerServicesUseCase>((ref) =>
  GetOwnerServicesUseCase(ref.read(_loyaltyRepoProvider)),
);
final _getBarbersUC = Provider<GetBarbersWithScheduleUseCase>((ref) =>
  GetBarbersWithScheduleUseCase(ref.read(_loyaltyRepoProvider)),
);
final _createAppointmentUC = Provider<CreateAppointmentUseCase>((ref) =>
  CreateAppointmentUseCase(ref.read(_dashboardRepoProvider)),
);
final _setScheduleUC = Provider<SetBarberScheduleUseCase>((ref) =>
  SetBarberScheduleUseCase(ref.read(_dashboardRepoProvider)),
);
final _createServiceUC = Provider<CreateServiceUseCase>((ref) =>
  CreateServiceUseCase(ref.read(_dashboardRepoProvider)),
);
final _updateServiceUC = Provider<UpdateServiceUseCase>((ref) =>
  UpdateServiceUseCase(ref.read(_dashboardRepoProvider)),
);
final _deleteServiceUC = Provider<DeleteServiceUseCase>((ref) =>
  DeleteServiceUseCase(ref.read(_dashboardRepoProvider)),
);

// ─── HomeOwnerNotifier ────────────────────────────────────────────────────────

class HomeOwnerNotifier extends StateNotifier<HomeOwnerState> {
  final GetAppointmentsByDateUseCase _getAppointments;
  final GetDashboardMetricsUseCase _getMetrics;
  final MarkNoShowUseCase _markNoShow;
  final GetTenantStatusUseCase _getTenantStatus;
  final ToggleTenantOpenUseCase _toggleStatus;
  final GetLoyaltyProgramUseCase _getLoyalty;
  final AddLoyaltyRuleUseCase _addRule;
  final ToggleLoyaltyRuleUseCase _toggleRule;
  final GetOwnerServicesUseCase _getServices;
  final GetBarbersWithScheduleUseCase _getBarbers;
  final CreateAppointmentUseCase _createAppointment;
  final SetBarberScheduleUseCase _setSchedule;
  final CreateServiceUseCase _createService;
  final UpdateServiceUseCase _updateService;
  final DeleteServiceUseCase _deleteService;
  final String _token;
  final String _tenantId;

  HomeOwnerNotifier({
    required GetAppointmentsByDateUseCase getAppointments,
    required GetDashboardMetricsUseCase getMetrics,
    required MarkNoShowUseCase markNoShow,
    required GetTenantStatusUseCase getTenantStatus,
    required ToggleTenantOpenUseCase toggleStatus,
    required GetLoyaltyProgramUseCase getLoyalty,
    required AddLoyaltyRuleUseCase addRule,
    required ToggleLoyaltyRuleUseCase toggleRule,
    required GetOwnerServicesUseCase getServices,
    required GetBarbersWithScheduleUseCase getBarbers,
    required CreateAppointmentUseCase createAppointment,
    required SetBarberScheduleUseCase setSchedule,
    required CreateServiceUseCase createService,
    required UpdateServiceUseCase updateService,
    required DeleteServiceUseCase deleteService,
    required String token,
    required String tenantId,
  })  : _getAppointments = getAppointments,
        _getMetrics      = getMetrics,
        _markNoShow      = markNoShow,
        _getTenantStatus = getTenantStatus,
        _toggleStatus    = toggleStatus,
        _getLoyalty      = getLoyalty,
        _addRule         = addRule,
        _toggleRule      = toggleRule,
        _getServices     = getServices,
        _getBarbers      = getBarbers,
        _createAppointment = createAppointment,
        _setSchedule     = setSchedule,
        _createService   = createService,
        _updateService   = updateService,
        _deleteService   = deleteService,
        _token           = token,
        _tenantId        = tenantId,
        super(HomeOwnerState(selectedDate: DateTime.now())) {
    _loadAll();
  }

  // ─── Carga inicial en paralelo ─────────────────────────────────────────────

  Future<void> _loadAll() async {
    await Future.wait([
      _loadAppointments(DateTime.now()),
      _loadStatus(),
      _loadServices(),
      _loadLoyalty(),
      _loadBarbers(),
    ]);
  }

  // ─── Citas ─────────────────────────────────────────────────────────────────

  Future<void> _loadAppointments(DateTime date) async {
    state = state.copyWith(appointmentsLoading: true, failure: null);
    final result = await _getAppointments(
      date: date, token: _token, tenantId: _tenantId,
    );
    result.fold(
      (f) => state = state.copyWith(
        appointmentsLoading: false, failure: f,
      ),
      (apts) {
        final metrics = _getMetrics(apts);
        state = state.copyWith(
          appointments: apts, metrics: metrics, appointmentsLoading: false,
        );
      },
    );
  }

  Future<void> changeDate(DateTime date) async {
    state = state.copyWith(selectedDate: date);
    await _loadAppointments(date);
  }

  Future<void> markNoShow(String appointmentId) async {
    final result = await _markNoShow(
      appointmentId: appointmentId, token: _token,
    );
    result.fold(
      (_) => null,
      (_) {
        final updated = state.appointments.map((a) => a.id == appointmentId
            ? a.copyWith(status: AppointmentStatus.noShow)
            : a,
        ).toList();
        final metrics = _getMetrics(updated);
        state = state.copyWith(appointments: updated, metrics: metrics);
      },
    );
  }

  // ─── Estado de apertura ────────────────────────────────────────────────────

  Future<void> _loadStatus() async {
    final result = await _getTenantStatus(tenantId: _tenantId, token: _token);
    result.fold(
      (_) => null,
      (status) => state = state.copyWith(openStatus: status),
    );
  }

  Future<void> toggleOpenStatus() async {
    final currentlyOpen = state.isOpen;
    state = state.copyWith(togglingStatus: true);

    final result = await _toggleStatus(
      tenantId: _tenantId,
      isOpen: !currentlyOpen,
      token: _token,
    );

    result.fold(
      (f) => state = state.copyWith(togglingStatus: false, failure: f),
      (status) => state = state.copyWith(
        openStatus: status, togglingStatus: false,
      ),
    );
  }

  // ─── Servicios ─────────────────────────────────────────────────────────────

  Future<void> _loadServices() async {
    state = state.copyWith(servicesLoading: true);
    final result = await _getServices(tenantId: _tenantId, token: _token);
    result.fold(
      (_) => state = state.copyWith(servicesLoading: false),
      (svcs) => state = state.copyWith(
        services: svcs.where((s) => s.isActive).toList(),
        servicesLoading: false,
      ),
    );
  }

  // ─── Barberos con horario ──────────────────────────────────────────────────

  Future<void> _loadBarbers() async {
    final result = await _getBarbers(tenantId: _tenantId, token: _token);
    result.fold(
      (_) => null,
      (barbers) => state = state.copyWith(barbers: barbers),
    );
  }

  // ─── Fidelización ──────────────────────────────────────────────────────────

  Future<void> _loadLoyalty() async {
    state = state.copyWith(loyaltyLoading: true);
    final result = await _getLoyalty(tenantId: _tenantId, token: _token);
    result.fold(
      (_) => state = state.copyWith(loyaltyLoading: false),
      (program) => state = state.copyWith(
        loyaltyProgram: program, loyaltyLoading: false,
      ),
    );
  }

  Future<void> addLoyaltyRule(LoyaltyRule rule) async {
    final programId = state.loyaltyProgram?.id;
    if (programId == null) return;

    final result = await _addRule(
      programId: programId, rule: rule, token: _token,
    );
    result.fold(
      (f) => state = state.copyWith(failure: f),
      (newRule) {
        final program = state.loyaltyProgram!;
        state = state.copyWith(
          loyaltyProgram: program.copyWith(
            rules: [...program.rules, newRule],
          ),
        );
      },
    );
  }

  Future<void> toggleLoyaltyRule(String ruleId, bool isActive) async {
    final result = await _toggleRule(
      ruleId: ruleId, isActive: isActive, token: _token,
    );
    result.fold(
      (f) => state = state.copyWith(failure: f),
      (_) {
        final program = state.loyaltyProgram;
        if (program == null) return;
        state = state.copyWith(
          loyaltyProgram: program.copyWith(
            rules: program.rules.map((r) =>
              r.id == ruleId ? r.copyWith(isActive: isActive) : r,
            ).toList(),
          ),
        );
      },
    );
  }

  // ─── Refresh ───────────────────────────────────────────────────────────────

  Future<void> refresh() => _loadAll();

  /// Calcula métricas filtradas por las citas de un barbero específico
  DashboardMetrics calculateMetricsForBarber(List<Appointment> appointments) =>
      _getMetrics(appointments);

  // ─── Crear cita ────────────────────────────────────────────────────────────

  Future<Either<DashboardFailure, CreateAppointmentResult>> createAppointment({
    required String clientName,
    required String clientPhone,
    required String serviceId,
    required String barberId,
    required DateTime startTime,
    String? clientEmail,
  }) async {
    final result = await _createAppointment(
      clientName: clientName,
      clientPhone: clientPhone,
      serviceId: serviceId,
      barberId: barberId,
      startTime: startTime,
      token: _token,
      clientEmail: clientEmail,
    );
    // Si fue exitoso, recargar las citas del día
    result.fold(
      (f) => state = state.copyWith(failure: f),
      (_) => _loadAppointments(state.selectedDate),
    );
    return result;
  }

  // ─── Guardar horario ───────────────────────────────────────────────────────

  Future<Either<DashboardFailure, Unit>> saveBarberSchedule({
    required String barberId,
    required List<DayScheduleInput> schedules,
  }) async {
    final result = await _setSchedule(
      barberId: barberId,
      schedules: schedules,
      token: _token,
    );
    // Recargar barberos con el nuevo horario
    result.fold(
      (f) => state = state.copyWith(failure: f),
      (_) => _loadBarbers(),
    );
    return result;
  }

  // ─── Servicios CRUD ────────────────────────────────────────────────────────

  Future<Either<DashboardFailure, OwnerService>> createService(
      CreateServiceInput input) async {
    final result = await _createService(input: input, token: _token);
    result.fold(
      (f) => state = state.copyWith(failure: f),
      (svc) => state = state.copyWith(
        services: [...state.services, svc],
      ),
    );
    return result;
  }

  Future<Either<DashboardFailure, OwnerService>> updateService(
      String serviceId, CreateServiceInput input) async {
    final result = await _updateService(
      serviceId: serviceId, input: input, token: _token);
    result.fold(
      (f) => state = state.copyWith(failure: f),
      (updated) => state = state.copyWith(
        services: state.services.map((s) =>
            s.id == serviceId ? updated : s).toList(),
      ),
    );
    return result;
  }

  Future<Either<DashboardFailure, Unit>> deleteService(String serviceId) async {
    final result = await _deleteService(
      serviceId: serviceId, token: _token);
    result.fold(
      (f) => state = state.copyWith(failure: f),
      (_) => state = state.copyWith(
        services: state.services.where((s) => s.id != serviceId).toList(),
      ),
    );
    return result;
  }
}

// ─── Provider público ─────────────────────────────────────────────────────────

final homeOwnerNotifierProvider =
    StateNotifierProvider<HomeOwnerNotifier, HomeOwnerState>((ref) {
  final user = ref.watch(currentUserProvider);
  return HomeOwnerNotifier(
    getAppointments:   ref.read(_getAppointmentsUC),
    getMetrics:        ref.read(_getMetricsUC),
    markNoShow:        ref.read(_markNoShowUC),
    getTenantStatus:   ref.read(_getTenantStatusUC),
    toggleStatus:      ref.read(_toggleStatusUC),
    getLoyalty:        ref.read(_getLoyaltyUC),
    addRule:           ref.read(_addRuleUC),
    toggleRule:        ref.read(_toggleRuleUC),
    getServices:       ref.read(_getServicesUC),
    getBarbers:        ref.read(_getBarbersUC),
    createAppointment: ref.read(_createAppointmentUC),
    setSchedule:       ref.read(_setScheduleUC),
    createService:     ref.read(_createServiceUC),
    updateService:     ref.read(_updateServiceUC),
    deleteService:     ref.read(_deleteServiceUC),
    token:             user?.token ?? '',
    tenantId:          user?.tenant?.id ?? '',
  );
});
