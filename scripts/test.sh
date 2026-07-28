#!/usr/bin/env bash
# Runs each project's build+test verification -- either everything, or a
# single project by name. This is what "the app builds, Docker works, the
# Android app compiles, and basic functionality is retained" means in
# practice for this repo; run it before publishing or deploying.
#
# Usage:
#   scripts/test.sh           # everything
#   scripts/test.sh server    # server: typecheck + vitest
#   scripts/test.sh web       # web: typecheck + vitest + production build
#   scripts/test.sh android   # Android: JVM unit tests + debug build
#   scripts/test.sh docker    # Docker image: build + smoke test

set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

TARGET="${1:-all}"
FAILED=()

run_server() {
  echo "==> server: install, typecheck, test"
  (cd server && npm install --silent && npm run typecheck && npm test) || FAILED+=("server")
}

run_web() {
  echo "==> web: install, typecheck, test, build"
  (cd web && npm install --silent && npm run typecheck && npm test && npm run build) || FAILED+=("web")
}

run_android() {
  echo "==> android: unit tests + debug build"
  if [ -z "${JAVA_HOME:-}" ] && [ -f "$HOME/.android-toolchain/env.sh" ]; then
    # shellcheck disable=SC1091
    source "$HOME/.android-toolchain/env.sh"
  fi
  if ! command -v java >/dev/null 2>&1; then
    echo "Skipping android: no JDK on PATH and ~/.android-toolchain/env.sh not found -- see android/README.md" >&2
    FAILED+=("android (skipped -- no JDK)")
    return
  fi
  (cd android && ./gradlew testDebugUnitTest assembleDebug --console=plain) || FAILED+=("android")
}

run_docker() {
  echo "==> docker: build + smoke test"
  if ! command -v docker >/dev/null 2>&1; then
    echo "Skipping docker: docker not found on PATH" >&2
    FAILED+=("docker (skipped -- no docker)")
    return
  fi
  ./scripts/docker-smoke-test.sh || FAILED+=("docker")
}

case "$TARGET" in
  server) run_server ;;
  web) run_web ;;
  android) run_android ;;
  docker) run_docker ;;
  all)
    run_server
    run_web
    run_android
    run_docker
    ;;
  *)
    echo "Unknown target: ${TARGET} (expected: server, web, android, docker, or all)" >&2
    exit 1
    ;;
esac

echo
if [ ${#FAILED[@]} -eq 0 ]; then
  echo "All checks passed."
  exit 0
else
  echo "FAILED: ${FAILED[*]}" >&2
  exit 1
fi
