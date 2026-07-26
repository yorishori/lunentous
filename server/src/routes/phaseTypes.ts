import type { FastifyInstance } from "fastify";
import { registerArchivableTypeRoutes } from "./archivableTypeFactory.js";

export function registerPhaseTypeRoutes(fastify: FastifyInstance): void {
  registerArchivableTypeRoutes(fastify, {
    path: "/api/phase-types",
    table: "phase_types",
    usageTable: "plant_phase_windows",
    usageColumn: "phase_type_id",
    hasIcon: false,
  });
}
