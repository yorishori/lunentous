import path from "node:path";
import fs from "node:fs";
import Database from "better-sqlite3";

const dbPath = process.env.DB_PATH ?? path.join(process.cwd(), "data", "db.sqlite");
fs.mkdirSync(path.dirname(dbPath), { recursive: true });

export const db = new Database(dbPath);
db.pragma("journal_mode = WAL");
db.pragma("foreign_keys = ON");

export const photosDir = process.env.PHOTOS_DIR ?? path.join(process.cwd(), "data", "photos");
fs.mkdirSync(photosDir, { recursive: true });
