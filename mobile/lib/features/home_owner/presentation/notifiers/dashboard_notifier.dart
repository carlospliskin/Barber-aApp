import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/providers/infrastructure_providers.dart';
import '../../../../features/auth/presentation/notifiers/auth_notifier.dart';
import '../../data/datasources/dashboard_remote_datasource.dart';
import '../../data/repositories/dashboard_repository_impl.dart';
import '../../domain/repositories/i_dashboard_repository.dart';
import '../../domain/usecases/dashboard_use_cases.dart';
import 'dashboard_state.dart';

// ─── Providers ────────────────────────────────────────────────────────────────

final _dashboardDatasourceProvider = Provider<DashboardRemoteDatasource>((ref) =>
  DashboardRemoteDatasource(client: ref.read(httpClientProvider)),
);

final dashboardRepositoryProvider = Provider<IDashboardRepository>((ref) =>
  DashboardRepositoryImpl(remote: ref.read(_dashboardDatasourceProvider)),
);

final _getAppointmentsUseCaseProvider =
    Provider<GetAppointmentsByDateUseCase>((ref) =>
  GetAppointmentsByDateUseCase(ref.read(dashboardRepositoryProvider)),
);

final _getMetricsUseCaseProvider = Provider<GetDashboardMetricsUseCase>((ref) =>
  GetDashboardMetricsUseCase(ref.read(dashboardRepositoryProvider)),
);

final _markNoShowUseCaseProvider = Provider<MarkNoShowUseCase>((ref) =>
  MarkNoShowUseCase(ref.read(dashboardRepositoryProvider)),
);

// ─── DashboardNotifier ────────────────────────────────────────────────────────

class DashboardNotifier extends StateNotifier<DashboardState> {
  final GetAppointmentsByDateUseCase _getAppointments;
  final GetDashboardMetricsUseCase _getMetrics;
  final MarkNoShowUseCase _markNoShow;
  final String _token;
  final String _tenantId;

  DashboardNotifier({
    required GetAppointmentsByDateUseCase getAppointments,
    required GetDashboardMetricsUseCase getMetrics,
    required MarkNoShowUseCase markNoShow,
    required String token,
    required String tenantId,
  })  : _getAppointments = getAppointments,
        _getMetrics = getMetrics,
        _markNoShow = markNoShow,
        _token = token,
        _tenantId = tenantId,
        super(const DashboardState.loading()) {
    loadAppointments(DateTime.now());
  }

  // ─── Cargar citas ──────────────────────────────────────────────────────────

  Future<void> loadAppointments(DateTime date) async {
    state = const DashboardState.loading();

    final result = await _getAppointments(
      date: date,
      token: _token,
      tenantId: _tenantId,
    );

    state = result.fold(
      (failure) => DashboardState.error(failure: failure, selectedDate: date),
      (appointments) {
        final metrics = _getMetrics(appointments);
        return DashboardState.loaded(
          appointments: appointments,
          metrics: metrics,
          selectedDate: date,
        );
      },
    );
  }

  // ─── Cambiar fecha ─────────────────────────────────────────────────────────

  Future<void> changeDate(DateTime date) => loadAppointments(date);

  // ─── Marcar no-show ────────────────────────────────────────────────────────

  Future<void> markNoShow(String appointmentId) async {
    final current = state;
    if (current is! _Loaded) return;

    state = current.copyWith(isMarkingNoShow: true);

    final result = await _markNoShow(
      appointmentId: appointmentId,
      token: _token,
    );

    result.fold(
      (_) => state = current.copyWith(isMarkingNoShow: false),
      (_) {
        // Actualizar localmente sin nueva llamada al servidor
        final updated = current.appointments.map((apt) {
          if (apt.id == appointmentId) {
            return apt.copyWith(status: AppointmentStatus.noShow);
          }
          return apt;
        }).toList();
        final metrics = _getMetrics(updated);
        state = DashboardState.loaded(
          appointments: updated,
          metrics: metrics,
          selectedDate: current.selectedDate,
        );
      },
    );
  }

  // ─── Refresh ───────────────────────────────────────────────────────────────

  Future<void> refresh() => loadAppointments(state.selectedDate);
}

// ─── Provider público ─────────────────────────────────────────────────────────

final dashboardNotifierProvider =
    StateNotifierProvider<DashboardNotifier, DashboardState>((ref) {
  final user = ref.watch(currentUserProvider);

  return DashboardNotifier(
    getAppointments: ref.read(_getAppointmentsUseCaseProvider),
    getMetrics:      ref.read(_getMetricsUseCaseProvider),
    markNoShow:      ref.read(_markNoShowUseCaseProvider),
    token:           user?.token ?? '',
    tenantId:        user?.tenant?.id ?? '',
  );
});
