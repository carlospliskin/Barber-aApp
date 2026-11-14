# Arquitectura — Barber-aApp (Kotlin nativo)

## Módulos

```
app                          Composition root: Application, Koin, MainActivity, NavHost
core/common      (JVM)       AppResult<E, T> (éxito o fallo tipado)
core/network     (JVM)       GraphQLClient (interfaz) + OkHttpGraphQLClient, NetworkException
core/mvi         (Android)   MviViewModel<State, Intent, Effect>
core/designsystem(Android)   BarberTheme (Obsidian + Gold), BarberButton, BarberTextField
feature/auth/domain       (JVM)      Entidades, AuthFailure, AuthRepository (interfaz), casos de uso
feature/auth/data         (Android)  DTOs, AuthRemoteDataSource, SessionStorage (DataStore), AuthRepositoryImpl
feature/auth/presentation (Android)  Splash, Login, ModeSelector (Contract + ViewModel + Screen)
```

Dependencias permitidas (la flecha apunta a quien se conoce):

```
app ──► feature:*:presentation ──► feature:*:domain ◄── feature:*:data ──► core:network
          │                                 │
          └──► core:mvi, core:designsystem   └──► core:common
```

- `domain` es Kotlin puro: no puede importar Android, Compose, OkHttp ni DataStore. Gradle lo garantiza porque es un módulo JVM.
- `presentation` no conoce `data`; solo casos de uso del dominio.
- `app` es el único que ve todo y elige las implementaciones concretas (Koin).

## MVI

Cada pantalla tiene un `*Contract.kt` con tres tipos:

| Tipo | Qué es | Ejemplo |
|---|---|---|
| `State` | `data class` inmutable que la vista dibuja | `LoginState(email, password, isLoading, errorMessage)` |
| `Intent` | `sealed interface` con lo que el usuario hace | `EmailChanged`, `Submit`, `DismissError` |
| `Effect` | evento de una sola vez (no es estado) | `Navigate(SessionDestination)` |

Flujo: `Screen → onIntent(intent) → handleIntent → caso de uso → setState { copy(...) } / sendEffect(...)`.

- `XRoute` conecta el ViewModel (Koin) y recoge los efectos; `XScreen` no tiene estado, recibe `state` + `onIntent` y tiene `@Preview`.
- El estado solo cambia en `setState`.

## SOLID aplicado
- **S**: un caso de uso = una acción; el datasource solo habla con la red; el repositorio solo orquesta y mapea.
- **O**: nuevos errores o destinos se agregan a `sealed`/`enum` y el compilador obliga a cubrirlos en cada `when`.
- **L**: los fakes de los tests sustituyen a `AuthRepository` / `GraphQLClient` / `SessionStorage` sin cambios.
- **I**: interfaces chicas (`SessionStorage`, `GraphQLClient`).
- **D**: ViewModel → caso de uso → `AuthRepository` (interfaz); la implementación se inyecta desde `app`.

## Reglas de negocio de sesión
`AuthUser?.destination(afterModeSwitch)` en el dominio decide a dónde va el usuario:
cliente puro → `ClientHome`; dueño o barbero tras login → `ModeSelection`; tras elegir modo → home del `primaryRole`.
La navegación (`BarberiaNavHost`) solo traduce ese destino a una ruta.

## Cómo agregar una feature (ej. `home-owner`)
1. `feature/home-owner/domain` (JVM): entidades, `XFailure`, `XRepository`, casos de uso + tests.
2. `feature/home-owner/data`: DTOs, datasource sobre `GraphQLClient`, `XRepositoryImpl`, módulo Koin.
3. `feature/home-owner/presentation`: `Contract`, `ViewModel : MviViewModel`, `Route` + `Screen`, módulo Koin.
4. Registrar los módulos en `settings.gradle.kts`, `app/build.gradle.kts` y `BarberiaApplication`.
5. Reemplazar el `HomeDest` provisional del `NavHost`.

## Pendiente
- Portar desde `flutter-legacy`: registro, home de dueño/barbero (dashboard, agenda, servicios, horarios, clientes, lealtad), home de cliente (reservas).
- Cifrar tokens con Android Keystore (`DataStoreSessionStorage` tiene el TODO).
- Integrar Firebase (FCM para notificaciones; ver si Auth/Firestore reemplaza al backend GraphQL).
- Fuentes Playfair Display / DM Sans.
