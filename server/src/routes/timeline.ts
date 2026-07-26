import path from "node:path";
import fs from "node:fs";
import crypto from "node:crypto";
import { pipeline } from "node:stream/promises";
import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { db, photosDir } from "../db/client.js";
import { recomputeReminderState } from "../lib/recompute.js";

interface TimelineEventRow {
  id: number;
  plant_id: number;
  reminder_type_id: number | null;
  event_date: string;
  text: string | null;
  created_at: string;
}

interface PhotoRow {
  id: number;
  timeline_event_id: number;
  file_path: string;
}

function attachPhotos(events: TimelineEventRow[]) {
  if (events.length === 0) return events.map((e) => ({ ...e, photos: [] }));
  const placeholders = events.map(() => "?").join(",");
  const photos = db
    .prepare(`SELECT * FROM photos WHERE timeline_event_id IN (${placeholders})`)
    .all(...events.map((e) => e.id)) as PhotoRow[];
  const byEvent = new Map<number, PhotoRow[]>();
  for (const p of photos) {
    const list = byEvent.get(p.timeline_event_id) ?? [];
    list.push(p);
    byEvent.set(p.timeline_event_id, list);
  }
  return events.map((e) => ({ ...e, photos: byEvent.get(e.id) ?? [] }));
}

function getTimelineEventWithPhotos(id: number) {
  const event = db.prepare("SELECT * FROM timeline_events WHERE id = ?").get(id) as TimelineEventRow | undefined;
  if (!event) return null;
  return attachPhotos([event])[0];
}

const updateTimelineSchema = z.object({
  event_date: z.string().optional(),
  reminder_type_id: z.number().int().nullable().optional(),
  text: z.string().nullable().optional(),
});

export function registerTimelineRoutes(fastify: FastifyInstance): void {
  fastify.get("/api/plants/:plantId/timeline", async (request) => {
    const { plantId } = request.params as { plantId: string };
    const query = request.query as { reminder_type_id?: string; limit?: string; before?: string };
    const limit = query.limit ? Math.max(1, Math.min(100, parseInt(query.limit, 10))) : 20;

    const conditions = ["plant_id = ?"];
    const params: (string | number)[] = [plantId];

    if (query.reminder_type_id) {
      conditions.push("reminder_type_id = ?");
      params.push(query.reminder_type_id);
    }

    // `before` is a timeline event id cursor: returns events strictly older
    // than that event (by event_date, then id, for same-day tie-breaking).
    if (query.before) {
      const cursor = db.prepare("SELECT event_date, id FROM timeline_events WHERE id = ?").get(query.before) as
        | { event_date: string; id: number }
        | undefined;
      if (cursor) {
        conditions.push("(event_date < ? OR (event_date = ? AND id < ?))");
        params.push(cursor.event_date, cursor.event_date, cursor.id);
      }
    }

    const rows = db
      .prepare(
        `SELECT * FROM timeline_events WHERE ${conditions.join(" AND ")} ORDER BY event_date DESC, id DESC LIMIT ?`
      )
      .all(...params, limit) as TimelineEventRow[];

    return attachPhotos(rows);
  });

  fastify.post("/api/plants/:plantId/timeline", async (request, reply) => {
    const { plantId } = request.params as { plantId: string };
    const parts = request.parts();

    let eventDate: string | undefined;
    let reminderTypeId: number | null = null;
    let text: string | null = null;
    const savedFilenames: string[] = [];

    for await (const part of parts) {
      if (part.type === "file") {
        const ext = path.extname(part.filename) || "";
        const filename = `${crypto.randomUUID()}${ext}`;
        await pipeline(part.file, fs.createWriteStream(path.join(photosDir, filename)));
        savedFilenames.push(filename);
      } else if (part.fieldname === "event_date") {
        eventDate = String(part.value);
      } else if (part.fieldname === "reminder_type_id") {
        reminderTypeId = part.value ? Number(part.value) : null;
      } else if (part.fieldname === "text") {
        text = part.value ? String(part.value) : null;
      }
    }

    if (!eventDate) return reply.code(400).send({ error: "event_date is required" });

    const result = db
      .prepare("INSERT INTO timeline_events (plant_id, reminder_type_id, event_date, text) VALUES (?, ?, ?, ?)")
      .run(plantId, reminderTypeId, eventDate, text);
    const eventId = Number(result.lastInsertRowid);

    const insertPhoto = db.prepare("INSERT INTO photos (plant_id, timeline_event_id, file_path) VALUES (?, ?, ?)");
    for (const filename of savedFilenames) {
      insertPhoto.run(plantId, eventId, filename);
    }

    if (reminderTypeId) recomputeReminderState(Number(plantId), reminderTypeId);

    return reply.code(201).send(getTimelineEventWithPhotos(eventId));
  });

  fastify.patch("/api/timeline/:id", async (request, reply) => {
    const { id } = request.params as { id: string };
    const existing = db.prepare("SELECT * FROM timeline_events WHERE id = ?").get(id) as
      | TimelineEventRow
      | undefined;
    if (!existing) return reply.code(404).send({ error: "not found" });

    const body = updateTimelineSchema.parse(request.body);
    const newEventDate = body.event_date ?? existing.event_date;
    const newReminderTypeId = body.reminder_type_id !== undefined ? body.reminder_type_id : existing.reminder_type_id;
    const newText = body.text !== undefined ? body.text : existing.text;

    db.prepare("UPDATE timeline_events SET event_date = ?, reminder_type_id = ?, text = ? WHERE id = ?").run(
      newEventDate,
      newReminderTypeId,
      newText,
      id
    );

    const changed = newEventDate !== existing.event_date || newReminderTypeId !== existing.reminder_type_id;
    if (changed) {
      if (existing.reminder_type_id) recomputeReminderState(existing.plant_id, existing.reminder_type_id);
      if (newReminderTypeId && newReminderTypeId !== existing.reminder_type_id) {
        recomputeReminderState(existing.plant_id, newReminderTypeId);
      }
    }

    return getTimelineEventWithPhotos(Number(id));
  });

  fastify.delete("/api/timeline/:id", async (request, reply) => {
    const { id } = request.params as { id: string };
    const existing = db.prepare("SELECT * FROM timeline_events WHERE id = ?").get(id) as
      | TimelineEventRow
      | undefined;
    if (!existing) return reply.code(404).send({ error: "not found" });

    const photos = db.prepare("SELECT file_path FROM photos WHERE timeline_event_id = ?").all(id) as Array<{
      file_path: string;
    }>;
    db.prepare("DELETE FROM timeline_events WHERE id = ?").run(id); // cascades photos rows
    for (const p of photos) {
      fs.unlink(path.join(photosDir, p.file_path), () => {});
    }

    if (existing.reminder_type_id) recomputeReminderState(existing.plant_id, existing.reminder_type_id);
    return reply.code(204).send();
  });
}
