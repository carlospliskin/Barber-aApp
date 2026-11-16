# Firebase — cómo se usa en este proyecto

## Plan Spark (costo cero)
Solo usamos productos que el plan gratuito permite. Ver la tabla y las limitaciones en [PLAN.md](PLAN.md).

## Desarrollo local: siempre con emuladores
El build **debug** usa el proyecto `demo-barberia`, que no existe en la nube: todo va al Emulator Suite y no gasta cuota.

```bash
cd firebase && npm install && npm run emulators
```
(usa `firebase-tools` 15.36.0 del proyecto, no el global)

| Servicio | Puerto | Nota |
|---|---|---|
| Auth | 9199 | 9099 lo usa otro proyecto en esta Mac |
| Firestore | 8085 | 8080 está ocupado |
| UI del emulador | 4005 | http://127.0.0.1:4005 |

La app en el emulador de Android llega a la Mac por `10.0.2.2` (ver `FIREBASE_EMULATOR_HOST` en `app/build.gradle.kts`).

## Probar las reglas de seguridad
```bash
cd firebase && npm install && npm test
```
Levanta el emulador de Firestore, corre `firebase/test/*.test.js` y lo apaga.
Si ya tienes los emuladores corriendo, usa `node --test --test-concurrency=1 firebase/test/*.test.js` desde la raíz.
Los tests usan el proyecto `demo-rules`: no borran los datos de la app (`demo-barberia`).

## Build QA
`./gradlew :app:assembleQa` genera el mismo APK que release (con R8) pero apuntando al emulador.
Así se prueban los problemas de R8 antes de publicar.

## Producción (cuando exista el proyecto real)
1. Crear el proyecto en la consola de Firebase (plan Spark) y una app Android `com.barberia.app`.
2. Crear `firebase.properties` en la raíz (está en `.gitignore`):
   ```properties
   projectId=tu-proyecto
   appId=1:1234567890:android:abcdef
   apiKey=AIza...
   ```
3. Publicar reglas e índices: `firebase deploy --only firestore --project tu-proyecto`.
4. **App Check (V3):** en la consola → App Check → registrar la app Android con Play Integrity (huella SHA-256 de la firma de release).
   Dejarlo unos días en **modo monitor** para ver que las peticiones legítimas llegan con token; luego **Enforce** para Firestore.
   El código ya instala Play Integrity en release (`app/src/release/.../AppCheckSetup.kt`); debug y QA no, porque usan el emulador.
5. **Crashlytics:** activar `mappingFileUploadEnabled = true` en `app/build.gradle.kts` para ver los fallos sin ofuscar.

## Reglas para no gastar cuota
- Leer catálogos y configuración como **un documento** por barbería, no una colección.
- Escuchar en vivo solo lo de hoy y con `limit`.
- Evitar `get()`/`exists()` en reglas: cada uno cuenta como lectura. Por eso `tenants/{id}` usa el uid del dueño.
- Lo que la app lee de la caché local no se cobra (caché persistente de 50 MB configurada en `core/firebase`).
