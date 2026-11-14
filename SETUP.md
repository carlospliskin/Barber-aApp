# Barbería SaaS — Setup

## Requisitos
- Flutter SDK ≥ 3.0.0
- Dart SDK ≥ 3.0.0
- Android Studio / Xcode
- PostgreSQL 14+
- JDK 17+

---

## Backend

### 1. Variables de entorno
Copiar `.env.example` a `.env` y configurar:
```bash
cd backend
cp .env.example .env
```

Variables obligatorias:
```
DATABASE_URL=jdbc:postgresql://localhost:5432/barberia_dev
DATABASE_USER=postgres
DATABASE_PASSWORD=postgres
JWT_SECRET=min-32-chars-secret-key-here-ok
SERVER_PORT=8080
APP_BASE_URL=http://localhost:8080
```

Variables opcionales (funciona en modo simulado sin ellas):
```
MP_ACCESS_TOKEN=           # MercadoPago — vacío = modo simulado
FCM_SERVER_KEY=            # Firebase Cloud Messaging
FCM_PROJECT_ID=
```

### 2. Base de datos
```bash
createdb barberia_dev
```
Las migraciones Flyway corren automáticamente al iniciar.

### 3. Arrancar
```bash
cd backend
./gradlew run
# → http://localhost:8080/graphql
# → Playground en desarrollo
```

---

## Flutter (Mobile)

### 1. Instalar fuentes tipográficas
Descargar de Google Fonts y colocar en `mobile/assets/fonts/`:
- [Playfair Display](https://fonts.google.com/specimen/Playfair+Display)
  - PlayfairDisplay-Regular.ttf
  - PlayfairDisplay-SemiBold.ttf
  - PlayfairDisplay-Bold.ttf
  - PlayfairDisplay-Black.ttf
- [DM Sans](https://fonts.google.com/specimen/DM+Sans)
  - DMSans-Light.ttf
  - DMSans-Regular.ttf
  - DMSans-Medium.ttf
  - DMSans-SemiBold.ttf
- [DM Mono](https://fonts.google.com/specimen/DM+Mono)
  - DMMono-Regular.ttf
  - DMMono-Medium.ttf

> Sin las fuentes la app corre usando la fuente del sistema.

### 2. Instalar dependencias y generar código
```bash
cd mobile
flutter pub get

# Generar archivos Freezed y json_serializable
# OBLIGATORIO antes del primer run
dart run build_runner build --delete-conflicting-outputs
```

### 3. Correr la app
```bash
# Android (emulador o dispositivo)
flutter run

# iOS (simulador)
flutter run -d "iPhone 15"

# Verificar dispositivos disponibles
flutter devices
```

### 4. URL del backend según plataforma
Configurado en cada datasource:
```dart
static const _baseUrl = kDebugMode
    ? 'http://10.0.2.2:8080/graphql'  // Android emulator
    : 'https://tu-backend.com/graphql'; // Producción
```

Para **iOS Simulator**, cambiar a `http://localhost:8080/graphql`.

### 5. Regenerar código (cuando se modifica un @freezed o @JsonSerializable)
```bash
dart run build_runner build --delete-conflicting-outputs
# o en modo watch:
dart run build_runner watch --delete-conflicting-outputs
```

---

## Arquitectura

Ver `ARCHITECTURE.md` en la carpeta `mobile/`.

## GraphQL — Contratos

Ver `backend/CONTRACTS.graphql` y `backend/FLOWS.graphql`.
