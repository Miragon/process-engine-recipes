# Architecture

This document describes the contracts between the parts of the recipe. The [README](../README.md) explains how to run it.

## Components

| Component | Port | Owns |
| --- | --- | --- |
| Camunda 8.10 orchestration cluster | 8080 | process state, user task state, variables; Operate at `/operate` |
| Elasticsearch | 9200 | Camunda's secondary storage (search, Operate) |
| Task manager | 8090 | the task projection, the API of the tasklist, routing to the applications |
| Order application (`domain=order`) | 8081 | orders, the order process and its forms |
| Payment application (`domain=payment`) | 8082 | invoices, the payment process and its forms |
| Tasklist | 3000 | the browser UI; talks only to the task manager |
| PostgreSQL | 5432 | one database each for the task manager, orders and payments |

## Integration contract

### 1. BPMN conventions for a process application

A process application joins the tasklist by modelling its user tasks like this:

```xml
<bpmn:userTask id="checkOrder" name="Check order">
  <bpmn:extensionElements>
    <zeebe:userTask />
    <zeebe:formDefinition externalReference="check-order@1" />
    <zeebe:assignmentDefinition candidateGroups="sales" />
    <zeebe:taskHeaders>
      <zeebe:header key="domain" value="order" />
    </zeebe:taskHeaders>
  </bpmn:extensionElements>
</bpmn:userTask>
```

| Element | Rule |
| --- | --- |
| `zeebe:userTask` | Camunda user task, never a job-worker-based one. |
| `externalReference` | The form reference `<form>@<version>` (Modeler: *Custom form key*), a static string. The application serves exactly this form version. |
| `candidateGroups` / `assignee` / `priority` / `dueDate` | Standard Camunda assignment; the tasklist filters by them. |
| Task header `domain` | The key of the owning application in the task manager's registry. |
| `businessId` | Set when the process instance is created (`businessId` of the create command), for example the order number. |
| Variable `subject` | A one-line summary for the task list, set before the first user task, for example `Order ORD-1001 · Alice Smith · 129.90 EUR`. |

The application does not declare any listener for the tasklist and never calls the task manager. The task manager's global user task listener picks up every user task in the cluster.

### 2. Global user task listener

The task manager registers it at startup via `POST /v2/global-task-listeners`. If it already exists (409), it updates it with `PUT`:

| Property | Value |
| --- | --- |
| `id` | `taskmanager-projection` |
| `type` | `taskmanager.projection` (job type of the task manager's worker) |
| `eventTypes` | `all` (`creating`, `assigning`, `updating`, `completing`, `canceling`) |
| `afterNonGlobal` | `true`, so it runs after listeners the applications declare in their models |
| `priority` | `0`, the lowest, so it also runs after other global listeners |
| `retries` | `10`; the worker fails a job with a growing backoff (2 s, 4 s, ... up to 1 min) |

The worker fetches only the variable `subject` and completes each job without variables.

### 3. Provider API (`/tasklist/v1`)

Every process application offers this API to the task manager. Module `tasklist-contract` implements it: the application adds `@EnableTasklistProvider` and one `TaskFormHandler` per form version. The task manager authenticates with a per-application bearer token (`tasklist.provider.token`); the browser never sees it.

| Call | Body | Answer |
| --- | --- | --- |
| `POST /tasklist/v1/tasks/{userTaskKey}/form` | `TaskContext` | `FormView` |
| `POST /tasklist/v1/tasks/{userTaskKey}/draft` | `{context: TaskContext, data}` | `204`; `405` if the form keeps no drafts |
| `POST /tasklist/v1/tasks/{userTaskKey}/completion` | `{context: TaskContext, data}` | `204` completed, `202` accepted but not yet confirmed by Camunda |
| `GET /tasklist/v1/start-forms` | – | `[{id, formRef, label, description}]` |
| `POST /tasklist/v1/start-forms/{id}/form` | `{user}` | `FormView` |
| `POST /tasklist/v1/start-forms/{id}/submission` | `{user, data}` | `201 {processInstanceKey, businessId}` |
| `GET /tasklist/v1/forms/{formRef}/frame` | – | the HTML bundle of the application's micro-frontend, with its own content security policy |

**`TaskContext`** is built by the task manager from its projection and the signed-in user. It is never taken from the browser:

```json
{
  "userTaskKey": "2251799813685337", "processInstanceKey": "2251799813685328",
  "processDefinitionKey": "2251799813685327", "processDefinitionId": "order-process", "processDefinitionVersion": 1,
  "elementId": "checkOrder", "formRef": "check-order@1", "businessId": "ORD-1001", "state": "OPEN",
  "assignee": "alice", "candidateGroups": ["sales"], "tenantId": "<default>",
  "user": {"id": "alice", "groups": ["sales"]}
}
```

**`FormView`** is either `{"type": "json-forms", formRef, schema, uiSchema, data, readOnly, draftable}`, which the tasklist renders with JSON Forms, or `{"type": "html", formRef, data, readOnly, draftable}`, which the tasklist loads as the application's micro-frontend. The micro-frontend runs in a sandboxed iframe and speaks the [form protocol](../tasklist-contract/src/main/typescript/PROTOCOL.md).

Applications load their own entities by `businessId` and bind the submitted `data` to a type with only the fields the form may change. Any other field is ignored.

**Errors** are `application/problem+json` with `code` and, for validation, `errors: [{field, message}]`. `field` is the property path in the form data with dots, for example `customer.email` or `amountReceived`:

| Status | `code` | Meaning |
| --- | --- | --- |
| 400 | `BAD_REQUEST` | Malformed request, or path and context name different tasks |
| 401 | `UNAUTHORIZED` | Missing or wrong token |
| 404 | `FORM_NOT_FOUND` | The application does not know this form reference or start form |
| 405 | `DRAFT_NOT_SUPPORTED` | The form keeps no drafts |
| 409 | `TASK_NOT_ACTIVE` | The task was completed or canceled in the meantime |
| 409 | `CONFLICT` | A domain rule forbids the change, for example a payment that was decided already |
| 422 | `VALIDATION_FAILED` | The data breaks a rule of the form; `errors` names the fields |
| 503 | `UNAVAILABLE` | Camunda is not reachable; nothing was changed, try again |

**Completing a task** is the application's job. It validates, stores its domain change and completes the user task inside one transaction (`CamundaClient.completeUserTask` from the contract module):

| Camunda answers | The application |
| --- | --- |
| `204` | commits and answers `204` |
| `404` (task gone) | rolls back and answers `409 TASK_NOT_ACTIVE` |
| `409` denied by a completing listener | rolls back and answers `422` with the listener's reason |
| `504` (listeners slower than 15 s), or no answer after the request went out | commits and answers `202`; Camunda completes the task when the listeners finish |
| no connection to Camunda | rolls back and answers `503 UNAVAILABLE`; nothing happened |

**Versioning.** A form reference carries its version (`check-order@1`). When the data a form reads or writes changes incompatibly, the application adds `check-order@2` and keeps serving `@1` until no running instance uses it anymore. The provider API itself is versioned in its path (`/tasklist/v1`).

### 4. Task manager API (`/api`)

The tasklist calls only this API. The signed-in user is the header `X-Demo-User` (one of the configured demo users; the recipe has no real authentication).

| Call | Purpose |
| --- | --- |
| `GET /api/tasks?state=&domain=&assignment=&candidateGroup=&processDefinitionId=&priorityMin=&dueBefore=&q=&sort=&order=&from=&limit=` | Page of tasks from the projection. `state`: `open` (default), `completed`, `canceled`, `all`. `assignment`: `mine`, `unassigned`, `any` (default). `q` searches name, subject and business ID. `sort`: `createdAt` (default), `priority`, `dueDate`. |
| `GET /api/tasks/facets?state=` | Counts per domain, candidate group, process and state for the filter bar |
| `GET /api/tasks/{key}` | One task |
| `GET /api/tasks/{key}/history` | Its lifecycle events |
| `GET /api/tasks/{key}/form` | The form, fetched from the owning application with a verified `TaskContext`. `readOnly` is forced unless the task is open and assigned to the user. |
| `POST /api/tasks/{key}/assignment` | Claim the task (body `{}`), only by a candidate |
| `DELETE /api/tasks/{key}/assignment` | Unclaim, only by the assignee |
| `PATCH /api/tasks/{key}` | Change `priority`, `dueDate` or `candidateGroups`, only by a candidate |
| `POST /api/tasks/{key}/draft` | `{data}`, forwarded to the application, only by the assignee |
| `POST /api/tasks/{key}/completion` | `{data}`, forwarded to the application, only by the assignee: `204`, `202`, or the application's problem |
| `GET /api/start-forms` | Start forms of all applications |
| `GET /api/start-forms/{domain}/{id}/form` | One start form |
| `POST /api/start-forms/{domain}/{id}/submission` | `{data}` → `201 {processInstanceKey, businessId}` |
| `GET /api/forms/{domain}/{formRef}/frame` | The application's micro-frontend for the iframe |
| `GET /api/applications`, `GET /api/users`, `GET /api/me` | Registry and demo users |
| `GET /api/events` | Server-sent events `task-changed {userTaskKey, state, domain}` after every projection change |

Tasks are JSON objects with `userTaskKey`, `name`, `processName`, `processDefinitionId`, `processDefinitionVersion`, `elementId`, `domain`, `domainLabel`, `businessId`, `subject`, `formRef`, `state` (`OPEN`, `COMPLETED`, `CANCELED`), `assignee`, `candidateGroups`, `candidateUsers`, `priority`, `dueDate`, `followUpDate`, `createdAt`, `updatedAt`, `closedAt`, `closedBy` (`LISTENER`, `RECONCILER`). The OpenAPI document of the task manager is in [`api/taskmanager.json`](api/taskmanager.json).

Commands (claim, unclaim, update, complete) return only after Camunda has run the user task listeners. The task manager's own listener is one of them, so the projection is already up to date when the answer arrives.

### 5. APIs between the services

Services never integrate through Camunda: each one correlates messages only into its own processes. They call each other over REST; the OpenAPI documents are in [`api/`](api/). Amounts are JSON numbers (`129.90` arrives as `129.9`), so compare them numerically.

| Call | Answer |
| --- | --- |
| `POST payment-app/api/v1/payments {orderId, amount, currency, customer: {name, email}, callbackUrl}` | `201 {paymentId}` new; `200` the same order, amount and currency again; `409 {paymentId}` the same order with another amount or currency; `400 {errors}` invalid, or a callback host outside `payment.callback.allowed-hosts`; `503` Camunda refused the start, nothing was stored (retry) |
| `GET payment-app/api/v1/payments/{paymentId}` | `200` the payment; `404` unknown |
| `POST order-app/api/v1/orders/{orderId}/payment-result {paymentId, status: PAID \| REJECTED, reason}` | `204` applied, also when repeated; `409` contradicts an earlier result, names another payment, or the order no longer waits for a payment (for example declined); `503` the order process does not wait for a result yet (retry) |
| `POST order-app/api/v1/orders/{orderId}/cancellation {reason}` | `204` canceled; `409` the order can no longer be canceled |
| `POST order-app/api/v1/orders {customer, items}` | `201 {orderId, processInstanceKey}`, the same as the start form, for scripts |

The calling side is always a job worker. It completes the job on `2xx`. On `5xx`, `503` or a timeout it fails the job with one retry less and exponential backoff. Every other answer, `400` and `409` included, fails the job without retries: that raises an incident for a human, with the answer as its message (the workers complete and fail their jobs themselves, so the incident shows the reason and not a stack trace). Redirects are never followed.

## Task projection

The task manager keeps one row per user task in its own PostgreSQL database (`taskmanager/src/main/resources/db/migration/V1__projection.sql`):

| Column | Source | Used for |
| --- | --- | --- |
| `user_task_key`, process and element keys, `process_definition_id`, version, `element_id`, `tenant_id` | listener job | identity, routing, the `TaskContext` |
| `name`, `process_name`, `form_ref` | the BPMN XML of the process definition (cached per key), else `GET /v2/user-tasks/{key}` | display, the form to load |
| `domain` | task header `domain`, else the process ID | routing to the application, filter |
| `business_id`, `subject` | the job's `businessId` and variable `subject` | display, search |
| `state` (`OPEN`, `COMPLETED`, `CANCELED`), `assignee`, `candidate_groups`, `candidate_users`, `priority`, `due_date`, `follow_up_date` | every listener job carries the whole task | lifecycle, filters, permissions |
| `created_at`, `updated_at`, `closed_at`, `closed_by` (`LISTENER`, `RECONCILER`) | the task manager | sorting, history |
| `last_job_key`, `verified_at` | the task manager | idempotency, reconciliation |

`user_task_event` keeps the history: one row per listener job (unique `job_key`) or reconciler correction.

**What the projection does not hold.** Variables, form data and domain data stay with Camunda and the applications. The task manager only reads the one variable `subject`, and the applications load their entities by business ID when they render or complete a form. Schemas and HTML of the forms are never stored.

**Idempotency.** Each event is one upsert that only applies if the job key is larger than `last_job_key` and the task is still open. The listener jobs of one task run strictly one after another with growing keys, so a redelivered job (the task manager crashed after the commit, before completing the job) or an outdated one changes nothing. A closed task never reopens.

## Lifecycle

| Trigger | Listener event | Projection |
| --- | --- | --- |
| The process reaches the user task | `creating` | insert `OPEN` |
| Claim and unclaim through the task manager | `assigning` (`action` `claim` or `unassign`) | `assignee` |
| Update of priority, due date or candidate groups | `updating` (`changedAttributes`) | the fields |
| The application completes the task | `completing` | `COMPLETED` |
| Interrupting boundary event, event subprocess, process instance canceled | `canceling` | `CANCELED` |
| Process instance modification terminates the task | none | the reconciler sets `CANCELED`, `closed_by = RECONCILER` |

User task listeners block: Camunda keeps the task in `CREATING`, `ASSIGNING`, `UPDATING`, `COMPLETING` or `CANCELING` until the listener job is completed. An interrupting boundary event waits for the `canceling` job too, so the projection is updated before the process moves on. Two consequences:

- **The task manager is on the critical path of every user task in the cluster.** While it is down, new tasks stay in `CREATING`, claims and completions time out after 15 s and canceled tasks stay in `CANCELING`. Nothing is lost: when it comes back, it handles the waiting jobs. When storing an event fails, the worker fails the job with a growing backoff; after 10 attempts it raises an incident that you resolve in Operate. Its health is `DOWN` until the listener is registered. The handler therefore does nothing but the database write: names and form references come from a cache of the BPMN, an unknown definition is loaded in the background (its tasks are filled in a moment later), and server-sent events go out on their own threads.
- **Commands return after the listener ran.** The REST commands for claim, update and completion wait for the listeners, the task manager's own one included. When the application answers the completion with `204`, the projection already says `COMPLETED`: the tasklist needs no polling. When the listeners take longer than Camunda's 15 s request timeout, the command answers `504`, but Camunda still applies it once the listeners finish. The task manager and the applications pass this on as `202`.

**Limits of listeners**

- A process instance modification does not trigger `canceling`: "Process instance modification does not trigger the canceling event" (Camunda docs). The reconciler closes such tasks.
- Listeners cannot deny `creating` or `canceling`, and a listener job is completed without variables.
- The order of listeners is: global listeners, then the listeners of the model, then global listeners with `afterNonGlobal`. The task manager's listener is the last one, so it sees the final state of each transition, including corrections and denials of model listeners.

**Reconciler.** Every 30 s (`taskmanager.reconciler.interval`) it checks open tasks that were quiet for 10 s (`lag`) against `GET /v2/user-tasks/{key}`, oldest check first. A task Camunda reports as completed or canceled is closed; a task Camunda's search does not know is closed as canceled only after 30 minutes (`missing-grace`), because the search can lag. A task that fails to check does not hold up the others. Once after startup it reads, page by page, the open tasks created before the task manager registered its listener and inserts those it does not have; later tasks always come through the listener. It also fills in missing names and form references. It closes a task only if no listener event arrived in between and never reopens one, but a listener event that arrives for a task it closed corrects the projection.

## Failure handling and consistency

| Situation | What happens |
| --- | --- |
| The browser sends fields a form may not change | The application binds the data to a type with only the editable fields; the rest is ignored. |
| A task is completed twice, or after it was canceled | The task manager refuses because its projection is closed (`409 TASK_NOT_ACTIVE`). If the projection was behind, Camunda answers `404`, the application rolls back and answers `409`, and the task manager checks the task at once. |
| The application validation fails | `422` with field errors; nothing changes in Camunda. |
| Camunda is slower than 15 s | `202`; the change is applied later. |
| An application is down | `503` with `Retry-After`; the projection is unchanged. |
| The form reference is not known yet | `503 SYNCING` with `Retry-After`; the task manager looks it up in the model, then in Camunda's search, and the reconciler fills it in. |
| A service call between the applications fails | The job worker retries with backoff, then raises an incident. |
| A listener job is delivered again | The `last_job_key` guard ignores it. |

**Two systems, one action.** The applications write their database and Camunda in one action: a completion stores the domain change and completes the user task, the payment API stores the payment and starts the process. The database transaction commits only if Camunda accepted the command, so a refusal leaves nothing behind. If Camunda accepts but its answer is lost, the transaction rolls back although Camunda went on: for example a payment process runs without a payment row. Production systems close that gap with an outbox or by making the Camunda side idempotent.

**Eventually consistent reads.** Camunda's search, the BPMN XML and the user task details come from Elasticsearch and lag about a second behind. The projection itself is updated synchronously by the listener, so the task list never waits for Elasticsearch. Only the reconciler and the lazy form reference lookup read Camunda's search, and both tolerate the lag.

## Decisions

| Decision | Why | Instead of |
| --- | --- | --- |
| Camunda user tasks with one global user task listener | Every lifecycle event, cancellation included, arrives in order and before the task moves on; nothing to declare in the models; works in SaaS | job-worker-based user tasks (deprecated, need polling), a custom exporter (not in SaaS, can stall the broker), polling the search API (lag, no cancel event) |
| A projection in the task manager | Fast filtering on domain, groups, assignment and text, routing data and history independent of Camunda's search | querying Camunda's search for every list request |
| Forms owned by the applications | The application that owns the data also owns its form, its validation and the completion | Camunda Forms rendered by the tasklist (see the README's Variations) |
| The task manager as the only backend of the tasklist | The browser never talks to the applications, so they can trust the `TaskContext` (#158) | the browser loading forms from each application |
| HTML forms in a sandboxed iframe with their own CSP | A micro-frontend cannot read the tasklist or send data anywhere (#159) | trusting the application's script in the tasklist's origin |
| REST with OpenAPI between the services | A standard, versioned API; Camunda only orchestrates inside one service | Camunda messages across services, Kafka (see Variations) |
| A static registry of applications | Explicit: the task manager only forwards to configured applications | self-registration (see Variations) |

## Limits

- **No authentication.** The demo user travels in `X-Demo-User`, and the tokens between the task manager and the applications are static. Use your identity provider's tokens for the user and OAuth2 client credentials between the services.
- **One task manager instance.** Server-sent events reach only the clients of the instance that applied the change. The projection itself is safe for several instances; fan the events out through PostgreSQL `LISTEN`/`NOTIFY` or a broker.
- **Single tenant.** Tenant IDs are stored but not used for filtering or permissions.
- **Demo data.** The databases and Camunda start empty with each `docker compose down -v`.
