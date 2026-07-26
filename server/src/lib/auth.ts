import crypto from "node:crypto";
import type { FastifyReply, FastifyRequest } from "fastify";
import { db } from "../db/client.js";

export function generateApiKeyToken(): string {
  return crypto.randomBytes(32).toString("hex");
}

export function hashApiKeyToken(token: string): string {
  return crypto.createHash("sha256").update(token).digest("hex");
}

interface ApiKeyRow {
  id: number;
}

/** Unauthenticated paths: health check only, per spec §5. */
const PUBLIC_PATHS = new Set(["/api/health"]);

export async function requireApiKey(request: FastifyRequest, reply: FastifyReply): Promise<void> {
  const pathname = request.routeOptions?.url ?? request.url.split("?")[0];

  // Only the API surface requires auth -- static assets and the SPA's own
  // index.html fallback must stay reachable so the web app can load its
  // login gate in the first place.
  if (!pathname.startsWith("/api/") || PUBLIC_PATHS.has(pathname)) {
    return;
  }

  const header = request.headers.authorization;
  const token = header?.startsWith("Bearer ") ? header.slice("Bearer ".length).trim() : undefined;

  if (!token) {
    return reply.code(401).send({ error: "missing bearer token" });
  }

  const hash = hashApiKeyToken(token);
  const row = db.prepare("SELECT id FROM api_keys WHERE key_hash = ?").get(hash) as ApiKeyRow | undefined;

  if (!row) {
    return reply.code(401).send({ error: "invalid api key" });
  }
}
