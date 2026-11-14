import 'package:fpdart/fpdart.dart';
import '../entities/dashboard_entities.dart';
import '../entities/loyalty_entities.dart';
import '../failures/dashboard_failure.dart';

abstract interface class IDashboardRepository {
  /// Citas del día para el tenant del owner
  Future<Either<DashboardFailure, List<Appointment>>> getAppointmentsByDate({
    required DateTime date,
    required String token,
    required String tenantId,
  });

  /// Métricas calculadas localmente desde la lista de citas
  DashboardMetrics calculateMetrics(List<Appointment> appointments);

  /// Marcar cita como no-show
  Future<Either<DashboardFailure, Unit>> markNoShow({
    required String appointmentId,
    required String token,
  });
}

  /// Crear una cita desde el dashboard del owner
  Future<Either<DashboardFailure, CreateAppointmentResult>> createAppointment({
    required String clientName,
    required String clientPhone,
    required String serviceId,
    required String barberId,
    required DateTime startTime,
    required String token,
    String? clientEmail,
  });

  /// Guardar horario semanal de un barbero
  Future<Either<DashboardFailure, Unit>> setBarberSchedule({
    required String barberId,
    required List<DayScheduleInput> schedules,
    required String token,
  });

  /// Crear servicio
  Future<Either<DashboardFailure, OwnerService>> createService({
    required CreateServiceInput input,
    required String token,
  });

  /// Actualizar servicio
  Future<Either<DashboardFailure, OwnerService>> updateService({
    required String serviceId,
    required CreateServiceInput input,
    required String token,
  });

  /// Desactivar servicio (soft delete)
  Future<Either<DashboardFailure, Unit>> deleteService({
    required String serviceId,
    required String token,
  });
