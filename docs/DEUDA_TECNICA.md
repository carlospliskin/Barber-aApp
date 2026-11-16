# Deuda técnica y pendientes

Backlog único de todo lo que falta, ordenado por prioridad. Cada punto se resuelve con TDD:
primero el test que demuestra el hueco, luego el arreglo. Al cerrarlo, se marca aquí y en [PLAN.md](PLAN.md).

**Prioridad**
- **P0** bloquea el lanzamiento en Google Play.
- **P1** antes del lanzamiento: riesgo real para el negocio o los datos.
- **P2** primeras semanas después del lanzamiento.
- **P3** cuando haya tiempo.

**Esfuerzo:** S (≤ 2 h) · M (medio día a 1 día) · L (varios días).
**Decisión:** el punto necesita que el dueño del producto elija una opción antes de programarlo.

Estado a la fecha: 8 de 10 fases terminadas · 205 tests Kotlin · 93 pruebas de reglas · CI en verde.

---

## 1. Huecos de la fase 7 (clientes, lealtad y penalizaciones)

Encontrados revisando el código ya implementado.

| ID | Prioridad | Esfuerzo | Hueco | Cómo resolverlo | Cómo probarlo |
|---|---|---|---|---|---|
| F7-1 | P1 | S | **Re-penalización inmediata.** Al terminar la penalización, `noShows` sigue en 3; el 4.º no-show vuelve a penalizar 14 días (`total >= 3` en `Loyalty.kt`). **Decisión:** ¿se cuenta desde cero después de cada penalización? | Opción A: penalizar cuando `noShows % 3 == 0`. Opción B: contar solo no-shows de los últimos 90 días. | Test de dominio: 3 no-shows → penaliza; 4.º → no penaliza (opción A). |
| F7-2 | P1 | M | **No hay "deshacer".** Si el personal marca atendida o no-show por error, no se puede corregir (A6). Contadores y penalización quedan mal; perdonar solo borra no-shows. | "Deshacer" solo del dueño, dentro de 15 min: estado → BOOKED y contador −1, en el mismo batch. Regla nueva para esa transición. | Reglas: deshacer en ≤ 15 min pasa; después, o hecho por un barbero, falla. Dominio: contador −1. |
| F7-3 | P2 | S | **Penalizado con citas futuras.** La penalización no toca las citas ya reservadas. **Decisión:** ¿se cancelan o se respetan? | Si se cancelan: en el mismo flujo del 3.er no-show, cancelar sus citas BOOKED futuras y liberar bloques. | Dominio + reglas del batch. |
| F7-4 | P2 | S | **Pantallas desactualizadas.** "Penalizado hasta", y los botones atendida, no-show y cancelar, se calculan al llegar datos. Con la pantalla abierta no se actualizan al pasar la hora. | Un "reloj" en el ViewModel que actualice `now` cada minuto. | ViewModel con reloj de prueba: avanzar el tiempo cambia `canComplete` y `penalizedUntil`. |
| F7-5 | P2 | M | **Lista de clientes limitada.** Solo los 50 actualizados más recientemente, sin búsqueda ni paginación. Un penalizado antiguo puede no aparecer. | Paginación (`startAfter`) y búsqueda por nombre (prefijo en un campo `nameLower`). | Gateway contra emulador; ViewModel con fake. |
| F7-6 | P2 | S | **Clientes sin ficha.** Quien solo ha reservado (sin visita marcada) no aparece en la lista. **Decisión:** ¿deben aparecer? | Crear la ficha al reservar: 1 escritura más por reserva y su regla. | Reglas + dominio. |
| F7-7 | P3 | S | El nombre de la ficha se copia una vez; si el cliente lo cambia, no se actualiza. | Actualizar `name` en cada atendida o no-show (la regla ya permite el campo). | Dominio. |
| F7-8 | P2 | S | **Barberos canjean premios.** La regla usa `isTenantStaff()`. **Decisión:** ¿solo el dueño? | Cambiar a `isSelf(tenantId)` si se decide. | Reglas. |
| F7-9 | P2 | M | **Marcar estado requiere internet.** Es una transacción, y sin red falla. Hay barberías con conexión mala. | Mostrar claramente "sin conexión, reintenta" con un botón de reintentar. Cola offline más adelante. | ViewModel con repo que falla por red. |
| F7-10 | P2 | S | **Canje de premio sin probar en dispositivo.** Necesita 10 visitas. | Sembrar 10 visitas en el emulador y canjear desde la pantalla de clientes. | Prueba de punta a punta (ver sección 4). |
| F7-11 | P3 | S | **Costo de marcar estado sin medir.** Unas 4 lecturas de reglas + 1 de la transacción por cita. | Documentarlo en FIREBASE.md y vigilar el uso en la consola. | — |

## 2. Seguridad y abuso

| ID | Prioridad | Esfuerzo | Hueco | Cómo resolverlo |
|---|---|---|---|---|
| V3 | **P0** | M | **Abusos saltándose la app.** Las reglas no pueden recorrer listas: alguien con un script puede reservar fuera del horario del barbero, saltarse el límite de 2 citas (B5) o crear muchas citas. | **App Check con Play Integrity** (gratis en Spark) y exigirlo en Firestore. Probar primero en modo "monitor". |
| A14 | P1 | S | **Verificación de email.** Se envía el correo pero no se exige. **Decisión:** ¿el dueño debe verificar antes de operar? | Si sí: bloquear catálogo y código hasta `emailVerified`, con botón "ya verifiqué" que recargue el usuario. |
| R2 | P2 | S | **Precio escrito por el cliente.** La regla solo pide entero ≥ 0; podría guardar $0. Hoy se cobra en el local con el precio real. | Si algún día se cobra en la app: guardar los servicios como mapa por id en el catálogo para que la regla compare el precio. |
| W3 | P3 | S | **Dos invitaciones para el mismo barbero** ligan dos cuentas a él. | Al crear una invitación, avisar si ya hay una cuenta ligada a ese barbero. |

## 3. Funcionalidad que falta (negocio)

| ID | Prioridad | Esfuerzo | Falta | Notas |
|---|---|---|---|---|
| A7 | **P0** | M | **Eliminar cuenta y datos** (obligatorio en Google Play). | Fase 9. Sin Cloud Functions: borrar desde la app el perfil y las fichas; las citas pasadas se anonimizan. Regla nueva de `delete`. |
| PLAY-1 | **P0** | S | **Política de privacidad y formulario de "Seguridad de los datos"** en Play Console. | Datos que se guardan: email, nombre, teléfono, citas. |
| N1 | P1 | L | **El personal no puede reservar por un cliente** (por teléfono o de mostrador). Hoy la regla exige `clientUid == auth.uid`. | Cita "a nombre de" un cliente sin cuenta (`clientUid = null`, nombre y teléfono). Nueva regla para el personal. Es la falta de negocio más grande. |
| N2 | P1 | M | **Sin excepciones de horario.** No hay vacaciones, días festivos ni "hoy salgo temprano"; si el barbero falta, los clientes reservan igual. | `blockedRanges` por fecha en el catálogo, y que el calculador de disponibilidad los respete. |
| N3 | P1 | M | **Recordatorios de cita** (fase 8). | Notificaciones locales con WorkManager. Push del servidor requiere Blaze (no). |
| N4 | P2 | M | **Reagendar.** Hoy es cancelar y volver a reservar, y pierde la regla de 2 h. | Caso de uso que haga las dos cosas en una transacción. |
| N5 | P2 | S | **Zona horaria fija** (America/Mexico_City); no hay pantalla para cambiarla. | Selector en la configuración del dueño; la regla ya permite `timezone`. |
| N6 | P3 | M | Iniciar sesión con Google. | Credential Manager + Firebase Auth (gratis). |
| — | Fuera | — | Pagos en la app (MercadoPago) y push del servidor. | Requieren Cloud Functions → plan Blaze. Se retoman si se cambia de plan. |

## 4. Calidad y pruebas

| ID | Prioridad | Esfuerzo | Deuda | Cómo resolverla |
|---|---|---|---|---|
| Q1 | P1 | M | **0 tests instrumentados.** El código real de Firebase (transacciones, conversión de fechas, consultas) solo se probó a mano en el emulador. | Tests `androidTest` de cada gateway contra Firebase Emulator Suite. |
| Q2 | P1 | M | **Las pruebas de punta a punta no están en el repo.** Se hicieron con un script local de adb. | Versionarlas con Maestro (YAML legible) o Compose UI Test; correrlas en CI con un emulador. |
| Q3 | P2 | S | **detekt / ktlint** no corren en CI. | Agregar el plugin al `build-logic` y el paso al CI. |
| Q4 | P2 | M | Sin tests de UI (Compose) ni capturas de referencia. | Compose UI Test para formularios críticos (login, reserva). |
| Q5 | P2 | M | Accesibilidad revisada solo en puntos sueltos (botón flotante, etiquetas de estado). | Revisión completa: contraste, tamaños táctiles, TalkBack en cada pantalla. |
| Q6 | P3 | S | La revisión de versiones n/n-1 es manual. | Script que compare el catálogo de versiones con Maven, o Renovate con política "n-1". |

## 5. Arquitectura

| ID | Prioridad | Esfuerzo | Deuda | Cómo resolverla |
|---|---|---|---|---|
| AR1 | P2 | S | **Textos fijos en `HomeScreen`** ("Agenda", "Catálogo", "Clientes", "Cerrar sesión", "Revisa tu correo…"), fuera de `strings.xml`. | Moverlos a recursos. |
| AR2 | P2 | M | **El home vive en `:app`** (`HomeViewModel`, `HomeScreen`): lógica de pantalla en el composition root. | Crear `feature:home:presentation`. |
| AR3 | P2 | M | **`feature:booking` creció demasiado:** reservas + agenda + clientes + lealtad (27 archivos). | Dividir en `feature:agenda` y `feature:clients`; el mapa mostrará el antes y el después. |
| AR4 | P3 | M | **Acoplamiento entre features.** Tres módulos de presentación dependen de `auth:domain` solo para saber quién es el usuario. | `core:session` con una interfaz mínima (`CurrentUser`); auth la implementa. |
| C17 | P3 | — | Editar el catálogo sin red falla (las transacciones necesitan servidor). | Aceptado para el lanzamiento. |

## 6. Lanzamiento

| ID | Prioridad | Esfuerzo | Falta | Notas |
|---|---|---|---|---|
| L1 | **P0** | S | **Proyecto real de Firebase** (Spark), `firebase.properties` y publicar reglas e índices. | Pasos en [FIREBASE.md](FIREBASE.md). Lo hace el dueño de la cuenta de Google. |
| L2 | **P0** | S | **Firma de release:** `release` no tiene `signingConfig` y no se puede publicar. | Keystore fuera de git (`keystore.properties`), como en pos-farmacia. |
| L3 | P1 | S | **Ícono de la app y fuentes:** no hay `mipmap`, se ve el ícono por defecto. Faltan Playfair Display y DM Sans. | Ícono adaptativo y fuentes en `core:designsystem`. |
| L4 | P1 | S | **Crashlytics y Analytics** (fase 8). | Gratis en Spark. Sin datos personales en los eventos. |

---

## Orden sugerido

| Bloque | Qué incluye | Por qué |
|---|---|---|
| 1. Decisiones (sin código) | F7-1, F7-3, F7-6, F7-8, A14 | Hay que elegir política antes de programar. |
| 2. Fase 8 | V3 (App Check), N3 (recordatorios), L4 (Crashlytics), L3 (ícono y fuentes) | Ya era la siguiente fase y cierra el riesgo de abuso. |
| 3. Huecos de negocio | F7-1, F7-2, N1, N2 | Lo que más va a notar una barbería real en su primera semana. |
| 4. Red de seguridad | Q1, Q2, Q3 | Antes de que el código crezca más. |
| 5. Fase 9 | A7, PLAY-1, L1, L2 | Publicación. |
| 6. Después del lanzamiento | El resto de P2 y P3 (AR1-AR4, F7-4, F7-5, N4-N6, Q4-Q6) | Mejoras sin bloqueo. |
