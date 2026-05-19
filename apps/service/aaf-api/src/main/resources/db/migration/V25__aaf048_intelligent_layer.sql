-- ============================================================
-- V25: AAF-048 智能层表结构（v0.4）
-- 按层级顺序：Core → Cognition → Agent → Assistant → Team
-- ============================================================

-- ============================================================
-- Core 层
-- ============================================================

-- AI 模型配置表：管理所有可用的 LLM 模型及其参数
CREATE TABLE ai_model (
    id              BIGSERIAL PRIMARY KEY,
    version         INT NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    create_by       BIGINT,
    create_time     TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    remark          TEXT,
    model_id        VARCHAR(64) NOT NULL UNIQUE,
    display_name    VARCHAR(128) NOT NULL,
    provider        VARCHAR(32) NOT NULL,
    model_name      VARCHAR(128) NOT NULL,
    base_url        VARCHAR(512),
    api_key_encrypted VARCHAR(1024),
    temperature     DOUBLE PRECISION,
    max_tokens      INT,
    context_window  INT,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    capabilities    VARCHAR(256),
    fallback_model_id VARCHAR(64),
    sort_order      INT DEFAULT 100
);

COMMENT ON TABLE ai_model IS 'AI 模型配置表，管理所有可用的 LLM 模型及其连接参数';
COMMENT ON COLUMN ai_model.api_key_encrypted IS 'API Key 加密存储（AES）';
COMMENT ON COLUMN ai_model.capabilities IS '多模态能力标记，逗号分隔（vision,audio,embedding,function_calling）';

-- Prompt 模板表：版本化管理系统级 Prompt 模板
CREATE TABLE ai_prompt_template (
    id              BIGSERIAL PRIMARY KEY,
    version         INT NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    create_by       BIGINT,
    create_time     TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    remark          TEXT,
    name            VARCHAR(128) NOT NULL,
    template_version INT NOT NULL DEFAULT 1,
    content         TEXT NOT NULL,
    description     VARCHAR(512),
    variables       TEXT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    category        VARCHAR(64),
    UNIQUE (name, template_version)
);

COMMENT ON TABLE ai_prompt_template IS 'Prompt 模板表，版本化管理系统级 Prompt';
COMMENT ON COLUMN ai_prompt_template.variables IS '模板变量定义（JSON 格式）';

-- Token 用量记录表：记录每次 LLM 调用的 Token 消耗
CREATE TABLE ai_token_usage (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    conversation_id     VARCHAR(64),
    model_id            VARCHAR(64) NOT NULL,
    prompt_tokens       BIGINT NOT NULL,
    completion_tokens   BIGINT NOT NULL,
    total_tokens        BIGINT NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_token_usage_user_created ON ai_token_usage (user_id, created_at);
CREATE INDEX idx_ai_token_usage_conversation ON ai_token_usage (conversation_id);
CREATE INDEX idx_ai_token_usage_model_created ON ai_token_usage (model_id, created_at);

COMMENT ON TABLE ai_token_usage IS 'Token 用量记录表，支持配额控制和成本分析';

-- ============================================================
-- Cognition 层
-- ============================================================

-- 长期记忆表：存储用户的长期记忆片段
CREATE TABLE ai_long_term_memory (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    content         TEXT NOT NULL,
    source_id       VARCHAR(128),
    memory_type     VARCHAR(32) NOT NULL,
    importance      DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    last_accessed_at TIMESTAMP,
    access_count    INT NOT NULL DEFAULT 0,
    event_time      TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_long_term_memory_user_importance ON ai_long_term_memory (user_id, importance DESC);
CREATE INDEX idx_ai_long_term_memory_user_created ON ai_long_term_memory (user_id, created_at DESC);

COMMENT ON TABLE ai_long_term_memory IS '长期记忆表，存储用户交互中提取的持久化记忆';
COMMENT ON COLUMN ai_long_term_memory.importance IS '记忆重要性评分（0.0-1.0）';
COMMENT ON COLUMN ai_long_term_memory.memory_type IS '记忆类型（EPISODIC/SEMANTIC/PREFERENCE）';

-- 程序性记忆表：存储任务执行经验和最佳实践
CREATE TABLE ai_procedural_memory (
    id              BIGSERIAL PRIMARY KEY,
    version         INT NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    create_by       BIGINT,
    create_time     TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    remark          TEXT,
    user_id         BIGINT,
    task_type       VARCHAR(64) NOT NULL,
    category        VARCHAR(64),
    title           VARCHAR(256) NOT NULL,
    content         TEXT NOT NULL,
    success_count   INT NOT NULL DEFAULT 0,
    use_count       INT NOT NULL DEFAULT 0,
    quality_score   DOUBLE PRECISION NOT NULL DEFAULT 0.5
);

CREATE INDEX idx_ai_procedural_memory_user_category ON ai_procedural_memory (user_id, category);
CREATE INDEX idx_ai_procedural_memory_task_type ON ai_procedural_memory (task_type);

COMMENT ON TABLE ai_procedural_memory IS '程序性记忆表，存储任务执行经验和最佳实践';
COMMENT ON COLUMN ai_procedural_memory.quality_score IS '质量评分（0.0-1.0），基于成功率和反馈';

-- ============================================================
-- Agent 层
-- ============================================================

-- Agent 定义表：管理智能体的配置和能力
CREATE TABLE ai_agent_definition (
    id              BIGSERIAL PRIMARY KEY,
    version         INT NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    create_by       BIGINT,
    create_time     TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    remark          TEXT,
    agent_id        VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    system_prompt   TEXT,
    model_id        VARCHAR(64),
    capabilities    TEXT,
    tools           TEXT,
    mcp_servers     TEXT,
    max_iterations  INT NOT NULL DEFAULT 10,
    timeout_seconds INT NOT NULL DEFAULT 120,
    status          VARCHAR(16) NOT NULL DEFAULT 'active'
);

COMMENT ON TABLE ai_agent_definition IS 'Agent 定义表，管理智能体的配置、能力和工具';
COMMENT ON COLUMN ai_agent_definition.tools IS '可用工具列表（JSON 数组）';
COMMENT ON COLUMN ai_agent_definition.mcp_servers IS 'MCP Server 配置（JSON 数组）';

-- ============================================================
-- Assistant 层
-- ============================================================

-- 技能定义表：管理助手的技能配置
CREATE TABLE ai_skill_definition (
    id              BIGSERIAL PRIMARY KEY,
    version         INT NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    create_by       BIGINT,
    create_time     TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    remark          TEXT,
    skill_id        VARCHAR(64) NOT NULL UNIQUE,
    assistant_id    VARCHAR(64) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    agent_id        VARCHAR(64) NOT NULL,
    trigger_intent  TEXT,
    system_prompt   TEXT,
    tools           TEXT,
    priority        INT NOT NULL DEFAULT 0,
    status          VARCHAR(16) NOT NULL DEFAULT 'active'
);

CREATE INDEX idx_ai_skill_definition_assistant ON ai_skill_definition (assistant_id);
CREATE INDEX idx_ai_skill_definition_trigger ON ai_skill_definition (trigger_intent);

COMMENT ON TABLE ai_skill_definition IS '技能定义表，管理助手可调用的技能及其触发条件';
COMMENT ON COLUMN ai_skill_definition.trigger_intent IS '触发意图（用于意图路由匹配）';

-- ============================================================
-- Team 层
-- ============================================================

-- 团队定义表：管理多智能体团队
CREATE TABLE ai_team (
    id              BIGSERIAL PRIMARY KEY,
    version         INT NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    create_by       BIGINT,
    create_time     TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    remark          TEXT,
    team_id         VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    collaboration_mode VARCHAR(32) NOT NULL DEFAULT 'LEADER_COORDINATED',
    status          VARCHAR(16) NOT NULL DEFAULT 'active'
);

COMMENT ON TABLE ai_team IS '多智能体团队定义表';
COMMENT ON COLUMN ai_team.collaboration_mode IS '协作模式（LEADER_COORDINATED/PEER_TO_PEER/PIPELINE）';

-- 团队成员表：记录团队中的助手成员及其角色
CREATE TABLE ai_team_member (
    id              BIGSERIAL PRIMARY KEY,
    team_id         VARCHAR(64) NOT NULL REFERENCES ai_team(team_id),
    assistant_id    VARCHAR(64) NOT NULL,
    role            VARCHAR(32) NOT NULL DEFAULT 'member',
    capabilities    TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (team_id, assistant_id)
);

COMMENT ON TABLE ai_team_member IS '团队成员表，记录团队中的助手及其角色分工';

-- 团队任务表：管理团队协作任务的分配和执行
CREATE TABLE ai_team_task (
    id              BIGSERIAL PRIMARY KEY,
    team_id         VARCHAR(64) NOT NULL,
    task_id         VARCHAR(64) NOT NULL UNIQUE,
    parent_task_id  VARCHAR(64),
    assignee_id     VARCHAR(64),
    description     TEXT NOT NULL,
    required_capability VARCHAR(128),
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    dependencies    TEXT,
    priority        INT NOT NULL DEFAULT 0,
    progress        INT NOT NULL DEFAULT 0,
    result          TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_team_task_team_status ON ai_team_task (team_id, status);
CREATE INDEX idx_ai_team_task_assignee_status ON ai_team_task (assignee_id, status);

COMMENT ON TABLE ai_team_task IS '团队任务表，管理多智能体协作任务的分配、依赖和执行状态';
COMMENT ON COLUMN ai_team_task.dependencies IS '依赖的任务 ID 列表（JSON 数组）';
