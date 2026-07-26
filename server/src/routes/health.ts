import type { FastifyInstance } from "fastify";

export function registerHealthRoutes(fastify: FastifyInstance): void {
  fastify.get("/api/health", async () => ({ status: "ok" }));
}
