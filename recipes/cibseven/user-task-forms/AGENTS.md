# AGENTS.md

Open this folder as the project. Build with `./gradlew build`, which runs `BikeRepairProcessTest`. Versions live in `gradle/libs.versions.toml` and `frontend/package.json`. `./e2e.sh` packages the jar, builds the frontend, starts both and runs the Playwright test in `e2e/` through all five user tasks. `UPDATE_SCREENSHOT=1 ./e2e.sh` also refreshes the screenshots in `images/`, except `0-modeler-form-type.png`, which is taken by hand in the Miragon BPMN Modeler.

The process has exactly five user tasks, one per form type, and nothing else. Keep it that way. Every deployed resource is listed in `src/main/resources/META-INF/processes.xml`; add new forms there. Change `bike-repair.bpmn` with the bpmn CLI, not by hand.

The service stays one module with a handful of classes: no layers, no service class between controller and `TaskService`. The frontend keeps its feature-based layout with about eight files: `app/` for the page shell, `features/tasks/` with `api/`, `components/` and `forms/`. No router, no generated client, no component library. Both resolution strategies live side by side in `frontend/src/features/tasks/forms/form-registry.ts`.
