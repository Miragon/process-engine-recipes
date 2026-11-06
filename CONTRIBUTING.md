# Contributing

See [AGENTS.md](AGENTS.md) for the rules of a recipe. Use Conventional Commits; `CI gate` must be green.

## Adding a recipe

1. Create `recipes/<engine>/<problem>/` with its `recipe.yaml`, `README.md` and `AGENTS.md`. Use an existing engine folder (`camunda7`, `camunda8`, `cibseven`) or add a new one. The recipe's `name` is `<engine>-<problem>`. Write the `README.md` along [`RECIPE_TEMPLATE.md`](RECIPE_TEMPLATE.md).
2. Add its row to the root `README.md`.
3. Add its isolated blocks to `.github/dependabot.yml`, labelled `recipe:<name>`.
4. Add yourself to `.github/CODEOWNERS` as owner of the recipe's path only, for example `/recipes/<engine>/<problem>/ @you`. The recipe is yours to review and keep up to date.

## Worktree setup with Orca

[`orca.yaml`](orca.yaml) prepares every worktree Orca creates: it runs each executable `recipes/<engine>/<problem>/orca-setup.sh`. A recipe opts in by adding that script. Keep it idempotent, let it check the tools the recipe needs and fetch dependencies and images, and do not start long-running services, because parallel worktrees would compete for the same ports. Orca reads `orca.yaml` from the main checkout and asks once whether you trust it; run a recipe's script by hand at any time.
