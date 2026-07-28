import crypto from "node:crypto";
import { db } from "../src/db/client.js";
import { generateApiKeyToken, hashApiKeyToken } from "../src/lib/auth.js";

/** Clears every table (children before parents) so each test starts from
 * a blank slate without needing a fresh :memory: database per test --
 * migrate() only needs to run once per test file. */
export function resetDb(): void {
  db.exec(`
    DELETE FROM photos;
    DELETE FROM timeline_events;
    DELETE FROM one_time_reminders;
    DELETE FROM plant_phase_windows;
    DELETE FROM override_periods;
    DELETE FROM reminder_states;
    DELETE FROM reminder_rules;
    DELETE FROM api_keys;
    DELETE FROM plants;
    DELETE FROM reminder_types;
    DELETE FROM phase_types;
  `);
}

/** Inserts a usable API key directly (bypassing the HTTP route) and
 * returns the raw bearer token for use in a test's Authorization header. */
export function createApiKey(label = "test"): string {
  const token = generateApiKeyToken();
  db.prepare("INSERT INTO api_keys (key_hash, label) VALUES (?, ?)").run(hashApiKeyToken(token), label);
  return token;
}

export function authHeader(token: string): { authorization: string } {
  return { authorization: `Bearer ${token}` };
}

export function randomName(prefix: string): string {
  return `${prefix}-${crypto.randomBytes(4).toString("hex")}`;
}
