-- Projection of the user tasks of the cluster. Camunda owns the tasks; this table holds what the
-- tasklist needs to list, filter and route them, and is updated by the global user task listener.
CREATE TABLE user_task
(
    user_task_key              BIGINT PRIMARY KEY,
    element_instance_key       BIGINT      NOT NULL,
    process_instance_key       BIGINT      NOT NULL,
    process_definition_key     BIGINT      NOT NULL,
    process_definition_id      TEXT        NOT NULL,
    process_definition_version INT         NOT NULL,
    element_id                 TEXT        NOT NULL,
    tenant_id                  TEXT        NOT NULL,
    name                       TEXT,
    process_name               TEXT,
    domain                     TEXT        NOT NULL,
    business_id                TEXT,
    subject                    TEXT,
    form_ref                   TEXT,
    state                      TEXT        NOT NULL CHECK (state IN ('OPEN', 'COMPLETED', 'CANCELED')),
    assignee                   TEXT,
    candidate_groups           TEXT[]      NOT NULL DEFAULT '{}',
    candidate_users            TEXT[]      NOT NULL DEFAULT '{}',
    priority                   INT         NOT NULL DEFAULT 50,
    due_date                   TIMESTAMPTZ,
    follow_up_date             TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ NOT NULL,
    updated_at                 TIMESTAMPTZ NOT NULL,
    closed_at                  TIMESTAMPTZ,
    closed_by                  TEXT CHECK (closed_by IN ('LISTENER', 'RECONCILER')),
    -- Key of the last listener job applied. Jobs of one task run one after another with growing keys,
    -- so a job key that is not larger than this one is a redelivery and is ignored.
    last_job_key               BIGINT      NOT NULL,
    verified_at                TIMESTAMPTZ NOT NULL
);

CREATE INDEX user_task_state_created ON user_task (state, created_at DESC);
CREATE INDEX user_task_domain ON user_task (domain);
CREATE INDEX user_task_assignee ON user_task (assignee);
CREATE INDEX user_task_candidate_groups ON user_task USING GIN (candidate_groups);

-- Lifecycle history of each task, one row per listener job (or reconciler correction).
CREATE TABLE user_task_event
(
    id                 BIGSERIAL PRIMARY KEY,
    job_key            BIGINT UNIQUE,
    user_task_key      BIGINT      NOT NULL REFERENCES user_task (user_task_key),
    event_type         TEXT        NOT NULL,
    action             TEXT,
    assignee           TEXT,
    changed_attributes TEXT[]      NOT NULL DEFAULT '{}',
    source             TEXT        NOT NULL CHECK (source IN ('LISTENER', 'RECONCILER')),
    occurred_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX user_task_event_task ON user_task_event (user_task_key, occurred_at);
