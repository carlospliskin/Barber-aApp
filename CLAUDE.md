# Barber-aApp — guía para IA y desarrolladores

App Android nativa (Kotlin + Compose) para barberías, con Firebase en plan **Spark** (costo cero).

## Empieza aquí
1. **[docs/APP_MAP.md](docs/APP_MAP.md)** — mapa generado del código: módulos, navegación, pantallas,
   ViewModels, intents, casos de uso, repositorios, datos de Firestore, recorridos de prueba y
   "dónde le muevo". Versión máquina: [docs/app-map.json](docs/app-map.json).
2. [docs/PLAN.md](docs/PLAN.md) — fases, huecos (abiertos y cerrados) y versiones.
   Pendientes priorizados (P0–P3): [docs/DEUDA_TECNICA.md](docs/DEUDA_TECNICA.md).
3. [ARCHITECTURE.md](ARCHITECTURE.md) — reglas de capas, MVI y SOLID.
4. [docs/FIREBASE.md](docs/FIREBASE.md) — emuladores, puertos, reglas y build QA.
5. [docs/RELEASE.md](docs/RELEASE.md) — firma, páginas de privacidad/borrado y formulario de Play.

## Reglas del proyecto
- **TDD**: primero el test que falla, luego el código. Ningún caso de uso, reducer, ViewModel o regla de
  Firestore entra sin test.
- **Buscar huecos** antes y después de cada fase; anotarlos en `docs/PLAN.md` (abiertos `[ ]`, cerrados `[x]`).
- **Solo Spark**: nada de Cloud Functions, Storage ni SMS. Pensar cada lectura de Firestore (un documento
  por catálogo, sin `list` abiertos, caché local).
- **Librerías en versión n o n-1** (última estable o la anterior). Tabla en `docs/PLAN.md`.
- `domain` es Kotlin puro; `presentation` no conoce `data`; `app` es el único que conecta todo (Koin).
- Textos en `strings.xml`; el ViewModel guarda tipos de dominio, no textos.

## Comandos
```bash
./gradlew test                          # tests JVM de todos los módulos (+ grafo de Koin)
./gradlew :app:assembleDebug            # debug contra emuladores
./gradlew :app:assembleQa               # igual que release (R8) pero contra emuladores
(cd firebase && npm test)               # reglas de Firestore en el emulador
(cd firebase && npm run emulators)      # Auth 9199 · Firestore 8085 · UI 4005
python3 scripts/app_map.py              # regenerar el mapa (el CI falla si está desactualizado)
scripts/android-tests.sh                # tests instrumentados (SDK real + reglas) en emulator-5570; requiere emuladores
```

## Al cambiar algo
- Nueva pantalla, ruta, ViewModel, caso de uso, módulo o regla → `python3 scripts/app_map.py`.
- Nuevo módulo → usa los plugins `barberia.jvm.library` / `barberia.android.library` / `barberia.android.compose`.
- Nueva dependencia en Koin → `KoinGraphTest` debe seguir en verde.
- Enum usado como argumento de navegación → regla `-keep` en `app/proguard-rules.pro` y probar con `assembleQa`.

## Entorno local (Mac de Carlos)
- Puertos 9099 y 8080 los usa otro proyecto: no cambiar los de `firebase.json`.
- Los emuladores Android `Rinde_*` son de otro proyecto. Para probar esta app usa uno propio
  (p. ej. `Medium_Phone_API_36.0` en el puerto 5570), ligero: `-no-window -cores 2 -memory 2048 -no-boot-anim`.
  No uses `connectedAndroidTest` (instala en todos los dispositivos): usa `scripts/android-tests.sh`.
