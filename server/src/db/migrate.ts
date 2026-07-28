import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { db } from "./client.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export function migrate(): void {
  const schemaPath = path.join(__dirname, "schema.sql");
  const schema = fs.readFileSync(schemaPath, "utf-8");
  db.exec(schema);
  applyColumnMigrations();
  seedDefaultReminderTypes();
}

/** schema.sql's CREATE TABLE IF NOT EXISTS only ever helps brand-new
 * installs -- an existing reminder_rules table from before annual_month/
 * annual_day existed needs these added by hand. Guarded by PRAGMA
 * table_info so this stays a no-op once the columns are already there. */
function applyColumnMigrations(): void {
  const columns = db.prepare("PRAGMA table_info(reminder_rules)").all() as Array<{ name: string }>;
  const names = new Set(columns.map((c) => c.name));
  if (!names.has("annual_month")) db.exec("ALTER TABLE reminder_rules ADD COLUMN annual_month INTEGER");
  if (!names.has("annual_day")) db.exec("ALTER TABLE reminder_rules ADD COLUMN annual_day INTEGER");
}

function seedDefaultReminderTypes(): void {
  const insert = db.prepare(
    "INSERT OR IGNORE INTO reminder_types (name, icon, color) VALUES (?, ?, ?)"
  );
  insert.run("Watering", "Droplet", "#89b4fa");
  insert.run("Fertilizing", "Leaf", "#a6e3a1");
}
