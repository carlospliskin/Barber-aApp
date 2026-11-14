import 'package:fpdart/fpdart.dart';
import '../entities/dashboard_entities.dart';
import '../entities/loyalty_entities.dart';
import '../failures/dashboard_failure.dart';
import '../repositories/i_dashboard_repository.dart';

// ─── GetAppointmentsByDateUseCase ─────────────────────────────────────────────

class GetAppointmentsByDateUseCase {
  final IDashboardRepository _repo;
  const GetAppointmentsByDateUseCase(this._repo);

  Future<Either<DashboardFailure, List<Appointment>>> call({
    required DateTime date,
    required String token,
    required String tenantId,
  }) =>
      _repo.getAppointmentsByDate(
        date: date,
        token: token,
        tenantId: tenantId,
      );
}

// ─── GetDashboardMetricsUseCase ───────────────────────────────────────────────

class GetDashboardMetricsUseCase {
  final IDashboardRepository _repo;
  const GetDashboardMetricsUseCase(this._repo);

  /// Calcula métricas a partir de las citas ya cargadas — sin red
  DashboardMetrics call(List<Appointment> appointments) =>
      _repo.calculateMetrics(appointments);
}

// ─── MarkNoShowUseCase ────────────────────────────────────────────────────────

class MarkNoShowUseCase {
  final IDashboardRepository _repo;
  const MarkNoShowUseCase(this._repo);

  Future<Either<DashboardFailure, Unit>> call({
    required String appointmentId,
    required String token,
  }) =>
      _repo.markNoShow(
        appointmentId: appointmentId,
        token: token,
      );
}

// ─── CreateAppointmentUseCase ─────────────────────────────────────────────────

class CreateAppointmentUseCase {
  final IDashboardRepository _repo;
  const CreateAppointmentUseCase(this._repo);

  Future<Either<DashboardFailure, CreateAppointmentResult>> call({
    required String clientName,
    required String clientPhone,
    required String serviceId,
    required String barberId,
    required DateTime startTime,
    required String token,
    String? clientEmail,
  }) =>
      _repo.createAppointment(
        clientName: clientName,
        clientPhone: clientPhone,
        serviceId: serviceId,
        barberId: barberId,
        startTime: startTime,
        token: token,
        clientEmail: clientEmail,
      );
}

// ─── SetBarberScheduleUseCase ─────────────────────────────────────────────────

class SetBarberScheduleUseCase {
  final IDashboardRepository _repo;
  const SetBarberScheduleUseCase(this._repo);

  Future<Either<DashboardFailure, Unit>> call({
    required String barberId,
    required List<DayScheduleInput> schedules,
    required String token,
  }) =>
      _repo.setBarberSchedule(
        barberId: barberId,
        schedules: schedules,
        token: token,
      );
}

// ─── CreateServiceUseCase ─────────────────────────────────────────────────────

class CreateServiceUseCase {
  final IDashboardRepository _repo;
  const CreateServiceUseCase(this._repo);

  Future<Either<DashboardFailure, OwnerService>> call({
    required CreateServiceInput input,
    required String token,
  }) =>
      _repo.createService(input: input, token: token);
}

// ─── UpdateServiceUseCase ─────────────────────────────────────────────────────

class UpdateServiceUseCase {
  final IDashboardRepository _repo;
  const UpdateServiceUseCase(this._repo);

  Future<Either<DashboardFailure, OwnerService>> call({
    required String serviceId,
    required CreateServiceInput input,
    required String token,
  }) =>
      _repo.updateService(serviceId: serviceId, input: input, token: token);
}

// ─── DeleteServiceUseCase ─────────────────────────────────────────────────────

class DeleteServiceUseCase {
  final IDashboardRepository _repo;
  const DeleteServiceUseCase(this._repo);

  Future<Either<DashboardFailure, Unit>> call({
    required String serviceId,
    required String token,
  }) =>
      _repo.deleteService(serviceId: serviceId, token: token);
}
