// N1: el personal agenda a un cliente sin cuenta (por teléfono o en el local).
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, deleteField, doc, getDoc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc, writeBatch } from "firebase/firestore";

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

async function world() {
  await seed("users/ana", profile({ staffTenantId: "ana", staffRole: "OWNER" }));
  await seed("users/luis", profile({ staffTenantId: "ana", staffRole: "BARBER", staffBarberId: "b1", staffInviteCode: "INV234" }));
  await seed("users/cli", profile());
}

/** Cita que empieza [offsetMin] minutos desde ahora (en UTC, redondeada a 5 min). */
function walkIn(offsetMin, extra = {}) {
  const t = new Date(Date.now() + offsetMin * 60_000);
  t.setUTCSeconds(0, 0);
  t.setUTCMinutes(t.getUTCMinutes() - (t.getUTCMinutes() % 5));
  const date = t.toISOString().slice(0, 10);
  const start = t.getUTCHours() * 60 + t.getUTCMinutes();
  const keys = [start, start + 5, start + 10].map(String);
  return {
    clientUid: null, clientName: "Pedro", clientPhone: "5512345678", barberId: "b1", barberName: "Luis",
    serviceId: "s1", serviceName: "Corte", priceCents: 15000, date, startMinute: start, endMinute: start + 15,
    startsAt: Timestamp.fromDate(t), status: "BOOKED", slotKeys: keys, dayId: `b1_${date}`, createdAt: serverTimestamp(), ...extra,
  };
}

function book(uid, id, data) {
  const d = db(uid);
  const b = writeBatch(d);
  b.set(doc(d, `tenants/ana/appointments/${id}`), data);
  b.set(doc(d, `tenants/ana/days/${data.dayId}`), {
    booked: Object.fromEntries(data.slotKeys.map((k) => [k, id])), lastOp: id, updatedAt: serverTimestamp(),
  });
  return b.commit();
}

describe("N1 agendar sin cuenta", () => {
  test("el dueño agenda a un cliente que llamó", async () => {
    await world();
    await assertSucceeds(book("ana", "w1", walkIn(120)));
  });

  test("el barbero agenda en su propia agenda, no en la de otro", async () => {
    await world();
    await assertSucceeds(book("luis", "w1", walkIn(120)));
    await assertFails(book("luis", "w2", walkIn(240, { barberId: "b2", dayId: walkIn(240).dayId.replace("b1", "b2") })));
  });

  test("un cliente no puede crear citas sin cuenta (esquivaría penalización y límite)", async () => {
    await world();
    await assertFails(book("cli", "w1", walkIn(120)));
  });

  test("el que llega al local entra en el turno que empezó hace unos minutos", async () => {
    await world();
    await assertSucceeds(book("ana", "w1", walkIn(-10)));
    await assertFails(book("ana", "w2", walkIn(-40)));
  });

  test("nombre obligatorio y teléfono corto", async () => {
    await world();
    await assertFails(book("ana", "w1", walkIn(120, { clientName: "" })));
    await assertFails(book("ana", "w2", walkIn(180, { clientPhone: "1".repeat(21) })));
    await assertSucceeds(book("ana", "w3", walkIn(240, { clientPhone: null })));
  });

  test("una cita de cliente no lleva teléfono ajeno", async () => {
    await world();
    await assertFails(book("cli", "a1", walkIn(120, { clientUid: "cli" })));
    const { clientPhone, ...own } = walkIn(120, { clientUid: "cli" });
    await assertSucceeds(book("cli", "a1", own));
  });
});

describe("N1 atender, no-show y cancelar sin cuenta", () => {
  async function seeded(offsetMin) {
    await world();
    const data = { ...walkIn(offsetMin), createdAt: new Date() };
    await seed("tenants/ana/appointments/w1", data);
    await seed(`tenants/ana/days/${data.dayId}`, { booked: Object.fromEntries(data.slotKeys.map((k) => [k, "w1"])), lastOp: "w1", updatedAt: new Date() });
    return data;
  }

  test("se marca atendida sin ficha de cliente", async () => {
    await seeded(-5);
    await assertSucceeds(updateDoc(doc(db("luis"), "tenants/ana/appointments/w1"), { status: "COMPLETED", updatedAt: serverTimestamp() }));
  });

  test("se marca no-show sin ficha y sin penalizar a nadie", async () => {
    await seeded(-15);
    await assertSucceeds(updateDoc(doc(db("ana"), "tenants/ana/appointments/w1"), { status: "NO_SHOW", updatedAt: serverTimestamp() }));
    await env.withSecurityRulesDisabled(async (ctx) => {
      const fichas = await getDocs(collection(ctx.firestore(), "tenants/ana/clients"));
      if (!fichas.empty) throw new Error("no debe existir ficha");
    });
  });

  test("el personal la cancela antes de la hora y libera los bloques", async () => {
    const data = await seeded(120);
    const d = db("ana");
    const b = writeBatch(d);
    b.update(doc(d, "tenants/ana/appointments/w1"), { status: "CANCELLED", updatedAt: serverTimestamp() });
    b.update(doc(d, `tenants/ana/days/${data.dayId}`), {
      ...Object.fromEntries(data.slotKeys.map((k) => [`booked.${k}`, deleteField()])), lastOp: "w1", updatedAt: serverTimestamp(),
    });
    await assertSucceeds(b.commit());
  });

  test("un cliente no ve ni toca citas sin cuenta", async () => {
    await seeded(120);
    await assertFails(getDoc(doc(db("cli"), "tenants/ana/appointments/w1")));
  });
});
