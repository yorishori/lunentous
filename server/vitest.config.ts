import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",
    setupFiles: ["./test/setup.ts"],
    // Vitest's default per-file module isolation already gives each test
    // file its own fresh `db/client.ts` singleton (a clean in-memory
    // SQLite database, set up in test/setup.ts) -- parallelism is off just
    // to keep output/log interleaving simple for a suite this size.
    fileParallelism: false,
  },
});
