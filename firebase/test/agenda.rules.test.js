// Fase 6: agenda del personal e invitaciones de barbero.
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, doc, getDocs, query, serverTimestamp, setDoc, Timestamp, updateDoc, where, writeBatch, deleteField } from "firebase/firestore";

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-rules", /* A12: proyecto aparte; clearFirestore() no borra los datos de la app */
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8085 },
  });
});
after(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const db = (uid) => env.authenticatedContext(uid, { email: `${uid}@mail.com` }).firestore();
const ago = (min) => Timestamp.fromMillis(Date.now() - min * 60_000);
const inMin = (min) => Timestamp.fromMillis(Date.now() + min * 60_000);

const profile = (extra = {}) => ({ displayName: "X", email: "x@mail.com", phone: null, staffTenantId: null, staffRole: null, createdAt: new Date(), ...extra });

async function seed(path, data) {
  await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), path), data));
}

async function world({ startsAt = ago(30) } = {}) {
  await seed("users/ana", profile({ staffTenantId: "ana", staffRole: "OWNER" }));
  await seed("users/luis", profile({ staffTenantId: "ana", staffRole: "BARBER", staffBarberId: "b1", staffInviteCode: "INV234" }));
  await seed("users/pepe", profile({ staffTenantId: "ana", staffRole: "BARBER", staffBarberId: "b2", staffInviteCode: "INV567" }));
  await seed("users/cli", profile());
  const base = { clientUid: "cli", clientName: "C", barberName: "L", serviceId: "s1", serviceName: "Corte", priceCents: 15000,
    date: "2026-11-16", startMinute: 600, endMinute: 630, startsAt, status: "BOOKED", slotKeys: ["600", "605"], createdAt: new Date() };
  await seed("tenants/ana/appointments/a1", { ...base, barberId: "b1", dayId: "b1_2026-11-16" });
  await seed("tenants/ana/appointments/a2", { ...base, barberId: "b2", dayId: "b2_2026-11-16" });
  await seed("tenants/ana/days/b1_2026-11-16", { booked: { 600: "a1", 605: "a1" }, lastOp: "a1", updatedAt: new Date() });
}

const agenda = (uid, extra = []) =>
  getDocs(query(collection(db(uid), "tenants/ana/appointments"), where("date", "==", "2026-11-16"), ...extra));

describe("A2 / A3 leer la agenda del día", () => {
  test("el barbero lee su agenda del día", async () => {
    await world();
    await assertSucceeds(agenda("luis", [where("barberId", "==", "b1")]));
  });

  test("el barbero no lee la agenda de otro barbero", async () => {
    await world();
    await assertFails(agenda("luis", [where("barberId", "==", "b2")]));
    await assertFails(agenda("luis"));
  });

  test("el dueño lee la de todos", async () => {
    await world();
    await assertSucceeds(agenda("ana"));
  });

  test("un cliente no lee la agenda", async () => {
    await world();
    await assertFails(agenda("cli"));
  });
});

describe("A4 / A6 completar y no-show", () => {
  const mark = (uid, id, status) => updateDoc(doc(db(uid), `tenants/ana/appointments/${id}`), { status, updatedAt: serverTimestamp() });

  test("el barbero marca completada su cita después de la hora de inicio", async () => {
    await world();
    await assertSucceeds(mark("luis", "a1", "COMPLETED"));
  });

  test("el dueño marca no-show", async () => {
    await world();
    await assertSucceeds(mark("ana", "a2", "NO_SHOW"));
  });

  test("A4 no se completa antes de la hora de inicio", async () => {
    await world({ startsAt: inMin(60) });
    await assertFails(mark("luis", "a1", "COMPLETED"));
  });

  test("un barbero no toca las citas de otro", async () => {
    await world();
    await assertFails(mark("luis", "a2", "COMPLETED"));
  });

  test("el cliente no puede marcarse como atendido", async () => {
    await world();
    await assertFails(mark("cli", "a1", "COMPLETED"));
  });

  test("A6 un estado final no cambia", async () => {
    await world();
    await env.withSecurityRulesDisabled((ctx) => updateDoc(doc(ctx.firestore(), "tenants/ana/appointments/a1"), { status: "COMPLETED" }));
    await assertFails(mark("ana", "a1", "NO_SHOW"));
    await assertFails(mark("ana", "a1", "BOOKED"));
  });

  test("A5 el barbero cancela su cita y libera los bloques", async () => {
    await world({ startsAt: inMin(30) });
    const d = db("luis");
    const b = writeBatch(d);
    b.update(doc(d, "tenants/ana/appointments/a1"), { status: "CANCELLED", updatedAt: serverTimestamp() });
    b.update(doc(d, "tenants/ana/days/b1_2026-11-16"), { "booked.600": deleteField(), "booked.605": deleteField(), lastOp: "a1", updatedAt: serverTimestamp() });
    await assertSucceeds(b.commit());
  });
});

describe("A1 / A8 invitaciones de barbero", () => {
  const invite = (extra = {}) => ({ tenantId: "ana", barberId: "b3", barberName: "Toño", createdAt: serverTimestamp(), expiresAt: inMin(60 * 24 * 7 - 5), usedBy: null, ...extra });

  test("el dueño crea una invitación para un barbero de su catálogo", () =>
    assertSucceeds(setDoc(doc(db("ana"), "staffInvites/TON234"), invite())));

  test("nadie más crea invitaciones de esa barbería", () =>
    assertFails(setDoc(doc(db("cli"), "staffInvites/TON234"), invite())));

  test("A11 tolera que el reloj del teléfono vaya unos minutos adelantado", () =>
    assertSucceeds(setDoc(doc(db("ana"), "staffInvites/TON234"), invite({ expiresAt: inMin(60 * 24 * 7 + 3) }))));

  test("la invitación vence en máximo 7 días", () =>
    assertFails(setDoc(doc(db("ana"), "staffInvites/TON234"), invite({ expiresAt: inMin(60 * 24 * 8) }))));

  function redeem(uid, code, barberId = "b3", tenantId = "ana") {
    const d = db(uid);
    const b = writeBatch(d);
    b.update(doc(d, `users/${uid}`), { staffTenantId: tenantId, staffRole: "BARBER", staffBarberId: barberId, staffInviteCode: code });
    b.update(doc(d, `staffInvites/${code}`), { usedBy: uid });
    return b.commit();
  }

  async function seedInvite(extra = {}) {
    await seed("staffInvites/TON234", { tenantId: "ana", barberId: "b3", barberName: "Toño", createdAt: new Date(), expiresAt: inMin(600), usedBy: null, ...extra });
  }

  test("un cliente acepta la invitación y queda como barbero de esa barbería", async () => {
    await seed("users/tono", profile());
    await seedInvite();
    await assertSucceeds(redeem("tono", "TON234"));
  });

  test("A8 no se puede usar dos veces", async () => {
    await seed("users/tono", profile());
    await seed("users/otro", profile());
    await seedInvite({ usedBy: "tono" });
    await assertFails(redeem("otro", "TON234"));
  });

  test("A8 una invitación vencida no sirve", async () => {
    await seed("users/tono", profile());
    await seedInvite({ expiresAt: ago(1) });
    await assertFails(redeem("tono", "TON234"));
  });

  test("A8 quien ya es personal no la puede usar", async () => {
    await seedInvite();
    await world();
    await assertFails(redeem("luis", "TON234"));
  });

  test("no se puede elegir otro barbero u otra barbería que los de la invitación", async () => {
    await seed("users/tono", profile());
    await seedInvite();
    await assertFails(redeem("tono", "TON234", "b1"));
    await assertFails(redeem("tono", "TON234", "b3", "beto"));
  });

  test("nadie se da el rol de barbero sin invitación", async () => {
    await seed("users/tono", profile());
    await assertFails(updateDoc(doc(db("tono"), "users/tono"), { staffTenantId: "ana", staffRole: "BARBER", staffBarberId: "b3", staffInviteCode: "NOEXISTE" }));
  });
});
