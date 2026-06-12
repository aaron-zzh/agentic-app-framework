-- ============================================================
-- 生产必需种子数据（随应用一起部署，所有环境均执行）
-- ============================================================

-- ==================== 系统配置 ====================

INSERT INTO sys_config (category, config_key, value, default_value, value_type, name, description, visible, editable) VALUES
('site',     'site.name',                    'AAF',          'AAF',          'string',  '站点名称',             '系统显示名称',                       TRUE,  TRUE),
('site',     'site.logo',                    NULL,           NULL,           'string',  '站点 Logo',            'Logo 图片 URL',                      TRUE,  TRUE),
('user',     'user.default_password',        '123456',      '123456',      'string',  '用户默认密码',         '管理员创建用户时的初始密码',           FALSE, TRUE),
('user',     'user.register_enabled',        'true',        'true',        'boolean', '是否开放注册',         '关闭后禁止新用户自主注册',             TRUE,  TRUE),
('user',     'user.login_fail_lock_count',   '6',           '6',           'integer', '登录失败锁定次数',     '连续失败超过此次数后锁定账号',         TRUE,  TRUE),
('user',     'user.login_fail_lock_minutes', '5',           '5',           'integer', '账号锁定时长（分钟）', '登录失败锁定的持续时间',               TRUE,  TRUE),
('security', 'security.captcha_enabled',     'true',        'true',        'boolean', '是否启用验证码',       '登录时是否需要图形验证码',             TRUE,  TRUE),
('security', 'security.verify_code_expire',  '5',           '5',           'integer', '验证码有效期（分钟）', '邮件/短信验证码的有效时间',            TRUE,  TRUE),
('storage',  'storage.upload_max_size_mb',   '50',          '50',          'integer', '文件上传大小限制（MB）','单文件最大上传体积',                  TRUE,  TRUE),
('storage',  'storage.allowed_types',        'jpg,jpeg,png,gif,pdf,doc,docx,xls,xlsx,zip', NULL, 'string', '允许上传的文件类型', '逗号分隔的扩展名列表', TRUE, TRUE),
('ai',       'ai.default_model',             'gpt-4o-mini', 'gpt-4o-mini', 'string',  'AI 默认模型',             '未指定模型时使用的默认 LLM',                           TRUE,  TRUE),
('ai',       'ai.token_quota_per_user',      '100000',      '100000',      'integer', '用户 Token 配额',         '每用户每月 Token 使用上限，0=不限制',                  TRUE,  TRUE),
('ai',       'ai.credit_warn_threshold',     '10',          '10',          'integer', '积分预警阈值',             '用户积分低于此值时发送预警通知，提示充值',             TRUE,  TRUE),
('ai',       'ai.free_assistant_credit_cap', '100',         '100',         'integer', '免费助理虚拟用户预算上限', '免费助理入口绑定的系统虚拟用户每月积分预算上限',       TRUE,  TRUE),
('ai',       'ai.token_markup_rate',         '10',          '10',          'integer', 'Token计费加价倍数',       '相对供应商成本的加价倍数，默认10倍（1积分=1元）', TRUE, TRUE),
('brand',    'brand.company_name',           '学记智能',    '学记智能',    'string',  '公司名称',            '显示在邮件、页面标题等位置',           TRUE,  TRUE),
('brand',    'brand.logo_url',               NULL,          NULL,          'string',  'Logo URL',            '系统 Logo 图片地址',                   TRUE,  TRUE)
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_file_config (name, storage_type, config, master, status)
SELECT '本地存储', 'LOCAL', '{"basePath":"/data/aaf/files"}', TRUE, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_file_config WHERE master = TRUE AND deleted = FALSE);

-- ==================== 短信模板 ====================

INSERT INTO sys_sms_template (code, name, api_template_id, params, status)
VALUES ('AUTH_CODE', '登录/注册验证码', 'SMS_482485008', '["code"]', 1)
ON CONFLICT (code) DO NOTHING;

-- ==================== 消息模板（邮件） ====================

INSERT INTO sys_message_template (code, name, channel, subject, content, variables, status)
VALUES (
    'AUTH_VERIFY_CODE',
    '认证验证码',
    'EMAIL',
    '【${companyName}】安全验证码',
    '<!DOCTYPE html>
<html lang="zh-CN">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0"></head>
<body style="margin:0;padding:20px;background:#f5f5f5;font-family:-apple-system,BlinkMacSystemFont,''Segoe UI'',sans-serif;">
  <div style="max-width:560px;margin:0 auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,.08);">
    <div style="background:linear-gradient(135deg,#1565c0 0%,#1e88e5 100%);padding:28px 32px;">
      <div style="color:#fff;font-size:22px;font-weight:700;margin-bottom:4px;">${companyName}</div>
      <div style="color:rgba(255,255,255,.85);font-size:14px;">安全验证</div>
    </div>
    <div style="padding:32px;">
      <p style="margin:0 0 16px;color:#333;font-size:15px;">您好！</p>
      <p style="margin:0 0 24px;color:#333;font-size:15px;">您正在进行账号验证，您的${companyName}验证码为：</p>
      <div style="background:#e3f2fd;border-radius:10px;padding:20px 28px;display:inline-block;margin-bottom:24px;">
        <span style="color:#1565c0;font-size:36px;font-weight:700;letter-spacing:10px;">${code}</span>
      </div>
      <p style="margin:0;color:#888;font-size:13px;">验证码 ${expireMinutes} 分钟内有效，请勿泄露给他人。若非本人操作，请忽略此邮件。</p>
    </div>
    <div style="background:#f9f9f9;padding:16px 32px;border-top:1px solid #eee;">
      <p style="margin:0;color:#aaa;font-size:12px;">此邮件由系统自动发送，请勿直接回复。</p>
    </div>
  </div>
</body>
</html>',
    '["code","type","expireMinutes","companyName"]',
    1
) ON CONFLICT (code) DO UPDATE SET
    subject  = EXCLUDED.subject,
    content  = EXCLUDED.content,
    variables = EXCLUDED.variables;

-- 验证码邮件模板（旧格式，兼容 sendCode 调用）
INSERT INTO sys_message_template (code, name, channel, subject, content, variables)
VALUES
    ('auth.verify_code.register', '注册验证码', 'EMAIL', '注册验证码',
     '<p>您正在注册账号，验证码为：<strong>${code}</strong>，${expireMinutes} 分钟内有效，请勿泄露。</p>',
     '["code","expireMinutes"]'),
    ('auth.verify_code.login', '登录验证码', 'EMAIL', '登录验证码',
     '<p>您正在登录，验证码为：<strong>${code}</strong>，${expireMinutes} 分钟内有效，请勿泄露。</p>',
     '["code","expireMinutes"]'),
    ('auth.verify_code.reset', '重置密码验证码', 'EMAIL', '重置密码验证码',
     '<p>您正在重置密码，验证码为：<strong>${code}</strong>，${expireMinutes} 分钟内有效，请勿泄露。</p>',
     '["code","expireMinutes"]')
ON CONFLICT (code) DO NOTHING;


-- ==================== 初始管理员 ====================

-- 密码为 admin（BCrypt），首次登录后请修改
INSERT INTO sys_user (username, password, nickname, email, email_verified, status)
VALUES ('admin', '$2a$10$UyqdQK.M7V9FE4IzbbzeUeQnU.NsumDR.RCviFq4Pt04Y/F4VWLKC', '管理员', 'admin@xuejiai.com', TRUE, 0)
ON CONFLICT (username) DO NOTHING;

INSERT INTO sys_role (code, name, description)
VALUES ('admin', '管理员', '系统管理员，拥有全部权限'),
       ('user',  '普通用户', '普通用户，仅有只读权限'),
       ('super_admin', '超级管理员', '系统最高权限，不可删除'),
       ('org_admin', '组织管理员', '组织级管理权限'),
       ('member', '普通成员', '默认角色，基础读写权限'),
       ('guest', '访客', '只读权限'),
       ('agent', 'AI 智能体', 'AI Agent 专用角色')
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.code = 'super_admin'
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO sys_organization (name, slug, type, owner_id, create_by)
SELECT '默认工作空间', 'personal-' || u.id, 'personal', u.id, u.id
FROM sys_user u WHERE u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_organization o WHERE o.slug = 'personal-' || u.id);

INSERT INTO sys_org_member (org_id, user_id, role, create_by)
SELECT o.id, o.owner_id, 'owner', o.owner_id
FROM sys_organization o
WHERE o.type = 'personal' AND o.owner_id = (SELECT id FROM sys_user WHERE username = 'admin')
  AND NOT EXISTS (SELECT 1 FROM sys_org_member m WHERE m.org_id = o.id AND m.user_id = o.owner_id);


-- ==================== 积分转Token规则（默认） ====================

INSERT INTO credit_token_rule (name, credit_amount, token_amount, status, priority, remark)
VALUES
    ('基础套餐',   100,  100,  'ENABLED', 10, '100元=100积分'),
    ('标准套餐',   500,  550,  'ENABLED', 5,  '500元=550积分（赠10%）'),
    ('高级套餐',   1000, 1200, 'ENABLED', 1,  '1000元=1200积分（赠20%）')
ON CONFLICT DO NOTHING;


-- ==================== 系统菜单初始数据 ====================

INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible) VALUES
(NULL, '概览', NULL, NULL, 0, 'GROUP', true),
(NULL, 'AI 创作', NULL, NULL, 10, 'GROUP', true),
(NULL, '开发工具', NULL, NULL, 20, 'GROUP', false),
(NULL, '系统', NULL, NULL, 99, 'GROUP', true)
ON CONFLICT DO NOTHING;

INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible)
SELECT id, '工作台', '/dashboard', 'layout-dashboard', 0, 'MENU', true FROM sys_menu WHERE title = '概览' AND parent_id IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible)
SELECT id, '创作项目', '/aigc', 'sparkles', 0, 'MENU', true FROM sys_menu WHERE title = 'AI 创作' AND parent_id IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible)
SELECT id, '素材库', '/aigc/assets', 'image', 1, 'MENU', true FROM sys_menu WHERE title = 'AI 创作' AND parent_id IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible)
SELECT id, '文档管理', '/dev/docs', 'file-text', 0, 'MENU', false FROM sys_menu WHERE title = '开发工具' AND parent_id IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible)
SELECT id, '开发日志', '/dev/log', 'scroll-text', 1, 'MENU', false FROM sys_menu WHERE title = '开发工具' AND parent_id IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible)
SELECT id, '代码审查', '/dev/review', 'git-pull-request', 2, 'MENU', false FROM sys_menu WHERE title = '开发工具' AND parent_id IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible)
SELECT id, '迭代统计', '/dev/stats', 'bar-chart-3', 3, 'MENU', false FROM sys_menu WHERE title = '开发工具' AND parent_id IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible)
SELECT id, 'AI 模型', '/system/model', 'cpu', 0, 'MENU', true FROM sys_menu WHERE title = '系统' AND parent_id IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible)
SELECT id, '回收站', '/trash', 'trash-2', 1, 'MENU', true FROM sys_menu WHERE title = '系统' AND parent_id IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible)
SELECT id, '设置', '/settings', 'settings', 2, 'MENU', true FROM sys_menu WHERE title = '系统' AND parent_id IS NULL
ON CONFLICT DO NOTHING;


-- ==================== 画像维度预置数据 ====================

-- basic 基础信息
INSERT INTO profile_dimension (code, name, group_code, value_type, source, sort_order, searchable, ai_visible)
VALUES
('basic.age_range', '年龄段', 'basic', 'enum', 'manual', 1, true, true),
('basic.gender', '性别', 'basic', 'enum', 'manual', 2, true, true),
('basic.occupation', '职业', 'basic', 'text', 'manual', 3, true, true),
('basic.region', '地区', 'basic', 'text', 'manual', 4, true, false),
('basic.education', '教育程度', 'basic', 'enum', 'manual', 5, true, false)
ON CONFLICT (code) DO NOTHING;

UPDATE profile_dimension SET enum_options = '["18以下","18-25","26-35","36-45","46-55","56-65","65以上"]' WHERE code = 'basic.age_range';
UPDATE profile_dimension SET enum_options = '["男","女","其他"]' WHERE code = 'basic.gender';
UPDATE profile_dimension SET enum_options = '["小学","初中","高中","大专","本科","硕士","博士"]' WHERE code = 'basic.education';

-- preference 偏好
INSERT INTO profile_dimension (code, name, group_code, value_type, source, sort_order, searchable, ai_visible)
VALUES
('preference.interests', '兴趣爱好', 'preference', 'tags', 'manual', 1, true, true),
('preference.diet', '饮食偏好', 'preference', 'tags', 'manual', 2, false, true),
('preference.communication_style', '沟通风格偏好', 'preference', 'enum', 'ai', 3, false, true),
('preference.language', '语言', 'preference', 'text', 'manual', 4, true, true)
ON CONFLICT (code) DO NOTHING;

UPDATE profile_dimension SET enum_options = '["简洁直接","详细耐心","幽默轻松","正式专业"]' WHERE code = 'preference.communication_style';

-- behavior 行为（自动计算）
INSERT INTO profile_dimension (code, name, group_code, value_type, source, sort_order, searchable, ai_visible)
VALUES
('behavior.activity_level', '活跃度', 'behavior', 'enum', 'behavior', 1, true, true),
('behavior.usage_frequency', '使用频率', 'behavior', 'text', 'behavior', 2, true, false),
('behavior.spending_level', '消费等级', 'behavior', 'enum', 'behavior', 3, true, true)
ON CONFLICT (code) DO NOTHING;

UPDATE profile_dimension SET enum_options = '["高","中","低"]' WHERE code = 'behavior.activity_level';
UPDATE profile_dimension SET enum_options = '["高消费","中等","低消费","免费用户"]' WHERE code = 'behavior.spending_level';

-- health 健康（康养场景）
INSERT INTO profile_dimension (code, name, group_code, value_type, source, sort_order, searchable, ai_visible, unit)
VALUES
('health.blood_pressure', '血压', 'health', 'text', 'device', 1, false, true, 'mmHg'),
('health.blood_sugar', '血糖', 'health', 'number', 'device', 2, false, true, 'mmol/L'),
('health.medication', '用药情况', 'health', 'tags', 'manual', 3, false, true, NULL),
('health.allergy', '过敏史', 'health', 'tags', 'manual', 4, false, true, NULL),
('health.mobility', '行动能力', 'health', 'enum', 'manual', 5, true, true, NULL),
('health.cognitive', '认知状态', 'health', 'enum', 'manual', 6, true, true, NULL)
ON CONFLICT (code) DO NOTHING;

UPDATE profile_dimension SET enum_options = '["完全自理","需辅助","轮椅","卧床"]' WHERE code = 'health.mobility';
UPDATE profile_dimension SET enum_options = '["正常","轻度下降","中度下降","重度下降"]' WHERE code = 'health.cognitive';

-- living 生活
INSERT INTO profile_dimension (code, name, group_code, value_type, source, sort_order, searchable, ai_visible)
VALUES
('living.residence_type', '居住方式', 'living', 'enum', 'manual', 1, true, true),
('living.diet_restriction', '饮食禁忌', 'living', 'tags', 'manual', 2, false, true),
('living.transport', '出行方式', 'living', 'enum', 'manual', 3, false, false),
('living.emergency_contact', '紧急联系人', 'living', 'text', 'manual', 4, false, false)
ON CONFLICT (code) DO NOTHING;

UPDATE profile_dimension SET enum_options = '["独居","与配偶","与子女","养老院","其他"]' WHERE code = 'living.residence_type';
UPDATE profile_dimension SET enum_options = '["步行","公交","自驾","轮椅","不出门"]' WHERE code = 'living.transport';

-- personality 性格
INSERT INTO profile_dimension (code, name, group_code, value_type, source, sort_order, searchable, ai_visible)
VALUES
('personality.mbti', 'MBTI', 'personality', 'text', 'ai', 1, true, true),
('personality.emotion_tendency', '情绪倾向', 'personality', 'enum', 'ai', 2, false, true),
('personality.patience_level', '耐心程度', 'personality', 'enum', 'ai', 3, false, true)
ON CONFLICT (code) DO NOTHING;

UPDATE profile_dimension SET enum_options = '["乐观积极","平和稳定","容易焦虑","情绪波动"]' WHERE code = 'personality.emotion_tendency';
UPDATE profile_dimension SET enum_options = '["高","中","低"]' WHERE code = 'personality.patience_level';



-- ==================== 资源类权益定义 ====================

INSERT INTO entitlement_def (code, name, type, unit, description)
VALUES
('kb_storage',      '知识库存储',   'COUNTABLE', 'GB',  '知识库可用存储空间上限'),
('image_storage',   '图像存储',     'COUNTABLE', '张',  '图像素材库存储数量上限'),
('agent_count',     'Agent 数量',   'COUNTABLE', '个',  '可创建的 Agent 数量上限'),
('workflow_count',  '工作流数量',   'COUNTABLE', '个',  '可创建的工作流数量上限')
ON CONFLICT DO NOTHING;

-- ==================== AI 业务动作目录 ====================

INSERT INTO ai_action_catalog (
    action_key,
    entity_slug,
    display_name,
    description,
    enabled,
    risk_level,
    require_confirm,
    input_schema,
    sort_order,
    create_time,
    update_time
) VALUES
('query', 'system-role', '查询角色', '按分页和筛选条件查询角色列表，返回查询窗口。', TRUE, 'low', FALSE,
 '{"type":"object","properties":{"pageNo":{"type":"integer"},"pageSize":{"type":"integer"},"keyword":{"type":"string"},"status":{"type":"integer"},"fieldSet":{"type":"string"}}}',
 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('detail', 'system-role', '查看角色详情', '按 ID 查看角色详情。', TRUE, 'low', FALSE,
 '{"type":"object","required":["id"],"properties":{"id":{"type":"integer"},"queryToken":{"type":"string"},"fieldSet":{"type":"string"}}}',
 110, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('options', 'system-role', '角色选项', '查询角色选择器选项。', TRUE, 'low', FALSE,
 '{"type":"object","properties":{"q":{"type":"string"},"limit":{"type":"integer"}}}',
 120, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('meta', 'system-role', '角色元数据', '查询角色实体元数据和可用动作。', TRUE, 'low', FALSE,
 '{"type":"object","properties":{}}',
 130, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('create', 'system-role', '创建角色', '创建系统角色。', TRUE, 'medium', TRUE,
 '{"type":"object","required":["data"],"properties":{"data":{"type":"object","required":["code","name"],"properties":{"code":{"type":"string"},"name":{"type":"string"},"description":{"type":"string"}}}}}',
 200, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('update', 'system-role', '更新角色', '更新系统角色名称、描述或状态。', TRUE, 'medium', TRUE,
 '{"type":"object","required":["id","data"],"properties":{"id":{"type":"integer"},"data":{"type":"object","properties":{"name":{"type":"string"},"description":{"type":"string"},"status":{"type":"integer"}}}}}',
 210, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('delete', 'system-role', '删除角色', '删除单个系统角色。', TRUE, 'high', TRUE,
 '{"type":"object","required":["id"],"properties":{"id":{"type":"integer"}}}',
 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('batchDelete', 'system-role', '批量删除角色', '批量删除系统角色。', TRUE, 'high', TRUE,
 '{"type":"object","required":["ids"],"properties":{"ids":{"type":"array","items":{"type":"integer"}}}}',
 310, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (entity_slug, action_key) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    enabled = EXCLUDED.enabled,
    risk_level = EXCLUDED.risk_level,
    require_confirm = EXCLUDED.require_confirm,
    input_schema = EXCLUDED.input_schema,
    sort_order = EXCLUDED.sort_order,
    update_time = CURRENT_TIMESTAMP;

INSERT INTO ai_tool_catalog (
    tool_name,
    source,
    enabled,
    tool_type,
    category,
    risk_level,
    read_only,
    require_confirm,
    permission_code,
    entitlement_code,
    cost_expression,
    input_schema,
    sort_order,
    create_time,
    update_time
) VALUES
('listBusinessActions', 'LOCAL', TRUE, 'FUNCTION', 'BUSINESS_ACTION', 'LOW', TRUE, FALSE, 'tool:business-action:execute', NULL, NULL,
 '{"type":"object","properties":{}}',
 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('executeBusinessAction', 'LOCAL', TRUE, 'FUNCTION', 'BUSINESS_ACTION', 'MEDIUM', FALSE, TRUE, 'tool:business-action:execute', NULL, NULL,
 '{"type":"object","required":["requestJson"],"properties":{"requestJson":{"type":"string","description":"JSON 请求，包含 action、entity、params，可选 sessionId/confidence/verifiable"}}}',
 110, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('generateImage', 'LOCAL', TRUE, 'GENERATIVE', 'IMAGE_GENERATION', 'MEDIUM', FALSE, TRUE, 'tool:image-generate:execute', 'aigc_image', '10',
 '{"type":"object","required":["requestJson"],"properties":{"requestJson":{"type":"string","description":"JSON 参数：prompt 必填，width/height/model 可选"}}}',
 200, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('generateVideo', 'LOCAL', TRUE, 'GENERATIVE', 'VIDEO_GENERATION', 'HIGH', FALSE, TRUE, 'tool:video-generate:execute', 'aigc_video', '100',
 '{"type":"object","required":["requestJson"],"properties":{"requestJson":{"type":"string","description":"JSON 参数：prompt 必填，imageUrl/referenceImageUrls/model/resolution/ratio/duration/seed 可选"}}}',
 210, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('start_workflow', 'LOCAL', TRUE, 'FUNCTION', 'WORKFLOW', 'MEDIUM', FALSE, TRUE, 'tool:workflow:start', NULL, NULL,
 '{"type":"object","required":["process_key","description"],"properties":{"process_key":{"type":"string","description":"工作流定义 Key"},"description":{"type":"string","description":"工作流描述"},"variables":{"type":"string","description":"流程变量 JSON"}}}',
 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('list_workflows', 'LOCAL', TRUE, 'FUNCTION', 'WORKFLOW', 'LOW', TRUE, FALSE, NULL, NULL, NULL,
 '{"type":"object","properties":{}}',
 301, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (tool_name) DO UPDATE SET
    source = EXCLUDED.source,
    enabled = EXCLUDED.enabled,
    tool_type = EXCLUDED.tool_type,
    category = EXCLUDED.category,
    risk_level = EXCLUDED.risk_level,
    read_only = EXCLUDED.read_only,
    require_confirm = EXCLUDED.require_confirm,
    permission_code = EXCLUDED.permission_code,
    entitlement_code = EXCLUDED.entitlement_code,
    cost_expression = EXCLUDED.cost_expression,
    input_schema = EXCLUDED.input_schema,
    sort_order = EXCLUDED.sort_order,
    update_time = CURRENT_TIMESTAMP;

-- ==================== 订阅套餐 ====================

INSERT INTO subscription_plan (code, name, duration_days, price, market_price, monthly_credits, status, sort)
VALUES
    ('FREE', '免费套餐', 0,  0,    0,     0,    'ENABLED', 0),
    ('PRO',  '专业版',   30, 2900, 3900,  500,  'ENABLED', 1),
    ('TEAM', '团队版',   30, 9900, 12900, 2000, 'ENABLED', 2)
ON CONFLICT DO NOTHING;

-- ==================== FREE 套餐权益挂接 ====================

INSERT INTO plan_entitlement (plan_id, ent_id, quota, reset_cycle, refill_price)
SELECT p.id, e.id, v.quota, v.reset_cycle, 0
FROM subscription_plan p
CROSS JOIN (VALUES
    ('kb_storage',     1,    'NONE'),
    ('image_storage',  100,  'NONE'),
    ('agent_count',    3,    'NONE'),
    ('workflow_count', 5,    'NONE')
) AS v(code, quota, reset_cycle)
JOIN entitlement_def e ON e.code = v.code AND e.deleted = FALSE
WHERE p.code = 'FREE' AND p.deleted = FALSE
ON CONFLICT DO NOTHING;


-- ==================== 通用权限码 ====================

-- 通用权限码种子。菜单只引用权限码；接口安全边界由 @PreAuthorize/hasPermission 执行。

INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES
    ('角色读取', 'system:role:read', 'system', 'role', 'read', 0),
    ('角色创建', 'system:role:create', 'system', 'role', 'create', 0),
    ('角色更新', 'system:role:update', 'system', 'role', 'update', 0),
    ('角色删除', 'system:role:delete', 'system', 'role', 'delete', 0),
    ('角色导出', 'system:role:export', 'system', 'role', 'export', 0),
    ('用户读取', 'system:user:read', 'system', 'user', 'read', 0),
    ('用户创建', 'system:user:create', 'system', 'user', 'create', 0),
    ('用户更新', 'system:user:update', 'system', 'user', 'update', 0),
    ('用户删除', 'system:user:delete', 'system', 'user', 'delete', 0),
    ('菜单管理', 'system:menu:manage', 'system', 'menu', 'manage', 0),
    ('权限码管理', 'system:permission:manage', 'system', 'permission', 'manage', 0),
    ('数据权限规则管理', 'system:data-access-rule:manage', 'system', 'data-access-rule', 'manage', 0),
    ('ReBAC 关系管理', 'system:relation:manage', 'system', 'relation', 'manage', 0),
    ('访问策略管理', 'system:access-policy:manage', 'system', 'access-policy', 'manage', 0),
    ('开发者订阅套餐读取', 'developer:subscription-plan:read', 'developer', 'subscription-plan', 'read', 0),
    ('开发者订阅套餐创建', 'developer:subscription-plan:create', 'developer', 'subscription-plan', 'create', 0),
    ('开发者订阅套餐更新', 'developer:subscription-plan:update', 'developer', 'subscription-plan', 'update', 0),
    ('开发者订阅套餐删除', 'developer:subscription-plan:delete', 'developer', 'subscription-plan', 'delete', 0),
    ('开发者订阅套餐导出', 'developer:subscription-plan:export', 'developer', 'subscription-plan', 'export', 0),
    ('工具执行', 'tool:default:execute', 'tool', 'default', 'execute', 0),
    ('业务动作工具执行', 'tool:business-action:execute', 'tool', 'business-action', 'execute', 0),
    ('图片生成工具执行', 'tool:image-generate:execute', 'tool', 'image-generate', 'execute', 0),
    ('视频生成工具执行', 'tool:video-generate:execute', 'tool', 'video-generate', 'execute', 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission_code p
WHERE r.code IN ('super_admin', 'org_admin')
ON CONFLICT DO NOTHING;


-- ==================== 角色菜单与管理权限 ====================

-- 角色菜单与管理权限种子，保证初始管理员能看到动态菜单并管理系统配置。

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.code IN ('admin', 'org_admin', 'super_admin')
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission_code p
WHERE r.code IN ('admin', 'org_admin', 'super_admin')
ON CONFLICT DO NOTHING;


-- ==================== 销售演示角色 ====================

-- 销售演示角色：用于验证非管理员的菜单可见性与权限码授权。

INSERT INTO sys_role (code, name, description, status)
VALUES ('sales', '销售', '销售演示角色，拥有工作台与 AI 创作入口，不包含系统管理权限', 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.title IN ('概览', '工作台', 'AI 创作', '创作项目', '素材库')
WHERE r.code = 'sales'
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission_code p ON p.code IN ('tool:default:execute', 'tool:image-generate:execute')
WHERE r.code = 'sales'
ON CONFLICT DO NOTHING;


-- ==================== 内容创作助理 ====================

-- ============================================================
-- 内容创作助理：内置技能 + Agent + Role 种子数据
-- ============================================================

-- 内置技能：content-judge（爆款结构拆解器）
INSERT INTO ai_skill_definition (name, description, trigger_intent, instructions, priority, built_in, skill_version, status, create_time, update_time)
VALUES (
    '爆款结构拆解器',
    '分析爆款内容结构，判断是否值得复用。输入任意内容，输出核心观点、目标读者、展开路径、注意力钩子、情绪曲线、论证方式和可复用表达结构。',
    '["分析爆款","拆解结构","为什么火","分析内容","爆款分析","内容拆解"]',
    E'# 爆款结构拆解器 (Content-Judge)\n\n## 目标\n不是学写作，而是学「判断什么值得写」。\n\n## 规则\n- 不改写、不润色原内容\n- 不主观夸赞\n- 信息不足请标注「未知」\n- 判断基于结构与传播机制，而非个人喜好\n\n## 输出格式（严格按以下结构）\n\n1）核心观点（一句话）\n2）目标读者与使用场景\n3）内容展开路径（编号列表）\n4）注意力钩子（类型 + 原句）\n5）情绪变化曲线（开头 / 中段 / 结尾）\n6）论证方式（如：故事 / 对比 / 权威 / 反直觉）\n7）可复用表达结构（3-5 个模板）\n8）复用判断（是否值得复用 + 原因）',
    10, true, '1.0', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 内置技能：content-clarify（写作前元思考澄清器）
INSERT INTO ai_skill_definition (name, description, trigger_intent, instructions, priority, built_in, skill_version, status, create_time, update_time)
VALUES (
    '写作前元思考澄清器',
    '解决「我知道要写什么，但就是写不出来」。在写作前强制澄清 6 个关键决策变量。',
    '["不知道写什么","写作卡壳","逻辑混乱","想法很多","写不出来","澄清思路"]',
    E'# 写作前元思考澄清器 (Content-Clarify)\n\n## 目标\n在写作前强制澄清关键决策变量。\n\n## 引导用户回答以下 6 个问题\n\n1. 目标读者是谁？（具体画像，不是"所有人"）\n2. 发布平台是什么？（决定格式和语气）\n3. 读者此刻的真实痛点或欲望是什么？\n4. 这次内容的核心判断或结论是什么？（一句话）\n5. 内容将基于哪些经验/案例/证据？\n6. 整体表达风格偏向哪一种？（教学/故事/对话/清单/反直觉）\n\n## 规则\n- 逐个引导，不要一次性抛出所有问题\n- 用户回答模糊时追问细化\n- 6 个问题回答完毕后，输出一份「写作决策摘要」',
    10, true, '1.0', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 内置技能：content-architect（母内容结构构建器）
INSERT INTO ai_skill_definition (name, description, trigger_intent, instructions, priority, built_in, skill_version, status, create_time, update_time)
VALUES (
    '母内容结构构建器',
    '将已验证观点升级为可长期复用的核心内容结构。生成完整结构蓝图，包括钩子方案、正文结构、CTA 和裂变方向。',
    '["设计结构","写母内容","内容结构","构建文章","文章大纲","内容架构"]',
    E'# 母内容结构构建器 (Content-Architect)\n\n## 目标\n将已验证观点升级为「可长期复用的核心内容」。\n\n## 输出格式\n\n1）一句话承诺（读完能获得什么）\n2）开头钩子方案（3 个备选）\n3）正文结构\n   - 段落标题\n   - 段落目的\n   - 核心要点\n4）CTA 设计（软 CTA + 硬 CTA 各一）\n5）后续可裂变方向（5 个）\n\n## 规则\n- 基于用户提供的核心观点和素材\n- 结构必须可直接用于写作\n- 每个段落有明确目的，不允许「凑字数」段落',
    10, true, '1.0', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 内置技能：content-build（内容裂变与复利引擎）
INSERT INTO ai_skill_definition (name, description, trigger_intent, instructions, priority, built_in, skill_version, status, create_time, update_time)
VALUES (
    '内容裂变与复利引擎',
    '将一份母内容最大化利用，一次思考多次分发。生成短内容、强钩子、多平台版本、视频脚本和 CTA。',
    '["裂变内容","多平台分发","复用内容","改写成小红书","改写成公众号","一稿多用"]',
    E'# 内容裂变与复利引擎 (Content-Build)\n\n## 目标\n保持观点一致，生成多样表达，一次思考多平台使用。\n\n## 规则\n- 不新增核心观点，只拆观点\n- 每条内容只表达一个点\n- 表达方式必须不同\n\n## 输出格式\n\n1）短内容 × 10（100-200 字，适合社交媒体）\n2）强钩子 × 5（一句话，吸引点击）\n3）平台适配版本 × 3\n   - 公众号版（长图文，800-1500 字）\n   - 小红书版（图文笔记，300-500 字 + 配图建议）\n   - 抖音/视频号版（口播脚本，含前 3 秒钩子）\n4）CTA 备选 × 5',
    10, true, '1.0', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 内置技能：content-schedule（内容创作调度器）
INSERT INTO ai_skill_definition (name, description, trigger_intent, instructions, priority, built_in, skill_version, status, create_time, update_time)
VALUES (
    '内容创作调度器',
    '协调调度内容创作全流程。按「拆解→澄清→构建→裂变」顺序引导用户，判断当前阶段并调用对应技能。',
    '["内容创作","写文章","创作内容","帮我写","内容永动机","系统化创作"]',
    E'# 内容创作调度器 (Content-Schedule)\n\n## 目标\n按照「拆解→想清楚→写一次→用到极致」的顺序调度创作流程。\n\n## 阶段判断标准\n\n**阶段 1 - 拆解**：用户提到分析爆款、学习结构 → 调用 content-judge\n**阶段 2 - 澄清**：用户不知道写什么、逻辑混乱 → 调用 content-clarify\n**阶段 3 - 构建**：用户有验证过的观点、要写正文 → 调用 content-architect\n**阶段 4 - 裂变**：用户已完成内容、要多平台分发 → 调用 content-build\n\n## 规则\n- 首次交互时评估用户处于哪个阶段\n- 如果用户直接说「帮我写一篇 XXX」，从阶段 2（澄清）开始\n- 如果用户提供了爆款内容要分析，从阶段 1 开始\n- 每个阶段完成后，主动引导进入下一阶段\n- 全程可调用文档工具保存中间产出',
    20, true, '1.0', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- ============================================================
-- 内容创作助理 Agent
-- ============================================================

INSERT INTO ai_agent_definition (agent_id, name, description, system_prompt, model_id, capabilities, tools, max_iterations, timeout_seconds, memory_config, status, create_time, update_time)
VALUES (
    'content-creator',
    '内容创作助理',
    '帮助用户完成内容创作全流程：爆款拆解→思路澄清→结构构建→内容裂变→多平台发布。',
    E'你是一位专业的内容创作助理，擅长帮助用户系统化地创作高质量内容。\n\n你的核心能力：\n1. 分析爆款内容的传播结构\n2. 引导用户澄清写作思路\n3. 构建可复用的内容结构\n4. 将一份内容裂变为多平台版本\n5. 协助发布到各平台（公众号、小红书、抖音、视频号）\n\n工作原则：\n- 按「拆解→澄清→构建→裂变→发布」的顺序引导\n- 每个阶段产出保存为文档，方便后续编辑\n- 用中文交流，语气专业但不生硬\n- 主动推进流程，不等用户追问',
    NULL,  -- model_id: BIGINT，运行时按实际模型 ID 填写
    'CHAT',
    '["createDocument","updateDocument","publish","publishStatus","collect"]',
    15, 180,
    '{"maxToken": 100000, "msgThreshold": 50, "lastKeep": 10, "largePayloadThreshold": 2000, "minConsecutiveToolMessages": 6}',
    'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (agent_id) DO UPDATE SET
    system_prompt = EXCLUDED.system_prompt,
    tools = EXCLUDED.tools,
    memory_config = EXCLUDED.memory_config,
    update_time = CURRENT_TIMESTAMP;

-- 内容创作（人格）
INSERT INTO ai_persona (name, persona, system_prompt, status, create_time, update_time)
VALUES (
    '内容创作专家',
    '专业、有洞察力、善于引导。像一位资深内容策划师，既懂传播规律又懂用户心理。',
    '你是一位资深内容创作专家，帮助用户从 0 到 1 完成高质量内容创作和多平台分发。',
    'active',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 内容创作 Role（能力集）
INSERT INTO ai_role (name, description, skill_ids, tool_whitelist, status, create_time, update_time)
VALUES (
    '内容创作能力集',
    '内容拆解、思路澄清、结构构建、内容裂变、多平台发布',
    '["content-schedule","content-judge","content-clarify","content-architect","content-build"]',
    '["createDocument","updateDocument","publish","publishStatus","collect"]',
    'active',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- ============================================================
-- 开发者商业化套餐 Seed
-- ============================================================

INSERT INTO developer_subscription_plan (
    code,
    name,
    duration_days,
    price,
    included_tokens,
    allow_managed_gateway,
    allow_sub_proxy,
    max_proxy_depth,
    status,
    sort_order,
    create_time,
    update_time
) VALUES
    ('DEV_FREE', '开发者免费版', 0, 0, 0, FALSE, FALSE, 0, 'ENABLED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('DEV_MANAGED', '托管模型自用版', 30, 9900, 1000000, TRUE, FALSE, 0, 'ENABLED', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('DEV_RESELLER', '托管模型分销版', 30, 29900, 5000000, TRUE, TRUE, 1, 'ENABLED', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('DEV_ENTERPRISE', '企业代理版', 365, 299900, 100000000, TRUE, TRUE, 2, 'ENABLED', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;





-- ==================== 行级数据权限规则 ====================
-- 普通成员只能看/改自己的待办和通知；管理员/超级管理员自动绕过（isSuperAdmin 逻辑）

INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('todo',
     '["member","guest","sales","agent"]',
     '{"field":"assigneeId","op":"eq","value":"$user.id"}',
     'allow'),
    ('notification',
     '["member","guest","sales","agent"]',
     '{"field":"userId","op":"eq","value":"$user.id"}',
     'allow'),
    -- generation-template：普通用户只能看自己的模板或公开模板
    ('generation-template',
     '["member","guest","sales","agent"]',
     '{"or":[{"field":"userId","op":"eq","value":"$user.id"},{"field":"isPublic","op":"eq","value":true}]}',
     'allow')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 模型供应商
-- ============================================================
INSERT INTO ai_model_provider (provider_code, provider_name, provider_type, base_url, enabled, sort_order, description)
VALUES
('aliyun',    '阿里云百炼',   'OPENAI_COMPAT', 'https://dashscope.aliyuncs.com/compatible-mode/v1', true,  10, '阿里云百炼平台，支持通义千问、万相图像等模型，DashScope SDK'),
('deepseek',  'DeepSeek',    'OPENAI_COMPAT', 'https://api.deepseek.com/v1',                       true,  20, 'DeepSeek 官方 API，支持 deepseek-chat / deepseek-reasoner'),
('n1n',       'N1N',         'OPENAI_COMPAT', 'https://llm-api.net/v1',                            true,  30, 'N1N 聚合平台，兼容 OpenAI 接口协议')
ON CONFLICT (provider_code) DO NOTHING;



-- ============================================================
-- 提示词模板预置数据
-- ============================================================

INSERT INTO generation_template (name, type, category, prompt, negative_prompt, is_public, usage_count, user_id, create_time, update_time, version, deleted, scope)
VALUES
-- ===== 图像生成模板 =====
('赛博朋克城市夜景',  'IMAGE', '科幻',  '赛博朋克风格城市夜景，霓虹灯璀璨，雨后街道倒影，高楼林立，超写实，8K 细节',     '模糊，低质量，变形，水印',   true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('油画风格山水',      'IMAGE', '风景',  '中国传统山水画风格，云雾缭绕，古松苍劲，墨韵流动，意境深远，写意风格',           '现代元素，摄影感，低质量',   true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('写实人物肖像',      'IMAGE', '人物',  '专业摄影棚人物肖像，自然光，浅景深，清晰五官，高清细节，胶片质感',               '变形，模糊，水印，多人',     true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('梦幻奇幻场景',      'IMAGE', '奇幻',  '奇幻风格魔法森林，发光蘑菇，精灵光点，薄雾弥漫，神秘氛围，史诗级光效，4K',       '现实场景，普通，低质量',     true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('极简风格产品图',    'IMAGE', '商业',  '极简白色背景产品摄影，专业打光，高光反射，商业级品质，超清细节',                  '杂乱背景，阴影过重，变形',   true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('动漫二次元人物',    'IMAGE', '动漫',  '日系动漫风格，精致五官，明亮色彩，清晰线条，高品质插画，赛璐璐风格',             '写实风，模糊，低质量',       true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('水墨国风建筑',      'IMAGE', '建筑',  '中国传统建筑，水墨风格，飞檐翘角，红墙绿瓦，云雾缭绕，诗意意境',                 '现代建筑，照片感',           true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('星空银河风景',      'IMAGE', '自然',  '夜空星河壮观，银河清晰，流星划过，山脉剪影，长曝光摄影风格，超写实',             '白天，城市灯光，模糊',       true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
-- ===== 图像编辑模板（需配合参考图使用）=====
('水墨题诗',          'IMAGE', '图像编辑', '在画面右下角石板路旁、靠近树干根部的位置，以浅灰墨色手写体题写一首七言绝句，字体为行楷风格，笔触自然流畅、略带飞白，大小适中（约占画面高度1/10），与整体水墨淡雅氛围协调。诗文内容为："青石桥畔柳风轻， 素手拈花闭目听。 一水碧痕浮旧梦， 半篙烟雨入空舲。"诗句横向排列，四句分两行书写（前两句一行，后两句一行），末句"舲"字右下角钤一枚朱红小印，印文为"江南"二字篆书，尺寸约等于单字高度的1/3。', '低分辨率，低画质，文字模糊，扭曲', true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('爆款文字渲染',      'IMAGE', '图像编辑', '在画面适当位置以醒目字体添加以下文字内容，文字与背景融合自然，字体样式与整体画面风格协调，确保文字清晰可读，不遮挡主体核心区域。文字内容：[请替换为你的文字]', '文字模糊，扭曲，难以辨认', true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('风格迁移',          'IMAGE', '图像编辑', '将参考图中的主体保持不变，将整体画面风格转换为[请替换：水彩/油画/赛博朋克/动漫/写实摄影]风格，保留主体的形状、姿态和主要特征，调整色彩和纹理以匹配目标风格，使整体效果自然协调。', '风格不明显，主体变形，失真', true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('局部修改',          'IMAGE', '图像编辑', '对参考图进行局部修改：[请替换具体修改要求，例如：将背景替换为日落海边场景 / 去除画面中的杂物 / 为人物换上红色连衣裙]，保持其他区域与原图完全一致，修改区域过渡自然。', '大幅改变主体，失真，低质量', true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('场景扩充',          'IMAGE', '图像编辑', '基于参考图，向[请替换：左/右/上/下/四周]方向扩展画面，补全超出原图边界的场景内容，保持画面的光线、透视、风格与原图高度一致，新增区域与原图无缝融合。', '边界不自然，风格不统一，低质量', true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
-- ===== 视频生成模板 =====
('城市延时摄影',      'VIDEO', '城市',  '城市街道延时摄影，车流光轨，霓虹闪烁，人流穿梭，动感十足，电影质感',             null, true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('自然风光流动',      'VIDEO', '自然',  '自然风光，流水潺潺，云彩流动，光影变换，宁静氛围，4K 超清',                      null, true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('产品展示动画',      'VIDEO', '商业',  '产品 360 度旋转展示，专业光效，粒子特效，科技感十足，商业级品质',                 null, true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('人物动态表情',      'VIDEO', '人物',  '人物面部表情自然变化，微笑，真实感，情感丰富，电影质感镜头',                     null, true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
-- ===== 文案生成模板 =====
('小红书种草文案',   'COPYWRITING', '社交媒体', '请为以下产品写一篇小红书种草文案，要求：标题吸引眼球含 emoji，正文分段清晰，突出产品亮点，加入使用体验，结尾引导互动，字数 200-300 字。产品：', null, true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('抖音口播脚本',     'COPYWRITING', '视频脚本', '请为以下主题写一段 30 秒抖音口播脚本，要求：开头 3 秒抓眼球，中间说清楚一个核心卖点，结尾引导点赞关注，口语化表达，节奏紧凑。主题：',      null, true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('产品详情页文案',   'COPYWRITING', '电商',     '请为以下产品写电商详情页文案，要求：标题突出核心卖点，分模块描述产品特点、使用场景、用户痛点解决方案，结尾引导购买，语言专业有说服力。产品：', null, true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('公众号推文开头',   'COPYWRITING', '内容营销', '请为以下主题写一个公众号推文开头，要求：用故事或问题引入，制造悬念，激发读者继续阅读的欲望，字数 100 字左右。主题：',              null, true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
-- ===== 图像：更多风格 =====
('电影感胶片',      'IMAGE', '通用风格', 'cinematic film grain, anamorphic lens, golden hour, muted color palette, shallow depth of field', '模糊，低质量，噪点过重', true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('水彩插画',        'IMAGE', '通用风格', 'watercolor illustration, soft edges, pastel tones, paper texture, hand-painted',                  '照片感，锐利，数字感',   true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('油画质感',        'IMAGE', '通用风格', 'oil painting, impressionist brushstrokes, rich textures, museum quality',                          '低质量，现代感，模糊',   true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('极简主义',        'IMAGE', '通用风格', 'minimalist composition, clean background, single subject, flat design, Bauhaus style',             '杂乱，过度装饰，低质量', true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
-- ===== 人像摄影 =====
('商业人像',        'IMAGE', '人像摄影', 'professional portrait, studio lighting, white background, business attire, sharp eyes',            '变形，模糊，多人',       true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('时尚大片',        'IMAGE', '人像摄影', 'fashion editorial, high-end magazine, dramatic lighting, bold composition, luxury brand aesthetic', '普通，低质量，过时',     true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('街拍风格',        'IMAGE', '人像摄影', 'candid street photography, natural light, urban backdrop, authentic expression, reportage style',   '摆拍感，模糊，低质量',   true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
-- ===== 产品电商 =====
('白底产品图',      'IMAGE', '产品电商', 'product photography, pure white background, studio lighting, commercial, high detail, e-commerce',  '杂乱背景，阴影过重，变形', true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('场景产品图',      'IMAGE', '产品电商', 'lifestyle product shot, natural environment, contextual setting, warm tones, aspirational',         '白底，孤立感，低质量',    true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('科技产品风',      'IMAGE', '产品电商', 'tech product render, dark background, neon accent lighting, futuristic materials, 3D render quality','普通背景，低质量，模糊',  true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
-- ===== 文案：口播 =====
('产品口播脚本',    'COPYWRITING', '口播', '开头3秒痛点钩子，中间产品解决方案，结尾限时优惠+行动号召，总字数≤200字',                              null, true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('知识科普口播',    'COPYWRITING', '口播', '问题引入→核心知识点3条→实用建议，语言口语化，节奏明快，适合15-60秒短视频',                            null, true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('品牌故事口播',    'COPYWRITING', '口播', '创始人视角叙述品牌起源，强调核心价值观，情感共鸣，结尾点明品牌使命',                                   null, true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
-- ===== 文案：小红书细分 =====
('测评攻略',        'COPYWRITING', '小红书', '专业测评框架：外观→成分→使用感→性价比，数据量化，客观正反评价，适合护肤/美妆/数码',                  null, true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('旅行日记',        'COPYWRITING', '小红书', '沉浸式旅行叙述，五感描写，推荐 tips 清单，附地点/价格/交通信息，引发向往感',                          null, true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
('穿搭分享',        'COPYWRITING', '小红书', '场合定位+整体搭配思路+单品来源+价格，语气时髦轻松，突出性价比或独特性',                               null, true, 0, 1, NOW(), NOW(), 0, false, 'GENERATION'),
-- ===== 项目级：视觉风格（IMAGE） =====
('品牌视觉规范',    'IMAGE', '项目风格', '统一使用品牌主色调，构图留白充足，字体简洁无衬线，光线柔和漫射，整体调性专业现代',                        null, true, 0, 1, NOW(), NOW(), 0, false, 'PROJECT'),
('暖调生活方式',    'IMAGE', '项目风格', '暖黄橙色系，自然光优先，生活场景真实感，浅景深虚化背景，营造温馨治愈氛围',                               null, true, 0, 1, NOW(), NOW(), 0, false, 'PROJECT'),
('冷淡高级感',      'IMAGE', '项目风格', '冷灰蓝白色系，高对比度，极简构图，大面积留白，奢侈品/时尚品牌质感',                                      null, true, 0, 1, NOW(), NOW(), 0, false, 'PROJECT'),
('国潮东方美学',    'IMAGE', '项目风格', '中式色彩（朱红·墨黑·松石绿），传统纹样点缀，留白意境，水墨渐变，东方现代融合',                           null, true, 0, 1, NOW(), NOW(), 0, false, 'PROJECT'),
-- ===== 项目级：视频基调（VIDEO） =====
('品牌宣传片基调',  'VIDEO', '项目风格', '稳定运镜为主，慢推/慢拉，色彩饱和统一，背景音乐大气舒缓，叙事节奏从容，突出品质感',                      null, true, 0, 1, NOW(), NOW(), 0, false, 'PROJECT'),
('快节奏爆款短视频','VIDEO', '项目风格', '卡点剪辑，3秒一个画面切换，饱和高对比色调，字幕动效强烈，适合 15-30 秒竖版内容',                         null, true, 0, 1, NOW(), NOW(), 0, false, 'PROJECT'),
('纪录片人文风',    'VIDEO', '项目风格', '手持跟拍+固定机位交替，自然同期声，低饱和复古色调，慢速叙事，画外音温柔有力',                             null, true, 0, 1, NOW(), NOW(), 0, false, 'PROJECT'),
-- ===== 项目级：内容定位（COPYWRITING） =====
('美妆护肤账号定位','COPYWRITING', '项目定位', '目标受众：18-35岁女性；内容方向：真实测评+成分科普+妆容教程；语气：专业但亲切；避免：夸大效果、绝对化用词',                                      null, true, 0, 1, NOW(), NOW(), 0, false, 'PROJECT'),
('科技数码账号定位','COPYWRITING', '项目定位', '目标受众：20-40岁科技爱好者；内容方向：新品体验+横向对比+性价比分析；语气：理性客观；核心价值：帮用户做决策',                                      null, true, 0, 1, NOW(), NOW(), 0, false, 'PROJECT'),
('生活方式账号定位','COPYWRITING', '项目定位', '目标受众：25-40岁都市白领；内容方向：精致生活方式+好物推荐+轻松自救攻略；语气：轻松有温度；调性：向往感+可实现感',                                  null, true, 0, 1, NOW(), NOW(), 0, false, 'PROJECT'),
('知识创业账号定位','COPYWRITING', '项目定位', '目标受众：对个人成长/副业感兴趣的职场人；内容方向：干货方法论+案例拆解+思维框架；语气：直接有力；避免：鸡汤泛泛而谈',                              null, true, 0, 1, NOW(), NOW(), 0, false, 'PROJECT')
ON CONFLICT DO NOTHING;


-- ============================================================
-- 积分充值套餐
-- ============================================================
INSERT INTO credit_package (name, credits, bonus_credits, price, group_label, recommended, status, sort)
VALUES
    ('体验包',   100,    0,    100, '基础',   false, 'ENABLED', 1),
    ('入门包',   500,    0,    490, '基础',   false, 'ENABLED', 2),
    ('标准包',   1000,   100,  950, '热门',   true,  'ENABLED', 3),
    ('进阶包',   3000,   450,  2700,'热门',   false, 'ENABLED', 4),
    ('专业包',   5000,   1000, 4500,'高级',   false, 'ENABLED', 5),
    ('旗舰包',   10000,  2500, 8800,'高级',   false, 'ENABLED', 6)
ON CONFLICT DO NOTHING;


-- ============================================================
-- 内置写作提示词模板（type=COPYWRITING）
-- ============================================================

INSERT INTO generation_template (name, type, category, prompt, scope, is_public, user_id, create_time, update_time) VALUES

('爆款结构拆解器', 'COPYWRITING', '爆款拆解',
E'分析以下内容的爆款结构，按格式输出：\n\n1）核心观点（一句话）\n2）目标读者与使用场景\n3）内容展开路径\n4）注意力钩子（类型 + 原句）\n5）情绪变化曲线（开头 / 中段 / 结尾）\n6）论证方式（故事 / 对比 / 权威 / 反直觉）\n7）可复用表达结构（3-5 个模板）\n8）复用判断（是否值得复用 + 原因）',
'COPYWRITING', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('写作前元思考澄清', 'COPYWRITING', '写作准备',
E'在开始写作前，请逐一引导我回答以下 6 个问题（每次只问一个）：\n\n1. 目标读者是谁？（具体画像，不是"所有人"）\n2. 发布平台是什么？\n3. 读者此刻的真实痛点或欲望是什么？\n4. 这次内容的核心结论是什么？（一句话）\n5. 内容将基于哪些经验 / 案例 / 证据？\n6. 整体表达风格？（教学 / 故事 / 对话 / 清单 / 反直觉）\n\n6 个问题回答完后，输出一份「写作决策摘要」。',
'COPYWRITING', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('母内容结构构建', 'COPYWRITING', '内容结构',
E'基于以上核心观点，构建可长期复用的内容结构：\n\n1）一句话承诺（读完能获得什么）\n2）开头钩子方案（3 个备选）\n3）正文结构（段落标题 + 段落目的 + 核心要点）\n4）CTA 设计（软 CTA + 硬 CTA 各一）\n5）后续可裂变方向（5 个）',
'COPYWRITING', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('内容裂变多平台', 'COPYWRITING', '内容裂变',
E'将以上内容裂变为多平台版本（保持观点一致，表达方式不同）：\n\n1）短内容 × 5（100-200 字）\n2）强钩子 × 3（一句话）\n3）公众号版（800-1500 字）\n4）小红书版（300-500 字 + 配图建议）\n5）抖音口播脚本（含前 3 秒钩子）',
'COPYWRITING', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('小红书爆款笔记', 'COPYWRITING', '平台适配',
E'将以上内容改写为小红书风格笔记：\n- 标题带数字或悬念，不超过 20 字\n- 开头 3 行必须抓住眼球\n- 正文 300-500 字，分段清晰\n- 结尾引导互动\n- 附 5 个相关话题标签',
'COPYWRITING', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('公众号深度文章', 'COPYWRITING', '平台适配',
E'将以上内容扩写为公众号深度文章：\n- 标题：情绪 + 悬念 + 利益点三选二\n- 开头：故事或反直觉结论，150 字内\n- 正文：800-1500 字，每段有小标题\n- 结尾：行动号召 + 软 CTA',
'COPYWRITING', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('标题创意 10 版', 'COPYWRITING', '标题优化',
E'为以上主题生成 10 个标题，覆盖以下角度：\n- 数字型（如：3 个方法…）\n- 悬念型（如：为什么 90% 的人…）\n- 利益型（如：学会这个…）\n- 反直觉型（如：越努力越…）\n- 对话型（如：你有没有…）',
'COPYWRITING', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('开头钩子生成', 'COPYWRITING', '钩子设计',
E'为以上主题生成 5 个开头钩子（每个不超过 30 字）：\n- 制造悬念或反差\n- 直击读者痛点或欲望\n- 让人想继续读下去',
'COPYWRITING', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)

ON CONFLICT DO NOTHING;
