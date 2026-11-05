# AGENTS.md

Open this folder as the project. Build with `./gradlew build`. Versions live in `gradle/libs.versions.toml`. `./e2e.sh` packages the three services, starts `stack/docker-compose.yml`, starts one process instance per service and runs the Playwright test in `e2e/`. `UPDATE_SCREENSHOT=1 ./e2e.sh` also refreshes `images/engine-switch.png`.

The services contain no business logic on purpose. Keep them independent: no shared module, each one only `Application`, `application.yaml` and one BPMN. The engine list of the cockpit lives in `stack/webclient/application.yaml`.
