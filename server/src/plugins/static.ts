import path from "node:path";
import type { FastifyInstance } from "fastify";
import fastifyStatic from "@fastify/static";
import { photosDir } from "../db/client.js";

/** Serves the built React SPA and falls back to index.html for client-side
 * routes, per spec §8 ("one container serving both the REST API and the
 * built web frontend"). API 404s stay JSON.
 *
 * Also serves uploaded photos at /photos/:filename, unauthenticated -- like
 * the SPA's own assets, <img> tags can't attach a Bearer header, and photo
 * filenames are random UUIDs (see routes/timeline.ts, routes/plants.ts),
 * so this is the same trust model as the rest of the static surface. */
export async function registerStatic(fastify: FastifyInstance): Promise<void> {
  const webDist = process.env.WEB_DIST ?? path.join(process.cwd(), "..", "web", "dist");

  await fastify.register(fastifyStatic, { root: webDist });
  await fastify.register(fastifyStatic, { root: photosDir, prefix: "/photos/", decorateReply: false });

  fastify.setNotFoundHandler((request, reply) => {
    if (request.url.startsWith("/api/")) {
      return reply.code(404).send({ error: "not found" });
    }
    return reply.sendFile("index.html");
  });
}
