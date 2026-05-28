-- 审批流增强：抄送记录、表单模板、评论附件

-- 抄送记录表
CREATE TABLE approval_cc_record (
    id              BIGSERIAL PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    process_instance_id VARCHAR(64) NOT NULL,
    task_name       VARCHAR(128),
    cc_user         VARCHAR(64) NOT NULL,
    cc_time         TIMESTAMP NOT NULL,
    entity_type     VARCHAR(64),
    entity_id       VARCHAR(64),
    read            BOOLEAN NOT NULL DEFAULT FALSE,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    owner_id        BIGINT,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    remark          TEXT
);

CREATE INDEX idx_cc_record_user ON approval_cc_record(cc_user);
CREATE INDEX idx_cc_record_process ON approval_cc_record(process_instance_id);
CREATE INDEX idx_cc_record_user_read ON approval_cc_record(cc_user, read);

COMMENT ON TABLE approval_cc_record IS '审批抄送记录';
COMMENT ON COLUMN approval_cc_record.process_instance_id IS '流程实例 ID';
COMMENT ON COLUMN approval_cc_record.task_name IS '节点名称';
COMMENT ON COLUMN approval_cc_record.cc_user IS '被抄送人';
COMMENT ON COLUMN approval_cc_record.cc_time IS '抄送时间';
COMMENT ON COLUMN approval_cc_record.entity_type IS '关联实体类型';
COMMENT ON COLUMN approval_cc_record.entity_id IS '关联实体 ID';
COMMENT ON COLUMN approval_cc_record.read IS '是否已读';

-- 审批表单模板表
CREATE TABLE approval_form_template (
    id              BIGSERIAL PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(500),
    process_key     VARCHAR(128) NOT NULL,
    fields_json     TEXT,
    status          INTEGER NOT NULL DEFAULT 1,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    owner_id        BIGINT,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    remark          TEXT
);

CREATE INDEX idx_form_template_process_key ON approval_form_template(process_key);

COMMENT ON TABLE approval_form_template IS '审批表单模板';
COMMENT ON COLUMN approval_form_template.name IS '模板名称';
COMMENT ON COLUMN approval_form_template.process_key IS '关联流程定义 Key';
COMMENT ON COLUMN approval_form_template.fields_json IS '表单字段定义（JSON）';
COMMENT ON COLUMN approval_form_template.status IS '状态：0-禁用，1-启用';

-- 审批评论表
CREATE TABLE approval_comment (
    id              BIGSERIAL PRIMARY KEY,
    process_instance_id VARCHAR(64) NOT NULL,
    task_id         VARCHAR(64),
    user_id         VARCHAR(64) NOT NULL,
    content         TEXT,
    attachments     TEXT,
    mentioned_users TEXT,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_comment_process ON approval_comment(process_instance_id);
CREATE INDEX idx_comment_task ON approval_comment(task_id);

COMMENT ON TABLE approval_comment IS '审批评论';
COMMENT ON COLUMN approval_comment.process_instance_id IS '流程实例 ID';
COMMENT ON COLUMN approval_comment.task_id IS '任务 ID';
COMMENT ON COLUMN approval_comment.user_id IS '评论人';
COMMENT ON COLUMN approval_comment.content IS '评论内容';
COMMENT ON COLUMN approval_comment.attachments IS '附件列表（JSON 数组）';
COMMENT ON COLUMN approval_comment.mentioned_users IS '@提及的用户（JSON 数组）';
