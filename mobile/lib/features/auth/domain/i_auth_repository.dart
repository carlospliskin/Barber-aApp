import 'package:fpdart/fpdart.dart';
import 'auth_failure.dart';
import 'auth_user.dart';

/// Contrato que cualquier implementación de AuthRepository debe cumplir.
/// Usamos Either<AuthFailure, T> para hacer el error handling explícito
/// y exhaustivo — mismo concepto que Result<T, E> en Swift.
abstract interface class IAuthRepository {
  /// Recuperar sesión guardada en SecureStorage
  Future<Either<AuthFailure, AuthUser?>> getStoredSession();

  /// Login con email y password
  Future<Either<AuthFailure, AuthUser>> login({
    required String email,
    required String password,
  });

  /// Registrar barbería + owner
  Future<Either<AuthFailure, AuthUser>> registerOwner({
    required String email,
    required String password,
    required String barberiaName,
    required String ownerName,
    String? phone,
  });

  /// Registrar cliente
  Future<Either<AuthFailure, AuthUser>> registerClient({
    required String email,
    required String password,
    required String name,
    required String phone,
  });

  /// Cambiar modo activo (OWNER → CLIENT o CLIENT → OWNER)
  Future<Either<AuthFailure, AuthUser>> switchMode({
    required String mode,
    required String token,
  });

  /// Refrescar access token
  Future<Either<AuthFailure, AuthUser>> refreshToken({
    required String refreshToken,
    required String userId,
  });

  /// Cerrar sesión — limpia storage
  Future<void> logout();

  /// Guardar sesión en SecureStorage
  Future<void> saveSession(AuthUser user);
}
