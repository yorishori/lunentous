import type { FastifyInstance } from "fastify";
import { db } from "../db/client.js";
import { parseBoolFlag } from "../schemas/common.js";
import { daysBetween, todayLocalDate } from "../lib/dates.js";

interface ReminderStateRow {
  due_date: string | null;
}

export function registerReminderStateRoutes(fastify: FastifyInstance): void {
  // Used by the Android on-device polling job (spec §4.3).
  fastify.get("/api/reminder-states", async (request) => {
    const query = request.query as { due_before_or_on?: string; notified?: string };
    const conditions: string[] = [];
    const params: (string | number)[] = [];

    if (query.due_before_or_on) {
      conditions.push("rs.due_date <= ?");
      params.push(query.due_before_or_on);
    }
    const notified = parseBoolFlag(query.notified);
    if (notified !== null) {
      conditions.push("rs.notified = ?");
      params.push(notified ? 1 : 0);
    }

    const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
    const today = todayLocalDate();
    const rows = db
      .prepare(
        `SELECT rs.*, p.name as plant_name, rt.name as reminder_type_name
         FROM reminder_states rs
         JOIN plants p ON p.id = rs.plant_id
         JOIN reminder_types rt ON rt.id = rs.reminder_type_id
         ${where}`
      )
      .all(...params) as Array<ReminderStateRow & Record<string, unknown>>;

    return rows.map((r) => ({ ...r, days_overdue: r.due_date ? daysBetween(r.due_date, today) : null }));
  });

  fastify.get("/api/plants/:plantId/reminder-states", async (request) => {
    const { plantId } = request.params as { plantId: string };
    const today = todayLocalDate();
    const rows = db
      .prepare(
        `SELECT rs.*, rt.name as reminder_type_name, rt.color as reminder_type_color
         FROM reminder_states rs JOIN reminder_types rt ON rt.id = rs.reminder_type_id
         WHERE rs.plant_id = ?`
      )
      .all(plantId) as Array<ReminderStateRow & Record<string, unknown>>;

    return rows.map((r) => ({ ...r, days_overdue: r.due_date ? daysBetween(r.due_date, today) : null }));
  });

  fastify.post("/api/reminder-states/:id/mark-notified", async (request, reply) => {
    const { id } = request.params as { id: string };
    const result = db.prepare("UPDATE reminder_states SET notified = 1 WHERE id = ?").run(id);
    if (result.changes === 0) return reply.code(404).send({ error: "not found" });
    return db.prepare("SELECT * FROM reminder_states WHERE id = ?").get(id);
  });
}
