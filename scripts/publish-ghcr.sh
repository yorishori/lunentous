#!/usr/bin/env bash
# Builds the Lunentous Docker image and publishes it to the GitHub Container
# Registry (ghcr.io). Prompts for a GitHub PAT on every run instead of
# storing one -- the token never touches disk or shell history.
#
# Usage: scripts/publish-ghcr.sh [tag]
#   tag defaults to "latest". The image is always also tagged with the
#   current short git SHA (if run inside a git repo) for traceability.
#
# The PAT needs the `write:packages` scope (and `read:packages` to pull).

set -euo pipefail

GITHUB_USER="yorishori"
IMAGE_NAME="ghcr.io/${GITHUB_USER}/lunentous"
TAG="${1:-latest}"

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required but not found on PATH" >&2
  exit 1
fi

read -rsp "GitHub PAT for ${GITHUB_USER} (write:packages scope): " GHCR_TOKEN
echo
if [ -z "${GHCR_TOKEN}" ]; then
  echo "No token entered, aborting." >&2
  exit 1
fi

echo "${GHCR_TOKEN}" | docker login ghcr.io -u "${GITHUB_USER}" --password-stdin
unset GHCR_TOKEN

echo "Building ${IMAGE_NAME}:${TAG}..."
docker build -t "${IMAGE_NAME}:${TAG}" .

GIT_SHA="$(git rev-parse --short HEAD 2>/dev/null || true)"
if [ -n "${GIT_SHA}" ]; then
  docker tag "${IMAGE_NAME}:${TAG}" "${IMAGE_NAME}:${GIT_SHA}"
fi

echo "Pushing ${IMAGE_NAME}:${TAG}..."
docker push "${IMAGE_NAME}:${TAG}"

if [ -n "${GIT_SHA}" ]; then
  echo "Pushing ${IMAGE_NAME}:${GIT_SHA}..."
  docker push "${IMAGE_NAME}:${GIT_SHA}"
fi

docker logout ghcr.io >/dev/null

echo
echo "Published:"
echo "  ${IMAGE_NAME}:${TAG}"
[ -n "${GIT_SHA}" ] && echo "  ${IMAGE_NAME}:${GIT_SHA}"
