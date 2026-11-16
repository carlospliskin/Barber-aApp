// A16: el dueño quita el acceso a un barbero; A7-b: el barbero suelta su rol (p. ej. si borraron la barbería).
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import {
  collection, deleteDoc, doc, getDoc, getDocs, query, serverTimestamp, setDoc, Timestamp, updateDoc, where, writeBatch,
} from "firebase/firestore";

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
const barberProfile = profile({ staffTenantId: "ana", staffRole: "BARBER", staffBarberId: "b1", staffInviteCode: "INV234" });

function appt(offsetMin, extra = {}) {
  const t = new Date(Date.now() + offsetMin * 60_000);
  const date = t.toISOString().slice(0, 10);
  return {
    clientUid: "cli", clientName: "Cliente", barberId: "b1", barberName: "Luis", serviceId: "s1", serviceName: "Corte",
    priceCents: 100, date, startMinute: 600, endMinute: 615, startsAt: Timestamp.fromDate(t), status: "BOOKED",
    slotKeys: ["600", "605", "610"], dayId: `b1_${date}`, createdAt: new Date(), ...extra,
  };
}

async function world() {
  await seed("users/ana", profile({ staffTenantId: "ana", staffRole: "OWNER" }));
  await seed("users/luis", barberProfile);
  await seed("users/cli", profile());
  await seed("tenants/ana", { name: "Gold", ownerUid: "ana", timezone: "America/Mexico_City", createdAt: new Date() });
  await seed("tenants/ana/appointments/a1", appt(-5));
  await seed("tenants/ana/clients/cli", { name: "Cliente", completed: 0, noShows: 0, rewardsUsed: 0, penaltyUntil: null, lastAppointmentId: "a1", updatedAt: new Date() });
}

const revoke = (uid, data = { barberId: "b1", createdAt: serverTimestamp() }) => setDoc(doc(db(uid), "tenants/ana/revoked/luis"), data);
const agenda = (uid) => getDocs(query(collection(db(uid), "tenants/ana/appointments"), where("barberId", "==", "b1")));

describe("A16 el dueño quita el acceso a un barbero", () => {
  test("solo el dueño revoca, y con forma válida", async () => {
    await world();
    await assertFails(revoke("luis"));
    await assertFails(revoke("cli"));
    await assertFails(revoke("ana", { barberId: "b1", createdAt: serverTimestamp(), extra: 1 }));
    await assertSucceeds(revoke("ana"));
  });

  test("el barbero revocado ya no ve su agenda, ni marca citas, ni ve fichas", async () => {
    await world();
    await assertSucceeds(agenda("luis"));
    await revoke("ana");
    await assertFails(agenda("luis"));
    await assertFails(getDoc(doc(db("luis"), "tenants/ana/appointments/a1")));
    await assertFails(updateDoc(doc(db("luis"), "tenants/ana/appointments/a1"), { status: "COMPLETED", updatedAt: serverTimestamp() }));
    await assertFails(getDoc(doc(db("luis"), "tenants/ana/clients/cli")));
  });

  test("el dueño sigue operando y puede devolver el acceso", async () => {
    await world();
    await revoke("ana");
    await assertSucceeds(agenda("ana"));
    await assertSucceeds(getDocs(collection(db("ana"), "tenants/ana/revoked")));
    await assertSucceeds(deleteDoc(doc(db("ana"), "tenants/ana/revoked/luis")));
    await assertSucceeds(agenda("luis"));
  });

  test("nadie más ve ni lista las revocaciones", async () => {
    await world();
    await revoke("ana");
    await assertFails(getDocs(collection(db("cli"), "tenants/ana/revoked")));
    await assertFails(deleteDoc(doc(db("luis"), "tenants/ana/revoked/luis")));
  });

  test("el barbero sabe si lo revocaron (para avisarle)", async () => {
    await world();
    await revoke("ana");
    await assertSucceeds(getDoc(doc(db("luis"), "tenants/ana/revoked/luis")));
  });
});

describe("A7-b el barbero suelta su rol", () => {
  const leave = { staffTenantId: null, staffRole: null, staffBarberId: null, staffInviteCode: null };

  test("un barbero deja de serlo (borraron la barbería, lo revocaron o se fue)", async () => {
    await world();
    await assertSucceeds(updateDoc(doc(db("luis"), "users/luis"), leave));
  });

  test("al soltar el rol no cambia otra cosa ni se queda a medias", async () => {
    await world();
    await assertFails(updateDoc(doc(db("luis"), "users/luis"), { ...leave, displayName: "Otro" }));
    await assertFails(updateDoc(doc(db("luis"), "users/luis"), { staffRole: null }));
  });

  test("el dueño no suelta su rol así (su barbería depende de él)", async () => {
    await world();
    await assertFails(updateDoc(doc(db("ana"), "users/ana"), leave));
  });

  test("después puede aceptar una invitación de otra barbería", async () => {
    await world();
    await updateDoc(doc(db("luis"), "users/luis"), leave);
    await seed("staffInvites/NEW234", { tenantId: "pepe", barberId: "b9", barberName: "Luis", createdAt: new Date(), expiresAt: new Date(Date.now() + 86_400_000), usedBy: null });
    const d = db("luis");
    const b = writeBatch(d);
    b.update(doc(d, "staffInvites/NEW234"), { usedBy: "luis" });
    b.update(doc(d, "users/luis"), { staffTenantId: "pepe", staffRole: "BARBER", staffBarberId: "b9", staffInviteCode: "NEW234" });
    await assertSucceeds(b.commit());
  });
});

describe("A16 volver con una invitación nueva", () => {
  const leave = { staffTenantId: null, staffRole: null, staffBarberId: null, staffInviteCode: null };
  const inviteAt = (code, createdAt) => seed(`staffInvites/${code}`, {
    tenantId: "ana", barberId: "b1", barberName: "Luis", createdAt, expiresAt: new Date(Date.now() + 86_400_000), usedBy: null,
  });
  function redeem(code) {
    const d = db("luis");
    const b = writeBatch(d);
    b.update(doc(d, `staffInvites/${code}`), { usedBy: "luis" });
    b.update(doc(d, "users/luis"), { staffTenantId: "ana", staffRole: "BARBER", staffBarberId: "b1", staffInviteCode: code });
    b.delete(doc(d, "tenants/ana/revoked/luis"));
    return b.commit();
  }

  test("con una invitación creada después de quitarle el acceso, vuelve", async () => {
    await world();
    await seed("tenants/ana/revoked/luis", { barberId: "b1", createdAt: new Date(Date.now() - 60_000) });
    await updateDoc(doc(db("luis"), "users/luis"), leave);
    await inviteAt("NEW234", new Date());
    await assertSucceeds(redeem("NEW234"));
    await assertSucceeds(agenda("luis"));
  });

  test("una invitación vieja (de antes de quitarle el acceso) no lo devuelve", async () => {
    await world();
    await inviteAt("OLD234", new Date(Date.now() - 120_000));
    await seed("tenants/ana/revoked/luis", { barberId: "b1", createdAt: new Date(Date.now() - 60_000) });
    await updateDoc(doc(db("luis"), "users/luis"), leave);
    await assertFails(redeem("OLD234"));
  });

  test("sin revocación previa, aceptar invitación sigue funcionando (borrar algo que no existe)", async () => {
    await world();
    await updateDoc(doc(db("luis"), "users/luis"), leave);
    await inviteAt("NEW234", new Date());
    await assertSucceeds(redeem("NEW234"));
  });

  test("no puede borrar su revocación sin invitación", async () => {
    await world();
    await seed("tenants/ana/revoked/luis", { barberId: "b1", createdAt: new Date(Date.now() - 60_000) });
    await assertFails(deleteDoc(doc(db("luis"), "tenants/ana/revoked/luis")));
  });
});
