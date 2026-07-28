// Runs before this test file's own imports resolve (vitest re-executes
// setupFiles per test file under the default per-file module isolation),
// so db/client.ts -- which reads DB_PATH once, at import time -- always
// sees these values rather than a real on-disk database.
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

process.env.NODE_ENV = "test";
process.env.DB_PATH = ":memory:";

// @fastify/static's `root` option is validated at plugin-registration
// time, so this needs to exist even though nothing in these tests reads
// from it.
const tmpWebDist = fs.mkdtempSync(path.join(os.tmpdir(), "lunentous-test-web-dist-"));
fs.writeFileSync(path.join(tmpWebDist, "index.html"), "<!doctype html><title>test</title>");
process.env.WEB_DIST = tmpWebDist;

const tmpPhotos = fs.mkdtempSync(path.join(os.tmpdir(), "lunentous-test-photos-"));
process.env.PHOTOS_DIR = tmpPhotos;
