-- ============================================================
-- 生产必需种子数据（随应用一起部署，所有环境均执行）
-- ============================================================

-- ==================== 系统配置 ====================

INSERT INTO sys_config (category, config_key, value, default_value, value_type, name, description, visible, editable) VALUES
('user',     'user.default_password',        '123456',      '123456',      'string',  '用户默认密码',         '管理员创建用户时的初始密码',           FALSE, TRUE),
('user',     'user.register_enabled',        'true',        'true',        'boolean', '是否开放注册',         '关闭后禁止新用户自主注册',             TRUE,  TRUE),
('user',     'user.login_fail_lock_count',   '5',           '5',           'integer', '登录失败锁定次数',     '连续失败超过此次数后锁定账号',         TRUE,  TRUE),
('user',     'user.login_fail_lock_minutes', '30',          '30',          'integer', '账号锁定时长（分钟）', '登录失败锁定的持续时间',               TRUE,  TRUE),
('security', 'security.captcha_enabled',     'true',        'true',        'boolean', '是否启用验证码',       '登录时是否需要图形验证码',             TRUE,  TRUE),
('security', 'security.verify_code_expire',  '5',           '5',           'integer', '验证码有效期（分钟）', '邮件/短信验证码的有效时间',            TRUE,  TRUE),
('storage',  'storage.upload_max_size_mb',   '50',          '50',          'integer', '文件上传大小限制（MB）','单文件最大上传体积',                  TRUE,  TRUE),
('storage',  'storage.allowed_types',        'jpg,jpeg,png,gif,pdf,doc,docx,xls,xlsx,zip', NULL, 'string', '允许上传的文件类型', '逗号分隔的扩展名列表', TRUE, TRUE),
('ai',       'ai.default_model',             'gpt-4o-mini', 'gpt-4o-mini', 'string',  'AI 默认模型',             '未指定模型时使用的默认 LLM',                           TRUE,  TRUE),
('ai',       'ai.token_quota_per_user',      '100000',      '100000',      'integer', '用户 Token 配额',         '每用户每月 Token 使用上限，0=不限制',                  TRUE,  TRUE),
('ai',       'ai.credit_warn_threshold',     '10',          '10',          'integer', '积分预警阈值',             '用户积分低于此值时发送预警通知，提示充值',             TRUE,  TRUE),
('ai',       'ai.free_assistant_credit_cap', '100',         '100',         'integer', '免费助理虚拟用户预算上限', '免费助理入口绑定的系统虚拟用户每月积分预算上限',       TRUE,  TRUE),
('brand',    'brand.company_name',           '学记智能',    '学记智能',    'string',  '公司名称',            '显示在邮件、页面标题等位置',           TRUE,  TRUE),
('brand',    'brand.logo_url',               NULL,          NULL,          'string',  'Logo URL',            '系统 Logo 图片地址',                   TRUE,  TRUE)
ON CONFLICT (config_key) DO NOTHING;

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
       ('user',  '普通用户', '普通用户，仅有只读权限')
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.code = 'admin'
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO sys_organization (name, slug, type, owner_id, create_by)
SELECT '管理员工作空间', 'personal-' || u.id, 'personal', u.id, u.id
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
    ('基础套餐',   1000,  10000,  'ENABLED', 10, '1000积分=10000 Token'),
    ('标准套餐',   5000,  55000,  'ENABLED', 5,  '5000积分=55000 Token（赠10%）'),
    ('高级套餐',   10000, 120000, 'ENABLED', 1,  '10000积分=120000 Token（赠20%）')
ON CONFLICT DO NOTHING;


-- ==================== 系统菜单初始数据 ====================

INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type) VALUES
(NULL, '概览', NULL, NULL, 0, 'GROUP'),
(1, '工作台', '/dashboard', 'layout-dashboard', 0, 'MENU'),
(NULL, 'AI 创作', NULL, NULL, 10, 'GROUP'),
(3, '图像生成', '/aigc', 'sparkles', 0, 'MENU'),
(3, '视频生成', '/aigc/video', 'video', 1, 'MENU'),
(3, '3D 展示', '/aigc/3d', 'box', 2, 'MENU'),
(3, '素材库', '/aigc/assets', 'image', 3, 'MENU'),
(NULL, '开发工具', NULL, NULL, 20, 'GROUP'),
(8, '文档管理', '/dev/docs', 'file-text', 0, 'MENU'),
(8, '开发日志', '/dev/log', 'scroll-text', 1, 'MENU'),
(8, '代码审查', '/dev/review', 'git-pull-request', 2, 'MENU'),
(8, '迭代统计', '/dev/stats', 'bar-chart-3', 3, 'MENU'),
(NULL, '系统', NULL, NULL, 99, 'GROUP'),
(13, '回收站', '/trash', 'trash-2', 0, 'MENU'),
(13, '设置', '/settings', 'settings', 1, 'MENU')
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

-- ==================== FREE 套餐 ====================

INSERT INTO subscription_plan (code, name, duration_days, price, market_price, status, sort)
VALUES ('FREE', '免费套餐', 0, 0, 0, 'ENABLED', 0)
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
