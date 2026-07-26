# --- Stage 1: build web SPA ---
FROM node:20-slim AS web-build
WORKDIR /app/web
COPY web/package*.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

# --- Stage 2: build server ---
FROM node:20-slim AS server-build
RUN apt-get update && apt-get install -y --no-install-recommends python3 make g++ \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app/server
COPY server/package*.json ./
RUN npm ci
COPY server/ ./
RUN npm run build

# --- Stage 3: runtime ---
FROM node:20-slim AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends python3 make g++ \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /data/photos
WORKDIR /app
COPY server/package*.json ./
RUN npm ci --omit=dev && apt-get purge -y python3 make g++ && apt-get autoremove -y
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
