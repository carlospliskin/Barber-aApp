import 'dart:io';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:fpdart/fpdart.dart';
import '../domain/auth_failure.dart';
import '../domain/auth_user.dart';
import '../domain/i_auth_repository.dart';
import 'auth_remote_datasource.dart';

/// Implementación del repositorio de autenticación.
/// Convierte excepciones crudas en AuthFailure tipados.
/// Orquesta datasource + storage.
class AuthRepositoryImpl implements IAuthRepository {
  final AuthRemoteDatasource _datasource;
  final FlutterSecureStorage _storage;

  static const _tokenKey = 'auth_token';
  static const _refreshKey = 'auth_refresh_token';
  static const _userIdKey = 'auth_user_id';

  AuthRepositoryImpl({
    required AuthRemoteDatasource datasource,
    required FlutterSecureStorage storage,
  })  : _datasource = datasource,
        _storage = storage;

  // ─── getStoredSession ──────────────────────────────────────────────────────

  @override
  Future<Either<AuthFailure, AuthUser?>> getStoredSession() async {
    try {
      final token = await _storage.read(key: _tokenKey);
      if (token == null) return const Right(null);

      final data = await _datasource.me(token: token);
      final user = _mapMeToUser(data, token: token);
      await saveSession(user);
      return Right(user);
    } on SocketException {
      // Sin internet — intentar con datos cacheados
      return const Left(AuthFailure.noConnection());
    } catch (e) {
      await logout(); // Token inválido → limpiar
      return const Right(null);
    }
  }

  // ─── login ─────────────────────────────────────────────────────────────────

  @override
  Future<Either<AuthFailure, AuthUser>> login({
    required String email,
    required String password,
  }) async {
    try {
      final data = await _datasource.login(email: email, password: password);
      final user = _mapPayloadToUser(data);
      await saveSession(user);
      return Right(user);
    } on SocketException {
      return const Left(AuthFailure.noConnection());
    } catch (e) {
      return Left(_mapException(e));
    }
  }

  // ─── registerOwner ─────────────────────────────────────────────────────────

  @override
  Future<Either<AuthFailure, AuthUser>> registerOwner({
    required String email,
    required String password,
    required String barberiaName,
    required String ownerName,
    String? phone,
  }) async {
    try {
      final data = await _datasource.registerOwner(
        email: email,
        password: password,
        barberiaName: barberiaName,
        ownerName: ownerName,
        phone: phone,
      );
      final user = _mapPayloadToUser(data);
      await saveSession(user);
      return Right(user);
    } on SocketException {
      return const Left(AuthFailure.noConnection());
    } catch (e) {
      return Left(_mapException(e));
    }
  }

  // ─── registerClient ────────────────────────────────────────────────────────

  @override
  Future<Either<AuthFailure, AuthUser>> registerClient({
    required String email,
    required String password,
    required String name,
    required String phone,
  }) async {
    try {
      final data = await _datasource.registerClient(
        email: email,
        password: password,
        name: name,
        phone: phone,
      );
      final user = _mapPayloadToUser(data);
      await saveSession(user);
      return Right(user);
    } on SocketException {
      return const Left(AuthFailure.noConnection());
    } catch (e) {
      return Left(_mapException(e));
    }
  }

  // ─── switchMode ────────────────────────────────────────────────────────────

  @override
  Future<Either<AuthFailure, AuthUser>> switchMode({
    required String mode,
    required String token,
  }) async {
    try {
      final data = await _datasource.switchMode(mode: mode, token: token);
      final user = _mapPayloadToUser(data);
      await saveSession(user);
      return Right(user);
    } on SocketException {
      return const Left(AuthFailure.noConnection());
    } catch (e) {
      return Left(_mapException(e));
    }
  }

  // ─── refreshToken ──────────────────────────────────────────────────────────

  @override
  Future<Either<AuthFailure, AuthUser>> refreshToken({
    required String refreshToken,
    required String userId,
  }) async {
    // TODO: implementar cuando el backend tenga el endpoint
    return const Left(AuthFailure.sessionExpired());
  }

  // ─── logout ────────────────────────────────────────────────────────────────

  @override
  Future<void> logout() async {
    await _storage.delete(key: _tokenKey);
    await _storage.delete(key: _refreshKey);
    await _storage.delete(key: _userIdKey);
  }

  // ─── saveSession ───────────────────────────────────────────────────────────

  @override
  Future<void> saveSession(AuthUser user) async {
    await _storage.write(key: _tokenKey, value: user.token);
    await _storage.write(key: _refreshKey, value: user.refreshToken);
    await _storage.write(key: _userIdKey, value: user.userId);
  }

  // ─── Mappers ───────────────────────────────────────────────────────────────

  AuthUser _mapPayloadToUser(Map<String, dynamic> payload) {
    final tenantRaw = payload['tenant'] as Map<String, dynamic>?;
    final clientRaw = payload['clientProfile'] as Map<String, dynamic>?;

    return AuthUser(
      userId: payload['userId'] as String,
      token: payload['token'] as String,
      refreshToken: payload['refreshToken'] as String,
      capabilities: (payload['capabilities'] as List)
          .map((e) => e.toString())
          .toList(),
      primaryRole: payload['primaryRole'] as String? ?? 'CLIENT',
      tenant: tenantRaw != null ? TenantInfo.fromJson(tenantRaw) : null,
      clientProfile:
          clientRaw != null ? ClientProfileInfo.fromJson(clientRaw) : null,
    );
  }

  AuthUser _mapMeToUser(Map<String, dynamic> me, {required String token}) {
    // query "me" retorna formato ligeramente distinto
    final tenantRaw = me['tenant'] as Map<String, dynamic>?;
    final clientRaw = me['clientProfile'] as Map<String, dynamic>?;

    return AuthUser(
      userId: me['userId'] as String,
      token: token,
      refreshToken: '', // se lee del storage si se necesita
      capabilities: (me['capabilities'] as List)
          .map((e) => e.toString())
          .toList(),
      primaryRole: me['primaryRole'] as String? ?? 'CLIENT',
      tenant: tenantRaw != null ? TenantInfo.fromJson(tenantRaw) : null,
      clientProfile:
          clientRaw != null ? ClientProfileInfo.fromJson(clientRaw) : null,
    );
  }

  // ─── Exception → AuthFailure ───────────────────────────────────────────────

  AuthFailure _mapException(Object e) {
    final msg = e.toString().toLowerCase();
    if (msg.contains('invalid') || msg.contains('credentials') || msg.contains('invalidcredentials')) {
      return const AuthFailure.invalidCredentials();
    }
    if (msg.contains('already') || msg.contains('exists') || msg.contains('alreadyexists')) {
      return const AuthFailure.emailAlreadyExists();
    }
    if (msg.contains('unauthorized') || msg.contains('401') || msg.contains('expired')) {
      return const AuthFailure.sessionExpired();
    }
    if (msg.contains('500') || msg.contains('server')) {
      return AuthFailure.serverError(message: e.toString());
    }
    return AuthFailure.unknown(message: e.toString());
  }
}
