-- 渠道平台配置 + 机器人绑定（两表方案）
-- channel_platform：平台基础凭证（一个钉钉应用一条记录）
-- channel_bot_binding：机器人实例绑定 Assistant（一个平台多个机器人）

CREATE TABLE channel_platform (
    id              BIGSERIAL PRIMARY KEY,
    type            VARCHAR(32)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    config          JSONB,
    status          INT          NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    owner_id        BIGINT,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    remark          VARCHAR(500)
);

COMMENT ON TABLE channel_platform IS '渠道平台配置';
COMMENT ON COLUMN channel_platform.type IS '平台类型：dingtalk/feishu/wecom_kf/wechat_mp/wechat_mini';
COMMENT ON COLUMN channel_platform.config IS '平台凭证 JSON（按 type 结构不同）';

CREATE INDEX idx_channel_platform_type ON channel_platform (type) WHERE deleted = FALSE;

CREATE TABLE channel_bot_binding (
    id              BIGSERIAL PRIMARY KEY,
    platform_id     BIGINT       NOT NULL,
    name            VARCHAR(100) NOT NULL,
    assistant_id    VARCHAR(64)  NOT NULL,
    route_rule      JSONB,
    fallback_reply  VARCHAR(500),
    status          INT          NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    owner_id        BIGINT,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    remark          VARCHAR(500)
);

COMMENT ON TABLE channel_bot_binding IS '机器人绑定 Assistant';
COMMENT ON COLUMN channel_bot_binding.platform_id IS '关联 channel_platform.id';
COMMENT ON COLUMN channel_bot_binding.assistant_id IS '绑定的 Assistant ID';
COMMENT ON COLUMN channel_bot_binding.route_rule IS '触发规则 JSON（关键词/群 ID 等）';

CREATE INDEX idx_bot_binding_platform ON channel_bot_binding (platform_id) WHERE deleted = FALSE;
CREATE INDEX idx_bot_binding_assistant ON channel_bot_binding (assistant_id) WHERE deleted = FALSE;
