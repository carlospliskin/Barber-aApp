import 'package:fpdart/fpdart.dart';
import '../entities/client_entities.dart';
import '../failures/client_failure.dart';
import '../repositories/i_client_repository.dart';

// ─── Barberías ────────────────────────────────────────────────────────────────

class SearchBarberiasUseCase {
  final IBarberiaRepository _repo;
  const SearchBarberiasUseCase(this._repo);

  Future<Either<ClientFailure, List<BarberiaInfo>>> call({
    required String token,
    String? date,
  }) => _repo.search(token: token, date: date);
}

class GetBarberiaDetailUseCase {
  final IBarberiaRepository _repo;
  const GetBarberiaDetailUseCase(this._repo);

  Future<Either<ClientFailure, BarberiaInfo>> call({
    required String barberiaId,
    required String token,
    String? date,
  }) => _repo.getDetail(barberiaId: barberiaId, token: token, date: date);
}

class GetFavoritesUseCase {
  final IBarberiaRepository _repo;
  const GetFavoritesUseCase(this._repo);

  Future<Either<ClientFailure, List<FavoriteBarberia>>> call({
    required String token,
  }) => _repo.getFavorites(token: token);
}

class AddFavoriteUseCase {
  final IBarberiaRepository _repo;
  const AddFavoriteUseCase(this._repo);

  Future<Either<ClientFailure, Unit>> call({
    required String barberiaId,
    required String token,
  }) => _repo.addFavorite(barberiaId: barberiaId, token: token);
}

class RemoveFavoriteUseCase {
  final IBarberiaRepository _repo;
  const RemoveFavoriteUseCase(this._repo);

  Future<Either<ClientFailure, Unit>> call({
    required String barberiaId,
    required String token,
  }) => _repo.removeFavorite(barberiaId: barberiaId, token: token);
}

// ─── Reservas ─────────────────────────────────────────────────────────────────

class GetMyAppointmentsUseCase {
  final IBookingRepository _repo;
  const GetMyAppointmentsUseCase(this._repo);

  Future<Either<ClientFailure, List<ClientAppointment>>> call({
    required String token,
  }) => _repo.getMyAppointments(token: token);
}

class CreateBookingUseCase {
  final IBookingRepository _repo;
  const CreateBookingUseCase(this._repo);

  Future<Either<ClientFailure, BookingResult>> call({
    required String barberiaId,
    required String serviceId,
    required String barberId,
    required DateTime startTime,
    required String token,
  }) => _repo.createBooking(
    barberiaId: barberiaId,
    serviceId: serviceId,
    barberId: barberId,
    startTime: startTime,
    token: token,
  );
}

class CancelAppointmentUseCase {
  final IBookingRepository _repo;
  const CancelAppointmentUseCase(this._repo);

  Future<Either<ClientFailure, Unit>> call({
    required String appointmentId,
    required String token,
  }) => _repo.cancelAppointment(
    appointmentId: appointmentId, token: token);
}

// ─── Lealtad ──────────────────────────────────────────────────────────────────

class GetMyLoyaltyUseCase {
  final IClientLoyaltyRepository _repo;
  const GetMyLoyaltyUseCase(this._repo);

  Future<Either<ClientFailure, ClientLoyaltyStatus?>> call({
    required String barberiaId,
    required String token,
  }) => _repo.getMyLoyalty(barberiaId: barberiaId, token: token);
}
