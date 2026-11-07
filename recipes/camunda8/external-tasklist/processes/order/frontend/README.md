# Order forms

The HTML forms of the order application, `start-order@1` and `check-order@1`, as one React bundle. The tasklist loads it into a sandboxed frame and talks to it through the [form protocol](../../../tasklist-contract/src/main/typescript/PROTOCOL.md). The frame's content security policy allows no requests, so the build inlines scripts, styles and the Geist font into `../target/classes/tasklist/forms/index.html`, which the order application serves at `GET /tasklist/v1/forms/{formRef}/frame`.

Maven builds it with its own Node: `npm ci`, `lint`, `typecheck`, `build`, and `test` (skipped with `-DskipTests`).

| Command | What it does |
| --- | --- |
| `npm run dev` | Shows the forms with sample data and a stand-in for the tasklist. Open `/?form=check-order@1`, add `&readOnly` or `&result=error`. |
| `npm run lint` / `typecheck` / `test` | ESLint, TypeScript and Vitest |
| `npm run build` | The single-file bundle |

The styles use the Miragon product tokens in `src/styles/cd-tokens.generated.css`, copied unchanged from the `modeler-tool-design` skill. The forms stay light, because the protocol does not tell the frame which theme the tasklist shows.
