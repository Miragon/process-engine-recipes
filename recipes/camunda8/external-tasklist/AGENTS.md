# AGENTS.md

Open this folder as the project. Build with `./mvnw -B verify` (JDK 25; the build downloads its own Node for the micro-frontend). `./e2e.sh` packages the services, starts `stack/docker-compose.yml` and runs the Playwright specs in `e2e/`; `KEEP_STACK=1` leaves the stack running. `./orca-setup.sh` prepares a fresh worktree.

The contracts are in `docs/architecture.md`, `tasklist-contract/` and the OpenAPI documents in `docs/api/`; a test fails when an API changes, refresh them with `./mvnw verify -Dapi.snapshot.update=true`. Keep the services independent: the applications share only `tasklist-contract`, call each other only through their REST APIs (never Camunda messages across services) and never call the task manager. Only `engine/camunda` in the applications and `camunda` in the task manager import Camunda; everything else uses the Process Engine API or the task manager's ports, and a test in each service checks it. The task manager's listener handler must stay a pure database write, because every user task in the cluster waits for it.

Dependabot does not update the Node version in `pom.xml`, and the micro-frontend stays on TypeScript 6 until typescript-eslint supports 7. `docs/history/` is the original thesis material and is not maintained.
