# Publicar en Google Play

Lo que ya está listo en el repo y lo que te toca hacer a ti (cuenta de Google, contraseñas, Play Console).

## L1 · Proyecto real de Firebase (tú)
Pasos en [FIREBASE.md](FIREBASE.md): crear el proyecto en Spark, registrar la app `com.barberia.app`,
llenar `firebase.properties` y publicar reglas e índices.

## L2 · Firma de release
El build ya lee `keystore.properties` (fuera de git). Sin ese archivo, `assembleRelease` sale **sin firmar**.

1. Crea la llave de subida **fuera del repo** (te pedirá las contraseñas; guárdalas en tu gestor de contraseñas):
   ```bash
   keytool -genkeypair -v -keystore ~/keys/barberia-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Copia `keystore.properties.example` como `keystore.properties` y llena los cuatro valores.
3. Genera el bundle para Play:
   ```bash
   ./gradlew :app:bundleRelease
   ```
4. En Play Console activa **Play App Signing**: Google guarda la llave final y la tuya es solo de subida
   (si la pierdes, se puede cambiar).
5. Respalda el `.jks` y las contraseñas en dos lugares. No los subas a git ni a Drive sin cifrar.

`qa` sigue firmándose con la llave de debug: no necesita el archivo.

## PLAY-1 · Privacidad y borrado de cuenta
Play pide dos enlaces públicos: el aviso de privacidad y una página para pedir el borrado **sin instalar la app**.

- Páginas: [web/privacidad.html](../web/privacidad.html) y [web/borrar-cuenta.html](../web/borrar-cuenta.html).
- Antes de publicarlas reemplaza `{{RESPONSABLE}}`, `{{CORREO}}` y `{{FECHA}}` (el workflow se niega si quedan).
- Publicarlas gratis con GitHub Pages: Settings → Pages → Source: **GitHub Actions**, y luego Actions →
  «Páginas web» → Run workflow. Quedan en `https://<usuario>.github.io/Barber-aApp/privacidad.html`.
- Si cambia lo que guarda la app (nuevo dato, Analytics, otro proveedor), actualiza estas páginas y el formulario.

### Formulario «Seguridad de los datos» (respuestas sugeridas)
| Pregunta | Respuesta |
|---|---|
| ¿Recopila o comparte datos? | Recopila; **no comparte** con terceros (Firebase es proveedor de servicio). |
| ¿Cifrados en tránsito? | Sí (HTTPS). |
| ¿El usuario puede pedir el borrado? | Sí: en la app y en `borrar-cuenta.html`. |
| Información personal | Nombre, correo, teléfono → Funcionalidad de la app, gestión de la cuenta. Obligatorios. |
| Actividad en la app | Citas e historial (visitas, faltas) → Funcionalidad de la app. |
| Información y rendimiento de la app | Registros de fallos y diagnóstico (Crashlytics) → Análisis/diagnóstico. No identifican. |
| Ubicación, fotos, contactos, pagos, mensajes | No. |
| Publicidad | No. |

## Lista final antes de subir
- [ ] L1 hecho y `./gradlew :app:assembleRelease` apunta al proyecto real.
- [ ] Reglas e índices publicados (`firebase deploy --only firestore`).
- [ ] App Check (V3) en modo monitor una semana y luego exigido.
- [ ] Páginas publicadas y enlaces en Play Console.
- [ ] `versionCode` subido en `app/build.gradle.kts` en cada envío.
