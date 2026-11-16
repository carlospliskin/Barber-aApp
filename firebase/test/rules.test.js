// Pruebas de las reglas de seguridad contra el emulador de Firestore (nunca contra producción).
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { collection, doc, getDoc, getDocs, serverTimestamp, setDoc, updateDoc, writeBatch, deleteDoc } from "firebase/firestore";

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-barberia",
    firestore: {
      rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8"),
      host: "127.0.0.1",
      port: 8085,
    },
  });
});
after(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const ana = () => env.authenticatedContext("ana", { email: "ana@mail.com" }).firestore();
const beto = () => env.authenticatedContext("beto", { email: "beto@mail.com" }).firestore();
const anon = () => env.unauthenticatedContext().firestore();

const clientProfile = (email = "ana@mail.com") => ({
  displayName: "Ana", email, phone: "5512345678", staffTenantId: null, staffRole: null, createdAt: serverTimestamp(),
});
const ownerProfile = (uid = "ana") => ({ ...clientProfile(), staffTenantId: uid, staffRole: "OWNER" });
const tenant = (uid = "ana") => ({ name: "Barber Gold", ownerUid: uid, timezone: "America/Mexico_City", createdAt: serverTimestamp() });

async function seed(path, data) {
  await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), path), data));
}

describe("users/{uid}", () => {
  test("un cliente crea su propio perfil", () =>
    assertSucceeds(setDoc(doc(ana(), "users/ana"), clientProfile())));

  test("nadie crea el perfil de otro", () =>
    assertFails(setDoc(doc(beto(), "users/ana"), clientProfile())));

  test("sin sesión no se escribe nada", () =>
    assertFails(setDoc(doc(anon(), "users/ana"), clientProfile())));

  test("el email del perfil debe ser el de la cuenta", () =>
    assertFails(setDoc(doc(ana(), "users/ana"), clientProfile("otro@mail.com"))));

  test("no se puede autoasignar el rol de barbero", () =>
    assertFails(setDoc(doc(ana(), "users/ana"), { ...clientProfile(), staffTenantId: "x", staffRole: "BARBER" })));

  test("no se puede ser dueño de la barbería de otro", () =>
    assertFails(setDoc(doc(ana(), "users/ana"), { ...clientProfile(), staffTenantId: "beto", staffRole: "OWNER" })));

  test("dueño: perfil y barbería en el mismo batch (A9)", () => {
    const db = ana();
    const b = writeBatch(db);
    b.set(doc(db, "users/ana"), ownerProfile());
    b.set(doc(db, "tenants/ana"), tenant());
    return assertSucceeds(b.commit());
  });

  test("dueño sin barbería no se puede crear", () =>
    assertFails(setDoc(doc(ana(), "users/ana"), ownerProfile())));

  test("campos extra no se permiten", () =>
    assertFails(setDoc(doc(ana(), "users/ana"), { ...clientProfile(), admin: true })));

  test("solo el dueño del perfil lo lee", async () => {
    await seed("users/ana", clientProfile());
    await assertSucceeds(getDoc(doc(ana(), "users/ana")));
    await assertFails(getDoc(doc(beto(), "users/ana")));
  });

  test("puede cambiar nombre y teléfono, pero no su rol", async () => {
    await seed("users/ana", clientProfile());
    await assertSucceeds(updateDoc(doc(ana(), "users/ana"), { displayName: "Ana P", phone: "5599999999" }));
    await assertFails(updateDoc(doc(ana(), "users/ana"), { staffRole: "OWNER", staffTenantId: "ana" }));
  });

  test("no se borra desde la app (fase 9: eliminar cuenta)", async () => {
    await seed("users/ana", clientProfile());
    await assertFails(deleteDoc(doc(ana(), "users/ana")));
  });
});

describe("tenants/{ownerUid}", () => {
  test("cualquiera con sesión ve la barbería (para reservar)", async () => {
    await seed("tenants/ana", tenant());
    await assertSucceeds(getDoc(doc(beto(), "tenants/ana")));
    await assertFails(getDoc(doc(anon(), "tenants/ana")));
  });

  test("H5 no se puede listar todas las barberías (gastaría cuota)", async () => {
    await seed("tenants/ana", tenant());
    await assertFails(getDocs(collection(beto(), "tenants")));
  });

  test("el id de la barbería debe ser el uid del dueño", () =>
    assertFails(setDoc(doc(ana(), "tenants/otra"), tenant("ana"))));

  test("nombre vacío no se permite", () =>
    assertFails(setDoc(doc(ana(), "tenants/ana"), { ...tenant(), name: "" })));

  test("el dueño edita el nombre pero no puede cambiar de dueño", async () => {
    await seed("tenants/ana", tenant());
    await assertSucceeds(updateDoc(doc(ana(), "tenants/ana"), { name: "Gold 2" }));
    await assertFails(updateDoc(doc(ana(), "tenants/ana"), { ownerUid: "beto" }));
    await assertFails(updateDoc(doc(beto(), "tenants/ana"), { name: "Robada" }));
  });
});

describe("todo lo demás", () => {
  test("cualquier otra colección está cerrada por defecto", () =>
    assertFails(setDoc(doc(ana(), "random/x"), { a: 1 })));
});
