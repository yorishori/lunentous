# Lunentous — Architecture & Developer Reference

Technical reference for working on this codebase. `README.md` covers what
the app does and how to run it; this document covers how it's built, so you
can make changes without re-deriving everything from scratch.

## Repository layout

```
lunentous/
  Dockerfile                 multi-stage build: web -> server -> runtime
  docker-compose.yml
  scripts/publish-ghcr.sh    build + push to ghcr.io/yorishori/lunentous
  server/
    src/
      index.ts               Fastify bootstrap: plugins, auth hook, routes
      db/
        schema.sql            full DDL, applied on boot if not present
        migrate.ts             runs schema.sql, seeds default reminder types
        client.ts               better-sqlite3 singleton + photosDir setup
      lib/
        dates.ts                 pure calendar-date math (see below)
        recompute.ts              recompute_reminder_state
        auth.ts                    bearer-token verification
      routes/                one file per resource, registered in index.ts
      plugins/                static.ts (SPA + /photos), multipart.ts
      schemas/                shared zod/query-parsing helpers
      cli/create-api-key.ts  the only way to mint the first API key
  web/
    src/
      main.tsx, App.tsx      routing, providers, page-transition wrapper
      api/
        client.ts              apiFetch() wrapper (auth header, error shape)
        types.ts                 hand-written types mirroring server responses
      pages/                 one component per route (see Routing below)
      components/            see Component conventions below
      lib/
        dateMath.ts             client mirror of server date math (calendar
                                  view's *projected* occurrences only)
        icons.ts                 curated lucide-react icon set + lookup
        duplicateCheck.ts        shared same-day/same-type entry guard
        useMountTransition.ts    enter/exit animation lifecycle hook
      index.css               design system: CSS variables, animations,
                                every shared class (.card, .btn, .item-row,
                                .slideover-*, .modal-*, .task-row, etc.)
```

No test suite exists yet. Verification has been manual + a full Docker
build (`tsc` runs in both build stages, so type errors fail the build) plus
`curl`-driven smoke tests of the API during development.

## Domain model

SQLite, WAL mode, foreign keys on. All domain dates (`due_date`,
`event_date`, phase/override month-day bounds) are plain calendar dates —
no timezone component. "Today" is the server process's local date, set via
the `TZ` env var. `created_at`/`updated_at` are housekeeping timestamps only
and are exempt from that rule.

```
reminder_types    id, name (unique), icon, color, archived, created_at
phase_types       id, name (unique), color, archived, created_at
                  (no icon column — see Types below)

plants            id, name, species, location, acquired_date,
                  avatar_photo_id -> photos, general_notes, archived,
                  created_at, updated_at

reminder_rules    id, plant_id, reminder_type_id, default_interval_days,
                  created_at
                  UNIQUE(plant_id, reminder_type_id) — one rule per pair

override_periods  id, reminder_rule_id, start_month/day, end_month/day,
                  interval_days
                  seasonal exceptions to a rule's default interval;
                  interval_days = NULL means "paused" during this window

reminder_states   id, plant_id, reminder_type_id, due_date, notified
                  UNIQUE(plant_id, reminder_type_id) — the single
                  materialized "next occurrence"; NULL due_date = paused

plant_phase_windows  id, plant_id, phase_type_id, start_month/day,
                     end_month/day, notes
                     purely informational, never drives reminders

timeline_events   id, plant_id, reminder_type_id (nullable), event_date,
                  text, created_at
                  the unified log: reminder_type_id set = a reminder
                  completion, NULL = a plain journal note. There is no
                  separate "completion" record — a timeline_events row
                  IS the completion.

photos            id, plant_id, timeline_event_id (nullable), file_path,
                  created_at
                  timeline_event_id NULL = a plant's standalone avatar

api_keys          id, key_hash (sha256 hex), label, created_at
```

Indexes exist on the columns the app actually queries by: timeline lookups
by plant (+ type, for reminder completion history), reminder-state due-date
sweeps, override periods and phase windows by their owning row, photos by
plant/event.

**`reminder_types` has an `icon` column; `phase_types` does not.** This
asymmetry is real and intentional (phase types never needed an icon in the
UI), not an oversight — if you're touching the shared type-CRUD code
(`archivableTypeFactory.ts`, `TypeManager.tsx`, `TypeForm.tsx`), the `hasIcon`
flag threading through that code exists specifically to keep the SQL
column list correct per table. Getting this wrong previously caused a
`table phase_types has no column named icon` runtime error — the INSERT/
UPDATE statements build their column list conditionally on `hasIcon` for
exactly this reason.

## Core business logic

Everything here is implemented twice on purpose: once in
`server/src/lib/dates.ts` (source of truth, runs on write) and once in
`web/src/lib/dateMath.ts` (client mirror, used **only** to compute the
calendar view's dashed *projected* future occurrences — display math that's
never written back to the database).

**`resolveInterval(rule, overridePeriods, date)`** — for a given date, walks
the rule's override periods looking for one whose month-day range contains
that date (ranges may wrap the year boundary, e.g. Nov 1 → Feb 28); returns
the first match's `interval_days`, or the rule's `default_interval_days` if
none match. Either can be `null`, meaning "paused."

**`recomputeReminderState(plantId, reminderTypeId)`** (`recompute.ts`) —
the single function that keeps `reminder_states` correct. It:
1. Looks up the rule for this (plant, type) pair. No rule → delete any
   existing state and return.
2. Finds the most recent `timeline_events` row for this (plant, type),
   ordered by `event_date DESC, id DESC`. If none exists yet, the baseline
   is the rule's own `created_at` date — so a brand-new rule has an
   immediate first due date instead of sitting undefined.
3. Resolves the interval active *on that baseline date* and adds it to get
   `due_date` (or `NULL` if the resolved interval is `null`).
4. Upserts `reminder_states`, resetting `notified = 0`.

Critically, this always recomputes **from scratch** off the most recent
qualifying event — it never just nudges the previous due date forward. That's
what makes edits and deletions behave correctly: deleting the most recent
"Watering" log falls back to the next most recent one (or the rule's
creation date if none remain), not to some stale cached value.

**When to call it** — every route that could change the answer calls
`recomputeReminderState` at the end of its handler:
- creating a reminder rule (baseline = rule creation, no events yet)
- creating, editing (date or type change), or deleting a timeline event
  with a non-null `reminder_type_id`
- editing a rule's `default_interval_days` or its override periods
- deleting a rule (removes the state row entirely, no recompute needed)

If you add a new way to create/edit/delete a timeline event or a reminder
rule, you almost certainly need to call this too.

**Overdue math** is *never stored*. `days_overdue = today - due_date`
(via `daysBetween`) is computed at read time, wherever it's displayed —
`routes/plants.ts`, `routes/reminderStates.ts`, and the calendar's client-side
projection all do this independently. One notification-style flag
(`notified`) is stored, for the (currently unimplemented) on-device polling
use case described in the original spec — the web app doesn't use it.

## REST API reference

Base path `/api`. Every route requires `Authorization: Bearer <token>`
except `GET /api/health`. Bodies are JSON except where noted as multipart.
Auth is enforced by a single global `onRequest` hook (`lib/auth.ts`) that
skips anything not under `/api/` (so the SPA's static assets and uploaded
photos are reachable without a token — see Photo serving below).

| Method | Path | Notes |
|---|---|---|
| GET | `/api/health` | unauthenticated |
| GET | `/api/plants?archived=` | |
| POST | `/api/plants` | |
| GET | `/api/plants/:id` | includes `active_phase_windows` (today) and `reminder_states` with computed `days_overdue` |
| PATCH | `/api/plants/:id` | |
| POST | `/api/plants/:id/archive`  / `/unarchive` | |
| POST | `/api/plants/:id/avatar` | multipart, single file |
| GET | `/api/reminder-types?archived=` | |
| POST | `/api/reminder-types` | |
| PATCH | `/api/reminder-types/:id` | |
| POST | `/api/reminder-types/:id/archive` / `/unarchive` | |
| GET/POST/PATCH/archive/unarchive | `/api/phase-types/...` | identical shape to reminder-types, minus `icon` |
| GET | `/api/plants/:plantId/reminder-rules` | includes `override_periods` |
| POST | `/api/plants/:plantId/reminder-rules` | triggers recompute |
| PATCH | `/api/reminder-rules/:id` | replaces `default_interval_days` and/or the full `override_periods` array; triggers recompute |
| DELETE | `/api/reminder-rules/:id` | cascades `override_periods`; deletes the `reminder_states` row |
| GET | `/api/reminder-states?due_before_or_on=&notified=` | global, joined with plant/type names, icon, color |
| GET | `/api/plants/:plantId/reminder-states` | per-plant, with `days_overdue` |
| POST | `/api/reminder-states/:id/mark-notified` | |
| GET/POST | `/api/plants/:plantId/phase-windows` | |
| PATCH/DELETE | `/api/phase-windows/:id` | |
| GET | `/api/plants/:plantId/timeline?reminder_type_id=&limit=&before=&from=&to=` | see Pagination note below |
| POST | `/api/plants/:plantId/timeline` | multipart: `event_date`, `reminder_type_id`?, `text`?, any number of `photo` file parts |
| PATCH | `/api/timeline/:id` | JSON only — `event_date`/`reminder_type_id`/`text`; triggers recompute if date or type changed |
| POST | `/api/timeline/:id/photos` | multipart, appends photos to an existing entry (kept separate from PATCH so field updates stay JSON-only) |
| DELETE | `/api/timeline/:id` | deletes photo files from disk too; triggers recompute |
| DELETE | `/api/photos/:id` | removes one photo (DB row + file) without touching its entry |
| GET | `/api/api-keys` | list (id, label, created_at — never the hash or plaintext) |
| POST | `/api/api-keys` | creates a new key, returns the plaintext **once** |
| DELETE | `/api/api-keys/:id` | revoke |
| GET | `/api/export` | streams a `.tar.gz` of the SQLite file + photos dir |

**Pagination note**: `before` on the timeline endpoint is a *timeline event
ID cursor*, not a date — pass the last event's `id` from the previous page
to get strictly older entries (tie-broken by `id` for same-day entries).
`from`/`to` are plain inclusive date-range filters, added specifically so
the calendar view can fetch exactly one month's entries per plant instead
of paginating through everything.

**`/api/api-keys` isn't in the original spec's endpoint list** but is
implied by the "Settings → API key management" feature — and since reaching
it requires an already-valid key, it doesn't reopen the unauthenticated-
bootstrap problem the CLI script exists to avoid. That script
(`server/src/cli/create-api-key.ts`) is still the only way to mint the
*first* key.

## Authentication

- `api_keys.key_hash` stores a sha256 hex digest — never the plaintext.
- The CLI script (`npm run cli:create-api-key -- --label "x"`) generates a
  random token, hashes it, inserts the row, and prints the plaintext once.
  This is the only unauthenticated way to create a key, by design — see
  `lib/auth.ts` and the `api_keys` schema comment for why an HTTP bootstrap
  endpoint was deliberately avoided.
- `requireApiKey` (global `onRequest` hook) checks `Authorization: Bearer
  <token>` against the hashed keys for anything under `/api/`, except
  `/api/health`.
- The web app stores the token in `localStorage` (`web/src/api/client.ts`)
  and attaches it via `apiFetch()`. A 401 clears it and redirects to
  `/login`.

## Photo serving

Uploaded photos are served from `PHOTOS_DIR` at `/photos/:filename`,
**unauthenticated** — registered as a second `@fastify/static` instance in
`plugins/static.ts` (`decorateReply: false` since the SPA's own static
registration already claims that). This is deliberate, not an oversight:
`<img>` tags can't attach a bearer header, and filenames are
`crypto.randomUUID()`-generated, not enumerable. Same trust model as the
SPA's own JS/CSS assets.

## Frontend architecture

### Routing

`App.tsx` — a `RequireAuth` wrapper redirects to `/login` when no token is
stored; everything else lives under one shell (`Nav` topbar + `<main
className="app-main">`). Routes: `/`, `/plants/new`, `/plants/:id`,
`/calendar`, `/reminder-types`, `/phase-types`, `/settings`. A
`PageTransition` wrapper keyed on `location.pathname` forces a remount
per navigation so the page fade-in animation replays on every route change
(without it, `.app-main` never remounts and the CSS animation only plays
once, on first load).

### Data fetching

TanStack Query throughout, no other client state management. Conventions
worth knowing before adding a new query:
- Query keys are arrays like `["plant", plantId]`, `["reminder-rules",
  plantId]`, `["timeline", plantId, filterTypeId, cursor]` — mutations
  invalidate the specific keys they affect, not a blanket refetch-everything.
  If you add a mutation, check what else reads data it could have changed
  (e.g. logging a timeline entry should usually invalidate `reminder-states`
  too, since it may have triggered a recompute).
- Multi-plant views (Calendar) use `useQueries` to fan out one query per
  plant (rules, phase windows, timeline-in-range) rather than a bespoke
  aggregate endpoint — acceptable at the personal/self-hosted scale this
  app targets.
- `apiFetch<T>(path, { method, body, isFormData })` is the only HTTP client.
  Non-multipart bodies are JSON-stringified automatically; pass
  `isFormData: true` with a `FormData` body for uploads.

### Component conventions

- **SlideOver** (`components/SlideOver.tsx`) — right-side drawer, used for
  every create/edit form tied to a specific plant (reminder rules, phase
  windows, timeline entries, plant details, reminder/phase type edits).
- **Modal** (`components/Modal.tsx`) — centered dialog, used where there's
  no natural "owning" page to slide out from (calendar's create-entry flow,
  which needs a plant *picker*) and for small popups (`QuickLogModal`).
- **ConfirmDialog** — a `Modal` wrapper for yes/no confirmations, used for
  destructive actions and the "you already logged this today" duplicate
  warning (`lib/duplicateCheck.ts`'s `hasDuplicateEntry`, called before any
  new timeline entry is created — TimelineEntryForm, QuickLogModal, and
  Dashboard's mark-as-done all do this check).
- **Toast** (`components/Toast.tsx`) — a `ToastProvider` context wraps the
  whole app in `main.tsx`; `useToast().showToast(message, type)` fires an
  auto-dismissing notification. Every save/delete/archive mutation across
  the app calls this in its `onSuccess`/`onError`.
- **TypeManager / TypeForm** — the pattern for "a list of archivable,
  colored, optionally-iconed things" (reminder types, phase types). Both
  are driven by the same components with a `hasIcon` flag, mirroring the
  server's `archivableTypeFactory.ts`. If you add a third type like this,
  reuse these rather than writing new CRUD UI.
- **IconPicker / ColorPicker** — searchable dropdowns, not native `<input
  type="color">`/a plain text field. `lib/icons.ts` holds the curated
  lucide-react icon allowlist (icon *names* are stored as plain strings in
  `reminder_types.icon`, e.g. `"Droplet"` — must match a lucide-react
  export name exactly, looked up dynamically via `getIcon()`).
  `ColorPicker` offers the Catppuccin Mocha accent palette as swatches plus
  a native color input for anything else.
- **Skeleton / Spinner / LoadingBar** — perceived-latency signals, not
  artificial delay. `LoadingBar` reads TanStack Query's global
  `useIsFetching()`/`useIsMutating()` counts and shows an indeterminate
  top-of-viewport bar whenever either is nonzero — no per-request wiring
  needed. `Spinner` is used inline on buttons via each mutation's
  `isPending`. `Skeleton` replaces "Loading…" text on the two views that had
  a noticeable blank-state flash (Dashboard, PlantDetail).
- **`useMountTransition(open, durationMs)`** (`lib/useMountTransition.ts`)
  — both `Modal` and `SlideOver` use this so closing plays an exit
  animation instead of the DOM node just vanishing: it keeps the component
  mounted for `durationMs` after `open` goes false, exposing a `closing`
  boolean the component uses to swap in an exit CSS animation class before
  actually unmounting.

### Design system (`index.css`)

Two palettes as CSS custom properties on `:root`, switched via
`prefers-color-scheme` (no in-app toggle) — **Catppuccin Mocha** (dark,
default) and **Catppuccin Latte** (light). Semantic tokens layered on top
of the raw palette: `--accent`, `--overdue`/`--due-today`/`--ok` (+ each a
`-soft` translucent variant for badge backgrounds), `--surface`,
`--surface-hover`, `--border`, `--text`/`--text-muted`, `--radius`/
`--radius-sm`/`--radius-pill`, `--ease` (the one easing curve used
everywhere), `--shadow-color`.

Font is JetBrains Mono throughout (`@fontsource/jetbrains-mono`, self-hosted
— no external font CDN). Buttons, badges, and pills favor fully-rounded
corners; cards use `--radius`. Every interactive surface has a hover/active
transition using `--ease`; icon-only buttons additionally get a semantic
hover tint via modifier classes — `.icon-btn-edit` (accent), `.icon-btn-delete`
(red), `.icon-btn-archive` (amber), `.icon-btn-done` (green) — apply the
right one rather than leaving a bare `.icon-btn`.

Layout is a single topbar (brand + nav links spread across, no sidebar) —
see `.topbar`/`.topbar-nav` — plus a centered `max-width: 1100px` content
column (`.app-main`).

### Calendar internals

`pages/Calendar.tsx` is the most involved page — worth understanding before
touching it. For the visible month and the currently-selected plant filter,
it builds three parallel maps keyed by ISO date:
- `markersByDate` — what the grid cells render (small colored pills):
  `kind: "due" | "projected" | "logged"`, purely visual, no click handler
  (the whole day cell is the click target, not individual markers).
- `phaseBandsByDate` — a colored strip per day a phase window is active,
  computed by walking every day of the month against each plant's phase
  windows via `dateInRange`.
- `dayDetailsByDate` — the richer data backing the bottom detail panel
  (shown when a day is clicked): due/projected reminders as read-only rows,
  logged `timeline_events` as full cards with edit/delete actions.

`due` markers come straight from `/api/reminder-states`; `projected` ones
are computed client-side by `projectOccurrencesInRange` (walks
`resolveInterval` forward from the real due date, collecting every
occurrence inside the visible month, capped at 500 iterations) — display
only, never written back. Both are filtered to whichever plants are
selected in the top `MultiSelect` *before* being placed on the maps, which
is why the multiselect filters the bottom detail list too: it's reading
from the same already-filtered data, not filtering the panel separately.

Creating a new entry is exclusively via the top "New entry" button (opens a
`Modal` with a plant-selectable `TimelineEntryForm`), smart-prefilled with
whatever day is currently selected, if any. Clicking a day never opens the
create form — only selects it for the detail panel.

## Build & deploy

`Dockerfile` is a 3-stage build: `web-build` (`npm run build` → static
`dist/`), `server-build` (needs `python3 make g++` to compile
`better-sqlite3` from source), `runtime` (copies both build outputs, purges
the compiler toolchain afterward, ships `node:20-slim`). `docker-compose.yml`
mounts `./server/data` as `/data` — **mount the whole directory, not a
single file for the DB path**; Docker's bind-mount behavior creates a
*directory* in place of a missing file source, which silently breaks
`better-sqlite3` (`SQLITE_CANTOPEN`) — this bit us once already.

`scripts/publish-ghcr.sh` builds and pushes to
`ghcr.io/yorishori/lunentous`, tagged with both a given tag (default
`latest`) and the current git short SHA. Prompts for a GitHub PAT on every
invocation via `read -s` and pipes it to `docker login --password-stdin` —
never stored, never in shell history.

## Extending this app

**New CRUD resource shaped like reminder/phase types** (archivable, named,
colored): add a table, then a thin `routes/*.ts` that calls
`registerArchivableTypeRoutes` with the right `table`/`usageTable`/
`hasIcon`; on the frontend, a page that renders `<TypeManager
basePath="..." hasIcon={...} queryKey="..." noun="..." />` — no new
components needed.

**New page**: add the route in `App.tsx`'s inner `<Routes>`, add a
component under `pages/`, add a nav link in `components/Nav.tsx`.

**New endpoint that changes reminder timing**: if it creates, edits, or
deletes anything that touches `timeline_events.reminder_type_id` or a
`reminder_rules` row, call `recomputeReminderState(plantId,
reminderTypeId)` at the end of the handler — see Core business logic above
for the exact trigger list.

**New mutation on the frontend**: invalidate every query key it could have
made stale (not just the obvious one), and call `showToast()` in both
`onSuccess` and `onError` — this is the established convention across every
existing mutation in the codebase, not an exception.

## Android

`android/` has a toolchain scaffold, an app shell, a complete data layer,
and the Dashboard + Plant Detail screens (phases 1–3 of the build plan).
Built so far: a Catppuccin-themed adaptive nav shell (bottom bar in
portrait / rail in landscape, icons-only, 5 destinations mirroring the
web's nav); an optional server connection (URL + API key, Keystore-
encrypted, managed from Settings rather than a login gate — the app works
fully standalone); a Room database mirroring every server entity
(local-ID-first, server-ID nullable and lazily resolved); a Retrofit
client (`LunentousApi`) covering the full REST surface documented above;
repositories that read from Room and write through to the network when
connected, or straight to Room (local-only) when not, resolving related
entities' server IDs internally so callers only ever pass local IDs — no
outbox/write-queue yet, that's a later phase; a Dashboard screen (overdue/
next-tasks lists, plant grid, mark-done, pull-to-refresh); and a Plant
Detail screen (hero card with archive/unarchive, reminder rules, phase
windows, and a timeline feed, each with a shared Compose `ModalBottomSheet`
create/edit form — collapsed from the web's multi-step wizard into a
single scrollable sheet, and with photo capture/upload deferred to the
camera-capture phase). Calendar, Reminder/Phase Types, and the rest of
Settings still render placeholders. See `android/README.md` for the
(sudo-free) toolchain setup and how to build/install on a physical device,
and the architecture plan this is being built from for the full design
(the offline-first outbox/conflict/provisional-due-date design, the
initial-connect merge strategy, and the remaining screen-by-screen work).

The original spec's design for this app was on-device `WorkManager` polling
using `reminder_states.notified`; the `notified` column and the
`?due_before_or_on=&notified=` query support on `/api/reminder-states`
exist for that use case but are unused by the web app.

## Not built

- **Automated tests** — none exist. Changes have been verified by `tsc`
  (via the Docker build, which fails on type errors in either package) and
  manual `curl`/browser testing.
