import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { db } from "../db/client.js";
import { parseBoolFlag } from "../schemas/common.js";
import { daysBetween, todayLocalDate } from "../lib/dates.js";

interface OneTimeReminderRow {
  id: number;
  plant_id: number;
  due_date: string;
  text: string;
  completed_at: string | null;
  created_at: string;
}

const createSchema = z.object({
  due_date: z.string(),
  text: z.string().min(1),
});

const updateSchema = z.object({
  due_date: z.string().optional(),
  text: z.string().min(1).optional(),
  // Send an ISO timestamp to mark complete, or null to un-complete --
  // kept rather than deleted once completed, per product decision.
  completed_at: z.string().nullable().optional(),
});

/**
 * Per-plant, untyped, informational reminders -- no reminder type, and
 * completing one never touches timeline_events or reminder_states (see
 * schema.sql's comment on one_time_reminders). Mixed into the Dashboard's
 * overdue/due-soon lists and the Care Timeline alongside regular
 * reminders on both clients.
 */
export function registerOneTimeReminderRoutes(fastify: FastifyInstance): void {
  // Across every plant -- used by the Dashboard, mirroring
  // GET /api/reminder-states' own days_overdue computation.
  fastify.get("/api/one-time-reminders", async (request) => {
    const query = request.query as { completed?: string };
    const conditions: string[] = [];
    const completed = parseBoolFlag(query.completed);
    if (completed !== null) {
      conditions.push(completed ? "otr.completed_at IS NOT NULL" : "otr.completed_at IS NULL");
    }
    const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
    const today = todayLocalDate();
    const rows = db
      .prepare(`SELECT otr.*, p.name as plant_name FROM one_time_reminders otr JOIN plants p ON p.id = otr.plant_id ${where}`)
      .all() as Array<OneTimeReminderRow & { plant_name: string }>;

    return rows.map((r) => ({ ...r, days_overdue: daysBetween(r.due_date, today) }));
  });

  fastify.get("/api/plants/:plantId/one-time-reminders", async (request) => {
    const { plantId } = request.params as { plantId: string };
    return db
      .prepare("SELECT * FROM one_time_reminders WHERE plant_id = ? ORDER BY due_date")
      .all(plantId) as OneTimeReminderRow[];
  });

  fastify.post("/api/plants/:plantId/one-time-reminders", async (request, reply) => {
    const { plantId } = request.params as { plantId: string };
    const body = createSchema.parse(request.body);

    const result = db
      .prepare("INSERT INTO one_time_reminders (plant_id, due_date, text) VALUES (?, ?, ?)")
      .run(plantId, body.due_date, body.text);

    return reply.code(201).send(db.prepare("SELECT * FROM one_time_reminders WHERE id = ?").get(result.lastInsertRowid));
  });

  fastify.patch("/api/one-time-reminders/:id", async (request, reply) => {
    const { id } = request.params as { id: string };
    const existing = db.prepare("SELECT * FROM one_time_reminders WHERE id = ?").get(id) as OneTimeReminderRow | undefined;
    if (!existing) return reply.code(404).send({ error: "not found" });

    const body = updateSchema.parse(request.body);
    const merged = {
      due_date: body.due_date ?? existing.due_date,
      text: body.text ?? existing.text,
      completed_at: body.completed_at !== undefined ? body.completed_at : existing.completed_at,
    };

    db.prepare("UPDATE one_time_reminders SET due_date = ?, text = ?, completed_at = ? WHERE id = ?").run(
      merged.due_date,
      merged.text,
      merged.completed_at,
      id
    );

    return db.prepare("SELECT * FROM one_time_reminders WHERE id = ?").get(id);
  });

  fastify.delete("/api/one-time-reminders/:id", async (request, reply) => {
    const { id } = request.params as { id: string };
    const result = db.prepare("DELETE FROM one_time_reminders WHERE id = ?").run(id);
    if (result.changes === 0) return reply.code(404).send({ error: "not found" });
    return reply.code(204).send();
  });
}
