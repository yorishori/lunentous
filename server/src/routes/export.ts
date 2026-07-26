import path from "node:path";
import type { FastifyInstance } from "fastify";
import * as tar from "tar";
import { photosDir } from "../db/client.js";
import { todayLocalDate } from "../lib/dates.js";

export function registerExportRoutes(fastify: FastifyInstance): void {
  fastify.get("/api/export", async (request, reply) => {
    const dbPath = process.env.DB_PATH ?? path.join(process.cwd(), "data", "db.sqlite");
    const cwd = path.dirname(dbPath);
    const dbEntry = path.basename(dbPath);
    // Assumes DB_PATH and PHOTOS_DIR share a common parent directory (true of
    // the default docker-compose /data layout) so both can be archived from
    // one `cwd` as relative entries.
    const photosEntry = path.relative(cwd, photosDir);

    reply.header("Content-Type", "application/gzip");
    reply.header("Content-Disposition", `attachment; filename="lunentous-export-${todayLocalDate()}.tar.gz"`);

    const stream = tar.create({ gzip: true, cwd }, [dbEntry, photosEntry]);
    return reply.send(stream);
  });
}
