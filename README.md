# Lunentous

A self-hosted plant care tracker. Lunentous tells you when something's due —
once — logs what you actually did and when, and adapts future timing from
that real history instead of a fixed calendar. It doesn't identify plants or
diagnose problems; it's a logbook and a scheduler, not a plant ID app.

## Features

- **Adaptive reminders** — set a default watering/fertilizing/whatever
  interval per plant, with optional seasonal overrides (e.g. water less
  often over winter). Every time you log a completion, the next due date
  recalculates from that actual date, not the calendar.
- **Unified timeline** — a single per-plant feed mixes reminder completions
  and freeform journal notes, each with optional photos (multiple per
  entry).
- **Dashboard** — at a glance: what's overdue, what's coming up next, and a
  card per plant with a one-tap "log it" shortcut on its next reminder.
- **Calendar** — see everything for a given day: scheduled due dates,
  projected future occurrences, active seasonal phases (like a dormancy or
  repotting window), and logged entries — filterable by plant.
- **Custom reminder & phase types** — your own set of care activities and
  seasonal phases, each with a color and an icon, fully editable.
- **Backup** — one-click export of the whole database and photo library as
  a tarball.
- **No cloud, no subscription** — a single container, your own data, your
  own network.

## Quick start

The fastest path is Docker Compose:

```bash
git clone <this-repo>
cd lunentous
docker compose up --build -d
```

Then mint your first API key (the only step that has to happen outside the
web UI — see [Authentication](#authentication) for why):

```bash
docker compose exec lunentous node dist/cli/create-api-key.js --label "web"
```

Copy the printed token, open `http://localhost:8080`, and paste it into the
login screen. That's the whole setup.

## Using it

1. **Add a plant** from the dashboard. Name is the only required field —
   species, location, acquired date, notes, and a photo are all optional.
2. **Add a reminder rule** on the plant's page (e.g. "Watering, every 4
   days"). A short wizard walks you through the interval and any seasonal
   overrides. The first due date is set immediately, from the day you
   created the rule.
3. **Log things as you do them** — from the plant page, the dashboard's
   quick-log button, or the calendar. Logging a dated entry tagged with a
   reminder type recalculates that reminder's next due date from the date
   you actually logged, not from what was originally scheduled.
4. **Check the dashboard** for what's overdue or coming up, and the
   calendar for a fuller picture including active phases.

There's no "skip" or "snooze" — an unlogged reminder just becomes
increasingly overdue until you log it.

## Authentication

Lunentous is protected by a single kind of credential: a bearer API key,
checked on every `/api/*` request except `/api/health`. There's
deliberately no signup/login form backed by a database of accounts — mint
a key with the CLI script above, and from then on you can create or revoke
additional labeled keys from the Settings page in the app (useful for
giving a phone or another device its own key you can revoke independently).

The CLI is the *only* way to mint the first key. That's intentional: it
avoids ever exposing an unauthenticated "create a key" HTTP endpoint on a
service you're about to expose to your network.

## Deployment

### Docker Compose (recommended)

```yaml
services:
  lunentous:
    build: .
    ports:
      - "8080:8080"
    environment:
      - TZ=America/New_York   # your local timezone
    volumes:
      - ./data:/data
    restart: unless-stopped
```

`docker-compose.yml` in this repo is ready to use as-is — `docker compose up
--build -d` builds the image locally and starts it. Data (the SQLite file
and uploaded photos) lives in `./server/data`, bind-mounted into the
container, so it survives rebuilds and upgrades.

### Prebuilt image

Images can be published to `ghcr.io/yorishori/lunentous` via
`scripts/publish-ghcr.sh` (see that script's header for usage). If you're
pulling a published image instead of building locally, point
`docker-compose.yml`'s `build: .` at `image: ghcr.io/yorishori/lunentous:latest`
instead.

### Network exposure

There's no built-in TLS or reverse proxy. Put it behind whatever you
already use for self-hosted services on your network (a reverse proxy with
a certificate, a VPN like WireGuard/Tailscale, or just your LAN) — the app
itself only speaks plain HTTP.

### Configuration

| Variable      | Default              | Purpose                                          |
|---------------|----------------------|---------------------------------------------------|
| `PORT`        | `8080`               | Server listen port                                 |
| `DB_PATH`     | `./data/db.sqlite`   | SQLite file location                               |
| `PHOTOS_DIR`  | `./data/photos`      | Uploaded photo storage                             |
| `TZ`          | container default    | Sets what "today" means for reminder scheduling    |
| `WEB_DIST`    | `../web/dist`        | Where the built web app is served from             |

`TZ` matters: due dates and "today" are computed from the server process's
local date, with no per-request timezone negotiation. Set it to your own
timezone, not UTC, unless you actually live there.

### Backup

Settings → Backup → Download export streams a `.tar.gz` of the SQLite
database plus the full photo library. Restoring is just unpacking it back
into the volume the container reads from (`DB_PATH`'s and `PHOTOS_DIR`'s
parent) while the container is stopped.

## Local development

See [ARCHITECTURE.md](./ARCHITECTURE.md) for how the project is put
together, the full API reference, and where to make changes. Short version:

```bash
# server
cd server && npm install && npm run dev

# web (separate terminal)
cd web && npm install && npm run dev
```

Vite proxies `/api` to `http://localhost:8080` in dev, so both halves talk
to each other without any extra configuration.

## Stack

Node + TypeScript + Fastify + better-sqlite3 on the backend; React + Vite +
TanStack Query + React Router on the frontend. One Docker image serves
both. No external services, no cloud dependency, no telemetry.
