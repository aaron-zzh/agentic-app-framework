-- 计划任务注册表
CREATE TABLE sys_scheduled_task (
    id              BIGSERIAL PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    name            VARCHAR(100) NOT NULL,
    type            VARCHAR(50)  NOT NULL,
    cron            VARCHAR(50)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'active',
    last_run        TIMESTAMP,
    next_run        TIMESTAMP,
    fail_count      INTEGER NOT NULL DEFAULT 0,
    create_by       BIGINT,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    remark          VARCHAR(500)
);

COMMENT ON TABLE sys_scheduled_task IS '计划任务注册表';
COMMENT ON COLUMN sys_scheduled_task.name IS '任务名称';
COMMENT ON COLUMN sys_scheduled_task.type IS '任务类型：trash_cleanup/archive/automation_schedule';
COMMENT ON COLUMN sys_scheduled_task.cron IS 'Cron 表达式';
COMMENT ON COLUMN sys_scheduled_task.status IS '状态：active/paused/failed';
COMMENT ON COLUMN sys_scheduled_task.last_run IS '上次执行时间';
COMMENT ON COLUMN sys_scheduled_task.next_run IS '下次执行时间';
COMMENT ON COLUMN sys_scheduled_task.fail_count IS '连续失败次数';
