// N5: el dueño elige la zona horaria de su barbería, solo de la lista (igual que ShopZones.ALLOWED).
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, doc, getDocs, limit, query, serverTimestamp, setDoc, updateDoc, where } from "firebase/firestore";

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-rules",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8085 },
  });
});
after(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const db = (uid) => env.authenticatedContext(uid, { email: `${uid}@mail.com`, email_verified: true }).firestore();
const seed = (path, data) => env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), path), data));
const profile = (extra = {}) => ({ displayName: "X", email: "x@mail.com", phone: null, staffTenantId: null, staffRole: null, createdAt: new Date(), ...extra });

async function world() {
  await seed("users/ana", profile({ staffTenantId: "ana", staffRole: "OWNER" }));
  await seed("users/luis", profile({ staffTenantId: "ana", staffRole: "BARBER", staffBarberId: "b1", staffInviteCode: "INV234" }));
  await seed("tenants/ana", { name: "Gold", ownerUid: "ana", timezone: "America/Mexico_City", createdAt: new Date() });
}

describe("N5 zona horaria", () => {
  test("el dueño cambia a una zona de la lista", async () => {
    await world();
    await assertSucceeds(updateDoc(doc(db("ana"), "tenants/ana"), { timezone: "America/Tijuana" }));
  });

  test("una zona fuera de la lista no", async () => {
    await world();
    await assertFails(updateDoc(doc(db("ana"), "tenants/ana"), { timezone: "Asia/Tokyo" }));
    await assertFails(updateDoc(doc(db("ana"), "tenants/ana"), { timezone: "cualquier cosa" }));
  });

  test("al registrarse, la barbería nace con una zona de la lista", async () => {
    await seed("users/eva", profile({ staffTenantId: "eva", staffRole: "OWNER" }));
    await assertFails(setDoc(doc(db("eva"), "tenants/eva"), { name: "X", ownerUid: "eva", timezone: "Mars/Base", createdAt: serverTimestamp() }));
    await assertSucceeds(setDoc(doc(db("eva"), "tenants/eva"), { name: "X", ownerUid: "eva", timezone: "America/Mexico_City", createdAt: serverTimestamp() }));
  });

  test("un barbero no cambia la zona", async () => {
    await world();
    await assertFails(updateDoc(doc(db("luis"), "tenants/ana"), { timezone: "America/Tijuana" }));
  });

  test("el dueño consulta si hay citas por venir (status + date, límite 1)", async () => {
    await world();
    await assertSucceeds(getDocs(query(collection(db("ana"), "tenants/ana/appointments"),
      where("status", "==", "BOOKED"), where("date", ">=", "2026-11-15"), limit(1))));
  });
});
