# Form protocol v1

How the tasklist (host) talks to the HTML form of a process application (frame). Types and guards are in [`form-protocol.ts`](form-protocol.ts); both sides import that file.

## Loading

1. The tasklist asks the task manager for the form of a task (`GET /api/tasks/{key}/form`). For an HTML form it gets `{type: "html", formRef, data, readOnly, draftable}`.
2. It renders `<iframe sandbox="allow-scripts" src="/api/forms/{domain}/{formRef}/frame">`. The task manager fetches the bundle from the application and serves it with this policy:
   `Content-Security-Policy: sandbox allow-scripts; default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; font-src data:; connect-src 'none'; form-action 'none'; base-uri 'none'; frame-ancestors 'self'`.
   The frame therefore runs in an opaque origin (`"null"`): it cannot read the tasklist's DOM, cookies or storage. It cannot load or send anything, and only the tasklist may embed it.
3. The frame never receives data from the network. Everything it shows arrives in the `init` message.

## Messages

Every message is an object `{protocol: "tasklist-form", version: 1, type, payload}`.

| Direction | `type` | `payload` | Meaning |
| --- | --- | --- | --- |
| frame → host | `ready` | none | The bundle has loaded and listens. |
| host → frame | `init` | `{formRef, mode: "task" \| "start", data, readOnly, draftable}` | Render this form with this data. Sent once per load. |
| frame → host | `resize` | `{height}` | Content height in pixels, so the host can size the iframe. |
| frame → host | `draft` | `{data}` | Save without completing (only if `draftable`). |
| frame → host | `submit` | `{data}` | Complete the task or start the process. |
| host → frame | `result` | `{ok, message?, errors?: [{field, message}]}` | Outcome of the last `draft` or `submit`. On `ok: false` the frame shows the errors and stays editable. |

## Rules

- The host accepts a message only if `event.source` is the iframe's `contentWindow`, `event.origin` is `"null"`, and `protocol` and `version` match (`readFrameMessage`).
- The frame accepts a message only if `event.source` is `window.parent` (`readHostMessage`).
- Both sides post with target origin `"*"`. An opaque origin cannot be named, and `frame-ancestors 'self'` limits who can embed the frame.
- The host sends `init` once per `ready`. While `readOnly` is set, for example after the task was canceled, it answers `submit` and `draft` with `ok: false` and sends nothing.
- One bundle per application serves all its HTML forms and picks the view by `formRef`.
- A breaking change of this protocol increments `version`. Hosts and frames ignore messages of other versions.
