# Barbería SaaS — Contrato Arquitectural Flutter

## Stack
- **Estado**: flutter_riverpod ^2.5.1 + StateNotifier
- **Modelos**: freezed ^2.5.2 + json_serializable ^6.8.0
- **Errores**: fpdart Either<Failure, T>
- **Navegación**: go_router ^13.2.0
- **HTTP**: http ^1.2.1 (sin graphql_flutter — llamadas manuales)
- **Storage**: flutter_secure_storage ^9.0.0

---

## Capas por feature

```
feature/
├── domain/
│   ├── entities/       Freezed, sin imports externos
│   ├── failures/       Freezed union — AuthFailure, AppointmentFailure...
│   ├── repositories/   abstract interface class — contratos puros
│   └── usecases/       1 clase, 1 método call(), 1 responsabilidad
│
├── data/
│   ├── datasources/    HTTP puro — retorna Map<String,dynamic>
│   ├── models/         Freezed + fromJson — nunca expuestos al domain
│   └── repositories/   Implementación — convierte excepciones en Failure
│
└── presentation/
    ├── notifiers/      StateNotifier<XState> — solo llama UseCases
    ├── screens/        ConsumerWidget — solo ref.watch + ref.read
    └── widgets/        Widgets privados de la feature
```

---

## Reglas inviolables

### Domain
```dart
// ✅ Correcto
abstract interface class IAppointmentRepository {
  Future<Either<AppointmentFailure, List<Appointment>>> getByDate(DateTime date);
}

// ❌ Incorrecto — domain no sabe de http, storage ni flutter
import 'package:http/http.dart';
```

### UseCase
```dart
// ✅ 1 clase = 1 caso de uso
class LoginUseCase {
  final IAuthRepository _repo;
  LoginUseCase(this._repo);

  Future<Either<AuthFailure, AuthUser>> call({
    required String email,
    required String password,
  }) => _repo.login(email: email, password: password);
}

// ❌ Incorrecto — lógica mezclada
class AuthUseCase {
  Future<void> login(...) { ... }
  Future<void> register(...) { ... }
  Future<void> logout(...) { ... }
}
```

### Repository
```dart
// ✅ Retorna Either, nunca lanza excepciones
Future<Either<AuthFailure, AuthUser>> login({...}) async {
  try {
    final data = await _datasource.login(...);
    return Right(_mapper.toDomain(data));
  } on SocketException {
    return const Left(AuthFailure.noConnection());
  } catch (e) {
    return Left(AuthFailure.unknown(message: e.toString()));
  }
}
```

### Notifier
```dart
// ✅ Solo llama UseCases
class AuthNotifier extends StateNotifier<AuthState> {
  final LoginUseCase _loginUseCase;

  Future<void> login({required String email, required String password}) async {
    state = state.copyWith(isLoading: true, failure: null);
    final result = await _loginUseCase(email: email, password: password);
    state = result.fold(
      (failure) => state.copyWith(isLoading: false, failure: failure),
      (user) => AuthState.authenticated(user: user),
    );
  }
}

// ❌ Incorrecto — notifier accediendo al repository directamente
class AuthNotifier extends StateNotifier<AuthState> {
  final IAuthRepository _repo; // ← MAL
}
```

### Screen
```dart
// ✅ Solo observa estado y dispara acciones
class LoginScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isLoading = ref.watch(authNotifierProvider.select((s) => s.isLoading));
    // ...
    ElevatedButton(
      onPressed: () => ref.read(authNotifierProvider.notifier).login(...),
    );
  }
}

// ❌ Incorrecto — lógica de negocio en la pantalla
class LoginScreen extends ConsumerWidget {
  Future<void> _submit() async {
    final repo = ref.read(authRepositoryProvider); // ← MAL
    await repo.login(...);
  }
}
```

---

## Providers — convención de nombres

```dart
// Infraestructura
final secureStorageProvider = Provider<FlutterSecureStorage>(...);
final httpClientProvider    = Provider<http.Client>(...);

// Data
final authDatasourceProvider  = Provider<AuthRemoteDatasource>(...);
final authRepositoryProvider  = Provider<IAuthRepository>(...);

// UseCases — 1 provider por caso de uso
final loginUseCaseProvider          = Provider<LoginUseCase>(...);
final registerOwnerUseCaseProvider  = Provider<RegisterOwnerUseCase>(...);
final switchModeUseCaseProvider     = Provider<SwitchModeUseCase>(...);

// Presentation
final authNotifierProvider = StateNotifierProvider<AuthNotifier, AuthState>(...);
```

---

## Freezed — convención

```dart
// Entidades de dominio → sin fromJson (son puras)
@freezed
class Appointment with _$Appointment {
  const factory Appointment({
    required String id,
    required DateTime startTime,
    required AppointmentStatus status,
  }) = _Appointment;
}

// Modelos de data → con fromJson
@freezed
class AppointmentModel with _$AppointmentModel {
  const factory AppointmentModel({...}) = _AppointmentModel;
  factory AppointmentModel.fromJson(Map<String,dynamic> json) => ...;
}

// Estado de presentación → con named constructors semánticos
@freezed
class AuthState with _$AuthState {
  const factory AuthState.loading() = _Loading;
  const factory AuthState.authenticated({required AuthUser user}) = _Authenticated;
  const factory AuthState.unauthenticated() = _Unauthenticated;
}
```

---

## Either — convención de uso

```dart
// Retorno siempre tipado
Future<Either<AuthFailure, AuthUser>> call({...});

// Consumo en Notifier
final result = await _useCase(...);
state = result.fold(
  (failure) => AuthState.error(failure: failure),
  (user)    => AuthState.authenticated(user: user),
);

// Consumo en UI (solo para casos simples)
result.fold(
  (failure) => showSnackBar(failure.userMessage),
  (user)    => router.go('/home'),
);
```
