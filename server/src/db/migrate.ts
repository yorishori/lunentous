import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { db } from "./client.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export function migrate(): void {
  const schemaPath = path.join(__dirname, "schema.sql");
  const schema = fs.readFileSync(schemaPath, "utf-8");
  db.exec(schema);
  seedDefaultReminderTypes();
}

function seedDefaultReminderTypes(): void {
  const insert = db.prepare(
    "INSERT OR IGNORE INTO reminder_types (name, icon, color) VALUES (?, ?, ?)"
  );
  insert.run("Watering", "droplet", "#89b4fa");
  insert.run("Fertilizing", "leaf", "#a6e3a1");
}
