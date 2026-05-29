-- =============================================
-- AAF-076 客服系统 Schema
-- =============================================

-- 客服会话表
CREATE TABLE chat_session (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT,
    external_user_id VARCHAR(128) NOT NULL,
    channel_type    VARCHAR(32)  NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'bot',
    agent_id        BIGINT,
    staff_id        BIGINT,
    skill_group     VARCHAR(64),
    tags            VARCHAR(256),
    priority        INTEGER      DEFAULT 3,
    last_active_time TIMESTAMP,
    closed_time     TIMESTAMP,
    org_id          BIGINT,
    workspace_id    BIGINT,
    owner_id        BIGINT,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    remark          VARCHAR(255),
    version         INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_chat_session_external_user ON chat_session(external_user_id, channel_type);
CREATE INDEX idx_chat_session_status ON chat_session(status);
CREATE INDEX idx_chat_session_staff ON chat_session(staff_id, status);

COMMENT ON TABLE chat_session IS '客服会话';
COMMENT ON COLUMN chat_session.status IS '会话状态：bot/waiting/active/closed';
COMMENT ON COLUMN chat_session.external_user_id IS '渠道侧用户标识';

-- 客服消息表
CREATE TABLE chat_message (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT       NOT NULL,
    sender_type     VARCHAR(16)  NOT NULL,
    sender_id       BIGINT,
    message_type    VARCHAR(16)  NOT NULL DEFAULT 'TEXT',
    content         TEXT,
    internal        BOOLEAN      NOT NULL DEFAULT FALSE,
    org_id          BIGINT,
    workspace_id    BIGINT,
    owner_id        BIGINT,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    remark          VARCHAR(255),
    version         INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_chat_message_session ON chat_message(session_id, create_time);
CREATE INDEX idx_chat_message_session_visible ON chat_message(session_id, internal, create_time);

COMMENT ON TABLE chat_message IS '客服会话消息';
COMMENT ON COLUMN chat_message.sender_type IS '发送者类型：user/bot/staff';
COMMENT ON COLUMN chat_message.internal IS '是否内部消息（坐席间讨论）';

-- 客服坐席表
CREATE TABLE livechat_seat (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL UNIQUE,
    nickname        VARCHAR(64),
    skill_group     VARCHAR(128),
    status          VARCHAR(16)  NOT NULL DEFAULT 'offline',
    current_sessions INTEGER     NOT NULL DEFAULT 0,
    max_sessions    INTEGER      NOT NULL DEFAULT 5,
    org_id          BIGINT,
    workspace_id    BIGINT,
    owner_id        BIGINT,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    remark          VARCHAR(255),
    version         INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_livechat_seat_status ON livechat_seat(status);

COMMENT ON TABLE livechat_seat IS '客服坐席';
COMMENT ON COLUMN livechat_seat.skill_group IS '技能组（逗号分隔）';
COMMENT ON COLUMN livechat_seat.status IS '坐席状态：online/busy/offline';

-- 会话转接记录表
CREATE TABLE session_transfer (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT       NOT NULL,
    from_staff_id   BIGINT       NOT NULL,
    to_staff_id     BIGINT,
    to_skill_group  VARCHAR(64),
    reason          VARCHAR(32)  NOT NULL,
    note            VARCHAR(512),
    org_id          BIGINT,
    workspace_id    BIGINT,
    owner_id        BIGINT,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    remark          VARCHAR(255),
    version         INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_session_transfer_session ON session_transfer(session_id);

COMMENT ON TABLE session_transfer IS '会话转接记录';
COMMENT ON COLUMN session_transfer.reason IS '转接原因：skill_mismatch/workload/user_request/escalation/shift_change';

-- =============================================
-- 字典数据 seed
-- =============================================
INSERT INTO sys_dict_type (name, type, status, create_time, deleted, version)
VALUES ('客服会话状态', 'livechat_session_status', 0, CURRENT_TIMESTAMP, FALSE, 0);

INSERT INTO sys_dict_data (dict_type, label, value, sort, create_time, deleted, version)
VALUES
    ('livechat_session_status', '机器人服务中', 'bot', 1, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_session_status', '等待人工接入', 'waiting', 2, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_session_status', '人工服务中', 'active', 3, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_session_status', '已关闭', 'closed', 4, CURRENT_TIMESTAMP, FALSE, 0);

INSERT INTO sys_dict_type (name, type, status, create_time, deleted, version)
VALUES ('坐席状态', 'livechat_seat_status', 0, CURRENT_TIMESTAMP, FALSE, 0);

INSERT INTO sys_dict_data (dict_type, label, value, sort, create_time, deleted, version)
VALUES
    ('livechat_seat_status', '在线', 'online', 1, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_seat_status', '忙碌', 'busy', 2, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_seat_status', '离线', 'offline', 3, CURRENT_TIMESTAMP, FALSE, 0);

INSERT INTO sys_dict_type (name, type, status, create_time, deleted, version)
VALUES ('消息发送者类型', 'livechat_sender_type', 0, CURRENT_TIMESTAMP, FALSE, 0);

INSERT INTO sys_dict_data (dict_type, label, value, sort, create_time, deleted, version)
VALUES
    ('livechat_sender_type', '用户', 'user', 1, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_sender_type', '机器人', 'bot', 2, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_sender_type', '坐席', 'staff', 3, CURRENT_TIMESTAMP, FALSE, 0);

INSERT INTO sys_dict_type (name, type, status, create_time, deleted, version)
VALUES ('转接原因', 'livechat_transfer_reason', 0, CURRENT_TIMESTAMP, FALSE, 0);

INSERT INTO sys_dict_data (dict_type, label, value, sort, create_time, deleted, version)
VALUES
    ('livechat_transfer_reason', '技能不匹配', 'skill_mismatch', 1, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_transfer_reason', '工作量过大', 'workload', 2, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_transfer_reason', '用户要求', 'user_request', 3, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_transfer_reason', '问题升级', 'escalation', 4, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_transfer_reason', '换班交接', 'shift_change', 5, CURRENT_TIMESTAMP, FALSE, 0);



-- =============================================
-- #7604 满意度评价
-- =============================================

-- 会话满意度评价表
CREATE TABLE session_rating (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT       NOT NULL,
    user_id         BIGINT,
    staff_id        BIGINT,
    score           INTEGER      NOT NULL,
    comment         VARCHAR(512),
    org_id          BIGINT,
    workspace_id    BIGINT,
    owner_id        BIGINT,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    remark          VARCHAR(255),
    version         INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_session_rating_session ON session_rating(session_id) WHERE deleted = false;
CREATE INDEX idx_session_rating_staff ON session_rating(staff_id);
CREATE INDEX idx_session_rating_score ON session_rating(score, create_time);

COMMENT ON TABLE session_rating IS '会话满意度评价';
COMMENT ON COLUMN session_rating.score IS '评分（1-5）';

-- =============================================
-- #7605 工单管理
-- =============================================

-- 工单表
CREATE TABLE ticket (
    id              BIGSERIAL PRIMARY KEY,
    ticket_no       VARCHAR(32)  NOT NULL UNIQUE,
    title           VARCHAR(128) NOT NULL,
    description     TEXT,
    user_id         BIGINT,
    session_id      BIGINT,
    type            VARCHAR(32)  NOT NULL,
    priority        VARCHAR(16)  NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    assignee_id     BIGINT,
    sla_due_time    TIMESTAMP,
    closed_time     TIMESTAMP,
    org_id          BIGINT,
    workspace_id    BIGINT,
    owner_id        BIGINT,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    remark          VARCHAR(255),
    version         INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_ticket_status ON ticket(status);
CREATE INDEX idx_ticket_assignee ON ticket(assignee_id, status);
CREATE INDEX idx_ticket_user ON ticket(user_id);
CREATE INDEX idx_ticket_sla ON ticket(sla_due_time) WHERE status IN ('PENDING','PROCESSING','CONFIRMING');

COMMENT ON TABLE ticket IS '客服工单';
COMMENT ON COLUMN ticket.status IS '工单状态：PENDING/PROCESSING/CONFIRMING/CLOSED';
COMMENT ON COLUMN ticket.priority IS '优先级：LOW/MEDIUM/HIGH/URGENT';
COMMENT ON COLUMN ticket.sla_due_time IS 'SLA 截止时间，按优先级自动计算';

-- 工单流转记录表
CREATE TABLE ticket_record (
    id              BIGSERIAL PRIMARY KEY,
    ticket_id       BIGINT       NOT NULL,
    operation       VARCHAR(16)  NOT NULL,
    operator_id     BIGINT,
    from_status     VARCHAR(16),
    to_status       VARCHAR(16),
    record_remark   VARCHAR(512),
    org_id          BIGINT,
    workspace_id    BIGINT,
    owner_id        BIGINT,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    remark          VARCHAR(255),
    version         INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_ticket_record_ticket ON ticket_record(ticket_id, create_time);

COMMENT ON TABLE ticket_record IS '工单流转记录';
COMMENT ON COLUMN ticket_record.operation IS '操作类型：create/assign/start/confirm/close/reopen/transfer';

-- =============================================
-- #7604/#7605 字典数据
-- =============================================

INSERT INTO sys_dict_type (name, type, status, create_time, deleted, version)
VALUES ('工单状态', 'livechat_ticket_status', 0, CURRENT_TIMESTAMP, FALSE, 0);

INSERT INTO sys_dict_data (dict_type, label, value, sort, create_time, deleted, version)
VALUES
    ('livechat_ticket_status', '待处理', 'PENDING', 1, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_ticket_status', '处理中', 'PROCESSING', 2, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_ticket_status', '待确认', 'CONFIRMING', 3, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_ticket_status', '已关闭', 'CLOSED', 4, CURRENT_TIMESTAMP, FALSE, 0);

INSERT INTO sys_dict_type (name, type, status, create_time, deleted, version)
VALUES ('工单优先级', 'livechat_ticket_priority', 0, CURRENT_TIMESTAMP, FALSE, 0);

INSERT INTO sys_dict_data (dict_type, label, value, sort, create_time, deleted, version)
VALUES
    ('livechat_ticket_priority', '低', 'LOW', 1, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_ticket_priority', '中', 'MEDIUM', 2, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_ticket_priority', '高', 'HIGH', 3, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_ticket_priority', '紧急', 'URGENT', 4, CURRENT_TIMESTAMP, FALSE, 0);

INSERT INTO sys_dict_type (name, type, status, create_time, deleted, version)
VALUES ('工单类型', 'livechat_ticket_type', 0, CURRENT_TIMESTAMP, FALSE, 0);

INSERT INTO sys_dict_data (dict_type, label, value, sort, create_time, deleted, version)
VALUES
    ('livechat_ticket_type', '咨询', 'consultation', 1, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_ticket_type', '投诉', 'complaint', 2, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_ticket_type', '故障报告', 'bug_report', 3, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_ticket_type', '功能建议', 'feature_request', 4, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_ticket_type', '退款', 'refund', 5, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_ticket_type', '其他', 'other', 6, CURRENT_TIMESTAMP, FALSE, 0);

INSERT INTO sys_dict_type (name, type, status, create_time, deleted, version)
VALUES ('满意度评分', 'livechat_rating_score', 0, CURRENT_TIMESTAMP, FALSE, 0);

INSERT INTO sys_dict_data (dict_type, label, value, sort, create_time, deleted, version)
VALUES
    ('livechat_rating_score', '非常不满意', '1', 1, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_rating_score', '不满意', '2', 2, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_rating_score', '一般', '3', 3, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_rating_score', '满意', '4', 4, CURRENT_TIMESTAMP, FALSE, 0),
    ('livechat_rating_score', '非常满意', '5', 5, CURRENT_TIMESTAMP, FALSE, 0);
