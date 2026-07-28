import type { FastifyInstance } from "fastify";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { buildApp } from "../src/app.js";
import { todayLocalDate } from "../src/lib/dates.js";
import { authHeader, createApiKey, resetDb } from "./helpers.js";

let app: FastifyInstance;
let token: string;

beforeAll(async () => {
  app = await buildApp();
});

afterAll(async () => {
  await app?.close();
});

beforeEach(async () => {
  resetDb();
  token = createApiKey();
});

async function getApp(): Promise<FastifyInstance> {
  return app;
}

describe("auth", () => {
  it("allows the health check with no token", async () => {
    const app = await getApp();
    const res = await app.inject({ method: "GET", url: "/api/health" });
    expect(res.statusCode).toBe(200);
  });

  it("rejects an API request with no token", async () => {
    const app = await getApp();
    const res = await app.inject({ method: "GET", url: "/api/plants" });
    expect(res.statusCode).toBe(401);
  });

  it("rejects an API request with a bogus token", async () => {
    const app = await getApp();
    const res = await app.inject({ method: "GET", url: "/api/plants", headers: authHeader("not-a-real-token") });
    expect(res.statusCode).toBe(401);
  });

  it("accepts a request with a valid token", async () => {
    const app = await getApp();
    const res = await app.inject({ method: "GET", url: "/api/plants", headers: authHeader(token) });
    expect(res.statusCode).toBe(200);
  });

  it("serves the SPA fallback for a non-API route with no auth", async () => {
    const app = await getApp();
    const res = await app.inject({ method: "GET", url: "/dashboard" });
    expect(res.statusCode).toBe(200);
  });
});

describe("plants", () => {
  it("creates and then fetches a plant", async () => {
    const app = await getApp();
    const create = await app.inject({
      method: "POST",
      url: "/api/plants",
      headers: authHeader(token),
      payload: { name: "Monstera" },
    });
    expect(create.statusCode).toBe(201);
    const created = create.json();
    expect(created.name).toBe("Monstera");

    const get = await app.inject({ method: "GET", url: `/api/plants/${created.id}`, headers: authHeader(token) });
    expect(get.statusCode).toBe(200);
    expect(get.json().name).toBe("Monstera");
  });

  it("404s for a plant that doesn't exist", async () => {
    const app = await getApp();
    const res = await app.inject({ method: "GET", url: "/api/plants/999999", headers: authHeader(token) });
    expect(res.statusCode).toBe(404);
  });

  it("rejects a plant with no name", async () => {
    const app = await getApp();
    const res = await app.inject({
      method: "POST",
      url: "/api/plants",
      headers: authHeader(token),
      payload: {},
    });
    expect(res.statusCode).toBe(400);
  });
});

describe("reminder rules", () => {
  async function createPlantAndType(app: FastifyInstance) {
    const plant = (
      await app.inject({ method: "POST", url: "/api/plants", headers: authHeader(token), payload: { name: "P" } })
    ).json();
    const type = (
      await app.inject({
        method: "POST",
        url: "/api/reminder-types",
        headers: authHeader(token),
        payload: { name: "Watering" },
      })
    ).json();
    return { plantId: plant.id, typeId: type.id };
  }

  it("a new interval rule is immediately due today", async () => {
    const app = await getApp();
    const { plantId, typeId } = await createPlantAndType(app);

    const create = await app.inject({
      method: "POST",
      url: `/api/plants/${plantId}/reminder-rules`,
      headers: authHeader(token),
      payload: { reminder_type_id: typeId, default_interval_days: 3 },
    });
    expect(create.statusCode).toBe(201);

    const states = await app.inject({ method: "GET", url: "/api/reminder-states", headers: authHeader(token) });
    const state = states.json().find((s: { plant_id: number }) => s.plant_id === plantId);
    expect(state.due_date).toBe(todayLocalDate());
  });

  it("rejects an annual rule combined with a default interval", async () => {
    const app = await getApp();
    const { plantId, typeId } = await createPlantAndType(app);

    const res = await app.inject({
      method: "POST",
      url: `/api/plants/${plantId}/reminder-rules`,
      headers: authHeader(token),
      payload: { reminder_type_id: typeId, default_interval_days: 5, annual_month: 6, annual_day: 15 },
    });
    expect(res.statusCode).toBe(400);
  });

  it("rejects a duplicate rule for the same plant/reminder type", async () => {
    const app = await getApp();
    const { plantId, typeId } = await createPlantAndType(app);

    await app.inject({
      method: "POST",
      url: `/api/plants/${plantId}/reminder-rules`,
      headers: authHeader(token),
      payload: { reminder_type_id: typeId, default_interval_days: 3 },
    });
    const dup = await app.inject({
      method: "POST",
      url: `/api/plants/${plantId}/reminder-rules`,
      headers: authHeader(token),
      payload: { reminder_type_id: typeId, default_interval_days: 5 },
    });
    expect(dup.statusCode).toBe(409);
  });
});

describe("one-time reminders", () => {
  it("creates, completes, and lists a one-time reminder across plants", async () => {
    const app = await getApp();
    const plant = (
      await app.inject({ method: "POST", url: "/api/plants", headers: authHeader(token), payload: { name: "P" } })
    ).json();

    const create = await app.inject({
      method: "POST",
      url: `/api/plants/${plant.id}/one-time-reminders`,
      headers: authHeader(token),
      payload: { due_date: "2026-09-25", text: "Give this plant to a friend" },
    });
    expect(create.statusCode).toBe(201);
    const reminder = create.json();
    expect(reminder.completed_at).toBeNull();

    const listBeforeComplete = await app.inject({
      method: "GET",
      url: "/api/one-time-reminders?completed=false",
      headers: authHeader(token),
    });
    expect(listBeforeComplete.json()).toHaveLength(1);

    const complete = await app.inject({
      method: "PATCH",
      url: `/api/one-time-reminders/${reminder.id}`,
      headers: authHeader(token),
      payload: { completed_at: new Date().toISOString() },
    });
    expect(complete.statusCode).toBe(200);
    expect(complete.json().completed_at).not.toBeNull();

    const listAfterComplete = await app.inject({
      method: "GET",
      url: "/api/one-time-reminders?completed=false",
      headers: authHeader(token),
    });
    expect(listAfterComplete.json()).toHaveLength(0);
  });

  it("deletes a one-time reminder", async () => {
    const app = await getApp();
    const plant = (
      await app.inject({ method: "POST", url: "/api/plants", headers: authHeader(token), payload: { name: "P" } })
    ).json();
    const reminder = (
      await app.inject({
        method: "POST",
        url: `/api/plants/${plant.id}/one-time-reminders`,
        headers: authHeader(token),
        payload: { due_date: "2026-09-25", text: "Buy a new pot" },
      })
    ).json();

    const del = await app.inject({
      method: "DELETE",
      url: `/api/one-time-reminders/${reminder.id}`,
      headers: authHeader(token),
    });
    expect(del.statusCode).toBe(204);

    const get = await app.inject({
      method: "GET",
      url: `/api/plants/${plant.id}/one-time-reminders`,
      headers: authHeader(token),
    });
    expect(get.json()).toHaveLength(0);
  });
});
