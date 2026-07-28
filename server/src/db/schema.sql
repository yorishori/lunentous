-- Lunentous SQLite schema -- see ARCHITECTURE.md's Domain model section

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
    -- Mutually exclusive with default_interval_days/override_periods: a
    -- fixed calendar date the reminder recurs on every year, instead of an
    -- N-day interval. Enforced at the API layer (see routes/reminderRules.ts),
    -- not in DDL, same convention as override_periods' overlap rule below.
    annual_month INTEGER CHECK(annual_month IS NULL OR annual_month BETWEEN 1 AND 12),
    annual_day INTEGER CHECK(annual_day IS NULL OR annual_day BETWEEN 1 AND 31),
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

-- Per-plant, untyped, informational reminders -- no reminder_type, and
-- completing one never writes a timeline_events row (unlike a normal
-- reminder occurrence). completed_at is kept (not deleted) once set, so
-- there's a record of what was done, e.g. "give this plant to a friend
-- on 25 Sept" or "buy a new pot".
CREATE TABLE IF NOT EXISTS one_time_reminders (
    id INTEGER PRIMARY KEY,
    plant_id INTEGER NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    due_date DATE NOT NULL,
    text TEXT NOT NULL,
    completed_at DATETIME,
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
CREATE INDEX IF NOT EXISTS idx_one_time_reminders_plant ON one_time_reminders(plant_id, due_date);
