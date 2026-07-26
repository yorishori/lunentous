-- Lunentous SQLite schema (spec_v1.md §3)

CREATE TABLE IF NOT EXISTS reminder_types (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    icon TEXT,
    color TEXT,
    archived BOOLEAN NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS phase_types (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    color TEXT,
    archived BOOLEAN NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS plants (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    species TEXT,
    location TEXT,
    acquired_date DATE,
    avatar_photo_id INTEGER REFERENCES photos(id) ON DELETE SET NULL,
    general_notes TEXT,
    archived BOOLEAN NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reminder_rules (
    id INTEGER PRIMARY KEY,
    plant_id INTEGER NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    reminder_type_id INTEGER NOT NULL REFERENCES reminder_types(id),
    default_interval_days INTEGER,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(plant_id, reminder_type_id)
);

CREATE TABLE IF NOT EXISTS override_periods (
    id INTEGER PRIMARY KEY,
    reminder_rule_id INTEGER NOT NULL REFERENCES reminder_rules(id) ON DELETE CASCADE,
    start_month INTEGER NOT NULL CHECK(start_month BETWEEN 1 AND 12),
    start_day   INTEGER NOT NULL CHECK(start_day BETWEEN 1 AND 31),
    end_month   INTEGER NOT NULL CHECK(end_month BETWEEN 1 AND 12),
    end_day     INTEGER NOT NULL CHECK(end_day BETWEEN 1 AND 31),
    interval_days INTEGER
);
-- App-level validation (not enforceable in SQLite DDL): periods belonging to the
-- same reminder_rule_id must not overlap. See §9.

CREATE TABLE IF NOT EXISTS reminder_states (
    id INTEGER PRIMARY KEY,
    plant_id INTEGER NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    reminder_type_id INTEGER NOT NULL REFERENCES reminder_types(id),
    due_date DATE,
    notified BOOLEAN NOT NULL DEFAULT 0,
    UNIQUE(plant_id, reminder_type_id)
);

CREATE TABLE IF NOT EXISTS plant_phase_windows (
    id INTEGER PRIMARY KEY,
    plant_id INTEGER NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    phase_type_id INTEGER NOT NULL REFERENCES phase_types(id),
    start_month INTEGER NOT NULL CHECK(start_month BETWEEN 1 AND 12),
    start_day   INTEGER NOT NULL CHECK(start_day BETWEEN 1 AND 31),
    end_month   INTEGER NOT NULL CHECK(end_month BETWEEN 1 AND 12),
    end_day     INTEGER NOT NULL CHECK(end_day BETWEEN 1 AND 31),
    notes TEXT
);

CREATE TABLE IF NOT EXISTS timeline_events (
    id INTEGER PRIMARY KEY,
    plant_id INTEGER NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    reminder_type_id INTEGER REFERENCES reminder_types(id),
    event_date DATE NOT NULL,
    text TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS photos (
    id INTEGER PRIMARY KEY,
    plant_id INTEGER NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    timeline_event_id INTEGER REFERENCES timeline_events(id) ON DELETE CASCADE,
    file_path TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_keys (
    id INTEGER PRIMARY KEY,
    key_hash TEXT NOT NULL,
    label TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_timeline_events_plant ON timeline_events(plant_id, event_date DESC);
CREATE INDEX IF NOT EXISTS idx_timeline_events_plant_type ON timeline_events(plant_id, reminder_type_id, event_date DESC);
CREATE INDEX IF NOT EXISTS idx_reminder_states_due ON reminder_states(due_date, notified);
CREATE INDEX IF NOT EXISTS idx_override_periods_rule ON override_periods(reminder_rule_id);
CREATE INDEX IF NOT EXISTS idx_phase_windows_plant ON plant_phase_windows(plant_id);
CREATE INDEX IF NOT EXISTS idx_photos_plant ON photos(plant_id);
CREATE INDEX IF NOT EXISTS idx_photos_timeline_event ON photos(timeline_event_id);
