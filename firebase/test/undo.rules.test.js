// F7-2: el dueño deshace un "atendida" o "no llegó" marcado por error, dentro de 15 minutos.
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, serverTimestamp, setDoc, Timestamp, updateDoc, writeBatch } from "firebase/firestore";

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-rules", /* A12 */
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8085 },
  });
});
after(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const db = (uid) => env.authenticatedContext(uid, { email: `${uid}@mail.com`, email_verified: true }).firestore();
const profile = (extra = {}) => ({ displayName: "X", email: "x@mail.com", phone: null, staffTenantId: null, staffRole: null, createdAt: new Date(), ...extra });
const seed = (path, data) => env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), path), data));
const minutesAgo = (m) => new Date(Date.now() - m * 60_000);

function appt(status, markedMinutesAgo, extra = {}) {
  const t = minutesAgo(60);
  const date = t.toISOString().slice(0, 10);
  return {
    clientUid: "cli", clientName: "Cliente", barberId: "b1", barberName: "Luis", serviceId: "s1", serviceName: "Corte",
    priceCents: 100, date, startMinute: 600, endMinute: 615, startsAt: Timestamp.fromDate(t), status,
    slotKeys: ["600", "605", "610"], dayId: `b1_${date}`, createdAt: minutesAgo(120), updatedAt: minutesAgo(markedMinutesAgo), ...extra,
  };
}

const record = (extra = {}) => ({
  name: "Cliente", completed: 5, noShows: 1, rewardsUsed: 0, penaltyUntil: null, lastAppointmentId: "a1", updatedAt: new Date(), ...extra,
});

async function world(status, markedMinutesAgo, client = record(), extra = {}) {
  await seed("users/ana", profile({ staffTenantId: "ana", staffRole: "OWNER" }));
  await seed("users/luis", profile({ staffTenantId: "ana", staffRole: "BARBER", staffBarberId: "b1", staffInviteCode: "INV234" }));
  await seed("tenants/ana", { name: "Gold", ownerUid: "ana", timezone: "America/Mexico_City", createdAt: new Date() });
  await seed("tenants/ana/appointments/a1", appt(status, markedMinutesAgo, extra));
  if (client) await seed("tenants/ana/clients/cli", client);
}

function undo(uid, clientChanges) {
  const d = db(uid);
  const b = writeBatch(d);
  b.update(doc(d, "tenants/ana/appointments/a1"), { status: "BOOKED", updatedAt: serverTimestamp() });
  if (clientChanges) b.update(doc(d, "tenants/ana/clients/cli"), { ...clientChanges, lastAppointmentId: "a1", updatedAt: serverTimestamp() });
  return b.commit();
}

describe("F7-2 deshacer atendida", () => {
  test("el dueño la deshace dentro de 15 min y la visita se resta", async () => {
    await world("COMPLETED", 5);
    await assertSucceeds(undo("ana", { completed: 4 }));
  });

  test("después de 15 min ya no", async () => {
    await world("COMPLETED", 16);
    await assertFails(undo("ana", { completed: 4 }));
  });

  test("un barbero no deshace (solo el dueño)", async () => {
    await world("COMPLETED", 5);
    await assertFails(undo("luis", { completed: 4 }));
  });

  test("hay que restar la visita, ni más ni menos, y sin tocar lo demás", async () => {
    await world("COMPLETED", 5);
    await assertFails(undo("ana", null)); // sin restar
    await assertFails(undo("ana", { completed: 3 }));
    await assertFails(undo("ana", { completed: 4, noShows: 0 }));
  });

  test("no se deshace si ya canjeó un premio con esa visita", async () => {
    await world("COMPLETED", 5, record({ completed: 10, rewardsUsed: 1 }));
    await assertFails(undo("ana", { completed: 9 }));
  });
});

describe("F7-2 deshacer no-show", () => {
  test("resta la falta", async () => {
    await world("NO_SHOW", 5, record({ noShows: 2 }));
    await assertSucceeds(undo("ana", { noShows: 1 }));
  });

  test("si esa falta lo penalizó, se quita la penalización", async () => {
    const until = new Date(Date.now() + 14 * 86_400_000);
    await world("NO_SHOW", 5, record({ noShows: 3, penaltyUntil: until }));
    await assertSucceeds(undo("ana", { noShows: 2, penaltyUntil: null }));
  });

  test("una falta que no penalizó no sirve para quitar una penalización vieja", async () => {
    const until = new Date(Date.now() + 5 * 86_400_000);
    await world("NO_SHOW", 5, record({ noShows: 4, penaltyUntil: until }));
    await assertFails(undo("ana", { noShows: 3, penaltyUntil: null }));
    await assertSucceeds(undo("ana", { noShows: 3 }));
  });
});

describe("Marcar sin tocar la ficha", () => {
  test("no se puede marcar no-show sin sumar la falta aunque la ficha ya apunte a esa cita (primera reserva)", async () => {
    await world("BOOKED", 5, record({ lastAppointmentId: "a1" }), { startsAt: Timestamp.fromDate(minutesAgo(30)) });
    await assertFails(updateDoc(doc(db("luis"), "tenants/ana/appointments/a1"), { status: "NO_SHOW", updatedAt: serverTimestamp() }));
  });
});

describe("F7-2 sin cuenta y hora real", () => {
  test("una cita sin cuenta se deshace sin ficha", async () => {
    await world("COMPLETED", 5, null, { clientUid: null, clientName: "Pedro", clientPhone: "5512345678" });
    await assertSucceeds(undo("ana", null));
  });

  test("una cita BOOKED no se 'deshace'", async () => {
    await world("BOOKED", 5);
    await assertFails(undo("ana", { completed: 4 }));
  });

  test("la hora de marcar la pone el servidor (no se puede inventar para alargar el plazo)", async () => {
    await world("BOOKED", 5, record(), { startsAt: Timestamp.fromDate(minutesAgo(30)) });
    const d = db("luis");
    const b = writeBatch(d);
    b.update(doc(d, "tenants/ana/appointments/a1"), { status: "COMPLETED", updatedAt: new Date(Date.now() + 86_400_000) });
    b.update(doc(d, "tenants/ana/clients/cli"), { completed: 6, lastAppointmentId: "a1", updatedAt: serverTimestamp() });
    await assertFails(b.commit());
  });
});
