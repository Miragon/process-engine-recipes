# process-engine-recipes

[![CI](https://github.com/Miragon/process-engine-recipes/actions/workflows/build.yaml/badge.svg?branch=main)](https://github.com/Miragon/process-engine-recipes/actions/workflows/build.yaml) [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

One concrete process-engine problem per folder, solved in working code. Find your problem, read how it was solved and take what you need: a cookbook, not a meal kit.

## Why

Most engine material shows how to use an engine. The harder questions come later: how do I put single sign-on in front of Cockpit, or run my own tasklist on Zeebe? [process-engine-blueprints](https://github.com/Miragon/process-engine-blueprints) shows whole services that automate a process. A recipe here shows one solution:

- **One problem.** A recipe contains the code for its problem and nothing else.
- **As it is.** Each recipe exists in one language. There are no variants and no starter: copy the folder or the part you need.
- **Self-contained.** Every recipe builds on its own, and nothing is shared between recipes.
- **Checked by CI.** Each recipe is built on every change that touches it and names the engine version it was built for.

## On the menu

| Problem | Recipe | Engine | Language |
| --- | --- | --- | --- |
| Single sign-on for the web applications | [`camunda7-cockpit-sso`](recipes/camunda7/cockpit-sso/README.md) | Camunda 7.24 | Java |
| One cockpit for the engines of several services | [`cibseven-multi-engine-cockpit`](recipes/cibseven/multi-engine-cockpit/README.md) | CIB seven 2.2 | Java |
| Every user task form type side by side | [`cibseven-user-task-forms`](recipes/cibseven/user-task-forms/README.md) | CIB seven 2.2 | Java |
| Process tests with real delegates, natively and behavior-driven | [`cibseven-embedded-process-tests`](recipes/cibseven/embedded-process-tests/README.md) | CIB seven 2.2 | Java |
| Engine plugin that streams process events to an event bus | [`cibseven-engine-plugin-history-events`](recipes/cibseven/engine-plugin-history-events/README.md) | CIB seven 2.2 | Java |
| One tasklist for the user tasks of several process applications | [`camunda8-external-tasklist`](recipes/camunda8/external-tasklist/README.md) | Camunda 8.10 | Kotlin |

Looking for an older showcase? They are preserved at the tag [`archive/showcases-2026`](https://github.com/Miragon/process-engine-recipes/tree/archive/showcases-2026).

## Working on a recipe

Open the recipe's folder, not the repository root. Its `README.md` and `AGENTS.md` treat that folder as the project.

Every recipe README follows [`RECIPE_TEMPLATE.md`](RECIPE_TEMPLATE.md), so they all read the same way: **Ingredients** (versions and prerequisites), **How it works**, **Cook it** (run it locally), **Taste test** (the automated proof), **Take it home** (copy it into your project) and **Kitchen notes** (gotchas).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Distributed under the MIT License. See `LICENSE` file for more information.
