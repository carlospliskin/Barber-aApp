// Fase 7: tenants/{t}/clients/{uid} — visitas, no-shows, premios y penalización.
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, doc, getDoc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc, writeBatch } from "firebase/firestore";

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-rules", /* proyecto aparte de la app */
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8085 },
  });
});
after(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const db = (uid) => env.authenticatedContext(uid, { email: `${uid}@mail.com` }).firestore();
const ago = (min) => Timestamp.fromMillis(Date.now() - min * 60_000);
const inDays = (d) => Timestamp.fromMillis(Date.now() + d * 86_400_000);
const profile = (extra = {}) => ({ displayName: "X", email: "x@mail.com", phone: null, staffTenantId: null, staffRole: null, createdAt: new Date(), ...extra });
const seed = (path, data) => env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), path), data));

async function world(stats = null) {
  await seed("users/ana", profile({ staffTenantId: "ana", staffRole: "OWNER" }));
  await seed("users/luis", profile({ staffTenantId: "ana", staffRole: "BARBER", staffBarberId: "b1", staffInviteCode: "X" }));
  await seed("users/cli", profile());
  for (const id of ["a1", "a2"]) {
    await seed(`tenants/ana/appointments/${id}`, {
      clientUid: "cli", clientName: "Cli", barberId: "b1", barberName: "L", serviceId: "s1", serviceName: "Corte", priceCents: 15000,
      date: "2026-11-16", startMinute: 600, endMinute: 630, startsAt: ago(60), status: "BOOKED", slotKeys: ["600"], dayId: "b1_2026-11-16", createdAt: new Date(),
    });
  }
  if (stats) await seed("tenants/ana/clients/cli", { name: "Cli", completed: 0, noShows: 0, rewardsUsed: 0, penaltyUntil: null, lastAppointmentId: "x", updatedAt: new Date(), ...stats });
}

const stats = (extra = {}) => ({ name: "Cli", completed: 0, noShows: 0, rewardsUsed: 0, penaltyUntil: null, updatedAt: serverTimestamp(), ...extra });

/** Marca la cita y actualiza al cliente en el mismo batch (como la app). */
function mark(uid, apptId, status, clientData) {
  const d = db(uid);
  const b = writeBatch(d);
  b.update(doc(d, `tenants/ana/appointments/${apptId}`), { status, updatedAt: serverTimestamp() });
  b.set(doc(d, "tenants/ana/clients/cli"), { ...clientData, lastAppointmentId: apptId });
  return b.commit();
}

describe("P3 / P7 visitas y no-shows junto con la cita", () => {
  test("primera visita atendida crea al cliente con 1 visita", async () => {
    await world();
    await assertSucceeds(mark("luis", "a1", "COMPLETED", stats({ completed: 1 })));
  });

  test("P3 no se puede sumar más de 1 por cita", async () => {
    await world({ completed: 4 });
    await assertFails(mark("luis", "a1", "COMPLETED", stats({ completed: 6 })));
  });

  test("P3 sumar visitas sin marcar una cita no se permite", async () => {
    await world({ completed: 4 });
    await assertFails(setDoc(doc(db("ana"), "tenants/ana/clients/cli"), { ...stats({ completed: 5 }), lastAppointmentId: "a1" }));
  });

  test("P3 una cita ya atendida no vuelve a sumar", async () => {
    await world({ completed: 1 });
    await env.withSecurityRulesDisabled((ctx) => updateDoc(doc(ctx.firestore(), "tenants/ana/appointments/a1"), { status: "COMPLETED" }));
    await assertFails(setDoc(doc(db("ana"), "tenants/ana/clients/cli"), { ...stats({ completed: 2 }), lastAppointmentId: "a1" }));
  });

  test("no-show suma 1 sin penalizar antes de 3", async () => {
    await world({ noShows: 1 });
    await assertSucceeds(mark("ana", "a1", "NO_SHOW", stats({ noShows: 2 })));
  });

  test("P1 al tercer no-show se penaliza hasta 14 días", async () => {
    await world({ noShows: 2 });
    await assertSucceeds(mark("ana", "a1", "NO_SHOW", stats({ noShows: 3, penaltyUntil: inDays(14) })));
  });

  test("P1 no se puede penalizar antes de 3 ni por más de 14 días", async () => {
    await world({ noShows: 0 });
    await assertFails(mark("ana", "a1", "NO_SHOW", stats({ noShows: 1, penaltyUntil: inDays(14) })));
    await world({ noShows: 2 });
    await assertFails(mark("ana", "a2", "NO_SHOW", stats({ noShows: 3, penaltyUntil: inDays(60) })));
  });

  test("el cliente no puede escribir sus propias estadísticas", async () => {
    await world();
    await assertFails(setDoc(doc(db("cli"), "tenants/ana/clients/cli"), { ...stats({ completed: 50 }), lastAppointmentId: "a1" }));
  });
});

describe("P4 / P5 premios y penalización (personal)", () => {
  const owner = (data) => setDoc(doc(db("ana"), "tenants/ana/clients/cli"), { ...data, lastAppointmentId: "x" });

  test("P4 se canjea un premio si hay visitas suficientes", async () => {
    await world({ completed: 10 });
    await assertSucceeds(owner(stats({ completed: 10, rewardsUsed: 1 })));
  });

  test("P4 no se canjea más de lo ganado", async () => {
    await world({ completed: 9 });
    await assertFails(owner(stats({ completed: 9, rewardsUsed: 1 })));
  });

  test("P5 el dueño quita la penalización (y reinicia los no-shows)", async () => {
    await world({ noShows: 3, penaltyUntil: inDays(10) });
    await assertSucceeds(owner(stats({ noShows: 0, penaltyUntil: null })));
  });

  test("P5 un barbero no quita penalizaciones", async () => {
    await world({ noShows: 3, penaltyUntil: inDays(10) });
    await assertFails(setDoc(doc(db("luis"), "tenants/ana/clients/cli"), { ...stats({ noShows: 0, penaltyUntil: null }), lastAppointmentId: "x" }));
  });
});

describe("P8 / P5 leer", () => {
  test("el cliente lee su ficha; otro cliente no", async () => {
    await world({ completed: 3 });
    await seed("users/otro", profile());
    await assertSucceeds(getDoc(doc(db("cli"), "tenants/ana/clients/cli")));
    await assertFails(getDoc(doc(db("otro"), "tenants/ana/clients/cli")));
  });

  test("el dueño lista sus clientes; un barbero lee una ficha pero no lista", async () => {
    await world({ completed: 3 });
    await assertSucceeds(getDocs(collection(db("ana"), "tenants/ana/clients")));
    await assertSucceeds(getDoc(doc(db("luis"), "tenants/ana/clients/cli")));
    await assertFails(getDocs(collection(db("luis"), "tenants/ana/clients")));
  });
});

describe("P2 penalizado no reserva", () => {
  function book(uid) {
    const d = db(uid);
    const b = writeBatch(d);
    b.set(doc(d, "tenants/ana/appointments/n1"), {
      clientUid: uid, clientName: "Cli", barberId: "b1", barberName: "L", serviceId: "s1", serviceName: "Corte", priceCents: 15000,
      date: "2026-12-01", startMinute: 600, endMinute: 605, startsAt: Timestamp.fromDate(new Date("2026-12-01T16:00:00Z")),
      status: "BOOKED", slotKeys: ["600"], dayId: "b1_2026-12-01", createdAt: serverTimestamp(),
    });
    b.set(doc(d, "tenants/ana/days/b1_2026-12-01"), { booked: { 600: "n1" }, lastOp: "n1", updatedAt: serverTimestamp() });
    return b.commit();
  }

  test("con penalización vigente no puede reservar aunque se salte la app", async () => {
    await world({ noShows: 3, penaltyUntil: inDays(5) });
    await assertFails(book("cli"));
  });

  test("con la penalización vencida sí reserva", async () => {
    await world({ noShows: 3, penaltyUntil: ago(1) });
    await assertSucceeds(book("cli"));
  });
});
