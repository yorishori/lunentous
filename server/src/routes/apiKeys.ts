import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { db } from "../db/client.js";
import { generateApiKeyToken, hashApiKeyToken } from "../lib/auth.js";

// Not explicitly enumerated in spec §5's endpoint list, but implied by §6
// ("Settings -- API key management (create/revoke labeled keys)"). Since a
// caller must already hold a valid key to reach these (auth is required
// globally except /api/health), this doesn't reintroduce the unauthenticated
// bootstrap problem the spec's api_keys comment warns about -- that's still
// solely handled by the CLI script.
const createApiKeySchema = z.object({ label: z.string().nullable().optional() });

export function registerApiKeyRoutes(fastify: FastifyInstance): void {
  fastify.get("/api/api-keys", async () => {
    return db.prepare("SELECT id, label, created_at FROM api_keys ORDER BY created_at DESC").all();
  });

  fastify.post("/api/api-keys", async (request, reply) => {
    const body = createApiKeySchema.parse(request.body);
    const token = generateApiKeyToken();
    const hash = hashApiKeyToken(token);
    const result = db.prepare("INSERT INTO api_keys (key_hash, label) VALUES (?, ?)").run(hash, body.label ?? null);
    // token is only ever returned here, at creation time -- never again.
    return reply.code(201).send({ id: result.lastInsertRowid, label: body.label ?? null, token });
  });

  fastify.delete("/api/api-keys/:id", async (request, reply) => {
    const { id } = request.params as { id: string };
    const result = db.prepare("DELETE FROM api_keys WHERE id = ?").run(id);
    if (result.changes === 0) return reply.code(404).send({ error: "not found" });
    return reply.code(204).send();
  });
}
