# Design of the central tasklist

The tasklist ([`tasklist/`](../../tasklist)) is the browser UI of the recipe. It follows the Miragon product design system, the skill `miragon-brand:modeler-tool-design`. This document records how its layout was chosen, what was built from it and where the implementation differs from the mockups and from the skill.

## How the design came about

1. **Mockups in HTML, not generated pictures.** No API key for image generation was available, and the user wanted mockups built by hand. [`mockups/generate.py`](mockups/generate.py) writes them as HTML and CSS on the Miragon tokens ([`cd-tokens.generated.css`](mockups/cd-tokens.generated.css), copied from the skill), with Geist, Lucide icons and the official comet. The data is the recipe's own: orders and payments of the MiraVelo shop, the demo users Alice, Bob and Carol, the forms *Check order* and *Check payment*.
2. **No photos.** Unsplash was not reachable from the design session. A tasklist needs no photos, so the mockups use none.
3. **Three directions, three screens each.** Every direction shows the task list, a task with its form, and a task whose process canceled it.

   | Direction | Idea | Screens |
   | --- | --- | --- |
   | A · Split desk | Filter rail, compact two-line list and the open task side by side, like a mail client | [list](variants/variant-a-list.png), [form](variants/variant-a-form.png), [canceled](variants/variant-a-canceled.png) |
   | B · Ledger | Dense table with filter chips; the task opens in a wide drawer over the dimmed table | [list](variants/variant-b-list.png), [form](variants/variant-b-form.png), [canceled](variants/variant-b-canceled.png) |
   | C · Workbench | A queue grouped by domain next to a focused workspace, with facts and history at the side | [list](variants/variant-c-list.png), [form](variants/variant-c-form.png), [canceled](variants/variant-c-canceled.png) |

4. **Comparison board.** gstack `design-shotgun` put the nine screenshots on one board ([`board.png`](board.png)): options A to C are Split desk, D to F Ledger and G to I Workbench, each as list, form and canceled.
5. **Decision.** On 2026-11-06 the user chose **Split desk**: option B of the board, the Split desk screen with the *Check payment* JSON form, was preferred and confirmed in chat. The choice covers all three screens of the direction.

## Why Split desk

- Filters, list and the open task stay visible together. Moving from one task to the next keeps the filters and the place in the list, which suits a tasklist people work through task by task.
- The form opens next to the list. Claiming, completing and the live notice of a cancellation happen without leaving the list.
- The counts in the rail show where the work is before anything is opened.

Ledger is denser but hides the work behind a drawer; Workbench focuses on one task but shrinks the filters to a segmented control.

## What was built

| Mockup | Implementation |
| --- | --- |
| Top bar: comet and "Central tasklist", search, "New order", user switcher | `desk/TopBar.tsx`. The search sets `q`; the start action lists the start forms of all applications (one button, or a menu for several); the switcher offers the task manager's demo users and remembers the choice in the browser. |
| Filter rail with counts | `desk/FilterRail.tsx`. Assignment, Domain, State and Candidate group from `GET /api/tasks/facets`, the counts of Mine and Unassigned from list totals. The selected entry has the 2px indicator. All filters live in the query string. |
| Two-line rows, selected row, "Newest first" | `desk/TaskList.tsx`. Name, subject, domain chip with dot, candidate group, due date, avatar, relative time, "High" badge; a sort menu; the list is a listbox (Up, Down, Home, End, Enter). |
| Detail: header, facts, form, action bar, history | `desk/TaskDetail.tsx` and its parts. Candidates change priority and due date in place. Unclaimed tasks show the claim card, the assignee the form; the action bar offers Claim or Unclaim, Save draft and Complete task as the task allows. |
| *Check payment* as a form of the tasklist | `forms/`. JSON Forms with the vanilla renderers in the Miragon style; field errors of a refused completion (422) appear at their controls. |
| *Check order* framed and labelled "Form provided by Orders" | `forms/FormFrame.tsx`. The application's HTML form in an iframe with `sandbox="allow-scripts"`, talking to the tasklist through the form protocol. |
| Canceled banner | `TaskStateBox` in `desk/TaskDetail.tsx`. The task manager's server-sent events bring the cancellation; the form turns read-only without a reload. |

States the mockups do not show follow the skill: skeletons while loading, the empty-state card with the comet and two actions, error boxes with Retry, a choice of demo user when none is chosen or the task manager answers 401, "The form is not ready yet" with an automatic retry after `Retry-After` while a task is still being synchronized (503), and toasts at the bottom centre.

## Deviations

1. **English interface.** The skill's default is German with "du", and only the modelers may be English. By the user's decision the tasklist is English throughout: texts, ARIA labels and `<html lang="en">`, in sentence case and the skill's English style ("Couldn't load the tasks", "Loading…").
2. **Light only.** `data-cd-theme="light"` on `<html>` and no appearance switch, as the skill prescribes for a tool that does not support dark yet.
3. **Status colors only from the roles.** Badges have a 1px border in `--cd-ui-<status>`, status boxes add the 4px bar; the mockups mixed softer borders by hand. Domain dots take palette colors in the order of the task manager's registry (Orders blue, Payments green, with a darker ring mixed from the same token).
4. **Icons at stroke 2.5**, as the skill requires; the mockups used Lucide's default 2.
5. **HTML forms submit themselves.** Form protocol v1 has no message for the tasklist to trigger a submission, so an HTML form keeps its own buttons (Approve, Decline). Its action bar holds Claim or Unclaim and the state; the disabled "Complete task" of the canceled mockup appears for JSON forms only.
6. **Docked action bar.** It sticks to the bottom of the detail pane while the form is longer than the screen. A refused submission ("Couldn't complete the task") shows right above it, the field errors at their controls and "1 field needs attention" in the bar.
7. **"Show form" on the claim card.** Besides "Claim task" the card offers to look at the read-only form first, the two actions of the empty-state pattern.
8. **The applications' forms decide the labels.** Read-only fields of a JSON form appear as facts like the mockup's Invoice section, and yes or no as a segmented choice; labels and groups come from the application's schema, so *Check payment* reads "Check" and "Amount received" instead of the mockup's sample texts.
9. **Facts and rows.** The facts always include "Created", and "Completed" or "Canceled" for a closed task. A row shows a due date only when the task has one; the mockup showed "—".
10. **Self-hosted font.** The app bundles Geist from `@fontsource-variable/geist`; the mockups load it from Google Fonts for convenience only.

## Notes on the implementation

- The tokens are copied unchanged into `tasklist/src/theme/cd-tokens.generated.css`. The aliases in `tasklist/src/theme/app.css` point to the roles `--cd-ui-*`; no component has a color value of its own.
- nginx serves the app with `script-src 'self'`. Ajv, the validator JSON Forms uses by default, generates code with `new Function`, which that policy forbids. JSON Forms calls only `compile` and `validate` on it, so the tasklist provides both on top of `@cfworker/json-schema`, which interprets schemas ([`schemaValidator.ts`](../../tasklist/src/forms/schemaValidator.ts)).

## Files

| Path | Content |
| --- | --- |
| [`board.png`](board.png) | The comparison board with all nine screens |
| [`variants/`](variants) | The screenshots `variant-{a,b,c}-{list,form,canceled}.png` |
| [`mockups/`](mockups) | The HTML and CSS of the mockups; `python3 mockups/generate.py` writes the HTML again |
