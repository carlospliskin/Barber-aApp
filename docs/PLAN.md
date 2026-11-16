# Plan de construcción — Barber-aApp (Kotlin + Firebase Spark)

## Reglas del juego

| Regla | Cómo se cumple |
|---|---|
| **Costo cero** | Solo plan **Spark**: Auth (email), Firestore, FCM, Crashlytics, Analytics, App Check, Remote Config. **Sin** Cloud Functions, Storage ni SMS (son solo Blaze). |
| **Librerías n o n-1** | Cada versión del catálogo es la última estable o la anterior. Se revisa al inicio de cada fase (ver *Versiones*). |
| **TDD** | Rojo → verde → refactor. Primero el test del caso de uso, luego el código mínimo. Ningún caso de uso entra sin test. |
| **Clean + MVI + SOLID** | Ver [ARCHITECTURE.md](../ARCHITECTURE.md). El dominio no sabe que existe Firebase. |

## Estrategia de pruebas (pirámide)

| Nivel | Qué prueba | Herramienta | Dónde corre |
|---|---|---|---|
| 1. Dominio (la mayoría) | Casos de uso y reglas de negocio puras | JUnit4 + coroutines-test, fakes a mano | JVM, sin emulador |
| 2. Reducers MVI | `reduce(state, result)` puro | JUnit4 | JVM |
| 3. ViewModels | Intent → State/Effect con fakes de casos de uso | coroutines-test + Turbine | JVM |
| 4. Repositorios | Mapeo DTO ↔ dominio y errores, con *gateways* falsos | JUnit4 | JVM |
| 5. Reglas de seguridad | Quién puede leer/escribir qué | `@firebase/rules-unit-testing` | Emulador de Firestore |
| 6. Integración Firebase | Repositorio real contra el emulador | Instrumentado (androidTest) | Emulador Android + Firebase |

Los niveles 1–4 corren con `./gradlew test`. El 5 con `npm test` en `firebase/`. Nunca contra el proyecto real: así no se gasta cuota.

## Fases

| Fase | Entrega | Estado |
|---|---|---|
| 0 | Versiones n/n-1, plan, estrategia TDD | ✅ |
| 1 | Endurecer MVI y sesión (huecos 1–10 del diagnóstico) | ✅ |
| 2 | Auth con Firebase: login, registro dueño/cliente, recuperar contraseña, modo local, roles en Firestore | ✅ |
| 3 | Reglas de Firestore + tests en emulador + `firebase.json` | ✅ |
| 4 | Catálogo de servicios y barberos (dueño) | ✅ |
| 5 | Disponibilidad y reserva sin choques (cliente) | ✅ |
| 6 | Agenda del día, completar / cancelar / no-show (barbero y dueño) | ⏭️ siguiente |
| 7 | Clientes, penalizaciones y lealtad | ⏳ |
| 8 | Recordatorios locales, Crashlytics, App Check, versión release | ⏳ |
| 9 | Eliminar cuenta (requisito de Google Play) y publicación | ⏳ |

## Modelo de datos (pensado para gastar poco)

```
users/{uid}                         perfil + staffTenantId + staffRole   (1 lectura al iniciar sesión)
tenants/{ownerUid}                  barbería. El id ES el uid del dueño → las reglas no gastan lecturas extra
tenants/{t}/members/{uid}           barberos activos
tenants/{t}/catalog/main            TODOS los servicios en 1 documento   (1 lectura, se cachea)
tenants/{t}/availability/{barber_yyyyMMdd}  horarios ocupados del día   (1 lectura por día consultado)
tenants/{t}/appointments/{id}       citas (escucha en vivo solo las de hoy, con limit)
tenants/{t}/clients/{uid}           no-shows, penalización, puntos
```

Reservar = **transacción**: crear la cita y marcar el horario en `availability`. Son 2 escrituras y evita dobles reservas sin servidor.

## Huecos de casos de uso detectados

Lista viva: cada hueco se convierte en un test antes de cerrarse.

### Auth y cuenta
- [x] A1. No existe "olvidé mi contraseña".
- [x] A2. No se verifica el email al registrarse. Se envía el correo; todavía no bloquea el acceso (decidir en fase 9).
- [x] A3. Validación de registro: contraseña mínima, teléfono, nombre vacío.
- [x] A4. `switchMode` dependía del backend; con Firebase el modo es una preferencia local y debe sobrevivir al reinicio.
- [x] A5. Al cerrar sesión hay que borrar el modo elegido y los cachés.
- [x] A6. Un dueño que también es cliente de otra barbería: ¿qué modos ve? Todo el personal tiene modo cliente; el dueño también es barbero.
- [ ] A7. Eliminar cuenta y datos (obligatorio en Google Play).
- [x] A8. Sesión sin internet: entrar con la sesión cacheada, no mandar al login.
- [x] A9. Registrar dueño crea `users` + `tenants` juntos (batch). Si falla uno, no puede quedar a medias. Batch + borrar la cuenta si falla el perfil.

### Encontrados al construir (fases 1–3)
- [x] A10. Firebase guarda el email en minúsculas: si el perfil lo guardaba como se escribió, la regla lo rechazaba y el registro fallaba. Se normaliza en el dominio.
- [x] A11. Carrera al registrarse: la sesión se abría antes de escribir el perfil y el dueño aparecía como cliente. El repositorio refresca el perfil al terminar.
- [x] A12. Textos sin color sobre fondo oscuro (faltaba un `Surface` raíz).
- [x] A13. Dos clics en "Enviar" corrían en paralelo: la base MVI ahora procesa intents en cola.
- [ ] A14. Verificación de email: decidir si el dueño debe verificar antes de operar.

### Revisión antes de la fase 4
- [x] H1. Al registrar dueño se navegaba un instante al home de cliente (la cuenta existía antes que el perfil). La sesión queda en `Loading` mientras se registra.
- [x] H2. Sin red tras iniciar sesión, la app se quedaba en el login sin aviso. Nuevo destino `Offline` desde cualquier pantalla.
- [x] H3. En "sin conexión" no había forma de cerrar sesión.
- [x] H4. El modo guardado no era de un usuario: se guarda como `uid:MODO`.
- [x] H5. Las reglas permitían listar todas las barberías (un script podía gastar la cuota). Solo `get`.
- [x] H6. **R8 rompía la navegación en release** (renombraba `AppMode`). Lo encontró el nuevo build `qa`; regla de keep en `app/proguard-rules.pro`.
- [x] H7. `firebase-tools` global estaba en 15.15 (n-21). Se fija 15.36.0 en `firebase/package.json`.

### Reservas
- [x] B1. No permitir reservar en el pasado ni fuera del horario del barbero. → anticipación mínima 30 min.
- [x] B2. El servicio no puede terminar después del cierre. → el servicio cabe completo en un turno (no cruza el descanso).
- [x] B3. Dos clientes reservando el mismo horario a la vez (transacción). → mapa de bloques de 5 min por barbero y día; las reglas prohíben sobrescribir un bloque.
- [ ] B4. Cliente penalizado no puede reservar hasta `penaltyUntil`.
- [x] B5. Límite de citas activas por cliente (evita acaparar horarios). → máximo 2 citas activas.
- [x] B6. Zona horaria: guardar en UTC y mostrar en la de la barbería. → zona horaria de la barbería; `startsAt` en UTC.
- [x] B7. Cancelación: hasta cuántas horas antes y qué pasa con el horario liberado. → hasta 2 h antes; también lo exigen las reglas (el dueño puede siempre).

### Operación
- [ ] C1. Marcar no-show suma al contador y penaliza al llegar al límite.
- [ ] C2. Los puntos de lealtad se dan una sola vez por cita completada (idempotente).
- [x] C3. Borrar un servicio que tiene citas futuras. → nunca se borra, se desactiva (sigue existiendo para citas viejas).
- [x] C4. Desactivar a un barbero con citas futuras. → se desactiva y sale de las reservas.
- [x] C5. Horarios del barbero: turnos que se enciman, días libres. → rangos válidos en pasos de 5 min, sin encimarse, turno partido permitido.

### Encontrados en la fase 4 (catálogo)
- [x] C6. Nombres repetidos sin importar mayúsculas/espacios; tampoco al reactivar uno inactivo.
- [x] C7. Duración de 5 a 480 min en pasos de 5; precio en centavos, de 0 a 100 mil pesos.
- [x] C8. Máximo 50 servicios y 20 barberos (también en las reglas): el documento no crece sin control.
- [x] C9. Dueño editando desde dos teléfonos: transacción + `version` que las reglas obligan a subir de 1 en 1.
- [x] C10. Barbería nueva sin documento: se ve como catálogo vacío y el primer guardado lo crea.
- [x] C11. Quien no es dueño ve el catálogo en solo lectura.
- [x] C12. Precio escrito como texto ("150", "150,5", "$19.99") → centavos sin redondeos.
- [x] C13. El botón flotante se anunciaba solo como "+" en TalkBack.
- [x] C14. `CatalogEditor` recibía una lambda que Koin no puede verificar → interfaz `IdGenerator` (lo detectó `KoinGraphTest`).
- [x] C15. La traducción de errores de Firebase estaba en `auth:data`; se movió a `core:firebase` para no duplicarla.
- [ ] C16. Vincular un barbero del catálogo con una cuenta (para que vea su agenda): se necesita un código de invitación. Fase 6.
- [ ] C17. Editar el catálogo sin red falla con aviso (las transacciones necesitan servidor). Aceptado para el lanzamiento.

### Revisión de la fase 5 (antes de las pantallas y en la prueba de punta a punta)
- [x] R1. Una cita podía declarar bloques que no eran de su hora y **bloquear el día entero** del barbero → las reglas exigen que los bloques correspondan a inicio y duración (máx. 8 h).
- [ ] R2. El precio lo escribe el cliente (podría poner $0). Se valida entero ≥ 0; el cobro es en el local. Revisar si se cobra en la app.
- [x] R3. `startsAt` inventado para cancelar tarde → debe caer en la fecha y hora de la cita (±14 h por zona).
- [x] R4. "Mis citas" leía hasta 50 sin orden; las nuevas podían no aparecer → solo de hoy en adelante, ordenadas (índice compuesto).
- [ ] R5. El dueño podría leer todo el historial de citas → la agenda de la fase 6 debe filtrar por día.
- [x] R6. No había forma de leer nombre y zona horaria de la barbería.
- [x] R7. El dueño no podía crear su código ni guardarlo en su barbería; las reglas validan que el código sea suyo.
- [x] R8. La barbería del cliente se guarda en el teléfono (0 costo); al reinstalar se vuelve a poner el código.
- [x] B15. Si otro tomó la hora al confirmar, se avisa y se recargan los horarios.
- [x] B16. Cambiar servicio, barbero o día limpia la hora elegida.
- [x] U1. "1 días a la semana" → plurales.
- [x] U2. Se podían elegir días en que el barbero no trabaja.
- [x] U3. **Otro usuario del mismo teléfono heredaba la barbería guardada** (lo encontró la prueba de punta a punta).
- [x] U4. "Mis citas" mostraba (y cobraba lecturas de) las canceladas.
- [x] U5. Chips sin seleccionar se veían como deshabilitados → `BarberChip` en el design system.

### Encontrados en la fase 5 (reservas)
- [x] B0. Sin poder listar barberías (H5), el cliente no tenía cómo encontrar la suya → `shareCodes/{CODE}` de 6 caracteres sin letras confusas.
- [x] B8. No reservar más de 30 días adelante.
- [x] B9. Servicio o barbero desactivado entre abrir la pantalla y confirmar: se valida contra el catálogo del momento.
- [x] B10. La regla de cancelación necesitaba la hora como instante → `startsAt` (UTC) en la cita.
- [x] B11. Firebase envolvía el "horario tomado" lanzado en la transacción y se veía como error desconocido.
- [x] B12. `GetAvailableSlots` devolvía lista vacía ante un error de red ("no hay horarios") → ahora devuelve el error.
- [ ] B4. Cliente penalizado no puede reservar → con la fase 7 (penalizaciones).
- [x] B13. Pantallas de reserva (código de barbería, servicio, barbero, día, hora, confirmar, mis citas) + código del dueño.
- [x] B14. Prueba de punta a punta en emulador con el build QA: dueño arma catálogo y obtiene código; cliente se une, reserva y cancela; un segundo cliente ve el horario ocupado; el día queda liberado al cancelar.

### Revisión antes de la fase 5
- [x] G1. **Pérdida de datos**: el formulario de horario solo editaba el primer turno y al guardar borraba el turno partido.
- [x] G2. Si fallaba la escucha del catálogo se mostraba "sin conexión" en vez del motivo real (p. ej. sin permiso).
- [x] G3. No existía una vista general de la app: `scripts/app_map.py` genera `docs/APP_MAP.md` + JSON y el CI falla si se desactualiza.

### Limitaciones por usar solo Spark (decisiones conscientes)
- Sin Cloud Functions no hay **push del servidor** → recordatorios con notificaciones **locales** (WorkManager) y avisos en vivo con listeners mientras la app está abierta.
- Sin Storage no hay **fotos subidas** → logos e íconos van en la app.
- Sin servidor no hay **pagos con MercadoPago** → pago en el local. Se retoma si se pasa a Blaze.
- Los roles van en `users/{uid}`, no en custom claims (esos necesitan Admin SDK).

## Deuda técnica pendiente del diagnóstico
- [x] Convention plugins en `build-logic/` (`barberia.android.library`, `.android.compose`, `.jvm.library`).
- [x] CI (GitHub Actions): tests, build debug + QA y reglas en emulador.
- [x] Verificación del grafo de Koin (`KoinGraphTest`), comprobada quitando un registro a propósito.
- [x] Build release con R8 probado en ejecución mediante el build type `qa`.
- [ ] detekt/ktlint en CI.
- [ ] Tests instrumentados de los gateways reales contra el emulador.

## Versiones (revisión 2026-11-15)

| Librería | Usada | Última | ¿n/n-1? |
|---|---|---|---|
| Gradle | 9.9.1 | 9.9.1 | n |
| AGP | 9.5.1 | 9.5.1 | n |
| Kotlin | 2.4.21 | 2.4.21 | n |
| Compose BOM | 2026.09.00 | 2026.09.00 | n |
| Firebase BOM | 35.2.0 | 35.2.0 | n |
| firebase-tools (CLI) | 15.36.0 | 15.36.0 | n |
| actions/checkout · setup-java · setup-node · setup-gradle · upload-artifact | v7 · v6 · v7 · v6 · v7 | iguales | n |
| Coroutines | 1.11.0 | 1.11.0 | n |
| Serialization | 1.13.0 | 1.13.0 | n |
| Koin | 4.2.2 | 4.2.2 | n |
| Lifecycle | 2.11.0 | 2.11.0 | n |
| Navigation | 2.10.2 | 2.10.2 | n |
| DataStore | 1.3.0 | 1.3.0 | n |
| Activity Compose | 1.13.0 | 1.13.0 | n |
| Core KTX | 1.19.1 | 1.19.1 | n |
| OkHttp | 5.5.1 | 5.5.1 | n |
| Turbine | 1.2.1 | 1.2.1 | n |
| JUnit | 4.13.2 | 4.13.2 | n |

Cómo revisarlas: `maven-metadata.xml` de Google Maven y Maven Central, ignorando alpha/beta/rc.
