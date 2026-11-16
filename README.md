# Barber-aApp

App Android **nativa en Kotlin** para Barbería SaaS: reservas, panel del dueño y barbero, y programa de lealtad.

> La versión anterior en Flutter quedó en la rama [`flutter-legacy`](https://github.com/carlospliskin/Barber-aApp/tree/flutter-legacy) como referencia para portar pantallas.

## Stack
- Kotlin 2.4 · Jetpack Compose (Material 3) · Navigation Compose con rutas tipadas
- **MVI** (State / Intent / Effect) sobre `ViewModel` + `StateFlow`
- **Clean Architecture** en módulos Gradle: `domain` (Kotlin puro) ← `data` ← `presentation`
- Firebase (plan Spark): Auth + Firestore · Koin (DI) · DataStore (preferencias)
- TDD: JUnit4 · coroutines-test · Turbine · `@firebase/rules-unit-testing`

## Requisitos
- Android Studio con SDK 37 (compileSdk) y JDK 21 (Gradle lo elige con `gradle/gradle-daemon-jvm.properties`)
- Firebase CLI y Node para los emuladores y las pruebas de reglas ([docs/FIREBASE.md](docs/FIREBASE.md))

## Comandos
```bash
firebase emulators:start --only auth,firestore   # backend local para debug
./gradlew test                 # tests unitarios de todos los módulos
(cd firebase && npm test)      # tests de reglas de seguridad
./gradlew :app:assembleDebug   # APK debug
./gradlew :app:installDebug    # instalar en el emulador/dispositivo
```

Plan de trabajo y huecos: [docs/PLAN.md](docs/PLAN.md).

Arquitectura y reglas: [ARCHITECTURE.md](ARCHITECTURE.md).
