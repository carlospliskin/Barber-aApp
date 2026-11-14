import 'package:fpdart/fpdart.dart';
import '../entities/auth_user.dart';
import '../failures/auth_failure.dart';
import '../repositories/i_auth_repository.dart';

// ─── RestoreSessionUseCase ────────────────────────────────────────────────────

class RestoreSessionUseCase {
  final IAuthRepository _repo;
  const RestoreSessionUseCase(this._repo);

  Future<Either<AuthFailure, AuthUser?>> call() => _repo.restoreSession();
}

// ─── LoginUseCase ─────────────────────────────────────────────────────────────

class LoginUseCase {
  final IAuthRepository _repo;
  const LoginUseCase(this._repo);

  Future<Either<AuthFailure, AuthUser>> call({
    required String email,
    required String password,
  }) =>
      _repo.login(email: email, password: password);
}

// ─── RegisterOwnerUseCase ─────────────────────────────────────────────────────

class RegisterOwnerUseCase {
  final IAuthRepository _repo;
  const RegisterOwnerUseCase(this._repo);

  Future<Either<AuthFailure, AuthUser>> call({
    required String email,
    required String password,
    required String barberiaName,
    required String ownerName,
    String? phone,
  }) =>
      _repo.registerOwner(
        email: email,
        password: password,
        barberiaName: barberiaName,
        ownerName: ownerName,
        phone: phone,
      );
}

// ─── RegisterClientUseCase ────────────────────────────────────────────────────

class RegisterClientUseCase {
  final IAuthRepository _repo;
  const RegisterClientUseCase(this._repo);

  Future<Either<AuthFailure, AuthUser>> call({
    required String email,
    required String password,
    required String name,
    required String phone,
  }) =>
      _repo.registerClient(
        email: email,
        password: password,
        name: name,
        phone: phone,
      );
}

// ─── SwitchModeUseCase ────────────────────────────────────────────────────────

class SwitchModeUseCase {
  final IAuthRepository _repo;
  const SwitchModeUseCase(this._repo);

  Future<Either<AuthFailure, AuthUser>> call({
    required String mode,
    required String currentToken,
  }) =>
      _repo.switchMode(mode: mode, currentToken: currentToken);
}

// ─── LogoutUseCase ────────────────────────────────────────────────────────────

class LogoutUseCase {
  final IAuthRepository _repo;
  const LogoutUseCase(this._repo);

  Future<void> call() => _repo.logout();
}
