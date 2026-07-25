-- P5 非 Team 自动化：版本定义、幂等运行、组织策略、生命周期与审计。

CREATE TABLE ai_automation_definition (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    automation_id VARCHAR(128) NOT NULL,
    definition_version BIGINT NOT NULL,
    lifecycle VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    definition_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_automation_definition UNIQUE (tenant_id, automation_id, definition_version),
    CONSTRAINT ck_automation_definition_version CHECK (definition_version > 0),
    CONSTRAINT ck_automation_lifecycle CHECK (lifecycle IN ('DRAFT','PUBLISHED','DEPRECATED','DISABLED')),
    CONSTRAINT ck_automation_enable CHECK (enabled = FALSE OR lifecycle = 'PUBLISHED')
);
CREATE INDEX idx_automation_definition_tenant ON ai_automation_definition (tenant_id, automation_id, definition_version DESC);
CREATE INDEX idx_automation_definition_enabled ON ai_automation_definition (enabled, updated_at) WHERE enabled = TRUE;

CREATE TABLE ai_automation_run (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    automation_id VARCHAR(128) NOT NULL,
    definition_version BIGINT NOT NULL,
    trigger_key VARCHAR(256) NOT NULL,
    delegated_task_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    run_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_automation_run UNIQUE (tenant_id, run_id),
    CONSTRAINT uk_automation_trigger UNIQUE (tenant_id, automation_id, trigger_key),
    CONSTRAINT ck_automation_run_status CHECK (status IN ('PENDING','DISPATCHED','COMPLETED','FAILED','CANCELED'))
);
CREATE INDEX idx_automation_run_pending ON ai_automation_run (status, created_at) WHERE status = 'PENDING';
CREATE INDEX idx_automation_run_history ON ai_automation_run (tenant_id, automation_id, created_at DESC);

CREATE TABLE ai_automation_policy (
    tenant_id VARCHAR(128) PRIMARY KEY,
    global_stop BOOLEAN NOT NULL DEFAULT FALSE,
    policy_payload JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE ai_definition_lifecycle (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    definition_kind VARCHAR(32) NOT NULL,
    definition_id VARCHAR(128) NOT NULL,
    definition_version BIGINT NOT NULL,
    state VARCHAR(32) NOT NULL,
    lifecycle_payload JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_definition_lifecycle UNIQUE (tenant_id, definition_kind, definition_id, definition_version),
    CONSTRAINT ck_definition_kind CHECK (definition_kind IN ('ASSISTANT','SKILL','AUTOMATION','CONNECTOR')),
    CONSTRAINT ck_definition_state CHECK (state IN ('DRAFT','PUBLISHED','DEPRECATED','DISABLED'))
);

CREATE TABLE ai_automation_audit (
    audit_id VARCHAR(128) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    automation_id VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    details JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_automation_audit_search ON ai_automation_audit (tenant_id, automation_id, occurred_at DESC);

-- 自动化执行复用 P4 ai_task_notification_outbox；不创建第二套 outbox。
-- ai_execution_run/ai_execution_step/旧 ai_task_event archive 仅保留历史数据，v2 代码不再读写。
