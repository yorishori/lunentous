import path from "node:path";
import fs from "node:fs";
import crypto from "node:crypto";
import { pipeline } from "node:stream/promises";
import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { db, photosDir } from "../db/client.js";
import { parseBoolFlag } from "../schemas/common.js";
import { dateInRange, daysBetween, todayLocalDate } from "../lib/dates.js";

interface PlantRow {
  id: number;
  name: string;
  species: string | null;
  location: string | null;
  acquired_date: string | null;
  avatar_photo_id: number | null;
  avatar_photo_path: string | null;
  general_notes: string | null;
  archived: number;
}

// Joins in the avatar photo's file path so the frontend can render it
// directly without a second round-trip per plant.
const PLANT_SELECT = `SELECT plants.*, photos.file_path as avatar_photo_path
  FROM plants LEFT JOIN photos ON photos.id = plants.avatar_photo_id`;

const createPlantSchema = z.object({
  name: z.string().min(1),
  species: z.string().nullable().optional(),
  location: z.string().nullable().optional(),
  acquired_date: z.string().nullable().optional(),
  general_notes: z.string().nullable().optional(),
});
const updatePlantSchema = createPlantSchema.partial();

export function registerPlantRoutes(fastify: FastifyInstance): void {
  fastify.get("/api/plants", async (request) => {
    const query = request.query as { archived?: string };
    const archived = parseBoolFlag(query.archived);
    return archived === null
      ? db.prepare(`${PLANT_SELECT} ORDER BY plants.name`).all()
      : db.prepare(`${PLANT_SELECT} WHERE plants.archived = ? ORDER BY plants.name`).all(archived ? 1 : 0);
  });

  fastify.post("/api/plants", async (request, reply) => {
    const body = createPlantSchema.parse(request.body);
    const result = db
      .prepare(
        "INSERT INTO plants (name, species, location, acquired_date, general_notes) VALUES (?, ?, ?, ?, ?)"
      )
      .run(body.name, body.species ?? null, body.location ?? null, body.acquired_date ?? null, body.general_notes ?? null);
    return reply.code(201).send(db.prepare(`${PLANT_SELECT} WHERE plants.id = ?`).get(result.lastInsertRowid));
  });

  fastify.get("/api/plants/:id", async (request, reply) => {
    const { id } = request.params as { id: string };
    const plant = db.prepare(`${PLANT_SELECT} WHERE plants.id = ?`).get(id) as PlantRow | undefined;
    if (!plant) return reply.code(404).send({ error: "not found" });

    const today = todayLocalDate();

    const phaseWindows = db
      .prepare(
        `SELECT pw.*, pt.name as phase_type_name, pt.color as phase_type_color
         FROM plant_phase_windows pw JOIN phase_types pt ON pt.id = pw.phase_type_id
         WHERE pw.plant_id = ?`
      )
      .all(id) as Array<{ start_month: number; start_day: number; end_month: number; end_day: number }>;
    const activePhaseWindows = phaseWindows.filter((w) =>
      dateInRange(today, w.start_month, w.start_day, w.end_month, w.end_day)
    );

    const reminderStates = (
      db
        .prepare(
          `SELECT rs.*, rt.name as reminder_type_name, rt.color as reminder_type_color
           FROM reminder_states rs JOIN reminder_types rt ON rt.id = rs.reminder_type_id
           WHERE rs.plant_id = ?`
        )
        .all(id) as Array<{ due_date: string | null }>
    ).map((rs) => ({ ...rs, days_overdue: rs.due_date ? daysBetween(rs.due_date, today) : null }));

    return { ...plant, active_phase_windows: activePhaseWindows, reminder_states: reminderStates };
  });

  fastify.patch("/api/plants/:id", async (request, reply) => {
    const { id } = request.params as { id: string };
    const existing = db.prepare("SELECT * FROM plants WHERE id = ?").get(id) as PlantRow | undefined;
    if (!existing) return reply.code(404).send({ error: "not found" });

    const body = updatePlantSchema.parse(request.body);
    const merged = { ...existing, ...body };
    db.prepare(
      `UPDATE plants SET name = ?, species = ?, location = ?, acquired_date = ?, general_notes = ?,
       updated_at = CURRENT_TIMESTAMP WHERE id = ?`
    ).run(merged.name, merged.species, merged.location, merged.acquired_date, merged.general_notes, id);
    return db.prepare(`${PLANT_SELECT} WHERE plants.id = ?`).get(id);
  });

  for (const [suffix, archivedValue] of [
    ["archive", 1],
    ["unarchive", 0],
  ] as const) {
    fastify.post(`/api/plants/:id/${suffix}`, async (request, reply) => {
      const { id } = request.params as { id: string };
      const result = db.prepare("UPDATE plants SET archived = ? WHERE id = ?").run(archivedValue, id);
      if (result.changes === 0) return reply.code(404).send({ error: "not found" });
      return db.prepare(`${PLANT_SELECT} WHERE plants.id = ?`).get(id);
    });
  }

  fastify.post("/api/plants/:id/avatar", async (request, reply) => {
    const { id } = request.params as { id: string };
    const plant = db.prepare("SELECT id FROM plants WHERE id = ?").get(id);
    if (!plant) return reply.code(404).send({ error: "not found" });

    const file = await request.file();
    if (!file) return reply.code(400).send({ error: "no file uploaded" });

    const ext = path.extname(file.filename) || "";
    const filename = `${crypto.randomUUID()}${ext}`;
    await pipeline(file.file, fs.createWriteStream(path.join(photosDir, filename)));

    const result = db
      .prepare("INSERT INTO photos (plant_id, timeline_event_id, file_path) VALUES (?, NULL, ?)")
      .run(id, filename);
    db.prepare("UPDATE plants SET avatar_photo_id = ? WHERE id = ?").run(result.lastInsertRowid, id);

    return db.prepare(`${PLANT_SELECT} WHERE plants.id = ?`).get(id);
  });
}
