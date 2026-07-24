CREATE TABLE ai_assistant_definition_version (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    assistant_id VARCHAR(128) NOT NULL,
    system_key VARCHAR(128),
    definition_version BIGINT NOT NULL,
    definition_payload JSONB NOT NULL,
    CONSTRAINT uk_assistant_definition_tenant_id_version
        UNIQUE (tenant_id, assistant_id, definition_version),
    CONSTRAINT uk_assistant_definition_tenant_system_version
        UNIQUE (tenant_id, system_key, definition_version)
);

CREATE INDEX idx_assistant_definition_system_key
    ON ai_assistant_definition_version (tenant_id, system_key, definition_version DESC);

CREATE TABLE ai_assistant_task_control (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    task_status VARCHAR(32) NOT NULL,
    control_mode VARCHAR(32) NOT NULL,
    task_payload JSONB NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_assistant_task_tenant_task UNIQUE (tenant_id, task_id)
);

CREATE INDEX idx_assistant_task_status
    ON ai_assistant_task_control (tenant_id, task_status);



ALTER TABLE ai_tool_catalog
    ADD COLUMN reversible BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ai_tool_catalog.reversible IS '写操作是否具备真实补偿/撤销能力；默认 FALSE，禁止推断所有写操作可撤销';

ALTER TABLE ai_tool_catalog
    ADD CONSTRAINT ck_ai_tool_catalog_reversible_write
        CHECK (NOT read_only OR NOT reversible);
