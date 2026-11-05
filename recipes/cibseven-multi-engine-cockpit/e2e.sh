#!/usr/bin/env bash
set -euo pipefail

recipe_dir=$(cd "$(dirname "$0")" && pwd)
cd "$recipe_dir"

compose() {
  docker compose -f "$recipe_dir/stack/docker-compose.yml" "$@"
}

start_instance() {
  compose exec -T "$1" curl -fs -o /dev/null -X POST -H 'Content-Type: application/json' -d '{}' \
    "http://localhost:8080/engine-rest/process-definition/key/$2/start"
}

./gradlew bootJar
trap 'compose down' EXIT
compose up -d --wait

start_instance membership-service newsletter-subscription
start_instance leasing-service bike-leasing
start_instance logistics-service welcome-package

cd e2e
npm ci
npx playwright install --with-deps chromium
npx playwright test
