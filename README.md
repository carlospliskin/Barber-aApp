# Barber-aApp

App Android **nativa en Kotlin** para Barbería SaaS: reservas, panel del dueño y barbero, y programa de lealtad.

> La versión anterior en Flutter quedó en la rama [`flutter-legacy`](https://github.com/carlospliskin/Barber-aApp/tree/flutter-legacy) como referencia para portar pantallas.

## Stack
- Kotlin 2.4 · Jetpack Compose (Material 3) · Navigation Compose con rutas tipadas
- **MVI** (State / Intent / Effect) sobre `ViewModel` + `StateFlow`
- **Clean Architecture** en módulos Gradle: `domain` (Kotlin puro) ← `data` ← `presentation`
- Koin (DI) · OkHttp + kotlinx.serialization (GraphQL) · DataStore (sesión)
- JUnit4 · coroutines-test · Turbine · MockWebServer

## Requisitos
- Android Studio con SDK 37 (compileSdk) y JDK 21 (Gradle lo elige con `gradle/gradle-daemon-jvm.properties`)
- Backend GraphQL en `http://localhost:8080/graphql` para debug (el emulador lo ve como `10.0.2.2`)

## Comandos
```bash
./gradlew test                 # tests unitarios de todos los módulos
./gradlew :app:assembleDebug   # APK debug
./gradlew :app:installDebug    # instalar en el emulador/dispositivo
```

La URL del backend se define por build type en [`app/build.gradle.kts`](app/build.gradle.kts) (`GRAPHQL_URL`).

Arquitectura y reglas: [ARCHITECTURE.md](ARCHITECTURE.md).
