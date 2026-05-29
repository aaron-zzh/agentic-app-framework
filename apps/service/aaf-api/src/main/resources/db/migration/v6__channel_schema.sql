-- =============================================
-- AAF-075 渠道集成 Schema
-- =============================================

-- 渠道配置表
CREATE TABLE IF NOT EXISTS channel_config (
    id              BIGSERIAL PRIMARY KEY,
    channel_type    VARCHAR(32) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    app_id          VARCHAR(200),
    app_secret      VARCHAR(500),
    token           VARCHAR(200),
    encoding_aes_key VARCHAR(200),
    status          INT NOT NULL DEFAULT 0,
    ext_config      JSONB,
    -- BaseEntity 审计字段
    version         INT NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    owner_id        BIGINT,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    remark          VARCHAR(255)
);

CREATE INDEX idx_channel_config_type ON channel_config(channel_type) WHERE deleted = FALSE;

COMMENT ON TABLE channel_config IS '渠道配置';
COMMENT ON COLUMN channel_config.channel_type IS '渠道类型：wechat_mp/wechat_mini/dingtalk/feishu/web';
COMMENT ON COLUMN channel_config.status IS '状态：0 启用 / 1 禁用';

-- 渠道消息记录表
CREATE TABLE IF NOT EXISTS channel_message (
    id              BIGSERIAL PRIMARY KEY,
    channel_type    VARCHAR(32) NOT NULL,
    direction       VARCHAR(16) NOT NULL,
    message_type    VARCHAR(16) NOT NULL,
    external_user_id VARCHAR(200) NOT NULL,
    user_id         BIGINT,
    content         TEXT,
    media_url       VARCHAR(500),
    raw_payload     TEXT,
    message_time    TIMESTAMP,
    -- BaseEntity 审计字段
    version         INT NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    owner_id        BIGINT,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    remark          VARCHAR(255)
);

CREATE INDEX idx_channel_message_user ON channel_message(external_user_id, channel_type) WHERE deleted = FALSE;
CREATE INDEX idx_channel_message_time ON channel_message(message_time DESC) WHERE deleted = FALSE;

COMMENT ON TABLE channel_message IS '渠道消息记录';
COMMENT ON COLUMN channel_message.direction IS '消息方向：inbound（入站）/ outbound（出站）';
COMMENT ON COLUMN channel_message.message_type IS '消息类型：text/image/voice/event/template';

-- =============================================
-- 字典 seed：渠道类型
-- =============================================
INSERT INTO sys_dict_type (name, type, status, version, deleted, create_time, update_time)
SELECT '渠道类型', 'channel_type', 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE type = 'channel_type' AND deleted = FALSE);

INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time)
SELECT 'channel_type', '微信公众号', 'wechat_mp', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'channel_type' AND value = 'wechat_mp' AND deleted = FALSE);

INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time)
SELECT 'channel_type', '微信小程序', 'wechat_mini', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'channel_type' AND value = 'wechat_mini' AND deleted = FALSE);

INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time)
SELECT 'channel_type', '钉钉', 'dingtalk', 3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'channel_type' AND value = 'dingtalk' AND deleted = FALSE);

INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time)
SELECT 'channel_type', '飞书', 'feishu', 4, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'channel_type' AND value = 'feishu' AND deleted = FALSE);

INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time)
SELECT 'channel_type', '网页', 'web', 5, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'channel_type' AND value = 'web' AND deleted = FALSE);

-- 字典 seed：消息类型
INSERT INTO sys_dict_type (name, type, status, version, deleted, create_time, update_time)
SELECT '渠道消息类型', 'channel_message_type', 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE type = 'channel_message_type' AND deleted = FALSE);

INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time)
SELECT 'channel_message_type', '文本', 'text', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'channel_message_type' AND value = 'text' AND deleted = FALSE);

INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time)
SELECT 'channel_message_type', '图片', 'image', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'channel_message_type' AND value = 'image' AND deleted = FALSE);

INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time)
SELECT 'channel_message_type', '语音', 'voice', 3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'channel_message_type' AND value = 'voice' AND deleted = FALSE);

INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time)
SELECT 'channel_message_type', '事件', 'event', 4, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'channel_message_type' AND value = 'event' AND deleted = FALSE);

INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time)
SELECT 'channel_message_type', '模板消息', 'template', 5, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'channel_message_type' AND value = 'template' AND deleted = FALSE);


-- =============================================
-- AAF-075 #7503 Webhook Schema
-- =============================================

-- Webhook 配置表
CREATE TABLE IF NOT EXISTS webhook_config (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    url             VARCHAR(500) NOT NULL,
    event_types     VARCHAR(500),
    secret          VARCHAR(200),
    status          VARCHAR(16) NOT NULL DEFAULT 'active',
    direction       VARCHAR(16) NOT NULL DEFAULT 'outbound',
    failure_count   INT DEFAULT 0,
    max_retries     INT DEFAULT 3,
    -- BaseEntity 审计字段
    version         INT NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    owner_id        BIGINT,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    remark          VARCHAR(255)
);

CREATE INDEX idx_webhook_config_status ON webhook_config(status) WHERE deleted = FALSE;

COMMENT ON TABLE webhook_config IS 'Webhook 配置';
COMMENT ON COLUMN webhook_config.status IS '状态：active/inactive/failed';
COMMENT ON COLUMN webhook_config.direction IS '方向：outbound（推送到外部）/ inbound（接收外部推送）';

-- Webhook 推送日志表
CREATE TABLE IF NOT EXISTS webhook_log (
    id              BIGSERIAL PRIMARY KEY,
    webhook_id      BIGINT NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    request_body    TEXT,
    response_status INT,
    response_body   VARCHAR(2000),
    status          VARCHAR(16) NOT NULL DEFAULT 'pending',
    failure_reason  VARCHAR(500),
    retry_count     INT DEFAULT 0,
    next_retry_time TIMESTAMP,
    push_time       TIMESTAMP,
    -- BaseEntity 审计字段
    version         INT NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    owner_id        BIGINT,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    remark          VARCHAR(255)
);

CREATE INDEX idx_webhook_log_webhook ON webhook_log(webhook_id) WHERE deleted = FALSE;
CREATE INDEX idx_webhook_log_retry ON webhook_log(status, next_retry_time) WHERE deleted = FALSE;

COMMENT ON TABLE webhook_log IS 'Webhook 推送日志';
COMMENT ON COLUMN webhook_log.status IS '推送状态：success/failed/pending/abandoned';

-- =============================================
-- 字典 seed：Webhook 状态
-- =============================================
INSERT INTO sys_dict_type (name, type, status, version, deleted, create_time, update_time)
SELECT 'Webhook状态', 'webhook_status', 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE type = 'webhook_status' AND deleted = FALSE);

INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time)
SELECT 'webhook_status', '启用', 'active', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'webhook_status' AND value = 'active' AND deleted = FALSE);

INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time)
SELECT 'webhook_status', '停用', 'inactive', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'webhook_status' AND value = 'inactive' AND deleted = FALSE);

INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time)
SELECT 'webhook_status', '失败', 'failed', 3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'webhook_status' AND value = 'failed' AND deleted = FALSE);

-- 字典 seed：渠道类型补充 webhook
INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time)
SELECT 'channel_type', 'Webhook', 'webhook', 6, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'channel_type' AND value = 'webhook' AND deleted = FALSE);

-- 字典 seed：消息类型补充 markdown/card
INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time)
SELECT 'channel_message_type', 'Markdown', 'markdown', 6, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'channel_message_type' AND value = 'markdown' AND deleted = FALSE);

INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time)
SELECT 'channel_message_type', '卡片消息', 'card', 7, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'channel_message_type' AND value = 'card' AND deleted = FALSE);
