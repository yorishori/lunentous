import path from "node:path";
import fs from "node:fs";
import type { FastifyInstance } from "fastify";
import { db, photosDir } from "../db/client.js";

export function registerPhotoRoutes(fastify: FastifyInstance): void {
  fastify.delete("/api/photos/:id", async (request, reply) => {
    const { id } = request.params as { id: string };
    const photo = db.prepare("SELECT * FROM photos WHERE id = ?").get(id) as { file_path: string } | undefined;
    if (!photo) return reply.code(404).send({ error: "not found" });

    db.prepare("DELETE FROM photos WHERE id = ?").run(id);
    fs.unlink(path.join(photosDir, photo.file_path), () => {});
    return reply.code(204).send();
  });
}
