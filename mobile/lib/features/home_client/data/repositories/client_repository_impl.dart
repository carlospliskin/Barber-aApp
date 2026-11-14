import 'dart:io';
import 'package:fpdart/fpdart.dart';
import '../../domain/entities/client_entities.dart';
import '../../domain/failures/client_failure.dart';
import '../../domain/repositories/i_client_repository.dart';
import '../datasources/client_remote_datasource.dart';

// ─── Helper — mapeo de errores compartido ─────────────────────────────────────

ClientFailure _mapError(Object e) {
  final msg = e.toString().toLowerCase();
  if (msg.contains('unauthorized') || msg.contains('401')) {
    return const ClientFailure.unauthorized();
  }
  if (msg.contains('not found') || msg.contains('404')) {
    return const ClientFailure.notFound();
  }
  if (msg.contains('penaliz') || msg.contains('penalty')) {
    return ClientFailure.penalized(until: null);
  }
  if (msg.contains('cancel') && msg.contains('not allowed')) {
    return ClientFailure.cancelNotAllowed(reason: e.toString());
  }
  if (msg.contains('500') || msg.contains('server')) {
    return ClientFailure.serverError(message: e.toString());
  }
  return ClientFailure.unknown(message: e.toString());
}

// ─── BarberiaRepositoryImpl ───────────────────────────────────────────────────

class BarberiaRepositoryImpl implements IBarberiaRepository {
  final ClientRemoteDatasource _remote;
  const BarberiaRepositoryImpl({required ClientRemoteDatasource remote})
      : _remote = remote;

  @override
  Future<Either<ClientFailure, List<BarberiaInfo>>> search({
    required String token,
    String? date,
  }) async {
    try {
      final raw = await _remote.searchBarberías(token: token, date: date);
      return Right(raw.map(_mapBarberia).toList());
    } on SocketException {
      return const Left(ClientFailure.noConnection());
    } catch (e) {
      return Left(_mapError(e));
    }
  }

  @override
  Future<Either<ClientFailure, BarberiaInfo>> getDetail({
    required String barberiaId,
    required String token,
    String? date,
  }) async {
    try {
      final json = await _remote.getBarberiaDetail(
        barberiaId: barberiaId, token: token, date: date);
      if (json == null) return const Left(ClientFailure.notFound());
      return Right(_mapBarberia(json));
    } on SocketException {
      return const Left(ClientFailure.noConnection());
    } catch (e) {
      return Left(_mapError(e));
    }
  }

  @override
  Future<Either<ClientFailure, List<FavoriteBarberia>>> getFavorites({
    required String token,
  }) async {
    try {
      final raw = await _remote.getFavorites(token: token);
      return Right(raw.map(_mapFavorite).toList());
    } on SocketException {
      return const Left(ClientFailure.noConnection());
    } catch (e) {
      return Left(_mapError(e));
    }
  }

  @override
  Future<Either<ClientFailure, Unit>> addFavorite({
    required String barberiaId,
    required String token,
  }) async {
    try {
      await _remote.addFavorite(barberiaId: barberiaId, token: token);
      return const Right(unit);
    } on SocketException {
      return const Left(ClientFailure.noConnection());
    } catch (e) {
      return Left(_mapError(e));
    }
  }

  @override
  Future<Either<ClientFailure, Unit>> removeFavorite({
    required String barberiaId,
    required String token,
  }) async {
    try {
      await _remote.removeFavorite(barberiaId: barberiaId, token: token);
      return const Right(unit);
    } on SocketException {
      return const Left(ClientFailure.noConnection());
    } catch (e) {
      return Left(_mapError(e));
    }
  }

  // ─── Mappers ────────────────────────────────────────────────────────────────

  BarberiaInfo _mapBarberia(Map<String, dynamic> json) {
    final services = (json['services'] as List? ?? [])
        .cast<Map<String, dynamic>>()
        .map(_mapService)
        .toList();
    final barbers = (json['barbers'] as List? ?? [])
        .cast<Map<String, dynamic>>()
        .map(_mapBarber)
        .toList();

    return BarberiaInfo(
      id:          json['id'] as String,
      name:        json['name'] as String,
      isOpen:      json['isOpen'] as bool? ?? false,
      avgRating:   (json['avgRating'] as num?)?.toDouble() ?? 0.0,
      reviewCount: json['reviewCount'] as int? ?? 0,
      isFavorite:  json['isFavorite'] as bool? ?? false,
      services:    services,
      barbers:     barbers,
    );
  }

  PublicService _mapService(Map<String, dynamic> json) => PublicService(
    id:              json['id'] as String,
    name:            json['name'] as String,
    category:        json['category'] as String,
    durationMinutes: json['durationMinutes'] as int,
    basePrice:       json['basePrice'] as String,
    depositAmount:   json['depositAmount'] as String? ?? '0.00',
    depositType:     json['depositType'] as String? ?? 'NONE',
  );

  PublicBarber _mapBarber(Map<String, dynamic> json) {
    final slots = (json['availableSlots'] as List? ?? [])
        .cast<Map<String, dynamic>>()
        .map((s) => AvailableSlot(
          time:        s['time'] as String,
          slotNumber:  s['slotNumber'] as int,
          isAvailable: s['isAvailable'] as bool,
        ))
        .toList();
    return PublicBarber(
      id:             json['id'] as String,
      name:           json['name'] as String,
      capacity:       json['capacity'] as int? ?? 1,
      avgRating:      (json['avgRating'] as num?)?.toDouble() ?? 0.0,
      availableSlots: slots,
    );
  }

  FavoriteBarberia _mapFavorite(Map<String, dynamic> json) => FavoriteBarberia(
    id:          json['id'] as String,
    name:        json['name'] as String,
    avgRating:   (json['avgRating'] as num?)?.toDouble() ?? 0.0,
    reviewCount: json['reviewCount'] as int? ?? 0,
    isOpen:      json['isOpen'] as bool? ?? false,
  );
}

// ─── BookingRepositoryImpl ────────────────────────────────────────────────────

class BookingRepositoryImpl implements IBookingRepository {
  final ClientRemoteDatasource _remote;
  const BookingRepositoryImpl({required ClientRemoteDatasource remote})
      : _remote = remote;

  @override
  Future<Either<ClientFailure, List<ClientAppointment>>> getMyAppointments({
    required String token,
  }) async {
    try {
      final raw = await _remote.getMyAppointments(token: token);
      return Right(raw.map(_mapAppointment).toList());
    } on SocketException {
      return const Left(ClientFailure.noConnection());
    } catch (e) {
      return Left(_mapError(e));
    }
  }

  @override
  Future<Either<ClientFailure, BookingResult>> createBooking({
    required String barberiaId,
    required String serviceId,
    required String barberId,
    required DateTime startTime,
    required String token,
  }) async {
    try {
      final json = await _remote.createAppointment(
        serviceId: serviceId,
        barberId:  barberId,
        startTime: startTime.toIso8601String(),
        token:     token,
      );
      return Right(BookingResult(
        appointmentId:    json['appointmentId'] as String,
        confirmationCode: json['confirmationCode'] as String,
        depositStatus:    json['depositStatus'] as String? ?? 'NONE',
        paymentUrl:       json['paymentUrl'] as String?,
      ));
    } on SocketException {
      return const Left(ClientFailure.noConnection());
    } catch (e) {
      return Left(_mapError(e));
    }
  }

  @override
  Future<Either<ClientFailure, Unit>> cancelAppointment({
    required String appointmentId,
    required String token,
  }) async {
    try {
      await _remote.cancelAppointment(
        appointmentId: appointmentId, token: token);
      return const Right(unit);
    } on SocketException {
      return const Left(ClientFailure.noConnection());
    } catch (e) {
      return Left(_mapError(e));
    }
  }

  ClientAppointment _mapAppointment(Map<String, dynamic> json) {
    final svc = json['service'] as Map<String, dynamic>;
    return ClientAppointment(
      id:               json['id'] as String,
      confirmationCode: json['confirmationCode'] as String,
      barberiaId:       '', // viene del tenant via JWT
      barberiaName:     '', // se carga por separado si se necesita
      barberName:       json['barberName'] as String? ?? '',
      serviceName:      svc['name'] as String,
      serviceCategory:  svc['category'] as String,
      durationMinutes:  svc['durationMinutes'] as int,
      basePrice:        svc['basePrice'] as String,
      startTime:        DateTime.parse(json['startTime'] as String),
      endTime:          DateTime.parse(json['endTime'] as String),
      status:           ClientAppointmentStatusX.fromString(
                          json['status'] as String),
      depositStatus:    json['depositStatus'] as String? ?? 'NONE',
      depositAmount:    json['depositAmount'] as String?,
      rescheduleCount:  json['rescheduleCount'] as int? ?? 0,
    );
  }
}

// ─── ClientLoyaltyRepositoryImpl ─────────────────────────────────────────────

class ClientLoyaltyRepositoryImpl implements IClientLoyaltyRepository {
  final ClientRemoteDatasource _remote;
  const ClientLoyaltyRepositoryImpl({required ClientRemoteDatasource remote})
      : _remote = remote;

  @override
  Future<Either<ClientFailure, ClientLoyaltyStatus?>> getMyLoyalty({
    required String barberiaId,
    required String token,
  }) async {
    try {
      final json = await _remote.getMyLoyalty(
        barberiaId: barberiaId, token: token);
      if (json == null) return const Right(null);
      return Right(ClientLoyaltyStatus(
        id:              json['id'] as String,
        programId:       json['programId'] as String,
        programName:     '',   // se puede enriquecer si el backend lo retorna
        visitsCount:     json['visitsCount'] as int? ?? 0,
        totalVisitsEver: json['totalVisitsEver'] as int? ?? 0,
        lastVisitAt:     json['lastVisitAt'] != null
            ? DateTime.parse(json['lastVisitAt'] as String)
            : null,
      ));
    } on SocketException {
      return const Left(ClientFailure.noConnection());
    } catch (e) {
      return Left(_mapError(e));
    }
  }
}
