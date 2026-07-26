# Lunentous — Plant Care App Spec (v1.0)

Name: **Lunentous**.

## 0. Changelog from v0.1

- Reminder engine changed from "list of periods" to "default interval + override
  periods," which structurally eliminates the gap/fallback question.
- TimelineEvent simplified: dropped the `reminder_completion` / `manual_note` type
  enum in favor of a nullable `reminder_type_id` on every event. Presence of that
  field is what ties a log entry to a reminder and triggers recalculation — an
  ad-hoc log and a "completed" reminder are now literally the same write path.
- Notifications: on-device scheduled polling via the Android app, no Firebase/push
  service.
- ReminderType and PhaseType are archive-only, never hard-deleted, so history never
  references a type that's vanished.
- Added: initial due-date behavior (before anything's ever been logged), and
  recompute-on-edit/delete behavior for history.
- Full SQL schema and REST API surface added below — this is now implementation-
  ready, not just a design doc.

## 1. Overview & Goals

A self-hosted plant care tracker focused entirely on **care logging and
scheduling** — not identification or diagnosis. It tells you when something is
due (once), logs what you actually did and when, and adapts future timing from
that real history rather than a fixed calendar.

## 2. Non-Goals

- No plant identification / AI photo diagnosis
- No social/community features
- No subscription — self-hosted, open source
- No skip/snooze — an unlogged reminder just becomes increasingly overdue
- No multi-user support in v1
- No timezone handling — all domain dates are plain calendar dates (see §9)

## 3. Domain Model

SQLite schema. All tables use `INTEGER PRIMARY KEY` (SQLite rowid) unless noted.
`created_at`/`updated_at` are housekeeping timestamps for auditing only — unrelated
to the date-only reminder logic, so they're exempt from the "no timezone" rule.

```sql
-- Reusable reminder type templates (e.g. Watering, Fertilizing, Pruning)
CREATE TABLE reminder_types (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    icon TEXT,
    color TEXT,               -- hex, e.g. "#89b4fa"
    archived BOOLEAN NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Ships pre-seeded with Watering + Fertilizing; fully user editable/archivable,
-- never hard-deleted (see §4.4).

-- Reusable phase/season type templates (e.g. Potting Window, Dormancy)
CREATE TABLE phase_types (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    color TEXT,
    archived BOOLEAN NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE plants (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    species TEXT,
    location TEXT,
    acquired_date DATE,
    avatar_photo_id INTEGER REFERENCES photos(id) ON DELETE SET NULL,
    general_notes TEXT,        -- static freeform text, NOT the dated journal
    archived BOOLEAN NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- One rule per (plant, reminder type)
CREATE TABLE reminder_rules (
    id INTEGER PRIMARY KEY,
    plant_id INTEGER NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    reminder_type_id INTEGER NOT NULL REFERENCES reminder_types(id),
    default_interval_days INTEGER,   -- NULL = no reminder outside an override period
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(plant_id, reminder_type_id)
);

-- Seasonal exceptions to a rule's default interval
CREATE TABLE override_periods (
    id INTEGER PRIMARY KEY,
    reminder_rule_id INTEGER NOT NULL REFERENCES reminder_rules(id) ON DELETE CASCADE,
    start_month INTEGER NOT NULL CHECK(start_month BETWEEN 1 AND 12),
    start_day   INTEGER NOT NULL CHECK(start_day BETWEEN 1 AND 31),
    end_month   INTEGER NOT NULL CHECK(end_month BETWEEN 1 AND 12),
    end_day     INTEGER NOT NULL CHECK(end_day BETWEEN 1 AND 31),
    interval_days INTEGER   -- NULL = explicitly paused during this range
);
-- App-level validation (not enforceable in SQLite DDL): periods belonging to the
-- same reminder_rule_id must not overlap. See §9.

-- The single materialized "next occurrence" per (plant, reminder type)
CREATE TABLE reminder_states (
    id INTEGER PRIMARY KEY,
    plant_id INTEGER NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    reminder_type_id INTEGER NOT NULL REFERENCES reminder_types(id),
    due_date DATE,             -- NULL = paused / not currently active
    notified BOOLEAN NOT NULL DEFAULT 0,
    UNIQUE(plant_id, reminder_type_id)
);

CREATE TABLE plant_phase_windows (
    id INTEGER PRIMARY KEY,
    plant_id INTEGER NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    phase_type_id INTEGER NOT NULL REFERENCES phase_types(id),
    start_month INTEGER NOT NULL CHECK(start_month BETWEEN 1 AND 12),
    start_day   INTEGER NOT NULL CHECK(start_day BETWEEN 1 AND 31),
    end_month   INTEGER NOT NULL CHECK(end_month BETWEEN 1 AND 12),
    end_day     INTEGER NOT NULL CHECK(end_day BETWEEN 1 AND 31),
    notes TEXT
);
-- Purely informational, never drives reminders/notifications.

-- Unified per-plant timeline: both reminder logs and freeform journal entries
CREATE TABLE timeline_events (
    id INTEGER PRIMARY KEY,
    plant_id INTEGER NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    reminder_type_id INTEGER REFERENCES reminder_types(id),  -- NULL = pure journal note
    event_date DATE NOT NULL,
    text TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- If reminder_type_id is set, this row IS what "completing" or "ad-hoc logging"
-- a reminder means -- there is no separate completion record. See §4.

CREATE TABLE photos (
    id INTEGER PRIMARY KEY,
    plant_id INTEGER NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    timeline_event_id INTEGER REFERENCES timeline_events(id) ON DELETE CASCADE,
    file_path TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- timeline_event_id is NULL for a plant's avatar photo (uploaded standalone).

CREATE TABLE api_keys (
    id INTEGER PRIMARY KEY,
    key_hash TEXT NOT NULL,
    label TEXT,                -- e.g. "android-phone", "web"
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Provisioned via a CLI command / env var at deploy time, not over HTTP --
-- avoids a chicken-and-egg unauthenticated bootstrap endpoint.
```

## 4. Core Business Logic

### 4.1 Resolving the interval for a given date

```
function resolve_interval(rule, date D):
    for period in rule.override_periods:
        if date_in_range(D, period.start, period.end):   # handles year-wrap ranges
            return period.interval_days                  # may be NULL (paused)
    return rule.default_interval_days                     # may be NULL (paused)

function date_in_range(D, start, end):
    if start <= end:
        return start <= D <= end
    else:                                # wrapped range, e.g. Nov 1 -> Feb 28
        return D >= start or D <= end
```

### 4.2 Recomputing a ReminderState

This runs any time a TimelineEvent with a non-null `reminder_type_id` is
**created, edited (date changed), or deleted** for a given (plant, reminder type)
pair. It always recalculates from scratch off the most recent qualifying event --
this is what makes edits and deletions behave correctly, not just new writes.

```
function recompute_reminder_state(plant_id, reminder_type_id):
    rule = get_reminder_rule(plant_id, reminder_type_id)
    if rule is None:
        delete_reminder_state_if_exists(plant_id, reminder_type_id)
        return

    latest_event = most_recent(
        timeline_events WHERE plant_id = plant_id
                        AND reminder_type_id = reminder_type_id
    )

    if latest_event is not None:
        baseline_date = latest_event.event_date
    else:
        # Never logged: use the rule's creation date as the baseline, so a new
        # rule has an immediate first due date instead of sitting undefined.
        baseline_date = rule.created_at (as DATE)

    interval = resolve_interval(rule, baseline_date)
    state = get_or_create_reminder_state(plant_id, reminder_type_id)
    state.due_date = (baseline_date + interval days) if interval is not None else NULL
    state.notified = false
    save(state)
```

Call this function:
- After creating a ReminderRule (baseline = rule creation date, no events yet)
- After creating/editing/deleting a TimelineEvent with a non-null `reminder_type_id`
- After editing a ReminderRule's `default_interval_days` or its override periods
  (the interval landscape changed, so the current due_date may need to shift --
  recompute from the same most-recent-event logic above)
- After deleting a ReminderRule (removes the ReminderState entirely)

### 4.3 Notification check (runs on-device, not server-initiated)

```
function check_notifications():                      # called by Android WorkManager
                                                       # at each configured time-of-day
    today = local_date_today()
    due = GET /api/reminder-states?due_before_or_on=today&notified=false
    for state in due:
        show_local_notification(state.plant_name, state.reminder_type_name)
        POST /api/reminder-states/{state.id}/mark-notified
```

One notification fires per occurrence. After that, "days overdue" is a pure
display calculation -- `today - due_date` -- computed wherever it's shown (web
page render, Android list item), never stored.

### 4.4 Archiving vs. deleting ReminderType / PhaseType

Both are archive-only (`archived = 1`), never hard-deleted, because
`timeline_events.reminder_type_id` and `override_periods` reference them --
deleting a type out from under existing history would corrupt past records or
require cascading deletes of real logged data, which is unacceptable. Archived
types are hidden from "add a reminder rule" / "add a phase window" pickers but
remain valid and fully displayed wherever they're already referenced.

## 5. REST API

All endpoints require `Authorization: Bearer <api_key>` except `/health`.
Request/response bodies are JSON; photo upload uses multipart.

**Plants**
- `GET /api/plants?archived=false` -- list
- `POST /api/plants` -- create
- `GET /api/plants/{id}` -- detail: includes active phase windows (today) and
  current reminder states with computed `days_overdue`
- `PATCH /api/plants/{id}` -- update
- `POST /api/plants/{id}/archive` / `POST /api/plants/{id}/unarchive`
- `POST /api/plants/{id}/avatar` -- multipart photo upload, sets `avatar_photo_id`

**Reminder Types**
- `GET /api/reminder-types?archived=false`
- `POST /api/reminder-types`
- `PATCH /api/reminder-types/{id}`
- `POST /api/reminder-types/{id}/archive`

**Phase Types** -- same shape as Reminder Types, path `/api/phase-types`

**Reminder Rules**
- `GET /api/plants/{plant_id}/reminder-rules`
- `POST /api/plants/{plant_id}/reminder-rules`
  ```json
  {
    "reminder_type_id": 1,
    "default_interval_days": 4,
    "override_periods": [
      {"start_month": 12, "start_day": 1, "end_month": 2, "end_day": 28, "interval_days": 14}
    ]
  }
  ```
  Triggers `recompute_reminder_state` (§4.2) on creation.
- `PATCH /api/reminder-rules/{id}` -- replaces `default_interval_days` and/or the
  full `override_periods` array; triggers recompute
- `DELETE /api/reminder-rules/{id}` -- cascades to delete its ReminderState

**Reminder States**
- `GET /api/reminder-states?due_before_or_on={date}&notified=false` -- used by the
  Android polling job
- `GET /api/plants/{plant_id}/reminder-states` -- used by plant detail/dashboard views
- `POST /api/reminder-states/{id}/mark-notified`

**Phase Windows**
- `GET/POST /api/plants/{plant_id}/phase-windows`
- `PATCH/DELETE /api/phase-windows/{id}`

**Timeline**
- `GET /api/plants/{plant_id}/timeline?reminder_type_id=&limit=&before=` -- paginated,
  newest first
- `POST /api/plants/{plant_id}/timeline` -- multipart (fields + photo files)
  ```json
  {"event_date": "2026-07-26", "reminder_type_id": 1, "text": "Repotted into terracotta"}
  ```
  Triggers recompute if `reminder_type_id` is set.
- `PATCH /api/timeline/{id}` -- triggers recompute if `event_date` or
  `reminder_type_id` changed
- `DELETE /api/timeline/{id}` -- triggers recompute (falls back to the next most
  recent qualifying event, or the rule's creation date if none remain)

**Photos**
- `DELETE /api/photos/{id}`

**Export**
- `GET /api/export` -- streams a full copy of the SQLite file plus the photo
  directory as a tarball, for backup

## 6. Web Frontend

- **Dashboard** -- card/row per active plant: avatar, name, current phase (if any,
  from phase windows active today), next reminder + overdue status, color-coded
  (on schedule / due today / overdue)
- **Plant detail** -- form fields (§3 `plants` columns), general notes, phase
  windows (current + upcoming, add/edit), reminder rules (add/edit types,
  default interval, override periods), full timeline feed (filterable by
  reminder type) with an "add entry" form (date, optional reminder type tag,
  text, photos), archive button
- **Calendar view** -- since only the single next occurrence per (plant, type) is
  ever materialized (§4.2), this view plots each ReminderState's real `due_date`
  as a solid marker, and may additionally render **projected** future
  occurrences (dashed/lighter) by repeatedly applying §4.1 forward from the
  materialized due_date, assuming on-time completion each time. Projections are
  computed for display only and never written to the database.
- **Reminder Types / Phase Types management** -- CRUD + archive, list shows
  usage count (how many plants reference each)
- **Settings** -- API key management (create/revoke labeled keys), export/backup
  trigger

## 7. Android App

- Mirrors the web app's dashboard, plant detail, and calendar views via the
  same REST API
- **NotificationSchedule** -- local device setting (not synced via API): one or
  more times of day (e.g. 12:00, 20:00) at which to run §4.3's check
- Implemented via WorkManager (or AlarmManager if tighter timing is ever
  needed); note that Doze/OEM battery restrictions may delay the exact run time
  on some devices -- acceptable for a "check around noon/8pm" use case
- Permissions: `INTERNET`, `POST_NOTIFICATIONS` (Android 13+)
- If reached outside the home network, connects through the existing WireGuard
  VPN rather than exposing the API publicly -- no additional infra needed beyond
  what's already running

## 8. Infrastructure

- Single Docker Compose service is sufficient: one container serving both the
  REST API and the built web frontend (static files), plus two volumes:
  - SQLite database file
  - photo uploads directory
- `TZ` environment variable sets the container's local date for "today" in
  reminder calculations (§9) -- pick your local timezone; this is a deliberate
  simplification, not a gap
- Suggested stack (swappable, not load-bearing for this spec): a lightweight
  backend framework (e.g. Python/FastAPI or Go) keeps the image small for a
  single-container deploy; a server-rendered or lightly-interactive frontend
  (e.g. HTMX-style) avoids a heavy JS build pipeline
- Android: Kotlin + Jetpack Compose, Retrofit for the API client, WorkManager
  for the scheduled check

## 9. Validation Rules

- `override_periods` belonging to the same `reminder_rule_id` must not overlap
  (app-level check on create/update, not enforceable in SQLite DDL)
- `start_month`/`end_month` in [1,12], `start_day`/`end_day` in [1,31] -- no
  calendar-validity check beyond that; flag impossible combinations like Feb 30
  at the API layer if stricter validation is desired
- One `reminder_rules` row per (plant, reminder_type) -- enforced by UNIQUE constraint
- One `reminder_states` row per (plant, reminder_type) -- enforced by UNIQUE constraint
- All domain dates (`due_date`, `event_date`, period boundaries) are DATE only,
  no time component; "today" is the API server's local date per its `TZ` setting
  (§8) -- no per-request timezone negotiation

## 10. Worked Examples

- **Late watering**: default interval 4 days, you water on day 6 instead of day
  4 -- §4.2 recomputes from the actual log date, so next due is day 6 + 4 = day
  10, not day 8.
- **Seasonal switch**: default 4 days, winter override Dec 1-Feb 28 @ 14 days.
  Log on Jan 10 -- next due Jan 24 (winter applies). Log on Jul 3 -- next due Jul
  7 (no override active, default applies). No year-to-year re-setup, since
  periods are month-day only.
- **Overdue across a season boundary**: due Feb 20 under the winter override,
  but you actually log it Mar 5 -- the interval active on Mar 5 (default, since
  winter override ended Feb 28) determines the next due date, not what was
  active when it was originally due.
- **Seasonal-only reminder**: `default_interval_days = NULL`, single override
  Apr 1-Jul 31 @ 21 days for a "check for repotting" reminder. Outside that
  window, `due_date` is NULL -- nothing to be overdue on.
- **First-ever due date**: a new ReminderRule with a 4-day default, created Jul
  26, no TimelineEvents yet -- baseline is the rule's creation date, so
  `due_date` = Jul 30 immediately, not undefined until first log.
- **Editing history**: you delete the most recent "Watering" log for a plant --
  §4.2 re-runs, falling back to the next most recent Watering log (or the rule's
  creation date if none remain) to recompute `due_date`.

This spec is implementation-ready: every mechanism has a defined algorithm, every
table has a defined schema, and every API interaction the frontend/Android app
needs is enumerated above.
