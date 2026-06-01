-- AI 工作流定义表
CREATE TABLE ai_flow_definition (
    id              BIGSERIAL     PRIMARY KEY,
    version         INTEGER       NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP,
    owner_id        BIGINT,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    remark          VARCHAR(512),

    name            VARCHAR(128)  NOT NULL,
    description     VARCHAR(512),
    mode            VARCHAR(32)   NOT NULL DEFAULT 'CHAT',
    definition      JSONB         NOT NULL DEFAULT '{}',
    status          VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    deployment_id   VARCHAR(64),
    published_at    TIMESTAMP,
    agent_callable  BOOLEAN       NOT NULL DEFAULT FALSE,
    require_confirm BOOLEAN       NOT NULL DEFAULT TRUE
);

COMMENT ON TABLE  ai_flow_definition                  IS 'AI 工作流定义';
COMMENT ON COLUMN ai_flow_definition.mode             IS '流程模式：CHAT / COMPLETION / AGENT';
COMMENT ON COLUMN ai_flow_definition.definition       IS '编辑态 JSON（ReactFlow 节点+连线）';
COMMENT ON COLUMN ai_flow_definition.status           IS '发布状态：DRAFT / PUBLISHED / DISABLED';
COMMENT ON COLUMN ai_flow_definition.deployment_id    IS 'Flowable deployment ID，发布后有值';
COMMENT ON COLUMN ai_flow_definition.agent_callable   IS '是否允许智能体自动调用';
COMMENT ON COLUMN ai_flow_definition.require_confirm  IS '智能体调用前是否需要用户确认';
