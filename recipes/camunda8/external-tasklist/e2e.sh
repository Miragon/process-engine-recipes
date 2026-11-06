#!/usr/bin/env bash
set -euo pipefail

recipe_dir=$(cd "$(dirname "$0")" && pwd)
cd "$recipe_dir"

compose() {
  docker compose -f "$recipe_dir/stack/docker-compose.yml" "$@"
}

cleanup() {
  status=$?
  if [ "$status" -ne 0 ]; then
    compose logs --no-color > e2e/stack.log 2>&1 || true
    echo "The stack logs are in e2e/stack.log"
  fi
  [ -n "${KEEP_STACK:-}" ] || compose down -v
  exit "$status"
}

./mvnw -B -q package -DskipTests
trap cleanup EXIT
# The reconciler runs every 5 s instead of 30 s, so the modification case does not wait long.
TASKMANAGER_RECONCILER_INTERVAL=5s TASKMANAGER_RECONCILER_LAG=3s compose up -d --wait --wait-timeout 300

cd e2e
npm ci --no-audit --no-fund
npx playwright test
