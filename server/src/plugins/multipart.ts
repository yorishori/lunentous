import type { FastifyInstance } from "fastify";
import fastifyMultipart from "@fastify/multipart";

export async function registerMultipart(fastify: FastifyInstance): Promise<void> {
  await fastify.register(fastifyMultipart, {
    limits: { fileSize: 25 * 1024 * 1024 },
  });
}
