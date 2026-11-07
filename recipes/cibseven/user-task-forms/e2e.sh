#!/usr/bin/env bash
set -euo pipefail

recipe_dir=$(cd "$(dirname "$0")" && pwd)
cd "$recipe_dir"

wait_for() {
  for _ in $(seq 1 90); do
    curl -fs -o /dev/null "$1" && return
    sleep 1
  done
  echo "$1 did not come up" >&2
  exit 1
}

./gradlew bootJar
(cd frontend && npm ci && npm run build)

java -jar build/libs/user-task-forms.jar > build/e2e-service.log 2>&1 &
service_pid=$!
frontend/node_modules/.bin/vite preview frontend --port 4173 --strictPort > build/e2e-frontend.log 2>&1 &
frontend_pid=$!
trap 'pkill -P "$frontend_pid" || true; kill "$service_pid" "$frontend_pid" 2>/dev/null || true' EXIT

wait_for http://localhost:8080/engine-rest/engine
wait_for http://localhost:4173/

cd e2e
npm ci
npx playwright install --with-deps chromium
npx playwright test
