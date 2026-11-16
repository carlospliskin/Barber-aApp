// A7: eliminar cuenta y datos desde la app (sin Cloud Functions).
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import {
  collection, collectionGroup, deleteDoc, doc, getDocs, limit, query, serverTimestamp, setDoc, Timestamp, updateDoc, where, writeBatch,
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

function appt(tenant, offsetMin, extra = {}) {
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
  await seed("users/cli", profile());
  await seed("users/otro", profile());
  await seed("tenants/ana", { name: "Gold", ownerUid: "ana", timezone: "America/Mexico_City", createdAt: new Date(), shareCode: "ABC234" });
  await seed("tenants/pepe", { name: "Pepe", ownerUid: "pepe", timezone: "America/Mexico_City", createdAt: new Date() });
  await seed("tenants/ana/catalog/main", { version: 1 });
  await seed("shareCodes/ABC234", { tenantId: "ana", createdAt: new Date() });
  await seed("staffInvites/INV234", { tenantId: "ana", barberId: "b1", barberName: "Luis", createdAt: new Date(), expiresAt: new Date(Date.now() + 86_400_000), usedBy: null });
  await seed("tenants/ana/clients/cli", { name: "Cliente", completed: 3, noShows: 0, rewardsUsed: 0, penaltyUntil: null, lastAppointmentId: "a1", updatedAt: new Date() });
  await seed("tenants/ana/appointments/a1", appt("ana", -60 * 24, { status: "COMPLETED" }));
  await seed("tenants/ana/appointments/a2", appt("ana", 60)); // en menos de 2 h: ya no se puede cancelar
  await seed("tenants/ana/appointments/a3", appt("ana", 60 * 48)); // se puede cancelar
  await seed("tenants/pepe/appointments/p1", appt("pepe", -60 * 48, { status: "NO_SHOW" }));
  await seed("tenants/pepe/appointments/o1", appt("pepe", -60 * 48, { clientUid: "otro" }));
  await seed(`tenants/ana/days/${appt("ana", 60).dayId}`, { booked: {}, lastOp: "a2", updatedAt: new Date() });
}

const anonymized = { clientUid: null, clientName: "", updatedAt: serverTimestamp() };

describe("A7 el cliente borra su cuenta", () => {
  test("encuentra sus citas en todas las barberías (consulta de grupo)", async () => {
    await world();
    const q = query(collectionGroup(db("cli"), "appointments"), where("clientUid", "==", "cli"), limit(200));
    const snap = await assertSucceeds(getDocs(q));
    if (snap.size !== 4) throw new Error(`esperaba 4 citas, hubo ${snap.size}`);
  });

  test("no puede listar citas ajenas por grupo", async () => {
    await world();
    await assertFails(getDocs(query(collectionGroup(db("cli"), "appointments"), where("clientUid", "==", "otro"))));
    await assertFails(getDocs(query(collectionGroup(db("cli"), "appointments"), limit(10))));
  });

  test("anonimiza citas pasadas y las que ya no puede cancelar", async () => {
    await world();
    await assertSucceeds(updateDoc(doc(db("cli"), "tenants/ana/appointments/a1"), anonymized));
    await assertSucceeds(updateDoc(doc(db("cli"), "tenants/ana/appointments/a2"), anonymized));
    await assertSucceeds(updateDoc(doc(db("cli"), "tenants/pepe/appointments/p1"), anonymized));
  });

  test("una cita que aún puede cancelar se cancela primero (no deja horarios ocupados)", async () => {
    await world();
    await assertFails(updateDoc(doc(db("cli"), "tenants/ana/appointments/a3"), anonymized));
    await seed("tenants/ana/appointments/a3", appt("ana", 60 * 48, { status: "CANCELLED" }));
    await assertSucceeds(updateDoc(doc(db("cli"), "tenants/ana/appointments/a3"), anonymized));
  });

  test("anonimizar no cambia otra cosa ni sirve con citas ajenas", async () => {
    await world();
    await assertFails(updateDoc(doc(db("cli"), "tenants/ana/appointments/a1"), { ...anonymized, priceCents: 0 }));
    await assertFails(updateDoc(doc(db("cli"), "tenants/ana/appointments/a1"), { ...anonymized, clientName: "Otro" }));
    await assertFails(updateDoc(doc(db("cli"), "tenants/pepe/appointments/o1"), anonymized));
  });

  test("borra su ficha y su perfil, no los de otros", async () => {
    await world();
    await assertSucceeds(deleteDoc(doc(db("cli"), "tenants/ana/clients/cli")));
    await assertSucceeds(deleteDoc(doc(db("cli"), "tenants/pepe/clients/cli"))); // no existe: no falla
    await assertFails(deleteDoc(doc(db("otro"), "tenants/ana/clients/cli")));
    await assertSucceeds(deleteDoc(doc(db("cli"), "users/cli")));
    await assertFails(deleteDoc(doc(db("cli"), "users/otro")));
  });

  test("el personal marca atendida una cita anonimizada (sin ficha)", async () => {
    await world();
    await updateDoc(doc(db("cli"), "tenants/ana/appointments/a2"), anonymized);
    await seed("tenants/ana/appointments/a2", { ...appt("ana", -5), clientUid: null, clientName: "" });
    await assertSucceeds(updateDoc(doc(db("ana"), "tenants/ana/appointments/a2"), { status: "COMPLETED", updatedAt: serverTimestamp() }));
  });
});

describe("A7 el dueño borra su barbería", () => {
  test("borra citas, días, fichas, catálogo, invitaciones, código y barbería", async () => {
    await world();
    const d = db("ana");
    const invites = await assertSucceeds(getDocs(query(collection(d, "staffInvites"), where("tenantId", "==", "ana"))));
    const b = writeBatch(d);
    for (const id of ["a1", "a2", "a3"]) b.delete(doc(d, `tenants/ana/appointments/${id}`));
    b.delete(doc(d, `tenants/ana/days/${appt("ana", 60).dayId}`));
    b.delete(doc(d, "tenants/ana/clients/cli"));
    invites.forEach((i) => b.delete(i.ref));
    b.delete(doc(d, "tenants/ana/catalog/main"));
    b.delete(doc(d, "shareCodes/ABC234"));
    b.delete(doc(d, "tenants/ana"));
    await assertSucceeds(b.commit());
  });

  test("nadie más borra la barbería ni sus datos", async () => {
    await world();
    const d = db("cli");
    await assertFails(deleteDoc(doc(d, "tenants/ana/appointments/a1")));
    await assertFails(deleteDoc(doc(d, `tenants/ana/days/${appt("ana", 60).dayId}`)));
    await assertFails(deleteDoc(doc(d, "tenants/ana/catalog/main")));
    await assertFails(deleteDoc(doc(d, "shareCodes/ABC234")));
    await assertFails(deleteDoc(doc(d, "staffInvites/INV234")));
    await assertFails(deleteDoc(doc(d, "tenants/ana")));
    await assertFails(getDocs(query(collection(d, "staffInvites"), where("tenantId", "==", "ana"))));
  });
});
