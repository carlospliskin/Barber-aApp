import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../../../core/providers/infrastructure_providers.dart';
import '../../data/datasources/auth_remote_datasource.dart';
import '../../data/repositories/auth_repository_impl.dart';
import '../../domain/entities/auth_user.dart';
import '../../domain/repositories/i_auth_repository.dart';
import '../../domain/usecases/auth_use_cases.dart';
import 'auth_state.dart';

// ─── Providers de la feature auth ─────────────────────────────────────────────
// Ordenados de infraestructura → data → domain → presentation

final _authDatasourceProvider = Provider<AuthRemoteDatasource>((ref) =>
  AuthRemoteDatasource(client: ref.read(httpClientProvider)),
);

final authRepositoryProvider = Provider<IAuthRepository>((ref) =>
  AuthRepositoryImpl(
    remote:  ref.read(_authDatasourceProvider),
    storage: ref.read(secureStorageProvider),
  ),
);

// 1 provider por caso de uso — facilita testing y sustitución
final _restoreSessionUseCaseProvider = Provider<RestoreSessionUseCase>((ref) =>
  RestoreSessionUseCase(ref.read(authRepositoryProvider)),
);
final _loginUseCaseProvider = Provider<LoginUseCase>((ref) =>
  LoginUseCase(ref.read(authRepositoryProvider)),
);
final _registerOwnerUseCaseProvider = Provider<RegisterOwnerUseCase>((ref) =>
  RegisterOwnerUseCase(ref.read(authRepositoryProvider)),
);
final _registerClientUseCaseProvider = Provider<RegisterClientUseCase>((ref) =>
  RegisterClientUseCase(ref.read(authRepositoryProvider)),
);
final _switchModeUseCaseProvider = Provider<SwitchModeUseCase>((ref) =>
  SwitchModeUseCase(ref.read(authRepositoryProvider)),
);
final _logoutUseCaseProvider = Provider<LogoutUseCase>((ref) =>
  LogoutUseCase(ref.read(authRepositoryProvider)),
);

// ─── AuthNotifier ─────────────────────────────────────────────────────────────
// Responsabilidad única: gestionar AuthState.
// SOLO conoce UseCases — no sabe nada de HTTP, storage ni implementaciones.

class AuthNotifier extends StateNotifier<AuthState> {
  final RestoreSessionUseCase _restoreSession;
  final LoginUseCase _login;
  final RegisterOwnerUseCase _registerOwner;
  final RegisterClientUseCase _registerClient;
  final SwitchModeUseCase _switchMode;
  final LogoutUseCase _logout;

  AuthNotifier({
    required RestoreSessionUseCase restoreSession,
    required LoginUseCase login,
    required RegisterOwnerUseCase registerOwner,
    required RegisterClientUseCase registerClient,
    required SwitchModeUseCase switchMode,
    required LogoutUseCase logout,
  })  : _restoreSession = restoreSession,
        _login = login,
        _registerOwner = registerOwner,
        _registerClient = registerClient,
        _switchMode = switchMode,
        _logout = logout,
        super(const AuthState.loading()) {
    _init();
  }

  // ─── Init ──────────────────────────────────────────────────────────────────

  Future<void> _init() async {
    final result = await _restoreSession();
    state = result.fold(
      (_) => const AuthState.unauthenticated(),
      (user) => user == null
          ? const AuthState.unauthenticated()
          : _resolveState(user, isSwitching: false),
    );
  }

  // ─── Login ─────────────────────────────────────────────────────────────────

  Future<void> login({required String email, required String password}) async {
    state = const AuthState.unauthenticated(isLoading: true);
    final result = await _login(email: email, password: password);
    state = result.fold(
      (f) => AuthState.unauthenticated(failure: f),
      (u) => _resolveState(u, isSwitching: false),
    );
  }

  // ─── Registro Owner ────────────────────────────────────────────────────────

  Future<void> registerOwner({
    required String email,
    required String password,
    required String barberiaName,
    required String ownerName,
    String? phone,
  }) async {
    state = const AuthState.unauthenticated(isLoading: true);
    final result = await _registerOwner(
      email: email, password: password,
      barberiaName: barberiaName, ownerName: ownerName, phone: phone,
    );
    state = result.fold(
      (f) => AuthState.unauthenticated(failure: f),
      (u) => _resolveState(u, isSwitching: false),
    );
  }

  // ─── Registro Cliente ──────────────────────────────────────────────────────

  Future<void> registerClient({
    required String email,
    required String password,
    required String name,
    required String phone,
  }) async {
    state = const AuthState.unauthenticated(isLoading: true);
    final result = await _registerClient(
      email: email, password: password, name: name, phone: phone,
    );
    state = result.fold(
      (f) => AuthState.unauthenticated(failure: f),
      (u) => _resolveState(u, isSwitching: false),
    );
  }

  // ─── Switch Mode ───────────────────────────────────────────────────────────

  Future<void> switchMode(String mode) async {
    final user = state.user;
    if (user == null) return;

    state = AuthState.modeSelection(user: user, isLoading: true);
    final result = await _switchMode(mode: mode, currentToken: user.token);
    state = result.fold(
      // switchMode falló → volver al modeSelection sin error de carga
      (f) => AuthState.modeSelection(user: user),
      (u) => _resolveState(u, isSwitching: true),
    );
  }

  // ─── Logout ────────────────────────────────────────────────────────────────

  Future<void> logout() async {
    await _logout();
    state = const AuthState.unauthenticated();
  }

  // ─── Clear error — BUG-07: usar maybeWhen en lugar de is _Unauthenticated ──

  void clearError() {
    state.maybeWhen(
      unauthenticated: (_, __) {
        state = const AuthState.unauthenticated();
      },
      orElse: () {},
    );
  }

  // ─── Resolver: AuthUser → AuthState correcto ───────────────────────────────

  AuthState _resolveState(AuthUser user, {required bool isSwitching}) {
    // Cliente puro → siempre directo al home de cliente
    if (!user.isOwner && !user.isBarber) {
      return AuthState.client(user: user);
    }

    if (isSwitching) {
      // Viene de switchMode → ir directo al home del modo elegido
      return switch (user.primaryRole) {
        'OWNER'  => AuthState.owner(user: user),
        'BARBER' => AuthState.barber(user: user),
        _        => AuthState.client(user: user),
      };
    }

    // Login inicial de owner/barber → mostrar selector de modo
    return AuthState.modeSelection(user: user);
  }
}

// ─── Providers públicos ───────────────────────────────────────────────────────

final authNotifierProvider =
    StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  return AuthNotifier(
    restoreSession: ref.read(_restoreSessionUseCaseProvider),
    login:          ref.read(_loginUseCaseProvider),
    registerOwner:  ref.read(_registerOwnerUseCaseProvider),
    registerClient: ref.read(_registerClientUseCaseProvider),
    switchMode:     ref.read(_switchModeUseCaseProvider),
    logout:         ref.read(_logoutUseCaseProvider),
  );
});

final currentUserProvider = Provider<AuthUser?>((ref) =>
  ref.watch(authNotifierProvider).user,
);

final authModeProvider = Provider<AuthMode>((ref) =>
  ref.watch(authNotifierProvider).mode,
);
