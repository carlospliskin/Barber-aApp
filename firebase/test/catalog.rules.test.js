// Reglas del catálogo: tenants/{ownerUid}/catalog/main
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, doc, getDoc, getDocs, serverTimestamp, setDoc, deleteDoc } from "firebase/firestore";

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-rules", /* A12: proyecto aparte; clearFirestore() no borra los datos de la app */
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8085 },
  });
});
after(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const owner = () => env.authenticatedContext("ana", { email: "ana@mail.com" }).firestore();
const client = () => env.authenticatedContext("beto", { email: "beto@mail.com" }).firestore();
const anon = () => env.unauthenticatedContext().firestore();

const service = (i) => ({ id: `s${i}`, name: `S${i}`, durationMinutes: 30, priceCents: 15000, active: true });
const catalog = (version, extra = {}) => ({
  version, services: [service(1)], barbers: [], updatedAt: serverTimestamp(), ...extra,
});
const seed = (data) => env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), "tenants/ana/catalog/main"), data));

describe("tenants/{t}/catalog/main", () => {
  test("el dueño crea su catálogo con versión 1", () =>
    assertSucceeds(setDoc(doc(owner(), "tenants/ana/catalog/main"), catalog(1))));

  test("otro usuario no puede escribir el catálogo de la barbería", () =>
    assertFails(setDoc(doc(client(), "tenants/ana/catalog/main"), catalog(1))));

  test("solo existe el documento main", () =>
    assertFails(setDoc(doc(owner(), "tenants/ana/catalog/otro"), catalog(1))));

  test("C9 cada guardado sube la versión en 1 (detecta ediciones simultáneas)", async () => {
    await seed({ ...catalog(3), updatedAt: new Date() });
    await assertSucceeds(setDoc(doc(owner(), "tenants/ana/catalog/main"), catalog(4)));
  });

  test("C9 no se puede guardar sobre una versión vieja", async () => {
    await seed({ ...catalog(3), updatedAt: new Date() });
    await assertFails(setDoc(doc(owner(), "tenants/ana/catalog/main"), catalog(3)));
    await assertFails(setDoc(doc(owner(), "tenants/ana/catalog/main"), catalog(5)));
  });

  test("C8 máximo 50 servicios", () =>
    assertFails(setDoc(doc(owner(), "tenants/ana/catalog/main"),
      catalog(1, { services: Array.from({ length: 51 }, (_, i) => service(i)) }))));

  test("campos extra no se permiten", () =>
    assertFails(setDoc(doc(owner(), "tenants/ana/catalog/main"), catalog(1, { hack: true }))));

  test("los clientes pueden leerlo (para reservar) y sin sesión no", async () => {
    await seed({ ...catalog(1), updatedAt: new Date() });
    await assertSucceeds(getDoc(doc(client(), "tenants/ana/catalog/main")));
    await assertFails(getDoc(doc(anon(), "tenants/ana/catalog/main")));
  });

  test("no se puede listar ni borrar", async () => {
    await seed({ ...catalog(1), updatedAt: new Date() });
    await assertFails(getDocs(collection(client(), "tenants/ana/catalog")));
    await assertFails(deleteDoc(doc(owner(), "tenants/ana/catalog/main")));
  });
});
