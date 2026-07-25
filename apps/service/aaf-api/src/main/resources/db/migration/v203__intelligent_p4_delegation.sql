-- P4 DELEGATED 生产闭环：持久任务、DAG、统一 receipt、通知 outbox 与 fencing 诊断。

ALTER TABLE ai_assistant_task_control
    ADD COLUMN fencing_token BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_assistant_task_fencing_token CHECK (fencing_token >= 0),
    ADD CONSTRAINT ck_assistant_task_delegated_fencing
        CHECK (control_mode <> 'DELEGATED' OR fencing_token > 0);

ALTER TABLE ai_task_event
    ADD COLUMN fencing_token BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_task_event_fencing_token CHECK (fencing_token >= 0);

ALTER TABLE ai_usage_record
    ADD COLUMN fencing_token BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_usage_record_fencing_token CHECK (fencing_token >= 0);

-- v202 connector 专用幂等表不再由生产代码读写；统一 receipt 是唯一事实。

CREATE TABLE ai_delegated_task (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    execution_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    owner_kind VARCHAR(16) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    next_run_at TIMESTAMPTZ NOT NULL,
    lease_owner VARCHAR(128),
    lease_until TIMESTAMPTZ,
    fencing_token BIGINT NOT NULL DEFAULT 0,
    task_payload JSONB NOT NULL,
    command_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_delegated_task_tenant_task UNIQUE (tenant_id, task_id),
    CONSTRAINT ck_delegated_task_status CHECK (status IN (
        'PENDING', 'RUNNING', 'PAUSED', 'AWAITING_AUTHORIZATION',
        'AWAITING_INPUT', 'COMPLETED', 'CANCELED', 'FAILED')),
    CONSTRAINT ck_delegated_task_owner CHECK (owner_kind IN ('ASSISTANT', 'AGENT', 'HUMAN')),
    CONSTRAINT ck_delegated_task_fencing CHECK (fencing_token >= 0),
    CONSTRAINT ck_delegated_task_running_lease CHECK (
        status <> 'RUNNING'
        OR (lease_owner IS NOT NULL AND lease_until IS NOT NULL AND fencing_token > 0)),
    CONSTRAINT ck_delegated_task_human_not_running CHECK (
        owner_kind <> 'HUMAN' OR status <> 'RUNNING')
);
CREATE INDEX idx_delegated_task_dispatch
    ON ai_delegated_task (status, next_run_at)
    WHERE status IN ('PENDING', 'RUNNING');
CREATE INDEX idx_delegated_task_conversation
    ON ai_delegated_task (tenant_id, conversation_id, status);
CREATE INDEX idx_delegated_task_user
    ON ai_delegated_task (tenant_id, user_id, updated_at DESC);

CREATE TABLE ai_task_board (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    board_payload JSONB NOT NULL,
    fencing_token BIGINT NOT NULL DEFAULT 0,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_task_board_tenant_task UNIQUE (tenant_id, task_id),
    CONSTRAINT ck_task_board_fencing CHECK (fencing_token >= 0)
);

CREATE TABLE ai_tool_invocation_receipt (
    receipt_key VARCHAR(128) PRIMARY KEY,
    request_digest VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    execution_id VARCHAR(128) NOT NULL,
    fencing_token BIGINT NOT NULL DEFAULT 0,
    tool_id VARCHAR(256) NOT NULL,
    action_key VARCHAR(256) NOT NULL,
    status VARCHAR(16) NOT NULL,
    result_payload JSONB,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_tool_receipt_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_tool_receipt_fencing CHECK (fencing_token >= 0)
);
CREATE INDEX idx_tool_receipt_task
    ON ai_tool_invocation_receipt (tenant_id, task_id, updated_at DESC);
CREATE INDEX idx_tool_receipt_action
    ON ai_tool_invocation_receipt (tenant_id, tool_id, action_key);

CREATE TABLE ai_task_notification_outbox (
    notification_id VARCHAR(128) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    notification_payload JSONB NOT NULL,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_task_notification_status CHECK (status IN ('PENDING', 'DISPATCHED', 'FAILED'))
);
CREATE INDEX idx_task_notification_pending
    ON ai_task_notification_outbox (status, updated_at)
    WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX idx_task_notification_task
    ON ai_task_notification_outbox (tenant_id, task_id, created_at DESC);
