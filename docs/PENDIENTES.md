# Pendientes

Resumen de lo que falta, al 17 de noviembre de 2026. El detalle de cada punto (cómo resolverlo y cómo probarlo) está en
[DEUDA_TECNICA.md](DEUDA_TECNICA.md); el historial, en [PLAN.md](PLAN.md).

**Prioridad:** P0 bloquea la publicación · P1 antes de publicar · P2 primeras semanas · P3 cuando haya tiempo.
**Esfuerzo:** S ≤ 2 h · M medio día a 1 día · L varios días.

## Estado general
- Fases 0 a 8.5 terminadas. Fase 9: eliminar cuenta ✅; la publicación espera los puntos de abajo.
- CI en verde: tests unitarios, ktlint, reglas de Firestore (152) y tests instrumentados (10).

## 1. Lo que te toca a ti (Carlos)

Nadie más puede hacerlo: piden tu cuenta de Google, tus contraseñas o tus datos.

| ID | Prioridad | Qué hacer | Dónde están los pasos |
|---|---|---|---|
| L1 | **P0** | Crear el proyecto real de Firebase (plan Spark), llenar `firebase.properties` y publicar reglas e índices. | [FIREBASE.md](FIREBASE.md) |
| L2 | **P0** | Crear tu llave de subida (`keytool`) y llenar `keystore.properties`. El build ya firma con ella. | [RELEASE.md](RELEASE.md) |
| PLAY-1 | **P0** | Decirme responsable y correo de contacto para las páginas de privacidad y borrado; activar GitHub Pages y publicarlas; llenar el formulario «Seguridad de los datos» en Play Console. | [RELEASE.md](RELEASE.md) |
| V3 | **P0** | Después de L1: activar App Check (Play Integrity) en modo monitor una semana y luego exigirlo. | [RELEASE.md](RELEASE.md) |
| X3 | P2 | Conseguir el ícono de marca (hoy son unas tijeras provisionales). | — |

## 2. Antes de publicar (los hago yo)

| ID | Prioridad | Esfuerzo | Qué falta |
|---|---|---|---|
| Q2 | P1 | M | Guardar las pruebas de punta a punta en el repo (Maestro o Compose UI Test) y correrlas en el CI. Hoy se hacen a mano con adb. |
| L3 | P1 | S | Fuentes de la marca: Playfair Display y DM Sans en `core:designsystem` (el ícono ya está). |

## 3. Primeras semanas después de publicar (P2)

| ID | Esfuerzo | Qué falta |
|---|---|---|
| F7-5 | M | Lista de clientes: hoy solo los 50 más recientes, sin búsqueda ni paginación. |
| F7-9 | M | Marcar atendida o no-show sin red falla; falta un "reintentar" claro. |
| F7-10 | S | Probar en el teléfono el canje de premio (necesita 10 visitas sembradas). |
| N4 | M | Reagendar en un solo paso (hoy es cancelar y volver a reservar, y pierde la regla de 2 h). |
| R2 | S | El precio de la cita lo escribe la app del cliente; solo importa si algún día se cobra en la app. |
| Q4 | M | Tests de pantallas con Compose (login y reserva). |
| Q5 | M | Revisión completa de accesibilidad: contraste, tamaños táctiles y TalkBack. |
| AR2 | M | Sacar el home de `:app` a su propio módulo. |
| AR3 | M | Dividir `feature:booking` (reservas, agenda, clientes y lealtad) en módulos más chicos. |

## 4. Cuando haya tiempo (P3)

| ID | Esfuerzo | Qué falta |
|---|---|---|
| F7-2-b | S | Deshacer el no-show que penalizó no recupera las citas que la penalización canceló. |
| F7-4 | S | "Mis citas", clientes y lealtad no actualizan la hora con la pantalla abierta (la agenda ya sí). |
| F7-7 | S | El nombre en la ficha del cliente no se actualiza si él lo cambia. |
| F7-11 | S | Medir el costo en lecturas de marcar una cita. |
| N1-c | M | Un cliente sin app que luego se registra no hereda sus visitas. |
| N1-d | S | El aviso por WhatsApp no incluye el nombre de la barbería. |
| N2-d | S | Avisar (WhatsApp) a los clientes sin app de las citas canceladas por un día libre. |
| N6 | M | Iniciar sesión con Google. |
| U14 | S | Al personal, un rechazo al cancelar le dice "faltan menos de 2 horas" (ese límite es solo del cliente). |
| W3 | S | Avisar si se crea una segunda invitación para un barbero que ya tiene cuenta. |
| AR4 | M | `core:session` para que las pantallas no dependan de `auth:domain`. |
| Q6 | S | Revisar versiones n/n-1 con un script o Renovate. |

## 5. Parciales o sin verificar

| ID | Qué falta |
|---|---|
| A7-d | Al llegar al límite diario de Spark ya se avisa; falta contar lo borrado. Probado solo con tests (el emulador no tiene cuotas). |
| A16-f | Borrar la caché al cerrar sesión: verificado solo de forma indirecta; falta comprobarlo en un teléfono. |
| N1-b | El botón de WhatsApp abre el enlace correcto; falta probarlo en un teléfono con WhatsApp instalado. |
| Q3 | ktlint ya corre; detekt espera una versión estable compatible con Kotlin 2.4. |
| C17 | Editar el catálogo sin red falla con aviso. Aceptado para el lanzamiento. |

## Fuera del plan Spark
Pagos en la app (MercadoPago) y notificaciones push desde el servidor: necesitan Cloud Functions (plan Blaze).
Se retoman solo si se cambia de plan.
