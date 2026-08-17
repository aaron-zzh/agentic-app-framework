-- ============================================================
-- v6: 企业智能运营模块（company）
-- 子模块：planning / okr / erp / ops / automation
-- ============================================================

-- ==================== planning: 企业规划 ====================

CREATE TABLE company_plan (
    id              BIGSERIAL PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    name            VARCHAR(128) NOT NULL,
    plan_type       VARCHAR(32) NOT NULL,
    period          VARCHAR(16) NOT NULL,
    year            INTEGER NOT NULL,
    quarter         INTEGER,
    content         TEXT,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
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

COMMENT ON TABLE company_plan IS '企业战略规划';
CREATE INDEX idx_company_plan_status_year ON company_plan(status, year) WHERE deleted = FALSE;

-- ==================== okr: 目标与关键结果 ====================

CREATE TABLE company_objective (
    id              BIGSERIAL PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    title           VARCHAR(256) NOT NULL,
    plan_id         BIGINT REFERENCES company_plan(id),
    parent_id       BIGINT REFERENCES company_objective(id),
    owner_user_id   BIGINT,
    progress        NUMERIC(5,2) DEFAULT 0,
    status          VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED',
    period          VARCHAR(16),
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

COMMENT ON TABLE company_objective IS 'OKR 目标';
CREATE INDEX idx_company_objective_period ON company_objective(period) WHERE deleted = FALSE;
CREATE INDEX idx_company_objective_owner ON company_objective(owner_user_id) WHERE deleted = FALSE;

CREATE TABLE company_key_result (
    id              BIGSERIAL PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    objective_id    BIGINT NOT NULL REFERENCES company_objective(id),
    title           VARCHAR(256) NOT NULL,
    metric_type     VARCHAR(16) NOT NULL,
    start_value     NUMERIC(18,4),
    target_value    NUMERIC(18,4),
    current_value   NUMERIC(18,4),
    owner_user_id   BIGINT,
    status          VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED',
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

COMMENT ON TABLE company_key_result IS 'OKR 关键结果';
CREATE INDEX idx_company_kr_objective ON company_key_result(objective_id) WHERE deleted = FALSE;

-- ==================== erp: 企业资源 ====================

CREATE TABLE company_resource (
    id              BIGSERIAL PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    name            VARCHAR(128) NOT NULL,
    resource_type   VARCHAR(32) NOT NULL,
    total_amount    NUMERIC(18,2),
    used_amount     NUMERIC(18,2) DEFAULT 0,
    unit            VARCHAR(32),
    department      VARCHAR(64),
    status          VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
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

COMMENT ON TABLE company_resource IS '企业资源（轻量 ERP）';
CREATE INDEX idx_company_resource_type ON company_resource(resource_type) WHERE deleted = FALSE;

-- ==================== ops: 运营任务 ====================

CREATE TABLE company_ops_task (
    id              BIGSERIAL PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    category        VARCHAR(32) NOT NULL,
    cron_expr       VARCHAR(64),
    trigger_type    VARCHAR(16) NOT NULL,
    agent_id        BIGINT,
    config          JSONB,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
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

COMMENT ON TABLE company_ops_task IS '运营任务定义';
CREATE INDEX idx_company_ops_task_enabled ON company_ops_task(enabled) WHERE deleted = FALSE;

CREATE TABLE company_ops_execution (
    id              BIGSERIAL PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    task_id         BIGINT NOT NULL REFERENCES company_ops_task(id),
    status          VARCHAR(16) NOT NULL,
    start_time      TIMESTAMP,
    end_time        TIMESTAMP,
    result          TEXT,
    error_message   VARCHAR(1024),
    triggered_by    VARCHAR(16) NOT NULL,
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

COMMENT ON TABLE company_ops_execution IS '运营任务执行记录';
CREATE INDEX idx_company_ops_exec_task ON company_ops_execution(task_id, create_time DESC) WHERE deleted = FALSE;

CREATE TABLE company_ops_metric (
    id              BIGSERIAL PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    name            VARCHAR(128) NOT NULL,
    code            VARCHAR(64) NOT NULL,
    value           NUMERIC(18,4) NOT NULL,
    unit            VARCHAR(32),
    recorded_at     TIMESTAMP NOT NULL,
    source          VARCHAR(64),
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

COMMENT ON TABLE company_ops_metric IS '运营指标';
CREATE INDEX idx_company_ops_metric_code ON company_ops_metric(code, recorded_at DESC) WHERE deleted = FALSE;
CREATE UNIQUE INDEX uk_company_ops_metric_code ON company_ops_metric (code) WHERE deleted = FALSE;

-- ==================== automation: AI 自动化规则 ====================

CREATE TABLE company_automation_rule (
    id              BIGSERIAL PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    name            VARCHAR(128) NOT NULL,
    trigger_event   VARCHAR(64) NOT NULL,
    conditions      JSONB,
    action_type     VARCHAR(32) NOT NULL,
    action_config   JSONB,
    agent_id        BIGINT,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
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

COMMENT ON TABLE company_automation_rule IS 'AI 自动化规则';
CREATE INDEX idx_company_auto_rule_event ON company_automation_rule(trigger_event) WHERE deleted = FALSE AND enabled = TRUE;


-- ==================== lead: 访客线索 ====================
-- 记录未登录用户的动作流水：匿名对话续聊、邮箱订阅、联系我们、用户反馈

CREATE TABLE ops_guest_lead (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    version         INTEGER       NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,

    -- 访客身份（前端 localStorage 持久 UUID，同一访客的多个动作共用）
    anonymous_id    VARCHAR(64)   NOT NULL,
    -- 动作渠道：CHAT=匿名对话 / NEWSLETTER=订阅通知 / CONTACT=联系我们 / FEEDBACK=用户反馈
    channel         VARCHAR(32)   NOT NULL,

    -- 内容字段（按 channel 用其中一部分，全部可空）
    email           VARCHAR(200),
    name            VARCHAR(100),
    phone           VARCHAR(50),
    subject         VARCHAR(200),
    content         TEXT,

    -- chat 续聊关联（channel=CHAT 时填充）
    thread_id       VARCHAR(64),
    agent_role      VARCHAR(64),
    last_message_at TIMESTAMP(6),

    -- 元数据
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(500),
    referer         VARCHAR(500),
    region          VARCHAR(100),

    -- 处理状态：NEW=新线索 / PROCESSING=处理中 / RESOLVED=已处理 / SPAM=垃圾 / CLOSED=关闭
    status          VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    handled_by      BIGINT,
    handled_time    TIMESTAMP(6),

    -- 访客转正后关联的 sys_contact.id（可空，访客注册成正式联系人时填充）
    contact_id      BIGINT        REFERENCES sys_contact(id),

    -- 标准字段
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    owner_id        BIGINT,
    delete_time     TIMESTAMP(6),
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    remark          TEXT
);

COMMENT ON TABLE ops_guest_lead IS '访客线索：未登录用户的动作记录（对话、订阅、反馈、联系等）';
COMMENT ON COLUMN ops_guest_lead.anonymous_id IS '前端 localStorage 持久 UUID，同一访客的不同动作共用';
COMMENT ON COLUMN ops_guest_lead.channel IS 'CHAT=匿名对话 / NEWSLETTER=订阅通知 / CONTACT=联系我们 / FEEDBACK=用户反馈';
COMMENT ON COLUMN ops_guest_lead.thread_id IS 'channel=CHAT 时的 AG-UI threadId，用于续聊';
COMMENT ON COLUMN ops_guest_lead.contact_id IS '访客转正后关联的 sys_contact.id（可空）';
COMMENT ON COLUMN ops_guest_lead.region IS 'IP 推断的归属地（如 "广东 深圳市 南山区"），由 IpUtils.getAreaName 填充';

CREATE INDEX idx_ops_guest_lead_anonymous ON ops_guest_lead (anonymous_id, channel) WHERE deleted = false;
CREATE INDEX idx_ops_guest_lead_thread ON ops_guest_lead (thread_id) WHERE deleted = false AND thread_id IS NOT NULL;
CREATE INDEX idx_ops_guest_lead_email ON ops_guest_lead (email) WHERE deleted = false AND email IS NOT NULL;
CREATE INDEX idx_ops_guest_lead_status_create_time ON ops_guest_lead (status, create_time DESC) WHERE deleted = false;
CREATE INDEX idx_ops_guest_lead_contact ON ops_guest_lead (contact_id) WHERE deleted = false AND contact_id IS NOT NULL;

-- NEWSLETTER 邮箱去重：同邮箱仅可订阅一次（部分唯一索引，仅约束 NEWSLETTER 渠道）
CREATE UNIQUE INDEX uk_ops_guest_lead_newsletter_email
    ON ops_guest_lead (email)
    WHERE deleted = false AND channel = 'NEWSLETTER' AND email IS NOT NULL;


-- ============================================================
-- 企业智能运营模块种子数据（角色、技能、字典）
-- ============================================================

-- 企业运营助理（承载所有企业运营 AI 角色）
-- 如需初始化请通过应用启动逻辑或单独数据初始化脚本处理。
-- INSERT INTO ai_persona (name, persona, system_prompt, status, create_time, update_time)
-- VALUES ('企业运营助理', '专业、务实、数据驱动。专注企业战略与运营管理领域。', '你是一个企业运营 AI 助理，专注于战略规划、运营管理和精益创业指导。', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
-- ;

-- 无法在迁移脚本中硬编码字符串 ID。如需初始化请通过应用启动逻辑或单独数据初始化脚本处理。
-- INSERT INTO ai_assistant (code, user_id, persona_id, memory_strategy, status, create_time, update_time)
-- VALUES ('company-ops-assistant', 0, 1, 'HYBRID', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
-- ON CONFLICT (code) DO NOTHING;

-- ==================== 企业运营 AI 角色 ====================

-- Role 使用稳定 code，Skill Scope 保存稳定 Skill code；挂载关系统一写入 ai_assistant_role。
-- INSERT INTO ai_role (code, name, description, skill_ids, tool_whitelist, status) VALUES
-- ('company-strategist', '企业战略顾问', '...', '["company-strategy"]', '[]', 'active')
-- ON CONFLICT (code) DO NOTHING;
-- INSERT INTO ai_assistant_role (assistant_id, role_id, is_default, sort_order)
-- SELECT a.id, r.id, TRUE, 100 FROM ai_assistant a JOIN ai_role r ON r.code = 'company-strategist'
-- WHERE a.code = 'company-ops-assistant'
-- ON CONFLICT (assistant_id, role_id) DO NOTHING;

-- ==================== 企业运营 AI 技能 ====================
-- 旧 Skill 路由与可变提示词种子已移除。Skill 仅以根对象和不可变执行版本存在，
-- 具体候选 Scope 与意图规则由 RoleSelector → SkillSelection 承担。
-- ==================== 企业运营字典类型 ====================

INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('企业规划类型', 'company_plan_type',     0, 'STRATEGY/PRODUCT/GROWTH/FINANCE'),
('企业规划状态', 'company_plan_status',   0, 'DRAFT/ACTIVE/COMPLETED/ARCHIVED'),
('OKR 状态',    'company_okr_status',    0, 'NOT_STARTED/IN_PROGRESS/AT_RISK/COMPLETED'),
('资源类型',    'company_resource_type', 0, 'BUDGET/HEADCOUNT/TOOL/LICENSE'),
('运营任务分类', 'company_ops_category', 0, 'REPORT/SYNC/CHECK/NOTIFY/CUSTOM'),
('自动化动作',  'company_auto_action',   0, 'NOTIFY/CREATE_TASK/CALL_AGENT/WEBHOOK');

-- ==================== 企业运营字典数据 ====================

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('company_plan_type', '战略规划', 'STRATEGY', 1, 'primary'),
('company_plan_type', '产品规划', 'PRODUCT',  2, 'success'),
('company_plan_type', '增长规划', 'GROWTH',   3, 'warning'),
('company_plan_type', '财务规划', 'FINANCE',  4, 'info');

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('company_plan_status', '草稿',   'DRAFT',     1, 'info'),
('company_plan_status', '执行中', 'ACTIVE',    2, 'success'),
('company_plan_status', '已完成', 'COMPLETED', 3, 'default'),
('company_plan_status', '已归档', 'ARCHIVED',  4, 'info');

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('company_okr_status', '未开始', 'NOT_STARTED', 1, 'info'),
('company_okr_status', '进行中', 'IN_PROGRESS', 2, 'success'),
('company_okr_status', '有风险', 'AT_RISK',     3, 'danger'),
('company_okr_status', '已完成', 'COMPLETED',   4, 'default');

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('company_resource_type', '预算',     'BUDGET',    1, 'warning'),
('company_resource_type', '人力',     'HEADCOUNT', 2, 'primary'),
('company_resource_type', '工具',     'TOOL',      3, 'success'),
('company_resource_type', '许可证',   'LICENSE',   4, 'info');

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('company_ops_category', '报告',   'REPORT', 1, 'primary'),
('company_ops_category', '同步',   'SYNC',   2, 'success'),
('company_ops_category', '检查',   'CHECK',  3, 'warning'),
('company_ops_category', '通知',   'NOTIFY', 4, 'info'),
('company_ops_category', '自定义', 'CUSTOM', 5, 'default');

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('company_auto_action', '发送通知',   'NOTIFY',      1, 'info'),
('company_auto_action', '创建任务',   'CREATE_TASK', 2, 'primary'),
('company_auto_action', '调用 Agent', 'CALL_AGENT',  3, 'success'),
('company_auto_action', 'Webhook',    'WEBHOOK',     4, 'warning');

-- ==================== 预置企业运营工作流 ====================
-- 工作流通过 OpsTask(category=WORKFLOW) + config JSON 描述多步骤编排
-- 助理按 config.steps 顺序调用对应 skill，每步输出作为下步输入

INSERT INTO company_ops_task (name, description, category, trigger_type, config, enabled, version, deleted) VALUES
(
    '创业想法验证流水线',
    '从想法到验证的完整流程：社区发现→想法验证→流程化设计→MVP定义',
    'WORKFLOW', 'MANUAL',
    '{"steps":[{"skill":"company-find-community","name":"社区发现","output":"communities"},{"skill":"company-validate-idea","name":"想法验证","input":"communities","output":"validation"},{"skill":"company-processize","name":"流程化设计","input":"validation","output":"process"},{"skill":"company-mvp-build","name":"MVP定义","input":"process","output":"mvp_spec"}],"description":"Idea阶段完整验证，适合新项目启动"}',
    TRUE, 0, FALSE
),
(
    '季度 OKR 全流程',
    '制定OKR→对齐检查→周期执行→复盘总结',
    'WORKFLOW', 'CRON',
    '{"steps":[{"skill":"company-planning","name":"季度规划","output":"plan"},{"skill":"company-okr-align","name":"OKR对齐","input":"plan","output":"aligned_okr"},{"skill":"company-okr-review","name":"周期复盘","input":"aligned_okr","output":"review"}],"cron_note":"每季度末触发复盘步骤"}',
    TRUE, 0, FALSE
),
(
    '周运营报告',
    '汇总指标→异常检测→生成报告→通知相关人',
    'WORKFLOW', 'CRON',
    '{"steps":[{"skill":"company-resource-track","name":"资源盘点","output":"resources"},{"skill":"company-ops-monitor","name":"指标监控","output":"alerts"},{"skill":"company-ops-report","name":"报告生成","input":"resources,alerts","output":"report"}],"cron":"0 9 * * 1","description":"每周一9点自动执行"}',
    TRUE, 0, FALSE
),
(
    '增长决策评审',
    '收集数据→盈利评估→精益审查→输出建议',
    'WORKFLOW', 'MANUAL',
    '{"steps":[{"skill":"company-resource-track","name":"资源现状","output":"status"},{"skill":"company-grow-sustainably","name":"增长评估","input":"status","output":"assessment"},{"skill":"company-decision-review","name":"精益审查","input":"assessment","output":"recommendation"}],"description":"重大花费或招聘决策前使用"}',
    TRUE, 0, FALSE
),
(
    'GTM 启动流水线',
    '定价→首批客户策略→营销计划',
    'WORKFLOW', 'MANUAL',
    '{"steps":[{"skill":"company-pricing","name":"定价策略","output":"pricing"},{"skill":"company-first-customers","name":"客户获取","input":"pricing","output":"sales_plan"},{"skill":"company-marketing-plan","name":"营销计划","input":"sales_plan","output":"marketing"}],"description":"产品就绪后启动商业化"}',
    TRUE, 0, FALSE
);
