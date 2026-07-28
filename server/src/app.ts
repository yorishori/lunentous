import Fastify, { type FastifyInstance } from "fastify";
import { ZodError } from "zod";
import { migrate } from "./db/migrate.js";
import { requireApiKey } from "./lib/auth.js";
import { registerMultipart } from "./plugins/multipart.js";
import { registerStatic } from "./plugins/static.js";
import { registerHealthRoutes } from "./routes/health.js";
import { registerPlantRoutes } from "./routes/plants.js";
import { registerReminderTypeRoutes } from "./routes/reminderTypes.js";
import { registerPhaseTypeRoutes } from "./routes/phaseTypes.js";
import { registerReminderRuleRoutes } from "./routes/reminderRules.js";
import { registerReminderStateRoutes } from "./routes/reminderStates.js";
import { registerPhaseWindowRoutes } from "./routes/phaseWindows.js";
import { registerTimelineRoutes } from "./routes/timeline.js";
import { registerOneTimeReminderRoutes } from "./routes/oneTimeReminders.js";
import { registerPhotoRoutes } from "./routes/photos.js";
import { registerApiKeyRoutes } from "./routes/apiKeys.js";
import { registerExportRoutes } from "./routes/export.js";

/**
 * Builds and fully registers the Fastify app, stopping short of listen()
 * -- split out from index.ts (which just calls this then listens) so
 * tests can build a real app instance and drive it via `.inject()`
 * without binding a port.
 */
export async function buildApp(): Promise<FastifyInstance> {
  migrate();

  const fastify = Fastify({ logger: process.env.NODE_ENV !== "test" });

  fastify.setErrorHandler((error, request, reply) => {
    if (error instanceof ZodError) {
      return reply.code(400).send({ error: "validation error", details: error.issues });
    }
    fastify.log.error(error);
    const statusCode =
      typeof error === "object" && error !== null && "statusCode" in error && typeof error.statusCode === "number"
        ? error.statusCode
        : 500;
    const message = error instanceof Error ? error.message : "internal server error";
    return reply.code(statusCode).send({ error: message });
  });

  await registerMultipart(fastify);
  await registerStatic(fastify);

  fastify.addHook("onRequest", requireApiKey);

  registerHealthRoutes(fastify);
  registerPlantRoutes(fastify);
  registerReminderTypeRoutes(fastify);
  registerPhaseTypeRoutes(fastify);
  registerReminderRuleRoutes(fastify);
  registerReminderStateRoutes(fastify);
  registerPhaseWindowRoutes(fastify);
  registerTimelineRoutes(fastify);
  registerOneTimeReminderRoutes(fastify);
  registerPhotoRoutes(fastify);
  registerApiKeyRoutes(fastify);
  registerExportRoutes(fastify);

  return fastify;
}
