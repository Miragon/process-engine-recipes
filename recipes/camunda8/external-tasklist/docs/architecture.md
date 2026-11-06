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
| `retries` | `5` |

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

**Errors** are `application/problem+json` with `code` and, for validation, `errors: [{field, message}]`:

| Status | `code` | Meaning |
| --- | --- | --- |
| 400 | `BAD_REQUEST` | Malformed request, or path and context name different tasks |
| 401 | `UNAUTHORIZED` | Missing or wrong token |
| 404 | `FORM_NOT_FOUND` | The application does not know this form reference or start form |
| 405 | `DRAFT_NOT_SUPPORTED` | The form keeps no drafts |
| 409 | `TASK_NOT_ACTIVE` | The task was completed or canceled in the meantime |
| 409 | `CONFLICT` | A domain rule forbids the change, for example an order that was already canceled |
| 422 | `VALIDATION_FAILED` | The data breaks a rule of the form; `errors` names the fields |

**Completing a task** is the application's job. It validates, stores its domain change and completes the user task inside one transaction (`CamundaClient.completeUserTask` from the contract module):

| Camunda answers | The application |
| --- | --- |
| `204` | commits and answers `204` |
| `404` (task gone) | rolls back and answers `409 TASK_NOT_ACTIVE` |
| `409` denied by a completing listener | rolls back and answers `422` with the listener's reason |
| `504` (listeners slower than 15 s) | commits and answers `202`; Camunda completes the task when the listeners finish |

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

Services never integrate through Camunda: each one correlates messages only into its own processes. They call each other over REST; the OpenAPI documents are in [`api/`](api/).

| Call | Answer |
| --- | --- |
| `POST payment-app/api/v1/payments {orderId, amount, currency, customer: {name, email}, callbackUrl}` | `201 {paymentId}` new; `200` same request again; `409` same order with another amount; `400` callback host not allowed |
| `POST order-app/api/v1/orders/{orderId}/payment-result {paymentId, status: PAID \| REJECTED, reason}` | `204` applied, also when repeated; `409` contradicts an earlier result; `503` the order process does not wait for a result yet (retry) |
| `POST order-app/api/v1/orders/{orderId}/cancellation {reason}` | `204` canceled; `409` the order can no longer be canceled |
| `POST order-app/api/v1/orders {customer, items}` | `201 {orderId, processInstanceKey}`, the same as the start form, for scripts |

The calling side is always a job worker. It completes the job on `2xx`. On `400`/`409` it fails the job without retries, which raises an incident for a human. On `5xx` or a timeout it fails the job with one retry less and exponential backoff.
