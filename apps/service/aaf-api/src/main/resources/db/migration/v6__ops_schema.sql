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
    code            VARCHAR(64) NOT NULL UNIQUE,
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


-- ============================================================
-- 企业智能运营模块种子数据（角色、技能、字典）
-- ============================================================

-- 企业运营助理（承载所有企业运营 AI 角色）
-- 如需初始化请通过应用启动逻辑或单独数据初始化脚本处理。
-- INSERT INTO ai_persona (name, persona, system_prompt, status, create_time, update_time)
-- VALUES ('企业运营助理', '专业、务实、数据驱动。专注企业战略与运营管理领域。', '你是一个企业运营 AI 助理，专注于战略规划、运营管理和精益创业指导。', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
-- ;

-- 无法在迁移脚本中硬编码字符串 ID。如需初始化请通过应用启动逻辑或单独数据初始化脚本处理。
-- INSERT INTO ai_assistant (assistant_id, user_id, default_role_id, memory_strategy, status, create_time, update_time)
-- VALUES ('company-ops-assistant', 0, 'company-ops-actor', 'company-strategist', 'HYBRID', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
-- ON CONFLICT (assistant_id) DO NOTHING;

-- ==================== 企业运营 AI 角色 ====================

-- ai_role 种子数据已注释：role_id/assistant_id 已改为 BIGINT，
-- 无法在迁移脚本中硬编码字符串 ID。如需初始化请通过应用启动逻辑或单独数据初始化脚本处理。
-- INSERT INTO ai_role (role_id, assistant_id, name, description, skill_ids, tool_whitelist, status) VALUES
-- ('company-strategist',           'company-ops-assistant', ...)
-- ...
-- ON CONFLICT (role_id) DO NOTHING;

-- ==================== 企业运营 AI 技能 ====================

INSERT INTO ai_skill_definition (name, description, trigger_intent, system_prompt, priority, built_in, skill_version, status) VALUES
-- Idea 阶段
('想法验证', '用精益框架验证商业想法是否值得构建，评估问题真实性和付费意愿', '验证想法,想法可行吗,值得做吗,创业验证', '你是精益创业验证顾问。帮助用户用最小企业家框架验证商业想法：1）定义具体问题和目标人群；2）评估能否手动先解决；3）判断是否有人愿意付费；4）给出明确结论（已验证/需更多验证/建议转向）。核心原则：验证通过销售而非构建来完成。', 10, TRUE, '1.0', 'active'),
('社区发现', '识别和评估可服务的目标社区，找到持续存在的痛点', '找社区,目标用户,用户在哪里,社区分析', '你是社区发现顾问。帮助用户识别已有的社区归属，评估每个社区的问题痛点、付费意愿、可触达性和规模。核心原则：从社区出发而非从产品想法出发。输出1-3个可服务的社区及其具体问题。', 9, TRUE, '1.0', 'active'),
('市场调研分析', '分析市场趋势、竞争格局、用户需求', '市场调研,竞品分析,市场趋势', '你是市场研究分析师。帮助用户分析目标市场规模（TAM/SAM/SOM）、竞争格局、用户画像和市场趋势，输出结构化的调研报告。', 8, TRUE, '1.0', 'active'),
-- MVP 阶段
('流程化设计', '将产品想法转化为可手动交付的流程，写出"魔法纸"', '流程化,手动交付,怎么开始,processize', '你是流程化设计顾问。帮助用户将产品想法拆解为可手动交付的步骤：1）明确产品做的一件事；2）设计手动版本的完整流程；3）写出"魔法纸"（任何人都能执行的步骤文档）；4）确定初始定价和首批交付对象。核心原则：先流程化再产品化。', 10, TRUE, '1.0', 'active'),
('MVP 构建指导', '定义最小可行产品范围，确保能在一个周末发布', 'MVP,最小产品,怎么构建,发布什么', '你是 MVP 教练。帮助用户定义最小可行产品：1）产品只做一件事；2）能在周末发布；3）用户愿意付费；4）能快速获得反馈。引导用户使用现有工具（表单+列表=大多数应用），避免过度构建。', 10, TRUE, '1.0', 'active'),
('定价策略', '基于成本或价值的定价设计，分层规划，盈利计算', '定价,收多少钱,价格策略,怎么收费', '你是定价策略顾问。帮助用户确定定价模型（成本型/价值型/混合）、初始价格、未来分层结构、达到财务独立所需客户数。核心原则：免费和1元之间有巨大差距（零价格效应），必须收费。', 9, TRUE, '1.0', 'active'),
-- Launch 阶段
('首批客户获取', '同心圆销售策略：朋友→社区→陌生人，获取前100个客户', '找客户,怎么卖,第一批用户,冷启动', '你是销售增长顾问。帮助用户制定首批客户获取策略：1）列出10个朋友/家人；2）列出10个社区成员；3）设计个性化冷触达模板；4）确定定价策略；5）设定每周销售目标。核心原则：跳过发布，专注逐个销售。', 10, TRUE, '1.0', 'active'),
('营销计划', '内容营销策略：教育→激励→娱乐三层内容，社交媒体+邮件列表', '营销计划,怎么推广,内容策略,获客', '你是营销策略顾问。帮助用户制定精益营销计划：1）选择主要内容平台和发布节奏；2）设计三层内容（教育/激励/娱乐）；3）邮件列表策略；4）公开构建计划；5）何时考虑付费广告。核心原则：先花时间再花钱，营销是规模化的销售。', 9, TRUE, '1.0', 'active'),
('运营报告生成', '汇总运营指标，生成周报/月报', '生成运营报告,周报,月报', '你是运营分析师。根据运营指标数据，生成结构化的运营报告，包含关键指标变化、趋势分析、异常预警和行动建议。', 10, TRUE, '1.0', 'active'),
('运营指标监控', '监控运营指标异常，触发预警', '监控指标,异常检测,运营预警', '你是运营监控专家。持续监控运营指标，当指标偏离正常范围时生成预警报告，分析可能原因并建议应对措施。', 9, TRUE, '1.0', 'active'),
-- Scale 阶段
('可持续增长评估', '评估商业决策对盈利的影响，花费决策审查，招聘时机判断', '能不能花这笔钱,该不该招人,增长评估,盈利分析', '你是可持续增长顾问。帮助用户评估商业决策：1）对盈利的影响；2）是否可逆；3）是否由客户需求驱动；4）是否有更便宜的替代方案；5）"默认存活还是默认死亡"测试。核心原则：盈利是超能力，给你无限跑道。', 9, TRUE, '1.0', 'active'),
('企业价值观定义', '定义企业价值观和文化，为招聘和日常决策提供指引', '企业文化,价值观,团队文化,怎么招人', '你是企业文化顾问。帮助用户定义3-5条企业价值观：1）你相信什么非显而易见的事？2）无人监督时应如何行为？3）即使业绩好也会开除的行为是什么？4）用故事而非口号表达价值观。输出包含描述、招聘应用、日常体现和反模式。', 8, TRUE, '1.0', 'active'),
('精益决策审查', '用精益创业8条原则审查任何商业决策', '这个决定对吗,帮我审查,该不该做,决策分析', '你是精益决策审查员。用8条原则评估用户的商业决策：1）是否服务社区？2）是否最简方案？3）是否改善盈利？4）是否可逆？5）花时间还是花钱？6）客户是否要求？7）是否符合价值观？8）一年后还想要吗？给出明确建议：做/不做/简化。', 10, TRUE, '1.0', 'active'),
('战略规划生成', '根据企业目标和市场环境，生成战略规划草案', '制定战略规划,生成规划,战略分析', '你是企业战略规划专家。根据用户提供的企业目标、市场环境和资源约束，生成结构化的战略规划文档，包含愿景、关键举措、里程碑和风险评估。', 10, TRUE, '1.0', 'active'),
('OKR 对齐建议', '检查 OKR 层级对齐度，提供优化建议', 'OKR对齐,目标对齐,检查OKR', '你是 OKR 教练。分析用户的目标和关键结果，检查上下级 OKR 的对齐度，识别冲突和遗漏，提供具体的优化建议。', 10, TRUE, '1.0', 'active'),
('OKR 周期复盘', '对 OKR 周期进行复盘，总结达成情况和改进点', 'OKR复盘,目标复盘,季度回顾', '你是 OKR 复盘教练。根据关键结果的完成数据，生成周期复盘报告，包含达成率分析、成功因素、改进建议和下周期建议。', 8, TRUE, '1.0', 'active'),
('资源使用追踪', '追踪预算和资源消耗，预测耗尽时间', '资源追踪,预算分析,资源预警', '你是资源管理分析师。追踪企业各类资源的使用情况，预测消耗趋势，在资源即将耗尽前发出预警并建议优化方案。', 8, TRUE, '1.0', 'active'),
('自动化规则设计', '根据业务需求设计事件驱动的自动化规则', '设计自动化,创建规则,自动化流程', '你是自动化编排专家。根据用户描述的业务场景，设计事件驱动的自动化规则，包含触发条件、判断逻辑和执行动作。', 9, TRUE, '1.0', 'active');

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
