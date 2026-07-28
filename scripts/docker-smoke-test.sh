#!/usr/bin/env bash
# Builds (unless SKIP_BUILD=1) and smoke-tests the Lunentous Docker image:
# runs it, waits a few seconds, and confirms it's still up and actually
# serving /api/health -- not just "didn't error during build". Exits
# non-zero (with container logs) if it crashed or never came up, e.g. the
# exit-139 native-module segfault this was written after.
#
# Usage: scripts/docker-smoke-test.sh [image-tag] [host-port]
#   image-tag defaults to "lunentous-smoke-test:local", built fresh from
#   the repo root's Dockerfile unless SKIP_BUILD=1 (e.g. publish-ghcr.sh
#   already built the exact tag it wants tested).
#   host-port defaults to 18080.

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

IMAGE_TAG="${1:-lunentous-smoke-test:local}"
HOST_PORT="${2:-18080}"
CONTAINER_NAME="lunentous-docker-smoke-test"

if [ "${SKIP_BUILD:-}" != "1" ]; then
  echo "Building ${IMAGE_TAG}..."
  docker build -t "${IMAGE_TAG}" .
fi

docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
cleanup() { docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "Starting a container from ${IMAGE_TAG}..."
docker run -d --name "${CONTAINER_NAME}" \
  -p "${HOST_PORT}:8080" \
  -e DB_PATH=/tmp/smoke.sqlite -e PHOTOS_DIR=/tmp/smoke-photos \
  "${IMAGE_TAG}" >/dev/null

sleep 3

if ! docker ps --filter "name=${CONTAINER_NAME}" --filter "status=running" --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
  echo "FAILED: container exited instead of staying up." >&2
  echo "--- container logs ---" >&2
  docker logs "${CONTAINER_NAME}" >&2 || true
  exit 1
fi

echo "Checking the health endpoint..."
if ! curl -fsS "http://localhost:${HOST_PORT}/api/health" >/dev/null; then
  echo "FAILED: container is running but /api/health didn't respond." >&2
  echo "--- container logs ---" >&2
  docker logs "${CONTAINER_NAME}" >&2 || true
  exit 1
fi

echo "Docker smoke test passed."
