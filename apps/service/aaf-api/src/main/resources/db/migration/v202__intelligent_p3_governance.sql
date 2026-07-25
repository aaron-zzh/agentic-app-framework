CREATE TABLE ai_cognition_memory (
    memory_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    subject_kind VARCHAR(16) NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    redacted_summary VARCHAR(256) NOT NULL,
    importance DOUBLE PRECISION NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    privacy VARCHAR(16) NOT NULL,
    tags TEXT[],
    embedding vector(1536),
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    forgotten_at TIMESTAMPTZ,
    CONSTRAINT ck_cognition_subject_kind CHECK (subject_kind IN ('USER', 'VISITOR')),
    CONSTRAINT ck_cognition_privacy CHECK (privacy IN ('PUBLIC', 'PERSONAL', 'SENSITIVE', 'SECRET')),
    CONSTRAINT ck_cognition_scores CHECK (
        importance BETWEEN 0 AND 1 AND confidence BETWEEN 0 AND 1),
    CONSTRAINT ck_cognition_visitor_ttl CHECK (
        subject_kind <> 'VISITOR' OR expires_at IS NOT NULL)
);
CREATE INDEX idx_cognition_memory_subject
    ON ai_cognition_memory (tenant_id, subject_kind, subject_id, importance DESC)
    WHERE forgotten_at IS NULL;
CREATE INDEX idx_cognition_memory_expiry
    ON ai_cognition_memory (expires_at)
    WHERE subject_kind = 'VISITOR' AND forgotten_at IS NULL;
CREATE INDEX idx_cognition_memory_embedding
    ON ai_cognition_memory USING hnsw (embedding vector_cosine_ops);

CREATE TABLE ai_context_source_preference (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    assistant_id VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_key VARCHAR(256) NOT NULL,
    disposition VARCHAR(16) NOT NULL,
    reason VARCHAR(512) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_context_source_preference
        UNIQUE (tenant_id, user_id, assistant_id, source_type, source_key),
    CONSTRAINT ck_context_source_disposition
        CHECK (disposition IN ('DEFAULT', 'PREFERRED', 'DISABLED', 'REMOVED'))
);

CREATE TABLE ai_effective_context_manifest (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    manifest_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_effective_context_task UNIQUE (tenant_id, task_id)
);

CREATE TABLE ai_credential_handle (
    handle_id VARCHAR(128) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    connector_id VARCHAR(256) NOT NULL,
    scopes TEXT[] NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    vault_ref VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_credential_handle_expiry CHECK (expires_at > created_at)
);
CREATE INDEX idx_credential_handle_active
    ON ai_credential_handle (tenant_id, user_id, connector_id, expires_at DESC)
    WHERE revoked_at IS NULL;

CREATE TABLE ai_authorization_grant (
    grant_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    action VARCHAR(256) NOT NULL,
    resource VARCHAR(256) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    grant_payload JSONB NOT NULL,
    CONSTRAINT ck_authorization_grant_resource CHECK (resource <> '*')
);
CREATE INDEX idx_authorization_grant_lookup
    ON ai_authorization_grant (tenant_id, task_id, action, resource, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE ai_hitl_approval (
    approval_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    approval_payload JSONB NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_hitl_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);
CREATE INDEX idx_hitl_approval_pending
    ON ai_hitl_approval (tenant_id, task_id, created_at)
    WHERE status = 'PENDING';

CREATE TABLE ai_task_recovery_command (
    command_key VARCHAR(300) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    command_payload JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_task_recovery_command UNIQUE (tenant_id, task_id)
);

CREATE TABLE ai_hitl_recovery (
    approval_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    lease_until TIMESTAMPTZ,
    last_error VARCHAR(1000),
    command_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_hitl_recovery_approval
        FOREIGN KEY (approval_id) REFERENCES ai_hitl_approval(approval_id),
    CONSTRAINT ck_hitl_recovery_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED'))
);
CREATE INDEX idx_hitl_recovery_task
    ON ai_hitl_recovery (tenant_id, task_id, status);

CREATE TABLE ai_execution_sequence (
    tenant_id VARCHAR(128) NOT NULL,
    execution_id VARCHAR(128) NOT NULL,
    next_sequence BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, execution_id),
    CONSTRAINT ck_execution_next_sequence CHECK (next_sequence > 0)
);

-- v2 任务事件仅保留历史，P3 起不再读写；P6 删除归档表。
ALTER TABLE ai_task_event RENAME TO ai_task_event_archive_v2;
ALTER TABLE ai_task_event_archive_v2
    RENAME CONSTRAINT ai_task_event_pkey TO ai_task_event_archive_v2_pkey;
ALTER INDEX idx_ai_task_event_task RENAME TO idx_ai_task_event_archive_v2_task;
ALTER INDEX idx_ai_task_event_execution RENAME TO idx_ai_task_event_archive_v2_execution;
COMMENT ON TABLE ai_task_event_archive_v2 IS 'v2 任务事件只读归档，P3 后不再写入，P6 删除';

CREATE TABLE ai_task_event (
    event_id VARCHAR(128) PRIMARY KEY,
    event_offset BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    execution_id VARCHAR(128) NOT NULL,
    sequence BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    event_payload JSONB NOT NULL,
    CONSTRAINT uk_task_event_execution_sequence UNIQUE (tenant_id, execution_id, sequence),
    CONSTRAINT ck_task_event_sequence CHECK (sequence > 0)
);
CREATE INDEX idx_task_event_task_offset
    ON ai_task_event (tenant_id, task_id, event_offset);
CREATE INDEX idx_task_event_execution_sequence
    ON ai_task_event (tenant_id, execution_id, sequence);

ALTER TABLE ai_tool_catalog
    ADD COLUMN idempotency_required BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE ai_tool_catalog
SET idempotency_required = TRUE
WHERE NOT read_only AND UPPER(source) IN ('MCP', 'CONNECTOR');
ALTER TABLE ai_tool_catalog
    ADD CONSTRAINT ck_tool_catalog_connector_idempotency
    CHECK (
        read_only
        OR UPPER(source) NOT IN ('MCP', 'CONNECTOR')
        OR idempotency_required
    );

CREATE TABLE ai_connector_action_execution (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    execution_id VARCHAR(128) NOT NULL,
    connector_id VARCHAR(256) NOT NULL,
    action_name VARCHAR(120) NOT NULL,
    request_digest VARCHAR(64) NOT NULL,
    provider_idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    result TEXT,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_connector_action_status CHECK (status IN ('PENDING', 'SUCCEEDED'))
);
CREATE INDEX idx_connector_action_execution
    ON ai_connector_action_execution (tenant_id, execution_id, created_at);

ALTER TABLE ai_usage_record
    ADD COLUMN usage_key VARCHAR(128),
    ADD COLUMN settlement_digest VARCHAR(64),
    ADD COLUMN tenant_id VARCHAR(128),
    ADD COLUMN task_id VARCHAR(128),
    ADD COLUMN execution_id VARCHAR(128),
    ADD COLUMN occurred_at TIMESTAMPTZ;
CREATE UNIQUE INDEX uk_ai_usage_record_usage_key
    ON ai_usage_record (usage_key);
CREATE INDEX idx_ai_usage_record_task
    ON ai_usage_record (tenant_id, task_id, occurred_at)
    WHERE usage_key IS NOT NULL;
CREATE INDEX idx_ai_usage_record_execution
    ON ai_usage_record (tenant_id, execution_id, occurred_at)
    WHERE usage_key IS NOT NULL;



INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES (
    'Connector 凭证管理',
    'ai:connector-credential:manage',
    'ai',
    'connector-credential',
    'manage',
    0
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission_code p ON p.code = 'ai:connector-credential:manage'
WHERE r.code IN ('member', 'org_admin', 'admin', 'super_admin')
ON CONFLICT DO NOTHING;