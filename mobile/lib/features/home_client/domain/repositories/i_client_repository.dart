import 'package:fpdart/fpdart.dart';
import '../entities/client_entities.dart';
import '../failures/client_failure.dart';

// ─── Barberías y búsqueda ─────────────────────────────────────────────────────

abstract interface class IBarberiaRepository {
  Future<Either<ClientFailure, List<BarberiaInfo>>> search({
    required String token,
    String? date,
  });

  Future<Either<ClientFailure, BarberiaInfo>> getDetail({
    required String barberiaId,
    required String token,
    String? date,
  });

  Future<Either<ClientFailure, List<FavoriteBarberia>>> getFavorites({
    required String token,
  });

  Future<Either<ClientFailure, Unit>> addFavorite({
    required String barberiaId,
    required String token,
  });

  Future<Either<ClientFailure, Unit>> removeFavorite({
    required String barberiaId,
    required String token,
  });
}

// ─── Reservas ─────────────────────────────────────────────────────────────────

abstract interface class IBookingRepository {
  Future<Either<ClientFailure, List<ClientAppointment>>> getMyAppointments({
    required String token,
  });

  Future<Either<ClientFailure, BookingResult>> createBooking({
    required String barberiaId,
    required String serviceId,
    required String barberId,
    required DateTime startTime,
    required String token,
  });

  Future<Either<ClientFailure, Unit>> cancelAppointment({
    required String appointmentId,
    required String token,
  });
}

// ─── Lealtad del cliente ──────────────────────────────────────────────────────

abstract interface class IClientLoyaltyRepository {
  Future<Either<ClientFailure, ClientLoyaltyStatus?>> getMyLoyalty({
    required String barberiaId,
    required String token,
  });
}
