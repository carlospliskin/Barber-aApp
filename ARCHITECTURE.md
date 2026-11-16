# Arquitectura — Barber-aApp (Kotlin nativo)

## Módulos

```
app                          Composition root: Application, Koin, MainActivity, NavHost
core/common      (JVM)       AppResult<E, T> (éxito o fallo tipado)
core/firebase    (Android)   FirebaseApp sin google-services.json, emulador en debug, caché persistente
core/mvi         (Android)   MviViewModel (cola de intents) + CollectEffects (respeta el ciclo de vida)
core/designsystem(Android)   BarberTheme (Obsidian + Gold), BarberButton, BarberTextField
feature/auth/domain       (JVM)      Entidades, SessionState, AuthValidator, AuthRepository + SessionRepository, casos de uso
                                     testFixtures: fakes compartidos con los tests de data y presentation
feature/auth/data         (Android)  Gateways (interfaces chicas) + implementación Firebase, FirebaseAccountRepository
feature/auth/presentation (Android)  SessionViewModel, Login (reducer puro), Registro, Selector de modo
feature/catalog/domain    (JVM)      Catalog (servicios + barberos + horarios), CatalogEditor (reglas puras), casos de uso
feature/catalog/data      (Android)  CatalogMapper tolerante, transacción con versión sobre tenants/{t}/catalog/main
feature/catalog/presentation         CatalogViewModel (formularios como texto → conversión al guardar), pantalla del dueño
build-logic                          Convention plugins: barberia.android.library / .android.compose / .jvm.library
```

Dependencias permitidas (la flecha apunta a quien se conoce):

```
app ──► feature:*:presentation ──► feature:*:domain ◄── feature:*:data ──► core:firebase
          │                                 │
          └──► core:mvi, core:designsystem   └──► core:common
```

- `domain` es Kotlin puro: no puede importar Android, Compose, Firebase ni DataStore. Gradle lo garantiza porque es un módulo JVM.
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

- `XRoute` conecta el ViewModel (Koin) y recoge los efectos con `CollectEffects`; `XScreen` no tiene estado, recibe `state` + `onIntent` y tiene `@Preview`.
- El estado solo cambia en `setState`. En pantallas con varias transiciones se usa un reducer puro `State.reduce(Mutation)` que se prueba sin corrutinas (ver `LoginContract.kt`).
- Los intents se procesan en cola, en orden. Lo que dura toda la vida del ViewModel (observar un Flow) va en `launchInScope` para no bloquear la cola.
- El ViewModel guarda tipos de dominio (`FieldError`, `AuthFailure`); la vista los convierte a texto con `strings.xml`.

## Navegación guiada por la sesión
Login, registro, elegir modo y cerrar sesión **solo cambian la sesión**. `SessionViewModel` observa
`ObserveSessionDestinationUseCase` y `BarberiaNavHost` navega cuando cambia el destino. Así:
- tras la muerte del proceso, la app vuelve sola al home correcto;
- ninguna pantalla decide a dónde ir después de autenticarse.

## SOLID aplicado
- **S**: un caso de uso = una acción; el datasource solo habla con la red; el repositorio solo orquesta y mapea.
- **O**: nuevos errores o destinos se agregan a `sealed`/`enum` y el compilador obliga a cubrirlos en cada `when`.
- **L**: los fakes de los tests sustituyen a `AuthRepository` / `SessionRepository` / los gateways sin cambios.
- **I**: interfaces chicas (`AuthGateway`, `ProfileGateway`, `ModeStore`); `AuthRepository` y `SessionRepository` separados.
- **D**: ViewModel → caso de uso → `AuthRepository` (interfaz); la implementación se inyecta desde `app`.

## Reglas de negocio de sesión
`resolveDestination(SessionState, AppMode?)` en el dominio decide a dónde va el usuario:
cliente puro → `ClientHome`; personal con un modo guardado que todavía tiene → ese home; si no → `ModeSelection`.
El modo elegido se guarda localmente (DataStore), no en Firestore: no gasta escrituras.

## TDD
Cada caso de uso, reducer, ViewModel y regla de seguridad se escribe primero como test. Ver la estrategia en [docs/PLAN.md](docs/PLAN.md).

## Cómo agregar una feature (ej. `home-owner`)
1. `feature/home-owner/domain` (JVM): entidades, `XFailure`, `XRepository`, casos de uso + tests.
2. `feature/home-owner/data`: gateways sobre Firestore, `XRepositoryImpl`, módulo Koin; reglas y sus tests en `firebase/`.
3. `feature/home-owner/presentation`: `Contract`, `ViewModel : MviViewModel`, `Route` + `Screen`, módulo Koin.
4. Registrar los módulos en `settings.gradle.kts`, `app/build.gradle.kts` y `BarberiaApplication`.
5. Reemplazar el `HomeDest` provisional del `NavHost`.

## Pendiente
Ver fases y huecos en [docs/PLAN.md](docs/PLAN.md).
