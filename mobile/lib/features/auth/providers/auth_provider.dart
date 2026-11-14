import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../../core/network/graphql_client.dart';

// ─── Models ───────────────────────────────────────────────────────────────────

enum AuthRole { owner, barber, client }

enum AuthMode { loading, unauthenticated, modeSelection, owner, barber, client }

class AuthUser {
  final String userId;
  final String token;
  final String refreshToken;
  final List<String> capabilities;
  final String primaryRole;
  final TenantInfo? tenant;
  final ClientProfileInfo? clientProfile;

  const AuthUser({
    required this.userId,
    required this.token,
    required this.refreshToken,
    required this.capabilities,
    required this.primaryRole,
    this.tenant,
    this.clientProfile,
  });

  bool get isOwner => capabilities.contains('OWNER');
  bool get isBarber => capabilities.contains('BARBER');
  bool get isClient => capabilities.contains('CLIENT');
  bool get hasMultipleRoles => capabilities.length > 1;

  AuthMode get activeMode {
    switch (primaryRole) {
      case 'OWNER': return AuthMode.owner;
      case 'BARBER': return AuthMode.barber;
      default: return AuthMode.client;
    }
  }

  AuthUser copyWith({
    String? token,
    String? refreshToken,
    String? primaryRole,
    TenantInfo? tenant,
    ClientProfileInfo? clientProfile,
  }) => AuthUser(
    userId: userId,
    token: token ?? this.token,
    refreshToken: refreshToken ?? this.refreshToken,
    capabilities: capabilities,
    primaryRole: primaryRole ?? this.primaryRole,
    tenant: tenant ?? this.tenant,
    clientProfile: clientProfile ?? this.clientProfile,
  );
}

class TenantInfo {
  final String id;
  final String name;
  final String timezone;
  final String plan;

  const TenantInfo({
    required this.id,
    required this.name,
    required this.timezone,
    required this.plan,
  });

  factory TenantInfo.fromMap(Map<String, dynamic> map) => TenantInfo(
    id: map['id'] as String,
    name: map['name'] as String,
    timezone: map['timezone'] as String? ?? 'America/Mexico_City',
    plan: map['plan'] as String? ?? 'FREE',
  );
}

class ClientProfileInfo {
  final String id;
  final String name;
  final String? phone;
  final bool isPenalized;
  final String? penaltyUntil;
  final int noShowCount;

  const ClientProfileInfo({
    required this.id,
    required this.name,
    this.phone,
    required this.isPenalized,
    this.penaltyUntil,
    required this.noShowCount,
  });

  factory ClientProfileInfo.fromMap(Map<String, dynamic> map) =>
    ClientProfileInfo(
      id: map['id'] as String,
      name: map['name'] as String,
      phone: map['phone'] as String?,
      isPenalized: map['isPenalized'] as bool? ?? false,
      penaltyUntil: map['penaltyUntil'] as String?,
      noShowCount: map['noShowCount'] as int? ?? 0,
    );
}

// ─── State ────────────────────────────────────────────────────────────────────

class AuthState {
  final AuthMode mode;
  final AuthUser? user;
  final String? error;

  const AuthState({
    this.mode = AuthMode.loading,
    this.user,
    this.error,
  });

  AuthState copyWith({
    AuthMode? mode,
    AuthUser? user,
    String? error,
  }) => AuthState(
    mode: mode ?? this.mode,
    user: user ?? this.user,
    error: error,
  );

  bool get isLoading => mode == AuthMode.loading;
  bool get isAuthenticated => user != null;
}

// ─── Notifier ─────────────────────────────────────────────────────────────────

class AuthNotifier extends StateNotifier<AuthState> {
  final FlutterSecureStorage _storage;
  final GraphQLService _graphql;

  static const _tokenKey = 'auth_token';
  static const _refreshKey = 'auth_refresh_token';
  static const _userIdKey = 'auth_user_id';

  AuthNotifier(this._storage, this._graphql) : super(const AuthState()) {
    _tryRestoreSession();
  }

  // ─── Restaurar sesión guardada ─────────────────────────────────────────────

  Future<void> _tryRestoreSession() async {
    try {
      final token = await _storage.read(key: _tokenKey);
      if (token == null) {
        state = state.copyWith(mode: AuthMode.unauthenticated);
        return;
      }

      // Verificar token con query "me"
      final result = await _graphql.query(
        query: _meQuery,
        token: token,
      );

      if (result == null || result['me'] == null) {
        await _clearStorage();
        state = state.copyWith(mode: AuthMode.unauthenticated);
        return;
      }

      final me = result['me'] as Map<String, dynamic>;
      final capabilities = (me['capabilities'] as List?)
          ?.map((e) => e.toString()).toList() ?? ['CLIENT'];
      final primaryRole = me['primaryRole'] as String? ?? 'CLIENT';

      final user = AuthUser(
        userId: me['userId'] as String,
        token: token,
        refreshToken: await _storage.read(key: _refreshKey) ?? '',
        capabilities: capabilities,
        primaryRole: primaryRole,
        tenant: me['tenant'] != null
            ? TenantInfo.fromMap(me['tenant'] as Map<String, dynamic>)
            : null,
        clientProfile: me['clientProfile'] != null
            ? ClientProfileInfo.fromMap(
                me['clientProfile'] as Map<String, dynamic>)
            : null,
      );

      state = AuthState(
        mode: _modeFromRole(primaryRole),
        user: user,
      );
    } catch (e) {
      await _clearStorage();
      state = state.copyWith(mode: AuthMode.unauthenticated);
    }
  }

  // ─── Login ─────────────────────────────────────────────────────────────────

  Future<String?> login(String email, String password) async {
    try {
      state = state.copyWith(mode: AuthMode.loading, error: null);

      final result = await _graphql.mutate(
        mutation: _loginMutation,
        variables: {'email': email.trim(), 'password': password},
      );

      if (result == null || result['login'] == null) {
        state = state.copyWith(
          mode: AuthMode.unauthenticated,
          error: 'Error de conexión. Intenta de nuevo.',
        );
        return 'Error de conexión';
      }

      return await _handleAuthPayload(result['login'] as Map<String, dynamic>);
    } catch (e) {
      final msg = _parseError(e.toString());
      state = state.copyWith(
        mode: AuthMode.unauthenticated,
        error: msg,
      );
      return msg;
    }
  }

  // ─── Registro Barbería ─────────────────────────────────────────────────────

  Future<String?> registerOwner({
    required String email,
    required String password,
    required String barberiaName,
    required String ownerName,
    String? phone,
  }) async {
    try {
      state = state.copyWith(mode: AuthMode.loading, error: null);

      final result = await _graphql.mutate(
        mutation: _registerMutation,
        variables: {
          'email': email.trim(),
          'password': password,
          'barberiaName': barberiaName.trim(),
          'ownerName': ownerName.trim(),
          'phone': phone?.trim(),
        },
      );

      if (result == null || result['register'] == null) {
        state = state.copyWith(mode: AuthMode.unauthenticated, error: 'Error al registrar');
        return 'Error al registrar';
      }

      return await _handleAuthPayload(
        result['register'] as Map<String, dynamic>,
      );
    } catch (e) {
      final msg = _parseError(e.toString());
      state = state.copyWith(mode: AuthMode.unauthenticated, error: msg);
      return msg;
    }
  }

  // ─── Registro Cliente ──────────────────────────────────────────────────────

  Future<String?> registerClient({
    required String email,
    required String password,
    required String name,
    required String phone,
  }) async {
    try {
      state = state.copyWith(mode: AuthMode.loading, error: null);

      final result = await _graphql.mutate(
        mutation: _registerClientMutation,
        variables: {
          'email': email.trim(),
          'password': password,
          'name': name.trim(),
          'phone': phone.trim(),
        },
      );

      if (result == null || result['registerClient'] == null) {
        state = state.copyWith(mode: AuthMode.unauthenticated, error: 'Error al registrar');
        return 'Error al registrar';
      }

      return await _handleAuthPayload(
        result['registerClient'] as Map<String, dynamic>,
      );
    } catch (e) {
      final msg = _parseError(e.toString());
      state = state.copyWith(mode: AuthMode.unauthenticated, error: msg);
      return msg;
    }
  }

  // ─── Switch Mode (Owner ↔ Client) ─────────────────────────────────────────

  Future<void> switchMode(String mode) async {
    if (state.user == null) return;
    try {
      _isSwitching = true;
      final result = await _graphql.mutate(
        mutation: _switchModeMutation,
        variables: {'mode': mode},
        token: state.user!.token,
      );

      if (result == null || result['switchMode'] == null) {
        _isSwitching = false;
        return;
      }

      await _handleAuthPayload(result['switchMode'] as Map<String, dynamic>);
    } catch (e) {
      debugPrint('switchMode error: $e');
    } finally {
      _isSwitching = false;
    }
  }

  // ─── Logout ────────────────────────────────────────────────────────────────

  Future<void> logout() async {
    await _clearStorage();
    state = const AuthState(mode: AuthMode.unauthenticated);
  }

  // ─── Helpers ───────────────────────────────────────────────────────────────

  Future<String?> _handleAuthPayload(Map<String, dynamic> payload) async {
    final token = payload['token'] as String;
    final refreshToken = payload['refreshToken'] as String;
    final userId = payload['userId'] as String;
    final capabilities = (payload['capabilities'] as List?)
        ?.map((e) => e.toString()).toList() ?? ['CLIENT'];
    final primaryRole = payload['primaryRole'] as String? ?? 'CLIENT';

    final user = AuthUser(
      userId: userId,
      token: token,
      refreshToken: refreshToken,
      capabilities: capabilities,
      primaryRole: primaryRole,
      tenant: payload['tenant'] != null
          ? TenantInfo.fromMap(payload['tenant'] as Map<String, dynamic>)
          : null,
      clientProfile: payload['clientProfile'] != null
          ? ClientProfileInfo.fromMap(
              payload['clientProfile'] as Map<String, dynamic>)
          : null,
    );

    await _storage.write(key: _tokenKey, value: token);
    await _storage.write(key: _refreshKey, value: refreshToken);
    await _storage.write(key: _userIdKey, value: userId);

    state = AuthState(
      mode: _modeFromRole(primaryRole, isSwitching: _isSwitching),
      user: user,
    );

    return null; // null = success
  }

  bool _isSwitching = false;

  /// Al hacer LOGIN, owner y barber van a modeSelection.
  /// Al hacer SWITCH, van directo al modo elegido.
  AuthMode _modeFromRole(String role, {bool isSwitching = false}) {
    if (isSwitching) {
      // switchMode va directo al home sin pasar por selector
      switch (role) {
        case 'OWNER': return AuthMode.owner;
        case 'BARBER': return AuthMode.barber;
        default: return AuthMode.client;
      }
    }
    // Login normal: owner/barber ven selector primero
    switch (role) {
      case 'OWNER':
      case 'BARBER':
        return AuthMode.modeSelection;
      default:
        return AuthMode.client;
    }
  }

  String _parseError(String raw) {
    if (raw.contains('Invalid credentials') ||
        raw.contains('InvalidCredentials')) {
      return 'Email o contraseña incorrectos';
    }
    if (raw.contains('already exists') || raw.contains('AlreadyExists')) {
      return 'Este email ya está registrado';
    }
    if (raw.contains('SocketException') || raw.contains('Connection')) {
      return 'Sin conexión. Verifica tu internet';
    }
    return 'Ocurrió un error. Intenta de nuevo.';
  }

  Future<void> _clearStorage() async {
    await _storage.delete(key: _tokenKey);
    await _storage.delete(key: _refreshKey);
    await _storage.delete(key: _userIdKey);
  }

  // ─── Queries / Mutations ───────────────────────────────────────────────────

  static const _loginMutation = '''
    mutation Login(\$email: String!, \$password: String!) {
      login(email: \$email, password: \$password) {
        token refreshToken userId
        capabilities primaryRole
        tenant { id name timezone plan }
        clientProfile { id name phone isPenalized penaltyUntil noShowCount }
      }
    }
  ''';

  static const _registerMutation = '''
    mutation Register(
      \$email: String!, \$password: String!,
      \$barberiaName: String!, \$ownerName: String!, \$phone: String
    ) {
      register(
        email: \$email, password: \$password,
        barberiaName: \$barberiaName, ownerName: \$ownerName, phone: \$phone
      ) {
        token refreshToken userId
        capabilities primaryRole
        tenant { id name timezone plan }
        clientProfile { id name phone isPenalized noShowCount }
      }
    }
  ''';

  static const _registerClientMutation = '''
    mutation RegisterClient(
      \$email: String!, \$password: String!,
      \$name: String!, \$phone: String!
    ) {
      registerClient(
        email: \$email, password: \$password,
        name: \$name, phone: \$phone
      ) {
        token refreshToken userId
        capabilities primaryRole
        clientProfile { id name phone isPenalized noShowCount }
      }
    }
  ''';

  static const _switchModeMutation = '''
    mutation SwitchMode(\$mode: String!) {
      switchMode(mode: \$mode) {
        token refreshToken
        capabilities primaryRole
        tenant { id name }
        clientProfile { name isPenalized }
      }
    }
  ''';

  static const _meQuery = '''
    query {
      me {
        userId email name
        capabilities primaryRole
        tenant { id name timezone plan }
        clientProfile { id name phone isPenalized penaltyUntil noShowCount }
      }
    }
  ''';
}

// ─── Providers ────────────────────────────────────────────────────────────────

final _storageProvider = Provider<FlutterSecureStorage>((_) =>
  const FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
    iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
  ),
);

final authProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  return AuthNotifier(
    ref.read(_storageProvider),
    ref.read(graphqlServiceProvider),
  );
});

// Conveniencia
final currentUserProvider = Provider<AuthUser?>((ref) {
  return ref.watch(authProvider).user;
});

final authModeProvider = Provider<AuthMode>((ref) {
  return ref.watch(authProvider).mode;
});
