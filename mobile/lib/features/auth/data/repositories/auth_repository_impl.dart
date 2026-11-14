import 'dart:io';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:fpdart/fpdart.dart';
import '../../domain/entities/auth_user.dart';
import '../../domain/failures/auth_failure.dart';
import '../../domain/repositories/i_auth_repository.dart';
import '../datasources/auth_remote_datasource.dart';
import '../models/auth_model.dart';

/// Implementación del repositorio.
/// Responsabilidades:
///   1. Orquestar datasource + storage
///   2. Convertir Map → Model → Entity
///   3. Convertir Exception → AuthFailure
class AuthRepositoryImpl implements IAuthRepository {
  final AuthRemoteDatasource _remote;
  final FlutterSecureStorage _storage;

  static const _kToken   = 'auth_token';
  static const _kRefresh = 'auth_refresh';
  static const _kUserId  = 'auth_user_id';

  const AuthRepositoryImpl({
    required AuthRemoteDatasource remote,
    required FlutterSecureStorage storage,
  })  : _remote = remote,
        _storage = storage;

  @override
  Future<Either<AuthFailure, AuthUser?>> restoreSession() async {
    try {
      final token = await _storage.read(key: _kToken);
      if (token == null) return const Right(null);

      final json = await _remote.me(token: token);
      // "me" retorna campos ligeramente distintos al payload de login
      final model = AuthPayloadModel(
        token: token,
        refreshToken: await _storage.read(key: _kRefresh) ?? '',
        userId: json['userId'] as String,
        capabilities: _parseList(json['capabilities']),
        primaryRole: json['primaryRole'] as String? ?? 'CLIENT',
        tenant: json['tenant'] != null
            ? TenantModel.fromJson(json['tenant'] as Map<String, dynamic>)
            : null,
        clientProfile: json['clientProfile'] != null
            ? ClientProfileModel.fromJson(json['clientProfile'] as Map<String, dynamic>)
            : null,
      );
      return Right(model.toDomain());
    } on SocketException {
      return const Left(AuthFailure.noConnection());
    } catch (_) {
      await logout(); // Token inválido o expirado
      return const Right(null);
    }
  }

  @override
  Future<Either<AuthFailure, AuthUser>> login({
    required String email,
    required String password,
  }) =>
      _execute(() => _remote.login(email: email, password: password));

  @override
  Future<Either<AuthFailure, AuthUser>> registerOwner({
    required String email,
    required String password,
    required String barberiaName,
    required String ownerName,
    String? phone,
  }) =>
      _execute(() => _remote.registerOwner(
            email: email,
            password: password,
            barberiaName: barberiaName,
            ownerName: ownerName,
            phone: phone,
          ));

  @override
  Future<Either<AuthFailure, AuthUser>> registerClient({
    required String email,
    required String password,
    required String name,
    required String phone,
  }) =>
      _execute(() => _remote.registerClient(
            email: email,
            password: password,
            name: name,
            phone: phone,
          ));

  @override
  Future<Either<AuthFailure, AuthUser>> switchMode({
    required String mode,
    required String currentToken,
  }) =>
      _execute(() => _remote.switchMode(mode: mode, token: currentToken));

  @override
  Future<void> logout() async {
    await _storage.delete(key: _kToken);
    await _storage.delete(key: _kRefresh);
    await _storage.delete(key: _kUserId);
  }

  // ─── Helper — patrón DRY para todas las operaciones ───────────────────────

  Future<Either<AuthFailure, AuthUser>> _execute(
    Future<Map<String, dynamic>> Function() call,
  ) async {
    try {
      final json = await call();
      final model = AuthPayloadModel.fromJson(json);
      final user  = model.toDomain();
      await _saveSession(user);
      return Right(user);
    } on SocketException {
      return const Left(AuthFailure.noConnection());
    } catch (e) {
      return Left(_mapError(e.toString()));
    }
  }

  Future<void> _saveSession(AuthUser user) async {
    await _storage.write(key: _kToken,   value: user.token);
    await _storage.write(key: _kRefresh, value: user.refreshToken);
    await _storage.write(key: _kUserId,  value: user.userId);
  }

  AuthFailure _mapError(String msg) {
    final lower = msg.toLowerCase();
    if (lower.contains('invalid') || lower.contains('credentials')) {
      return const AuthFailure.invalidCredentials();
    }
    if (lower.contains('already') || lower.contains('exists')) {
      return const AuthFailure.emailAlreadyExists();
    }
    if (lower.contains('unauthorized') || lower.contains('expired')) {
      return const AuthFailure.sessionExpired();
    }
    if (lower.contains('500') || lower.contains('server')) {
      return AuthFailure.serverError(message: msg);
    }
    return AuthFailure.unknown(message: msg);
  }

  List<String> _parseList(dynamic raw) =>
      (raw as List?)?.map((e) => e.toString()).toList() ?? ['CLIENT'];
}
