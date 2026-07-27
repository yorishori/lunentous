# Lunentous

A self-hosted plant care tracker — care logging and scheduling, not
identification or diagnosis. See `spec_v1.md` for the full design (schema,
business logic, API surface).

This repo currently contains the **backend API + web frontend**. The Android
app is a separate, later effort (see spec §7) and is not part of this build.

## Stack

- **Server**: Node + TypeScript, Fastify, `better-sqlite3`, Zod
- **Web**: React + Vite + TypeScript, React Query, React Router
- **Deploy**: single Docker image serving both the API and the built SPA

## Status

This was scaffolded end-to-end (schema, business logic, full REST API, and
all web views) but has **not been run or tested yet** — dependencies aren't
installed. `better-sqlite3` needs a native build toolchain (`python3`, `make`,
`g++`) to compile from source if no prebuilt binary matches your Node version.

## Local setup

```bash
# 1. Install server deps and create your first API key (bootstraps the DB too)
cd server
npm install
npm run build
npm run cli:create-api-key -- --label "web"
# ^ copy the printed token, you'll paste it into the web login gate

# 2. Install web deps
cd ../web
npm install
```

### Run in dev mode (two terminals)

```bash
# terminal 1
cd server && npm run dev

# terminal 2
cd web && npm run dev
```

Vite's dev server proxies `/api` to `http://localhost:8080` (see
`web/vite.config.ts`). Open the Vite dev URL, paste your API key when
prompted, and you're in.

### Run via Docker (production-style, single container)

```bash
docker compose up --build
```

Then open `http://localhost:8080` (or whatever `PORT` you set) and hit
`/api/health` to confirm the API is up. You'll still need to create an API
key once, either by running the CLI script inside the container or locally
against the same DB file before starting the container.

### Publishing to GHCR

```bash
scripts/publish-ghcr.sh [tag]   # tag defaults to "latest"
```

Builds the image and pushes it to `ghcr.io/yorishori/lunentous`, tagged with
both `[tag]` and the current git short SHA. Prompts for a GitHub PAT
(`write:packages` scope) on every run instead of storing one anywhere.

## Environment variables

| Variable      | Default              | Purpose                                  |
|---------------|-----------------------|-------------------------------------------|
| `PORT`        | `8080`                | Server listen port                        |
| `DB_PATH`     | `./data/db.sqlite`    | SQLite file location                      |
| `PHOTOS_DIR`  | `./data/photos`       | Uploaded photo storage                    |
| `TZ`          | container default     | Sets "today" for reminder date logic (§8, §9) |
| `WEB_DIST`    | `../web/dist`         | Where the built SPA is served from        |

## What's not built yet / assumptions worth double-checking

- **API key management endpoints** (`GET/POST /api/api-keys`,
  `DELETE /api/api-keys/{id}`) aren't explicitly listed in spec §5, but are
  implied by §6's "Settings → API key management." Since these require an
  existing valid key to call, they don't reopen the unauthenticated
  bootstrap problem the spec's `api_keys` table comment warns about — only
  the CLI script can mint the *first* key.
- **Timeline pagination's `before` param** is implemented as a timeline
  event ID cursor (not a date), since the spec doesn't pin down its exact
  semantics.
- **Photo serving** (`/photos/:filename`) is unauthenticated, like the SPA's
  own static assets — `<img>` tags can't attach a Bearer header, and
  filenames are random UUIDs, not enumerable.
- Android app: not started.
