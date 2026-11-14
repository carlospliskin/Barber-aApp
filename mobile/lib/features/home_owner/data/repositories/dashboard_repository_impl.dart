import 'dart:io';
import 'package:fpdart/fpdart.dart';
import '../../domain/entities/dashboard_entities.dart';
import '../../domain/entities/loyalty_entities.dart';
import '../../domain/failures/dashboard_failure.dart';
import '../../domain/repositories/i_dashboard_repository.dart';
import '../datasources/dashboard_remote_datasource.dart';
import '../models/dashboard_model.dart';

class DashboardRepositoryImpl implements IDashboardRepository {
  final DashboardRemoteDatasource _remote;

  const DashboardRepositoryImpl({required DashboardRemoteDatasource remote})
      : _remote = remote;

  @override
  Future<Either<DashboardFailure, List<Appointment>>> getAppointmentsByDate({
    required DateTime date,
    required String token,
    required String tenantId,
  }) async {
    try {
      final dateStr = '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
      final raw = await _remote.getAppointmentsByDate(date: dateStr, token: token);
      final appointments = raw
          .map((json) => AppointmentModel.fromJson(json).toDomain())
          .toList();
      return Right(appointments);
    } on SocketException {
      return const Left(DashboardFailure.noConnection());
    } catch (e) {
      return Left(_mapError(e));
    }
  }

  @override
  DashboardMetrics calculateMetrics(List<Appointment> appointments) {
    final today = appointments.where((a) =>
      a.status != AppointmentStatus.cancelled,
    ).toList();

    final confirmed  = today.where((a) => a.isConfirmed || a.isPending).length;
    final completed  = today.where((a) => a.status == AppointmentStatus.completed).length;
    final noShows    = today.where((a) => a.status == AppointmentStatus.noShow).length;
    final pending    = today.where((a) => a.isPending).length;

    final total = confirmed + completed + noShows;
    final rate  = total > 0 ? (completed + confirmed) / total : 0.0;

    return DashboardMetrics(
      appointmentsToday: today.length,
      appointmentsWeek: today.length,       // solo hoy por ahora
      revenueWeek: _sumRevenue(today),
      attendanceRate: rate,
      pendingConfirmations: pending,
      completedToday: completed,
    );
  }

  @override
  Future<Either<DashboardFailure, Unit>> markNoShow({
    required String appointmentId,
    required String token,
  }) async {
    try {
      await _remote.markNoShow(appointmentId: appointmentId, token: token);
      return const Right(unit);
    } on SocketException {
      return const Left(DashboardFailure.noConnection());
    } catch (e) {
      return Left(_mapError(e));
    }
  }

  String _sumRevenue(List<Appointment> appointments) {
    var total = 0.0;
    for (final apt in appointments) {
      if (apt.status == AppointmentStatus.completed) {
        final price = double.tryParse(apt.service.basePrice) ?? 0;
        total += price;
      }
    }
    return total.toStringAsFixed(2);
  }

  DashboardFailure _mapError(Object e) {
    final msg = e.toString().toLowerCase();
    if (msg.contains('unauthorized') || msg.contains('401')) {
      return const DashboardFailure.unauthorized();
    }
    if (msg.contains('not found') || msg.contains('404')) {
      return const DashboardFailure.notFound();
    }
    if (msg.contains('500') || msg.contains('server')) {
      return DashboardFailure.serverError(msg: e.toString());
    }
    return DashboardFailure.unknown(msg: e.toString());
  }
}

  @override
  Future<Either<DashboardFailure, CreateAppointmentResult>> createAppointment({
    required String clientName,
    required String clientPhone,
    required String serviceId,
    required String barberId,
    required DateTime startTime,
    required String token,
    String? clientEmail,
  }) async {
    try {
      final isoStart = startTime.toIso8601String();
      final json = await _remote.createAppointment(
        clientName: clientName,
        clientPhone: clientPhone,
        serviceId: serviceId,
        barberId: barberId,
        startTime: isoStart,
        token: token,
        clientEmail: clientEmail,
      );
      return Right(CreateAppointmentResult(
        appointmentId:    json['appointmentId'] as String,
        confirmationCode: json['confirmationCode'] as String,
        depositStatus:    json['depositStatus'] as String? ?? 'NONE',
        paymentUrl:       json['paymentUrl'] as String?,
      ));
    } on SocketException {
      return const Left(DashboardFailure.noConnection());
    } catch (e) {
      return Left(_mapError(e));
    }
  }

  @override
  Future<Either<DashboardFailure, Unit>> setBarberSchedule({
    required String barberId,
    required List<DayScheduleInput> schedules,
    required String token,
  }) async {
    try {
      final scheduleMaps = schedules.map((s) => {
        'dayOfWeek':  s.dayOfWeek,
        'isWorking':  s.isWorking,
        'openTime':   s.openTime,
        'closeTime':  s.closeTime,
        'capacity':   s.capacity,
      }).toList();
      await _remote.setBarberSchedule(
        barberId: barberId, schedules: scheduleMaps, token: token);
      return const Right(unit);
    } on SocketException {
      return const Left(DashboardFailure.noConnection());
    } catch (e) {
      return Left(_mapError(e));
    }
  }

  @override
  Future<Either<DashboardFailure, OwnerService>> createService({
    required CreateServiceInput input,
    required String token,
  }) async {
    try {
      final json = await _remote.createService(
        input: _serviceInputToMap(input), token: token);
      return Right(_mapService(json));
    } on SocketException {
      return const Left(DashboardFailure.noConnection());
    } catch (e) {
      return Left(_mapError(e));
    }
  }

  @override
  Future<Either<DashboardFailure, OwnerService>> updateService({
    required String serviceId,
    required CreateServiceInput input,
    required String token,
  }) async {
    try {
      final json = await _remote.updateService(
        serviceId: serviceId,
        input: _serviceInputToMap(input),
        token: token,
      );
      return Right(_mapService(json));
    } on SocketException {
      return const Left(DashboardFailure.noConnection());
    } catch (e) {
      return Left(_mapError(e));
    }
  }

  @override
  Future<Either<DashboardFailure, Unit>> deleteService({
    required String serviceId,
    required String token,
  }) async {
    try {
      await _remote.deleteService(serviceId: serviceId, token: token);
      return const Right(unit);
    } on SocketException {
      return const Left(DashboardFailure.noConnection());
    } catch (e) {
      return Left(_mapError(e));
    }
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  Map<String, dynamic> _serviceInputToMap(CreateServiceInput input) => {
    'name':            input.name,
    'category':        input.category,
    'durationMinutes': input.durationMinutes,
    'basePrice':       input.basePrice,
    'depositType':     input.depositType,
    'depositAmount':   input.depositAmount,
    'maxConcurrent':   input.maxConcurrent,
  };

  OwnerService _mapService(Map<String, dynamic> json) => OwnerService(
    id:              json['id'] as String,
    name:            json['name'] as String,
    category:        json['category'] as String,
    durationMinutes: json['durationMinutes'] as int,
    basePrice:       json['basePrice'] as String,
    depositType:     json['depositType'] as String? ?? 'NONE',
    depositAmount:   json['depositAmount'] as String? ?? '0.00',
    maxConcurrent:   json['maxConcurrent'] as int? ?? 1,
    isActive:        json['isActive'] as bool? ?? true,
  );
