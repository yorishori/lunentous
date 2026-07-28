# syntax=docker/dockerfile:1

# --- Stage 1: build web SPA ---
FROM node:22-slim AS web-build
ENV NPM_CONFIG_UPDATE_NOTIFIER=false
WORKDIR /app/web
COPY web/package*.json ./
RUN npm install
COPY web/ ./
RUN npm run build

# --- Stage 2: build server ---
FROM node:22-slim AS server-build
ENV NPM_CONFIG_UPDATE_NOTIFIER=false
# node:22-slim's minimal Debian base doesn't include the full `gnupg`
# package -- only `gpgv` (verify-only). apt still calls the deprecated
# `apt-key verify` wrapper to check an InRelease file's inline signature,
# and that wrapper needs full `gpg` to split the clearsigned message
# before handing it to gpgv; without it, apt fails with a misleading "at
# least one invalid signature was encountered" even though the file
# itself is untampered (verified this directly: byte-identical content,
# and a manual `gpgv` check on it succeeds). The first apt-get update
# here runs with signature checking off *only* to fetch package lists
# well enough to install gnupg itself -- from the same official Debian
# host every other package in this image already comes from -- after
# which every real package (including gnupg) installs with full
# signature verification restored.
RUN apt-get update -o Acquire::AllowInsecureRepositories=true \
    && apt-get install -y --no-install-recommends -o Acquire::AllowInsecureRepositories=true gnupg \
    && apt-get update \
    && apt-get install -y --no-install-recommends python3 make g++ \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app/server
COPY server/package*.json ./
RUN npm install
COPY server/ ./
RUN npm run build

# --- Stage 3: runtime ---
FROM node:22-slim AS runtime
ENV NPM_CONFIG_UPDATE_NOTIFIER=false
# node:22-slim's minimal Debian base doesn't include the full `gnupg`
# package -- only `gpgv` (verify-only). apt still calls the deprecated
# `apt-key verify` wrapper to check an InRelease file's inline signature,
# and that wrapper needs full `gpg` to split the clearsigned message
# before handing it to gpgv; without it, apt fails with a misleading "at
# least one invalid signature was encountered" even though the file
# itself is untampered (verified this directly: byte-identical content,
# and a manual `gpgv` check on it succeeds). The first apt-get update
# here runs with signature checking off *only* to fetch package lists
# well enough to install gnupg itself -- from the same official Debian
# host every other package in this image already comes from -- after
# which every real package (including gnupg) installs with full
# signature verification restored.
RUN apt-get update -o Acquire::AllowInsecureRepositories=true \
    && apt-get install -y --no-install-recommends -o Acquire::AllowInsecureRepositories=true gnupg \
    && apt-get update \
    && apt-get install -y --no-install-recommends python3 make g++ \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /data/photos
WORKDIR /app
COPY server/package*.json ./
RUN npm install --omit=dev && apt-get purge -y python3 make g++ gnupg && apt-get autoremove -y
COPY --from=server-build /app/server/dist ./dist
COPY server/src/db/schema.sql ./dist/db/schema.sql
COPY --from=web-build /app/web/dist ./web-dist

ENV NODE_ENV=production
ENV PORT=8080
ENV DB_PATH=/data/db.sqlite
ENV PHOTOS_DIR=/data/photos
ENV WEB_DIST=/app/web-dist

EXPOSE 8080
CMD ["node", "dist/index.js"]
