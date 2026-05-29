-- 审批记录表
CREATE TABLE sys_approval_record (
    id              BIGSERIAL PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    process_instance_id VARCHAR(64) NOT NULL,
    task_id         VARCHAR(64),
    assignee        VARCHAR(64) NOT NULL,
    operation_type  VARCHAR(20) NOT NULL,
    comment         VARCHAR(500),
    operation_time  TIMESTAMP NOT NULL,
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

CREATE INDEX idx_approval_record_process ON sys_approval_record(process_instance_id);
CREATE INDEX idx_approval_record_assignee ON sys_approval_record(assignee);
CREATE INDEX idx_approval_record_task ON sys_approval_record(task_id);

COMMENT ON TABLE sys_approval_record IS '审批记录';
COMMENT ON COLUMN sys_approval_record.process_instance_id IS '流程实例 ID';
COMMENT ON COLUMN sys_approval_record.task_id IS '任务 ID';
COMMENT ON COLUMN sys_approval_record.assignee IS '审批人标识';
COMMENT ON COLUMN sys_approval_record.operation_type IS '操作类型：APPROVE/REJECT/DELEGATE/ADD_SIGN/TRANSFER/WITHDRAW/URGE';
COMMENT ON COLUMN sys_approval_record.comment IS '审批意见';
COMMENT ON COLUMN sys_approval_record.operation_time IS '操作时间';
