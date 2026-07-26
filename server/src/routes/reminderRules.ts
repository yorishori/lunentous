import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { db } from "../db/client.js";
import { isValidMonthDay, periodsOverlap, type OverridePeriodLike } from "../lib/dates.js";
import { recomputeReminderState } from "../lib/recompute.js";

interface RuleRow {
  id: number;
  plant_id: number;
  reminder_type_id: number;
  default_interval_days: number | null;
  created_at: string;
}

const overridePeriodSchema = z.object({
  start_month: z.number().int(),
  start_day: z.number().int(),
  end_month: z.number().int(),
  end_day: z.number().int(),
  interval_days: z.number().int().positive().nullable(),
});

const createRuleSchema = z.object({
  reminder_type_id: z.number().int(),
  default_interval_days: z.number().int().positive().nullable().optional(),
  override_periods: z.array(overridePeriodSchema).optional(),
});

const updateRuleSchema = z.object({
  default_interval_days: z.number().int().positive().nullable().optional(),
  override_periods: z.array(overridePeriodSchema).optional(),
});

function validatePeriods(periods: OverridePeriodLike[]): string | null {
  for (const p of periods) {
    if (!isValidMonthDay(p.start_month, p.start_day) || !isValidMonthDay(p.end_month, p.end_day)) {
      return "override period month/day out of range";
    }
  }
  for (let i = 0; i < periods.length; i++) {
    for (let j = i + 1; j < periods.length; j++) {
      if (periodsOverlap(periods[i], periods[j])) {
        return "override periods must not overlap";
      }
    }
  }
  return null;
}

function getRuleWithPeriods(ruleId: number) {
  const rule = db.prepare("SELECT * FROM reminder_rules WHERE id = ?").get(ruleId) as RuleRow | undefined;
  if (!rule) return null;
  const override_periods = db
    .prepare("SELECT * FROM override_periods WHERE reminder_rule_id = ?")
    .all(ruleId);
  return { ...rule, override_periods };
}

export function registerReminderRuleRoutes(fastify: FastifyInstance): void {
  fastify.get("/api/plants/:plantId/reminder-rules", async (request) => {
    const { plantId } = request.params as { plantId: string };
    const rules = db.prepare("SELECT * FROM reminder_rules WHERE plant_id = ?").all(plantId) as RuleRow[];
    return rules.map((rule) => getRuleWithPeriods(rule.id));
  });

  fastify.post("/api/plants/:plantId/reminder-rules", async (request, reply) => {
    const { plantId } = request.params as { plantId: string };
    const body = createRuleSchema.parse(request.body);
    const periods = body.override_periods ?? [];

    const validationError = validatePeriods(periods);
    if (validationError) return reply.code(400).send({ error: validationError });

    let ruleId: number;
    try {
      const result = db
        .prepare("INSERT INTO reminder_rules (plant_id, reminder_type_id, default_interval_days) VALUES (?, ?, ?)")
        .run(plantId, body.reminder_type_id, body.default_interval_days ?? null);
      ruleId = Number(result.lastInsertRowid);
    } catch {
      return reply.code(409).send({ error: "a reminder rule for this plant/reminder type already exists" });
    }

    const insertPeriod = db.prepare(
      `INSERT INTO override_periods (reminder_rule_id, start_month, start_day, end_month, end_day, interval_days)
       VALUES (?, ?, ?, ?, ?, ?)`
    );
    for (const p of periods) {
      insertPeriod.run(ruleId, p.start_month, p.start_day, p.end_month, p.end_day, p.interval_days);
    }

    recomputeReminderState(Number(plantId), body.reminder_type_id);
    return reply.code(201).send(getRuleWithPeriods(ruleId));
  });

  fastify.patch("/api/reminder-rules/:id", async (request, reply) => {
    const { id } = request.params as { id: string };
    const rule = db.prepare("SELECT * FROM reminder_rules WHERE id = ?").get(id) as RuleRow | undefined;
    if (!rule) return reply.code(404).send({ error: "not found" });

    const body = updateRuleSchema.parse(request.body);

    if (body.override_periods) {
      const validationError = validatePeriods(body.override_periods);
      if (validationError) return reply.code(400).send({ error: validationError });

      db.prepare("DELETE FROM override_periods WHERE reminder_rule_id = ?").run(id);
      const insertPeriod = db.prepare(
        `INSERT INTO override_periods (reminder_rule_id, start_month, start_day, end_month, end_day, interval_days)
         VALUES (?, ?, ?, ?, ?, ?)`
      );
      for (const p of body.override_periods) {
        insertPeriod.run(id, p.start_month, p.start_day, p.end_month, p.end_day, p.interval_days);
      }
    }

    if (body.default_interval_days !== undefined) {
      db.prepare("UPDATE reminder_rules SET default_interval_days = ? WHERE id = ?").run(
        body.default_interval_days,
        id
      );
    }

    recomputeReminderState(rule.plant_id, rule.reminder_type_id);
    return getRuleWithPeriods(Number(id));
  });

  fastify.delete("/api/reminder-rules/:id", async (request, reply) => {
    const { id } = request.params as { id: string };
    const rule = db.prepare("SELECT * FROM reminder_rules WHERE id = ?").get(id) as RuleRow | undefined;
    if (!rule) return reply.code(404).send({ error: "not found" });

    db.prepare("DELETE FROM reminder_states WHERE plant_id = ? AND reminder_type_id = ?").run(
      rule.plant_id,
      rule.reminder_type_id
    );
    db.prepare("DELETE FROM reminder_rules WHERE id = ?").run(id); // cascades override_periods
    return reply.code(204).send();
  });
}
