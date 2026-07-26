import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { db } from "../db/client.js";
import { parseBoolFlag } from "../schemas/common.js";

interface ArchivableTypeOptions {
  /** e.g. "/api/reminder-types" */
  path: string;
  /** e.g. "reminder_types" -- table names here are fixed config, never user input. */
  table: "reminder_types" | "phase_types";
  /** Table that references this type, for computing usage_count. */
  usageTable: "reminder_rules" | "plant_phase_windows";
  usageColumn: "reminder_type_id" | "phase_type_id";
  hasIcon: boolean;
}

interface TypeRow {
  id: number;
  name: string;
  icon?: string | null;
  color: string | null;
  archived: number;
  created_at: string;
}

/** reminder_types and phase_types are identical shape (spec §5: "Phase Types
 * -- same shape as Reminder Types") aside from `icon`, so both are served by
 * one CRUD+archive implementation parameterized by table/usage config. */
export function registerArchivableTypeRoutes(fastify: FastifyInstance, opts: ArchivableTypeOptions): void {
  const { path, table, usageTable, usageColumn, hasIcon } = opts;

  const createSchema = z.object({
    name: z.string().min(1),
    icon: hasIcon ? z.string().optional() : z.undefined(),
    color: z.string().optional(),
  });
  const updateSchema = createSchema.partial();

  fastify.get(path, async (request) => {
    const query = request.query as { archived?: string };
    const archived = parseBoolFlag(query.archived);

    const rows = (
      archived === null
        ? db.prepare(`SELECT * FROM ${table} ORDER BY name`).all()
        : db.prepare(`SELECT * FROM ${table} WHERE archived = ? ORDER BY name`).all(archived ? 1 : 0)
    ) as TypeRow[];

    const usageStmt = db.prepare(`SELECT COUNT(*) as count FROM ${usageTable} WHERE ${usageColumn} = ?`);
    return rows.map((row) => ({
      ...row,
      usage_count: (usageStmt.get(row.id) as { count: number }).count,
    }));
  });

  fastify.post(path, async (request, reply) => {
    const body = createSchema.parse(request.body);
    const result = hasIcon
      ? db
          .prepare(`INSERT INTO ${table} (name, icon, color) VALUES (?, ?, ?)`)
          .run(body.name, body.icon ?? null, body.color ?? null)
      : db.prepare(`INSERT INTO ${table} (name, color) VALUES (?, ?)`).run(body.name, body.color ?? null);
    return reply.code(201).send(db.prepare(`SELECT * FROM ${table} WHERE id = ?`).get(result.lastInsertRowid));
  });

  fastify.patch(`${path}/:id`, async (request, reply) => {
    const { id } = request.params as { id: string };
    const existing = db.prepare(`SELECT * FROM ${table} WHERE id = ?`).get(id) as TypeRow | undefined;
    if (!existing) return reply.code(404).send({ error: "not found" });

    const body = updateSchema.parse(request.body);
    const merged = { ...existing, ...body };
    if (hasIcon) {
      db.prepare(`UPDATE ${table} SET name = ?, icon = ?, color = ? WHERE id = ?`).run(
        merged.name,
        merged.icon ?? null,
        merged.color ?? null,
        id
      );
    } else {
      db.prepare(`UPDATE ${table} SET name = ?, color = ? WHERE id = ?`).run(merged.name, merged.color ?? null, id);
    }
    return db.prepare(`SELECT * FROM ${table} WHERE id = ?`).get(id);
  });

  for (const [suffix, archivedValue] of [
    ["archive", 1],
    ["unarchive", 0],
  ] as const) {
    fastify.post(`${path}/:id/${suffix}`, async (request, reply) => {
      const { id } = request.params as { id: string };
      const result = db.prepare(`UPDATE ${table} SET archived = ? WHERE id = ?`).run(archivedValue, id);
      if (result.changes === 0) return reply.code(404).send({ error: "not found" });
      return db.prepare(`SELECT * FROM ${table} WHERE id = ?`).get(id);
    });
  }
}
