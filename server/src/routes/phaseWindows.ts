import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { db } from "../db/client.js";
import { isValidMonthDay } from "../lib/dates.js";

interface PhaseWindowRow {
  id: number;
  plant_id: number;
  phase_type_id: number;
  start_month: number;
  start_day: number;
  end_month: number;
  end_day: number;
  notes: string | null;
}

const phaseWindowSchema = z.object({
  phase_type_id: z.number().int(),
  start_month: z.number().int(),
  start_day: z.number().int(),
  end_month: z.number().int(),
  end_day: z.number().int(),
  notes: z.string().nullable().optional(),
});
const updatePhaseWindowSchema = phaseWindowSchema.partial();

export function registerPhaseWindowRoutes(fastify: FastifyInstance): void {
  fastify.get("/api/plants/:plantId/phase-windows", async (request) => {
    const { plantId } = request.params as { plantId: string };
    return db
      .prepare(
        `SELECT pw.*, pt.name as phase_type_name, pt.color as phase_type_color
         FROM plant_phase_windows pw JOIN phase_types pt ON pt.id = pw.phase_type_id
         WHERE pw.plant_id = ?`
      )
      .all(plantId);
  });

  fastify.post("/api/plants/:plantId/phase-windows", async (request, reply) => {
    const { plantId } = request.params as { plantId: string };
    const body = phaseWindowSchema.parse(request.body);

    if (!isValidMonthDay(body.start_month, body.start_day) || !isValidMonthDay(body.end_month, body.end_day)) {
      return reply.code(400).send({ error: "month/day out of range" });
    }

    const result = db
      .prepare(
        `INSERT INTO plant_phase_windows (plant_id, phase_type_id, start_month, start_day, end_month, end_day, notes)
         VALUES (?, ?, ?, ?, ?, ?, ?)`
      )
      .run(plantId, body.phase_type_id, body.start_month, body.start_day, body.end_month, body.end_day, body.notes ?? null);

    return reply.code(201).send(db.prepare("SELECT * FROM plant_phase_windows WHERE id = ?").get(result.lastInsertRowid));
  });

  fastify.patch("/api/phase-windows/:id", async (request, reply) => {
    const { id } = request.params as { id: string };
    const existing = db.prepare("SELECT * FROM plant_phase_windows WHERE id = ?").get(id) as
      | PhaseWindowRow
      | undefined;
    if (!existing) return reply.code(404).send({ error: "not found" });

    const body = updatePhaseWindowSchema.parse(request.body);
    const merged = { ...existing, ...body };

    if (!isValidMonthDay(merged.start_month, merged.start_day) || !isValidMonthDay(merged.end_month, merged.end_day)) {
      return reply.code(400).send({ error: "month/day out of range" });
    }

    db.prepare(
      `UPDATE plant_phase_windows SET phase_type_id = ?, start_month = ?, start_day = ?, end_month = ?, end_day = ?, notes = ?
       WHERE id = ?`
    ).run(merged.phase_type_id, merged.start_month, merged.start_day, merged.end_month, merged.end_day, merged.notes ?? null, id);

    return db.prepare("SELECT * FROM plant_phase_windows WHERE id = ?").get(id);
  });

  fastify.delete("/api/phase-windows/:id", async (request, reply) => {
    const { id } = request.params as { id: string };
    const result = db.prepare("DELETE FROM plant_phase_windows WHERE id = ?").run(id);
    if (result.changes === 0) return reply.code(404).send({ error: "not found" });
    return reply.code(204).send();
  });
}
