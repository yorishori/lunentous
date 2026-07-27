// Bootstrap script: mints the first API key out-of-band, avoiding an
// unauthenticated bootstrap HTTP endpoint (see ARCHITECTURE.md's
// Authentication section). Run with: npm run cli:create-api-key -- --label "web"
import { migrate } from "../db/migrate.js";
import { db } from "../db/client.js";
import { generateApiKeyToken, hashApiKeyToken } from "../lib/auth.js";

function parseLabelArg(): string | null {
  const args = process.argv.slice(2);
  const idx = args.indexOf("--label");
  if (idx !== -1 && args[idx + 1]) return args[idx + 1];
  return null;
}

migrate();

const label = parseLabelArg();
const token = generateApiKeyToken();
const hash = hashApiKeyToken(token);

db.prepare("INSERT INTO api_keys (key_hash, label) VALUES (?, ?)").run(hash, label);

console.log("API key created. This is shown only once -- store it now:");
console.log("");
console.log(token);
console.log("");
console.log(`Label: ${label ?? "(none)"}`);
console.log("Use it as: Authorization: Bearer " + token);
