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

Estado a la fecha: 8 de 10 fases terminadas · CI en verde. ✅ = resuelto (ver «Resueltos» al final).

---

## 1. Huecos de la fase 7 (clientes, lealtad y penalizaciones)

Encontrados revisando el código ya implementado.

| ID | Prioridad | Esfuerzo | Hueco | Cómo resolverlo | Cómo probarlo |
|---|---|---|---|---|---|
| F7-1 | ✅ | S | **Re-penalización inmediata.** Al terminar la penalización, `noShows` sigue en 3; el 4.º no-show vuelve a penalizar 14 días (`total >= 3` en `Loyalty.kt`). **Decisión:** ¿se cuenta desde cero después de cada penalización? | Opción A: penalizar cuando `noShows % 3 == 0`. Opción B: contar solo no-shows de los últimos 90 días. | Test de dominio: 3 no-shows → penaliza; 4.º → no penaliza (opción A). |
| F7-2 | P1 | M | **No hay "deshacer".** Si el personal marca atendida o no-show por error, no se puede corregir (A6). Contadores y penalización quedan mal; perdonar solo borra no-shows. | "Deshacer" solo del dueño, dentro de 15 min: estado → BOOKED y contador −1, en el mismo batch. Regla nueva para esa transición. | Reglas: deshacer en ≤ 15 min pasa; después, o hecho por un barbero, falla. Dominio: contador −1. |
| F7-3 | ✅ | S | **Penalizado con citas futuras.** La penalización no toca las citas ya reservadas. **Decisión:** ¿se cancelan o se respetan? | Si se cancelan: en el mismo flujo del 3.er no-show, cancelar sus citas BOOKED futuras y liberar bloques. | Dominio + reglas del batch. |
| F7-4 | P2 | S | **Pantallas desactualizadas.** "Penalizado hasta", y los botones atendida, no-show y cancelar, se calculan al llegar datos. Con la pantalla abierta no se actualizan al pasar la hora. | Un "reloj" en el ViewModel que actualice `now` cada minuto. | ViewModel con reloj de prueba: avanzar el tiempo cambia `canComplete` y `penalizedUntil`. |
| F7-5 | P2 | M | **Lista de clientes limitada.** Solo los 50 actualizados más recientemente, sin búsqueda ni paginación. Un penalizado antiguo puede no aparecer. | Paginación (`startAfter`) y búsqueda por nombre (prefijo en un campo `nameLower`). | Gateway contra emulador; ViewModel con fake. |
| F7-6 | ✅ | S | **Clientes sin ficha.** Quien solo ha reservado (sin visita marcada) no aparece en la lista. **Decisión:** ¿deben aparecer? | Crear la ficha al reservar: 1 escritura más por reserva y su regla. | Reglas + dominio. |
| F7-7 | P3 | S | El nombre de la ficha se copia una vez; si el cliente lo cambia, no se actualiza. | Actualizar `name` en cada atendida o no-show (la regla ya permite el campo). | Dominio. |
| F7-8 | ✅ | S | **Barberos canjean premios.** La regla usa `isTenantStaff()`. **Decisión:** ¿solo el dueño? | Cambiar a `isSelf(tenantId)` si se decide. | Reglas. |
| F7-9 | P2 | M | **Marcar estado requiere internet.** Es una transacción, y sin red falla. Hay barberías con conexión mala. | Mostrar claramente "sin conexión, reintenta" con un botón de reintentar. Cola offline más adelante. | ViewModel con repo que falla por red. |
| F7-10 | P2 | S | **Canje de premio sin probar en dispositivo.** Necesita 10 visitas. | Sembrar 10 visitas en el emulador y canjear desde la pantalla de clientes. | Prueba de punta a punta (ver sección 4). |
| F7-11 | P3 | S | **Costo de marcar estado sin medir.** Unas 4 lecturas de reglas + 1 de la transacción por cita. | Documentarlo en FIREBASE.md y vigilar el uso en la consola. | — |

## 2. Seguridad y abuso

| ID | Prioridad | Esfuerzo | Hueco | Cómo resolverlo |
|---|---|---|---|---|
| V3 | **P0 · parcial** | M | **Abusos saltándose la app.** Las reglas no pueden recorrer listas: alguien con un script puede reservar fuera del horario del barbero o en un día libre (N2), saltarse el límite de 2 citas (B5) o crear muchas citas. | **App Check con Play Integrity** (gratis en Spark) y exigirlo en Firestore. Probar primero en modo "monitor". |
| A14 | ✅ | S | **Verificación de email.** Se envía el correo pero no se exige. **Decisión:** ¿el dueño debe verificar antes de operar? | Si sí: bloquear catálogo y código hasta `emailVerified`, con botón "ya verifiqué" que recargue el usuario. |
| R2 | P2 | S | **Precio escrito por el cliente.** La regla solo pide entero ≥ 0; podría guardar $0. Hoy se cobra en el local con el precio real. | Si algún día se cobra en la app: guardar los servicios como mapa por id en el catálogo para que la regla compare el precio. |
| W3 | P3 | S | **Dos invitaciones para el mismo barbero** ligan dos cuentas a él. | Al crear una invitación, avisar si ya hay una cuenta ligada a ese barbero. |

## 3. Funcionalidad que falta (negocio)

| ID | Prioridad | Esfuerzo | Falta | Notas |
|---|---|---|---|---|
| A7 | **P0** | M | **Eliminar cuenta y datos** (obligatorio en Google Play). | Fase 9. Sin Cloud Functions: borrar desde la app el perfil y las fichas; las citas pasadas se anonimizan. Regla nueva de `delete`. |
| PLAY-1 | **P0** | S | **Política de privacidad y formulario de "Seguridad de los datos"** en Play Console. | Datos que se guardan: email, nombre, teléfono, citas. |
| X3 | P2 | S | **El ícono es provisional** (tijeras vectoriales). | Reemplazar por el ícono de marca cuando exista. |
| N1 | ✅ | L | **El personal no puede reservar por un cliente** (por teléfono o de mostrador). | Ver «Resueltos». |
| N2 | ✅ | M | **Sin excepciones de horario.** | Ver «Resueltos». |
| N1-b | P2 | S | **El cliente sin app no recibe recordatorio.** Solo queda su teléfono en la cita. | Botón "Avisar por WhatsApp" en la tarjeta de la agenda (enlace `wa.me`, gratis, lo manda el personal). |
| N1-c | P3 | M | **Un cliente sin app que después se registra** no hereda sus visitas. | Unir por teléfono cuando el dueño lo decida (pantalla de clientes). |
| N2-b | P2 | S | **Bloquear un día con citas ya reservadas** no las cancela ni avisa cuántas hay. Hoy el formulario solo lo recuerda con un texto. | Al guardar, consultar las citas de ese día (1 lectura por cita) y ofrecer cancelarlas. |
| N2-c | P3 | S | **"Hoy" del formulario de día libre** sale de la zona del teléfono, no de la barbería (N5). | Pasar la zona de la barbería al caso de uso cuando exista N5. |
| N3 | ✅ | M | **Recordatorios de cita** (fase 8). | Notificaciones locales con WorkManager. Push del servidor requiere Blaze (no). |
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
| AR1 | ✅ | S | **Textos fijos en `HomeScreen`** ("Agenda", "Catálogo", "Clientes", "Cerrar sesión", "Revisa tu correo…"), fuera de `strings.xml`. | Moverlos a recursos. |
| AR2 | P2 | M | **El home vive en `:app`** (`HomeViewModel`, `HomeScreen`): lógica de pantalla en el composition root. | Crear `feature:home:presentation`. |
| AR3 | P2 | M | **`feature:booking` creció demasiado:** reservas + agenda + clientes + lealtad (27 archivos). | Dividir en `feature:agenda` y `feature:clients`; el mapa mostrará el antes y el después. |
| AR4 | P3 | M | **Acoplamiento entre features.** Tres módulos de presentación dependen de `auth:domain` solo para saber quién es el usuario. | `core:session` con una interfaz mínima (`CurrentUser`); auth la implementa. |
| C17 | P3 | — | Editar el catálogo sin red falla (las transacciones necesitan servidor). | Aceptado para el lanzamiento. |

## 6. Lanzamiento

| ID | Prioridad | Esfuerzo | Falta | Notas |
|---|---|---|---|---|
| L1 | **P0** | S | **Proyecto real de Firebase** (Spark), `firebase.properties` y publicar reglas e índices. | Pasos en [FIREBASE.md](FIREBASE.md). Lo hace el dueño de la cuenta de Google. |
| L2 | **P0** | S | **Firma de release:** `release` no tiene `signingConfig` y no se puede publicar. | Keystore fuera de git (`keystore.properties`), como en pos-farmacia. |
| L3 | P1 · parcial | S | **Ícono de la app ✅ y fuentes:** no hay `mipmap`, se ve el ícono por defecto. Faltan Playfair Display y DM Sans. | Ícono adaptativo y fuentes en `core:designsystem`. |
| L4 | ✅ | S | **Crashlytics y Analytics** (fase 8). | Gratis en Spark. Sin datos personales en los eventos. |

---

## Orden sugerido

| Bloque | Qué incluye | Por qué |
|---|---|---|
| 1. Decisiones (sin código) | F7-1, F7-3, F7-6, F7-8, A14 | Hay que elegir política antes de programar. |
| 2. Fase 8 | V3 (App Check), N3 (recordatorios), L4 (Crashlytics), L3 (ícono y fuentes) | Ya era la siguiente fase y cierra el riesgo de abuso. |
| 3. Huecos de negocio | F7-1 ✅, F7-2, N1 ✅, N2 ✅ | Lo que más va a notar una barbería real en su primera semana. |
| 4. Red de seguridad | Q1, Q2, Q3 | Antes de que el código crezca más. |
| 5. Fase 9 | A7, PLAY-1, L1, L2 | Publicación. |
| 6. Después del lanzamiento | El resto de P2 y P3 (AR1-AR4, F7-4, F7-5, N4-N6, Q4-Q6) | Mejoras sin bloqueo. |

---

## Resueltos

| ID | Decisión | Cómo quedó |
|---|---|---|
| F7-1 | Contar desde cero | Penalización cada 3 faltas (3, 6, 9…); el total de faltas se conserva como historial. Dominio y reglas (`noShows % 3 == 0`). |
| F7-3 | Sí, cancelar | Al penalizar se cancelan sus citas futuras en esa barbería, con cualquier barbero, y se liberan los horarios. Cualquier personal puede ver y cancelar las citas de un cliente **solo si está penalizado**. La agenda avisa cuántas se cancelaron. |
| F7-6 | Sí: quienes reservaron | La ficha se crea en la primera reserva, en ceros y en la misma transacción. El cliente no puede regalarse visitas (reglas). Quien solo se unió con el código y no reservó no aparece. |
| F7-8 | Solo el dueño | Canjear premios: regla `isSelf(tenantId)`. |
| A14 | Sí (gratis en Spark: 1,000 correos de verificación al día) | El dueño ve "Verifica tu correo" con "Ya lo verifiqué" y "Reenviar" (máx. 1 cada 60 s). Catálogo, agenda, clientes y código quedan bloqueados hasta verificar; las reglas exigen `email_verified` para catálogo, códigos e invitaciones. |
| A15 | (nuevo) | Tras verificar se fuerza un token nuevo (`getIdToken(true)`); si no, las reglas veían "no verificado" hasta 1 hora. |
| AR1 | — | Los textos del home pasaron a `strings.xml`. |
| X1 | (nuevo) | El home del dueño arrancaba suponiendo "verificado": mostraba un instante el código e intentaba crearlo (rechazado) y podía no reintentar al verificar. Ahora espera a cargar el usuario y el código se crea solo con el correo verificado. |
| X2 | (nuevo) | Si fallaba cancelar alguna cita futura del penalizado, el aviso lo callaba. Ahora dice cuántas faltan. |
| N3 | Fase 8 | Recordatorio local 3 h antes (todavía se puede cancelar). Revisa que la cita siga vigente y no haya empezado (R2, R6); se sincroniza al abrir el home (R3, otro teléfono o reinstalación); se pide permiso en Android 13+ (R4); se borra al cancelar (R5). |
| L4 | Fase 8 | Crashlytics: solo envía en release (debug/QA apagado). Subir el mapa de R8 queda para cuando exista el proyecto real (L1). Declarar en Play (PLAY-1). |
| V3 (parcial) | Fase 8 | App Check con Play Integrity instalado en release. **Falta:** registrar la app en App Check y activar "Enforce" en la consola (primero modo monitor), cuando exista el proyecto real. |
| N1 | Huecos de negocio | **"+ Agendar cliente"** en la agenda: nombre, teléfono opcional (solo dígitos), servicio, barbero (el barbero solo en su agenda), día y hora. Se acepta el turno que empezó hace hasta 14 min (cliente que ya está en el local). La cita queda con `clientUid = null`: sin ficha, sin visitas, sin penalización ni límite de citas. Reglas: solo el personal la crea (un cliente no puede esquivar su penalización así), teléfono ≤ 20, inicio hasta 20 min atrás; atender o no-show sin ficha. En la agenda se ve "Sin app · teléfono". |
| N2 | Huecos de negocio | **Días libres** por barbero (todo el día o un rango: "hoy salgo temprano") y **"Cerrar la barbería un día"** (todos los barberos). Viven en el documento del catálogo: 0 lecturas extra. Los pasados se borran al guardar y hay máx. 30 por barbero. La disponibilidad los respeta y los días libres completos salen deshabilitados al reservar. |
| N1-a | (nuevo) | Las reglas no validaban el nombre del cliente en las reservas; ahora `clientName` es texto de 1 a 80. Una cita de cliente no puede llevar `clientPhone`. |
| L3 (parcial) | Fase 8 | Ícono adaptativo (con versión monocromática). **Faltan** las fuentes Playfair/DM Sans: hay que agregar los archivos (licencia OFL) o usar fuentes descargables. |
