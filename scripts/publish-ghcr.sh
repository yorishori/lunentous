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

# Resolved once, as an absolute path, before anything below `cd`s
# elsewhere -- re-deriving this from $BASH_SOURCE after a `cd` would
# resolve relative to the *new* directory instead of the original one
# (this bit a run from inside scripts/ itself: it broke the later
# docker-smoke-test.sh lookup).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

GITHUB_USER="yorishori"
IMAGE_NAME="ghcr.io/${GITHUB_USER}/lunentous"
TAG="${1:-latest}"

# The PAT is only ever meant to be entered at the read -rsp prompt below
# (never touching shell history or argv, per this script's whole point) --
# reject anything in $1 that looks like a GitHub token in case it ended up
# here by mistake (e.g. pasted onto the command line instead of at the
# prompt), rather than silently docker-tagging an image with a live
# credential.
case "$TAG" in
  ghp_*|gho_*|ghu_*|ghs_*|ghr_*|github_pat_*)
    echo "The tag argument looks like a GitHub token, not a tag -- refusing to continue." >&2
    echo "The PAT goes at the interactive prompt below, not on the command line." >&2
    echo "If you just ran this with a token as the tag: revoke that token now (it may" >&2
    echo "already be in your shell history and/or a local 'docker images' tag) and" >&2
    echo "re-run this script with no argument (or a real tag) instead." >&2
    exit 1
    ;;
esac

cd "$SCRIPT_DIR/.."

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
# --no-cache: a stale cached layer from an earlier, since-fixed Dockerfile
# state (e.g. a different base Node version) is exactly the kind of thing
# that should never ship silently -- every publish rebuilds from scratch.
docker build --no-cache -t "${IMAGE_NAME}:${TAG}" .

echo "Smoke-testing the built image before publishing..."
if ! SKIP_BUILD=1 "${SCRIPT_DIR}/docker-smoke-test.sh" "${IMAGE_NAME}:${TAG}"; then
  echo "Smoke test failed -- not publishing." >&2
  docker logout ghcr.io >/dev/null 2>&1 || true
  exit 1
fi

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
