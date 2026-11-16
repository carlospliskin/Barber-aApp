// Reglas de reservas: tenants/{t}/days/{barber_fecha}, tenants/{t}/appointments/{id}, shareCodes/{code}.
// La garantía contra doble reserva vive aquí: un bloque ocupado del mapa `booked` nunca se sobrescribe.
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, doc, getDoc, getDocs, query, serverTimestamp, setDoc, Timestamp, updateDoc, where, writeBatch } from "firebase/firestore";

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
const DAY = "b1_2026-11-20";
const inDays = (d) => Timestamp.fromMillis(Date.now() + d * 86_400_000);
// Fecha de cita fija y su inicio real: 2026-11-20 10:00 CDMX = 16:00 UTC (los tests corren antes de esa fecha).
const STARTS = Timestamp.fromDate(new Date("2026-11-20T16:00:00Z"));

const appt = (uid, keys, extra = {}) => ({
  clientUid: uid, clientName: "Ana", barberId: "b1", barberName: "Luis", serviceId: "s1", serviceName: "Corte",
  priceCents: 15000, date: "2026-11-20", startMinute: Number(keys[0]), endMinute: Number(keys.at(-1)) + 5,
  startsAt: STARTS, status: "BOOKED", slotKeys: keys, dayId: DAY, createdAt: serverTimestamp(), ...extra,
});

/** Reserva como lo hace la app: cita + bloques del día en el mismo batch. */
function book(uid, id, keys, current = {}, apptExtra = {}) {
  const d = db(uid);
  const b = writeBatch(d);
  b.set(doc(d, `tenants/ana/appointments/${id}`), appt(uid, keys, apptExtra));
  const booked = { ...current };
  keys.forEach((k) => (booked[k] = id));
  b.set(doc(d, `tenants/ana/days/${DAY}`), { booked, lastOp: id, updatedAt: serverTimestamp() });
  return b.commit();
}

async function seedBooking(uid, id, keys, startsAt = STARTS) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const f = ctx.firestore();
    await setDoc(doc(f, `tenants/ana/appointments/${id}`), { ...appt(uid, keys), startsAt, createdAt: new Date() });
    const booked = {};
    keys.forEach((k) => (booked[k] = id));
    await setDoc(doc(f, `tenants/ana/days/${DAY}`), { booked, lastOp: id, updatedAt: new Date() });
  });
}

function cancel(uid, id, keys) {
  const d = db(uid);
  const b = writeBatch(d);
  b.update(doc(d, `tenants/ana/appointments/${id}`), { status: "CANCELLED", updatedAt: serverTimestamp() });
  b.update(doc(d, `tenants/ana/days/${DAY}`), { ...Object.fromEntries(keys.map((k) => [`booked.${k}`, deleteField()])), lastOp: id, updatedAt: serverTimestamp() });
  return b.commit();
}
import { deleteField } from "firebase/firestore";

describe("reservar", () => {
  test("un cliente reserva: cita + bloques en el mismo batch", () =>
    assertSucceeds(book("cli", "a1", ["600", "605", "610"])));

  test("B3 no se puede tomar un bloque ya ocupado (doble reserva)", async () => {
    await seedBooking("cli", "a1", ["600", "605", "610"]);
    await assertFails(book("otro", "a2", ["610", "615"], { 600: "a1", 605: "a1" })); // pisa 610
  });

  test("B3 sí se puede reservar al lado", async () => {
    await seedBooking("cli", "a1", ["600", "605", "610"]);
    await assertSucceeds(book("otro", "a2", ["615", "620"], { 600: "a1", 605: "a1", 610: "a1" }));
  });

  test("no se puede reservar a nombre de otro", () =>
    assertFails(book("cli", "a1", ["600"], {}, { clientUid: "victima" })));

  test("los bloques del día deben ser exactamente los de la cita", () => {
    const d = db("cli");
    const b = writeBatch(d);
    b.set(doc(d, "tenants/ana/appointments/a1"), appt("cli", ["600"]));
    b.set(doc(d, `tenants/ana/days/${DAY}`), { booked: { 600: "a1", 605: "a1", 700: "a1" }, lastOp: "a1", updatedAt: serverTimestamp() });
    return assertFails(b.commit());
  });

  test("no se crea una cita sin ocupar sus bloques", () =>
    assertFails(setDoc(doc(db("cli"), "tenants/ana/appointments/a1"), appt("cli", ["600"]))));

  test("no se crea una cita ya cancelada ni con campos extra", async () => {
    await assertFails(book("cli", "a1", ["600"], {}, { status: "COMPLETED" }));
    await assertFails(book("cli", "a2", ["700"], {}, { pagado: true }));
  });
});

describe("abuso saltándose la app", () => {
  test("R1 los bloques deben empezar en la hora de la cita", () =>
    assertFails(book("cli", "a1", ["900", "905"], {}, { startMinute: 600, endMinute: 610 })));

  test("R1 la cantidad de bloques debe corresponder a la duración", () =>
    assertFails(book("cli", "a1", ["600", "605"], {}, { endMinute: 700 })));

  test("R1 una cita no puede durar más de 8 horas (bloquear el día)", () => {
    const keys = Array.from({ length: 97 }, (_, i) => String(480 + i * 5));
    return assertFails(book("cli", "a1", keys));
  });

  test("R3 startsAt debe caer en la fecha de la cita (no se puede inventar para cancelar tarde)", () =>
    assertFails(book("cli", "a1", ["600"], {}, { startsAt: Timestamp.fromDate(new Date("2027-01-01T16:00:00Z")) })));

  test("R2 el precio debe ser un entero no negativo", () =>
    assertFails(book("cli", "a1", ["600"], {}, { priceCents: -100 })));
});

describe("leer", () => {
  test("cualquier cliente ve qué bloques están ocupados (1 lectura por día)", async () => {
    await seedBooking("cli", "a1", ["600"]);
    await assertSucceeds(getDoc(doc(db("otro"), `tenants/ana/days/${DAY}`)));
  });

  test("un cliente ve sus citas pero no las de otros", async () => {
    await seedBooking("cli", "a1", ["600"]);
    await assertSucceeds(getDoc(doc(db("cli"), "tenants/ana/appointments/a1")));
    await assertFails(getDoc(doc(db("otro"), "tenants/ana/appointments/a1")));
    await assertSucceeds(getDocs(query(collection(db("cli"), "tenants/ana/appointments"), where("clientUid", "==", "cli"))));
    await assertFails(getDocs(collection(db("cli"), "tenants/ana/appointments")));
  });

  test("el dueño ve todas las citas de su barbería", async () => {
    await seedBooking("cli", "a1", ["600"]);
    await assertSucceeds(getDocs(collection(db("ana"), "tenants/ana/appointments")));
  });
});

describe("cancelar", () => {
  test("B7 el cliente cancela con tiempo y libera los bloques", async () => {
    await seedBooking("cli", "a1", ["600", "605"]);
    await assertSucceeds(cancel("cli", "a1", ["600", "605"]));
  });

  test("B7 el cliente no cancela con menos de 2 horas (aunque se salte la app)", async () => {
    await seedBooking("cli", "a1", ["600"], Timestamp.fromMillis(Date.now() + 60 * 60_000));
    await assertFails(cancel("cli", "a1", ["600"]));
  });

  test("el dueño sí puede cancelar a última hora", async () => {
    await seedBooking("cli", "a1", ["600"], Timestamp.fromMillis(Date.now() + 60 * 60_000));
    await assertSucceeds(cancel("ana", "a1", ["600"]));
  });

  test("nadie cancela la cita de otro", async () => {
    await seedBooking("cli", "a1", ["600"]);
    await assertFails(cancel("otro", "a1", ["600"]));
  });

  test("al cancelar no se pueden liberar bloques de otra cita", async () => {
    await seedBooking("cli", "a1", ["600"]);
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), `tenants/ana/days/${DAY}`), { booked: { 600: "a1", 700: "a9" }, lastOp: "a1", updatedAt: new Date() }));
    await assertFails(cancel("cli", "a1", ["600", "700"]));
  });
});

describe("shareCodes (B0)", () => {
  test("el dueño crea el código de su barbería", () =>
    assertSucceeds(setDoc(doc(db("ana"), "shareCodes/ABC234"), { tenantId: "ana", createdAt: serverTimestamp() })));

  test("R7 el dueño guarda en su barbería un código que es suyo", async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), "tenants/ana"), { name: "Gold", ownerUid: "ana", timezone: "America/Mexico_City", createdAt: new Date() }));
    const d = db("ana");
    const b = writeBatch(d);
    b.set(doc(d, "shareCodes/ABC234"), { tenantId: "ana", createdAt: serverTimestamp() });
    b.update(doc(d, "tenants/ana"), { shareCode: "ABC234" });
    await assertSucceeds(b.commit());
  });

  test("R7 no puede apuntar su barbería al código de otro", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "tenants/ana"), { name: "Gold", ownerUid: "ana", timezone: "America/Mexico_City", createdAt: new Date() });
      await setDoc(doc(ctx.firestore(), "shareCodes/ZZZ999"), { tenantId: "beto", createdAt: new Date() });
    });
    await assertFails(updateDoc(doc(db("ana"), "tenants/ana"), { shareCode: "ZZZ999" }));
  });

  test("nadie crea un código para la barbería de otro", () =>
    assertFails(setDoc(doc(db("cli"), "shareCodes/ABC234"), { tenantId: "ana", createdAt: serverTimestamp() })));

  test("un código existente no se reemplaza", async () => {
    await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), "shareCodes/ABC234"), { tenantId: "ana", createdAt: new Date() }));
    await assertFails(setDoc(doc(db("beto"), "shareCodes/ABC234"), { tenantId: "beto", createdAt: serverTimestamp() }));
  });

  test("con sesión se resuelve un código, pero no se pueden listar", async () => {
    await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), "shareCodes/ABC234"), { tenantId: "ana", createdAt: new Date() }));
    await assertSucceeds(getDoc(doc(db("cli"), "shareCodes/ABC234")));
    await assertFails(getDocs(collection(db("cli"), "shareCodes")));
  });
});

describe("V2 ventana de reserva en el servidor", () => {
  test("no se reserva a más de 31 días aunque se salte la app", () => {
    const d = db("cli");
    const b = writeBatch(d);
    b.set(doc(d, "tenants/ana/appointments/a1"), appt("cli", ["600"], {
      date: "2027-06-01", dayId: "b1_2027-06-01", startsAt: Timestamp.fromDate(new Date("2027-06-01T16:00:00Z")),
    }));
    b.set(doc(d, "tenants/ana/days/b1_2027-06-01"), { booked: { 600: "a1" }, lastOp: "a1", updatedAt: serverTimestamp() });
    return assertFails(b.commit());
  });
});
