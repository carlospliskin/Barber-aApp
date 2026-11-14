import 'package:fpdart/fpdart.dart';
import '../entities/auth_user.dart';
import '../failures/auth_failure.dart';

/// Contrato del repositorio de autenticación.
/// Interfaces pequeñas y enfocadas (ISP de SOLID).
/// Domain no sabe NADA de HTTP, storage ni Flutter.
abstract interface class IAuthRepository {
  Future<Either<AuthFailure, AuthUser?>> restoreSession();
  Future<Either<AuthFailure, AuthUser>> login({required String email, required String password});
  Future<Either<AuthFailure, AuthUser>> registerOwner({required String email, required String password, required String barberiaName, required String ownerName, String? phone});
  Future<Either<AuthFailure, AuthUser>> registerClient({required String email, required String password, required String name, required String phone});
  Future<Either<AuthFailure, AuthUser>> switchMode({required String mode, required String currentToken});
  Future<void> logout();
}
