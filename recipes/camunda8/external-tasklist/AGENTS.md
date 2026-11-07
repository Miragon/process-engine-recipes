# AGENTS.md

Open this folder as the project. Build with `./mvnw -B verify` (JDK 25; the build downloads its own Node for the frontends). `./e2e.sh` packages the services, starts `stack/docker-compose.yml` and runs the Playwright specs in `e2e/`; `KEEP_STACK=1` leaves the stack running. `./orca-setup.sh` prepares a fresh worktree.

The contracts are in `docs/architecture.md`, `tasklist-contract/` and the OpenAPI documents in `docs/api/`; a test fails when an API changes, refresh them with `./mvnw verify -Dapi.snapshot.update=true`. Keep the services independent: the applications share only `tasklist-contract`, call each other only through their REST APIs (never Camunda messages across services) and never call the task manager. Only `engine/camunda` in the applications and `camunda` in the task manager import Camunda; everything else uses the Process Engine API or the task manager's ports, and a test in each service checks it. The task manager's listener handler must stay a pure database write, because every user task in the cluster waits for it.

The tasklist UI is `tasklist/` (React, Vite, npm). `npm run dev` serves it with `/api` proxied to the task manager on port 8090 (`TASKMANAGER_URL` overrides it); after the task manager's API changed, refresh `docs/api/taskmanager.json` and run `npm run generate:api`. `UPDATE_SCREENSHOT=1 ./e2e.sh` refreshes `images/tasklist.png`.

Dependabot does not update the Node version in `pom.xml`, and the frontends stay on TypeScript 6 until typescript-eslint supports 7. `docs/history/` is the original thesis material and is not maintained.

## Design system (mandatory)

All UI work on the tasklist follows the Miragon product design system: the skill `miragon-brand:modeler-tool-design` from Miragon/corporate-identity, which loads with UI work when the plugin is installed. The tokens are copied unchanged into `tasklist/src/theme/cd-tokens.generated.css`; to update them, copy the skill's file again, never edit it. Components use only the roles `--cd-ui-*` and the aliases in `tasklist/src/theme/app.css`, no color values of their own. Geist is self-hosted, icons are Lucide, the comet `tasklist/public/miragon-komet-blau.svg` is the app icon and empty-state mark. The tasklist is light only and English by the user's decision; the design and its deviations are in `docs/design/decision.md`.
