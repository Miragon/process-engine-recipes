#!/usr/bin/env bash
# Prepares a fresh worktree for this recipe: checks the tools, then fetches the Maven and npm dependencies
# and the Docker images, so the first build, ./e2e.sh and `docker compose up` start without downloads.
# Orca runs it through the repository's orca.yaml when it creates a worktree. Running it again is harmless.
# It does not start the stack: parallel worktrees would compete for the same ports.
set -uo pipefail

recipe_dir=$(cd "$(dirname "$0")" && pwd)
cd "$recipe_dir"

problems=()
java_version=$(cat .java-version)
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"

if command -v "$java_bin" >/dev/null 2>&1; then
  found=$("$java_bin" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2}')
  if [ "$found" != "$java_version" ]; then
    problems+=("JDK $java_version is needed, found $found. Point JAVA_HOME to a JDK $java_version.")
  fi
else
  problems+=("JDK $java_version is missing. Install it and set JAVA_HOME.")
fi
command -v docker >/dev/null 2>&1 || problems+=("Docker or Podman with Compose is missing.")
command -v npm >/dev/null 2>&1 || problems+=("Node.js with npm is missing (only ./e2e.sh needs it; the build brings its own Node).")

if [ ${#problems[@]} -eq 0 ] || [ "${found:-}" = "$java_version" ]; then
  echo "==> Maven dependencies, Node and npm packages of the frontends"
  ./mvnw -B -q -DskipTests install || problems+=("./mvnw install failed; run it by hand to see why.")
fi
if command -v npm >/dev/null 2>&1; then
  echo "==> Playwright for ./e2e.sh"
  (cd e2e && npm ci --no-audit --no-fund --silent) || problems+=("npm ci in e2e failed.")
fi
if command -v docker >/dev/null 2>&1; then
  echo "==> Docker images of the stack"
  docker compose -f stack/docker-compose.yml pull --quiet || problems+=("docker compose pull failed.")
fi

if [ ${#problems[@]} -gt 0 ]; then
  printf 'camunda8-external-tasklist is not ready:\n'
  printf '  - %s\n' "${problems[@]}"
  exit 1
fi
cat <<'MESSAGE'
camunda8-external-tasklist is ready. Start it with:
  ./mvnw -B -DskipTests package && docker compose -f stack/docker-compose.yml up -d --wait
or run the end-to-end test with ./e2e.sh
MESSAGE
