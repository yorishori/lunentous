import type { FastifyInstance } from "fastify";
import { registerArchivableTypeRoutes } from "./archivableTypeFactory.js";

export function registerReminderTypeRoutes(fastify: FastifyInstance): void {
  registerArchivableTypeRoutes(fastify, {
    path: "/api/reminder-types",
    table: "reminder_types",
    usageTable: "reminder_rules",
    usageColumn: "reminder_type_id",
    hasIcon: true,
  });
}
