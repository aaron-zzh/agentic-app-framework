-- 消息模板表
CREATE TABLE sys_message_template (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(100) NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    channel         VARCHAR(20)  NOT NULL,
    subject         VARCHAR(500),
    content         TEXT         NOT NULL,
    variables       JSONB,
    status          SMALLINT     NOT NULL DEFAULT 1,
    -- 公共字段
    version         INTEGER      NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    create_by       BIGINT,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    remark          VARCHAR(500)
);

COMMENT ON TABLE sys_message_template IS '消息模板';
COMMENT ON COLUMN sys_message_template.code IS '模板编码（唯一）';
COMMENT ON COLUMN sys_message_template.name IS '模板名称';
COMMENT ON COLUMN sys_message_template.channel IS '渠道：SMS/EMAIL/INTERNAL';
COMMENT ON COLUMN sys_message_template.subject IS '邮件主题（仅 EMAIL 使用）';
COMMENT ON COLUMN sys_message_template.content IS '模板内容（FreeMarker 语法）';
COMMENT ON COLUMN sys_message_template.variables IS '变量定义（JSON 数组）';
COMMENT ON COLUMN sys_message_template.status IS '状态：0=禁用 1=启用';
