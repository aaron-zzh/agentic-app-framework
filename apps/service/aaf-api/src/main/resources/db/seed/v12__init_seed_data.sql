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
('ai',       'ai.credit_overdraft_limit',    '0',           '0',           'integer', '积分透支上限',             'token计费场景允许欠费的积分数，0=不允许透支',          TRUE,  TRUE),
('ai',       'ai.free_assistant_credit_cap', '100',         '100',         'integer', '免费助理虚拟用户预算上限', '免费助理入口绑定的系统虚拟用户每月积分预算上限',       TRUE,  TRUE),
('ai',       'ai.token_markup_rate',         '5',           '5',           'integer', 'Token计费加价倍数',       '相对供应商成本的加价倍数，默认5倍（1元成本→500积分）', TRUE, TRUE),
('brand',    'brand.company_name',           'XX智能',    'XX智能',    'string',  '公司名称',            '显示在邮件、页面标题等位置',           TRUE,  TRUE),
('brand',    'brand.logo_url',               NULL,          NULL,          'string',  'Logo URL',            '系统 Logo 图片地址',                   TRUE,  TRUE),
('contact',  'contact.wechat_qr_image',      NULL,          NULL,          'string',  '微信客服二维码',       '微信客服二维码图片 URL，公开接口可读取', TRUE,  TRUE),
('examples', 'examples.agentscope_rate_limit_per_minute', '20', '20', 'integer', 'AgentScope示例限流（次/分钟/IP）', 'AgentScope示例接口每个IP每分钟最多调用次数', TRUE, TRUE),
('sms',      'sms.rate_limit.max_per_minute',            '1',  '1',  'integer', '短信每分钟限制',                   '同一手机号每分钟最多发送短信次数',                   TRUE,  TRUE),
('sms',      'sms.rate_limit.max_per_hour',              '5',  '5',  'integer', '短信每小时限制',                   '同一手机号每小时最多发送短信次数',                   TRUE,  TRUE),
-- AIGC Mock 系统参数（开发调试用，默认开启）
('aigc', 'aigc.mock_enabled', 'true', 'true', 'boolean', 'AIGC Mock 开关',
 '开启后所有 AIGC 生成任务跳过真实 API 调用，直接返回 aigc.mock_data 中的固定值，适用于开发调试', TRUE, TRUE),
('aigc', 'aigc.mock_data',
 '{"image":"https://picsum.photos/720","video":"https://www.w3schools.com/html/mov_bbb.mp4","model3d":"","text":"这是一段 Mock 固定文字内容","audio":"https://www.w3schools.com/html/horse.ogg"}',
 '{"image":"","video":"","model3d":"","text":"","audio":""}',
 'json', 'AIGC Mock 数据',
 'JSON 格式，各类型固定返回值，key 为 image/video/model3d/text/audio', TRUE, TRUE),
-- 会员与积分 FAQ（订阅与积分定价页展示）
-- 前端通过 GET /api/public/system/configs/member.faq 读取（无需登录）
-- 前端 DEFAULT_MEMBER_FAQ 仍保留作为接口不可达时的兜底
-- 退款联系邮箱在文案中固定写运营邮箱（业务数据，由运营在 admin UI 维护）
('member', 'member.faq',
$$[{"q":"什么是积分，我如何获得？","a":"积分是 AAF 平台的标准计量单位。当你使用 AI 模型对话、图像 / 视频生成、知识库检索、工作流执行等功能时，系统会根据所使用的模型类型、调用次数、Token 消耗、生成时长、分辨率等参数自动扣除相应积分。\n\n你可以通过以下方式获取积分：\n• 订阅获取（Subscription Credits）：订阅会员套餐后，每月可获得固定额度积分，有效期 30 天\n• 充值获取（Top-up Credits）：在「积分详情」页通过订单充值获得，有效期 2 年（自发放之日起计算）\n• 每周积分（Weekly Credits）：每周一 00:01 自动刷新，有效期 7 天\n• 邀请奖励积分（Invite Bonus Credits）：成功邀请用户注册后获取，有效期 30 天\n• 活动奖励积分（Event Bonus Credits）：参与社区计划或运营活动获得，发放数量与有效期以活动规则为准\n\n⚠️ 积分规则、奖励政策及相关活动机制可能根据运营需要进行调整，调整可在提前通知或不提前通知的情况下进行。在法律允许的范围内，AAF 保留相关规则的最终解释权。"},{"q":"积分在使用过程中如何扣除？","a":"积分计费规则：积分的具体消耗以「积分详情」页中的模型与计费规则为准，不同模型、不同分辨率、不同生成时长所消耗的积分不同。\n\n积分扣除顺序：系统将优先扣除更快到期的积分，以最大程度保障你的积分使用权益。\n\n异常退还：若因系统问题导致执行失败，系统将自动退还相应积分，无需手动申请。\n\n⚠️ 免费体验期间将启用防刷与防自动化滥用机制，相关使用规则可能根据平台稳定性与公平性需要进行动态调整。"},{"q":"订阅是如何运作的？","a":"AAF 提供灵活的月度与年度订阅方案，每个方案都包含一定数量的积分，可用于对话、图像生成、视频生成、知识库检索、工作流执行等功能。\n\n当你升级订阅时：\n• 旧套餐仅按已使用积分比例计费\n• 剩余未使用余额将自动抵扣至新套餐\n• 你仅需支付补齐差价\n• 新的订阅周期将从升级当日重新计算"},{"q":"订阅会自动续费吗？","a":"会的。订阅将在每个计费周期结束时自动续费，除非你在续费日前主动取消。"},{"q":"如何修改或取消订阅？","a":"你可以随时进行升级：免费 → 高级 → 专业 → 企业，按月付费 → 按年付费。\n\n取消订阅方式：\n1. 进入「设置 → 价格套餐」\n2. 点击「管理订阅」\n3. 选择「取消订阅」\n\n取消后，你仍可在当前订阅周期内继续使用订阅权益；周期结束后订阅将自动失效，并不再进行自动续费。"},{"q":"我如何申请退款？","a":"如果你在最近一次付款后未有任何积分消耗记录（包括对话、图像 / 视频生成、知识库检索、工作流执行等），可在购买后 7 天内申请全额退款。\n\n若因系统问题导致执行失败，我们将自动进行相应积分退还，无需手动申请。\n\n如需申请退款，请联系 service@xuejiai.com。退款通常会在 5–10 个工作日内退回原支付方式。"}]$$,
 '[]',
 'json', '会员与积分常见问题',
 '订阅与积分定价页 FAQ 列表，JSON 数组格式 [{"q":"...","a":"..."}]', TRUE, TRUE),
('member', 'member.expiry_reminder_days', '7', '7', 'integer',
 '订阅到期提醒提前天数', '订阅 end_at 前几天发送提醒（含当天）', TRUE, TRUE),
-- 分销配置
('brokerage', 'brokerage.enabled_condition', 'MANUAL', 'MANUAL', 'string',
 '分销资格获取条件',
 'ALL=全员自动 / PAID=付费套餐激活后自动 / MANUAL=手动授权',
 TRUE, TRUE)
ON CONFLICT (config_key) WHERE deleted = FALSE DO NOTHING;

-- 动态存储规格保存在 sys_file_config；真实 AK/SK 仅由 credentialRef 从 YAML/ENV 解析。
-- 云端示例默认退役，填写实际 bucket/domain/roleArn 并确认凭证后可通过管理 API 启用和设为主配置。
INSERT INTO sys_file_config (name, storage_type, config, master, status)
SELECT '本地存储', 'LOCAL', '{"basePath":"./aaf-files","domain":"http://localhost:8080"}', TRUE, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sys_file_config WHERE master = TRUE AND deleted = FALSE);

INSERT INTO sys_file_config (name, storage_type, config, master, status)
SELECT
    'S3 兼容存储（私有示例）',
    'S3',
    '{"endpoint":"https://s3.amazonaws.com","bucketName":"aaf-example-bucket","region":"us-east-1","domain":"","enablePublicAccess":false,"downloadUrlExpirySeconds":3600,"credentialRef":"s3-default"}',
    FALSE,
    'RETIRED'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_file_config WHERE name = 'S3 兼容存储（私有示例）' AND deleted = FALSE
);

-- PUBLIC_ASSET 路由选择唯一 ACTIVE、enablePublicAccess=true 的 OSS 配置；部署时替换占位值后仅启用一条。
INSERT INTO sys_file_config (name, storage_type, config, master, status)
SELECT
    '阿里云 OSS（公开示例）',
    'OSS',
    '{"endpoint":"oss-cn-hangzhou.aliyuncs.com","bucketName":"aaf-public-example-bucket","roleArn":"","stsEndpoint":"sts.aliyuncs.com","domain":"https://cdn.example.com","enablePublicAccess":true,"downloadUrlExpirySeconds":3600,"durationSeconds":3600,"credentialRef":"oss-default"}',
    FALSE,
    'RETIRED'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_file_config WHERE name = '阿里云 OSS（公开示例）' AND deleted = FALSE
);

INSERT INTO sys_file_config (name, storage_type, config, master, status)
SELECT
    '阿里云 OSS（私有示例）',
    'OSS',
    '{"endpoint":"oss-cn-hangzhou.aliyuncs.com","bucketName":"aaf-private-example-bucket","roleArn":"","stsEndpoint":"sts.aliyuncs.com","domain":"","enablePublicAccess":false,"downloadUrlExpirySeconds":3600,"durationSeconds":3600,"credentialRef":"oss-default"}',
    FALSE,
    'RETIRED'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_file_config WHERE name = '阿里云 OSS（私有示例）' AND deleted = FALSE
);

-- ==================== 短信模板 ====================
-- code 与认证场景 type（register/login/reset）对齐，统一映射到同一厂商模板
INSERT INTO sys_sms_template (code, name, api_template_id, params, status)
VALUES
    ('register', '注册验证码',   'SMS_482485008', '["code"]', 1),
    ('login',    '登录验证码',   'SMS_482485008', '["code"]', 1),
    ('reset',    '重置密码验证码', 'SMS_482485008', '["code"]', 1)
ON CONFLICT (code) WHERE deleted = FALSE DO NOTHING;

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
) ON CONFLICT (code) WHERE deleted = FALSE DO UPDATE SET
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
ON CONFLICT (code) WHERE deleted = FALSE DO NOTHING;

-- 钉钉渠道验证码模板（开发/测试环境调试用）
INSERT INTO sys_message_template (code, name, channel, subject, content, variables)
VALUES (
    'AUTH_VERIFY_CODE_DINGTALK',
    '认证验证码（钉钉）',
    'DINGTALK',
    '安全验证码',
    '## 【${companyName}】安全验证码

您正在进行账号验证，您的验证码为：

# ${code}

验证码 **${expireMinutes} 分钟**内有效，请勿泄露给他人。

> 如非本人操作，请忽略此消息。',
    '["code","type","expireMinutes","companyName"]'
) ON CONFLICT (code) WHERE deleted = FALSE DO NOTHING;


-- ==================== 初始管理员 ====================

-- 密码为 admin（BCrypt），首次登录后请修改
INSERT INTO sys_user (username, password, nickname, email, email_verified, status)
VALUES ('admin', '$2a$10$UyqdQK.M7V9FE4IzbbzeUeQnU.NsumDR.RCviFq4Pt04Y/F4VWLKC', '管理员', 'admin@xuejiai.com', TRUE, 0)
ON CONFLICT (username) WHERE deleted = FALSE DO NOTHING;

INSERT INTO sys_organization (name, slug, type, owner_id, create_by)
SELECT u.username, 'personal-' || u.id, 'personal', u.id, u.id
FROM sys_user u WHERE u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_organization o WHERE o.slug = 'personal-' || u.id);

INSERT INTO sys_org_member (org_id, user_id, role, create_by)
SELECT o.id, o.owner_id, 'owner', o.owner_id
FROM sys_organization o
WHERE o.type = 'personal' AND o.owner_id = (SELECT id FROM sys_user WHERE username = 'admin')
  AND NOT EXISTS (SELECT 1 FROM sys_org_member m WHERE m.org_id = o.id AND m.user_id = o.owner_id);

INSERT INTO sys_workspace (org_id, name, slug, owner_id, create_by)
SELECT o.id, '默认工作区', 'default', u.id, u.id
FROM sys_user u
JOIN sys_organization o ON o.owner_id = u.id AND o.type = 'personal' AND o.deleted = FALSE
WHERE u.username = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_workspace w
      WHERE w.org_id = o.id AND w.slug = 'default' AND w.deleted = FALSE
  );

INSERT INTO sys_workspace_member (org_id, workspace_id, user_id, owner_id, create_by)
SELECT w.org_id, w.id, u.id, u.id, u.id
FROM sys_user u
JOIN sys_organization o ON o.owner_id = u.id AND o.type = 'personal' AND o.deleted = FALSE
JOIN sys_workspace w ON w.org_id = o.id AND w.slug = 'default' AND w.deleted = FALSE
WHERE u.username = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_workspace_member m
      WHERE m.workspace_id = w.id AND m.user_id = u.id AND m.deleted = FALSE
  );

-- ==================== 角色定义 ====================

INSERT INTO sys_role (code, name, description)
VALUES ('admin',       '管理员',     '系统管理员，拥有全部权限'),
       ('user',        '普通用户',   '普通用户，仅有只读权限'),
       ('super_admin', '超级管理员', '系统最高权限，不可删除'),
       ('org_admin',   '组织管理员', '组织级管理权限'),
       ('member',      '普通成员',   '默认角色，基础读写权限'),
       ('guest',       '访客',       '只读权限'),
       ('agent',       'AI 智能体',  'AI Agent 专用角色')
ON CONFLICT (code) WHERE deleted = FALSE DO NOTHING;

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.code = 'super_admin'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ==================== 通用权限码 ====================
-- 菜单只引用权限码；接口安全边界由 @PreAuthorize/hasPermission 执行。

INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES
    ('角色读取',           'system:role:read',                   'system',    'role',              'read',    0),
    ('角色创建',           'system:role:create',                 'system',    'role',              'create',  0),
    ('角色更新',           'system:role:update',                 'system',    'role',              'update',  0),
    ('角色删除',           'system:role:delete',                 'system',    'role',              'delete',  0),
    ('角色导出',           'system:role:export',                 'system',    'role',              'export',  0),
    ('用户读取',           'system:user:read',                   'system',    'user',              'read',    0),
    ('用户创建',           'system:user:create',                 'system',    'user',              'create',  0),
    ('用户更新',           'system:user:update',                 'system',    'user',              'update',  0),
    ('用户删除',           'system:user:delete',                 'system',    'user',              'delete',  0),
    ('工作区读取',         'system:workspace:read',              'system',    'workspace',         'read',    0),
    ('工作区创建',         'system:workspace:create',            'system',    'workspace',         'create',  0),
    ('工作区更新',         'system:workspace:update',            'system',    'workspace',         'update',  0),
    ('工作区删除',         'system:workspace:delete',            'system',    'workspace',         'delete',  0),
    ('工作区导出',         'system:workspace:export',            'system',    'workspace',         'export',  0),
    ('工作区引用',         'system:workspace:reference',         'system',    'workspace',         'reference', 0),
    ('菜单管理',           'system:menu:manage',                 'system',    'menu',              'manage',  0),
    ('权限码管理',         'system:permission:manage',           'system',    'permission',        'manage',  0),
    ('数据权限规则管理',   'system:data-access-rule:manage',     'system',    'data-access-rule',  'manage',  0),
    ('ReBAC 关系管理',     'system:relation:manage',             'system',    'relation',          'manage',  0),
    ('访问策略管理',       'system:access-policy:manage',        'system',    'access-policy',     'manage',  0),
    ('开发者订阅套餐读取', 'developer:subscription-plan:read',   'developer', 'subscription-plan', 'read',    0),
    ('开发者订阅套餐创建', 'developer:subscription-plan:create', 'developer', 'subscription-plan', 'create',  0),
    ('开发者订阅套餐更新', 'developer:subscription-plan:update', 'developer', 'subscription-plan', 'update',  0),
    ('开发者订阅套餐删除', 'developer:subscription-plan:delete', 'developer', 'subscription-plan', 'delete',  0),
    ('开发者订阅套餐导出', 'developer:subscription-plan:export', 'developer', 'subscription-plan', 'export',  0),
    ('会员等级读取',       'billing:level:read',                  'billing',   'level',             'read',    0),
    ('会员等级创建',       'billing:level:create',                'billing',   'level',             'create',  0),
    ('会员等级更新',       'billing:level:update',                'billing',   'level',             'update',  0),
    ('会员等级删除',       'billing:level:delete',                'billing',   'level',             'delete',  0),
    ('会员等级导出',       'billing:level:export',                'billing',   'level',             'export',  0),
    ('订阅套餐读取',       'billing:subscription-plan:read',      'billing',   'subscription-plan', 'read',    0),
    ('订阅套餐创建',       'billing:subscription-plan:create',    'billing',   'subscription-plan', 'create',  0),
    ('订阅套餐更新',       'billing:subscription-plan:update',    'billing',   'subscription-plan', 'update',  0),
    ('订阅套餐删除',       'billing:subscription-plan:delete',    'billing',   'subscription-plan', 'delete',  0),
    ('订阅套餐导出',       'billing:subscription-plan:export',    'billing',   'subscription-plan', 'export',  0),
    ('用户订阅读取',       'billing:subscription:read',           'billing',   'subscription',      'read',    0),
    ('用户订阅导出',       'billing:subscription:export',         'billing',   'subscription',      'export',  0),
    ('权益额度读取',       'billing:entitlement-quota:read',      'billing',   'entitlement-quota', 'read',    0),
    ('权益额度导出',       'billing:entitlement-quota:export',    'billing',   'entitlement-quota', 'export',  0),
    ('钱包流水读取',       'billing:wallet-transaction:read',     'billing',   'wallet-transaction','read',    0),
    ('钱包流水导出',       'billing:wallet-transaction:export',   'billing',   'wallet-transaction','export',  0),
    ('积分兑换码读取',     'billing:credit-redeem-code:read',     'billing',   'credit-redeem-code','read',    0),
    ('积分兑换码导出',     'billing:credit-redeem-code:export',   'billing',   'credit-redeem-code','export',  0),
    ('工具执行',           'tool:default:execute',               'tool',      'default',           'execute', 0),
    ('业务动作工具执行',   'tool:business-action:execute',       'tool',      'business-action',   'execute', 0),
    ('图片生成工具执行',   'tool:image-generate:execute',        'tool',      'image-generate',    'execute', 0),
    ('视频生成工具执行',   'tool:video-generate:execute',        'tool',      'video-generate',    'execute', 0),
    ('提示词模板读取',     'system:prompt-template:read',        'system',    'prompt-template',   'read',    0),
    ('提示词模板创建',     'system:prompt-template:create',      'system',    'prompt-template',   'create',  0),
    ('提示词模板更新',     'system:prompt-template:update',      'system',    'prompt-template',   'update',  0),
    ('提示词模板删除',     'system:prompt-template:delete',      'system',    'prompt-template',   'delete',  0),
    ('文件存储配置读取',   'system:file-config:read',            'system',    'file-config',       'read',    0),
    ('文件存储配置创建',   'system:file-config:create',          'system',    'file-config',       'create',  0),
    ('文件存储配置更新',   'system:file-config:update',          'system',    'file-config',       'update',  0),
    ('文件存储配置删除',   'system:file-config:delete',          'system',    'file-config',       'delete',  0)
ON CONFLICT (code) WHERE deleted = FALSE DO NOTHING;

-- 文件存储配置允许普通登录角色读取；写权限仅由管理员角色的全量权限映射获得。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission_code p ON p.code = 'system:file-config:read'
WHERE r.code IN ('super_admin', 'member', 'user', 'guest')
ON CONFLICT DO NOTHING;

-- ==================== 角色菜单与权限挂接 ====================

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission_code p
WHERE r.code IN ('super_admin', 'admin', 'org_admin')
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission_code p
WHERE r.code = 'member'
  AND p.code IN (
      'system:workspace:read',
      'system:workspace:create',
      'system:workspace:update',
      'system:workspace:delete',
      'system:workspace:export',
      'system:workspace:reference',
      'system:prompt-template:read',
      'system:prompt-template:create',
      'system:prompt-template:update',
      'system:prompt-template:delete'
  )
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission_code p
WHERE r.code IN ('user', 'guest')
  AND p.code IN (
      'system:workspace:read',
      'system:workspace:export',
      'system:workspace:reference'
  )
ON CONFLICT DO NOTHING;

-- ==================== 提示词模板数据范围 ====================
-- 标准 CRUD 只暴露创建者记录和当前租户 PUBLIC 记录。
-- SYSTEM 由专用后端谓词只读/使用/复制；ENGINE 永不进入 Studio 数据范围。
DELETE FROM sys_data_access_rule WHERE entity_slug = 'prompt-template';

INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES (
    'prompt-template',
    '["*"]',
    '{"or":[{"field":"ownerId","op":"eq","value":"$user.id"},{"field":"visibility","op":"eq","value":"PUBLIC"}]}',
    'allow'
)
ON CONFLICT DO NOTHING;
-- ==================== 销售演示角色 ====================

INSERT INTO sys_role (code, name, description, status)
VALUES ('sales', '销售', '销售演示角色，拥有工作台与 AI 创作入口，不包含系统管理权限', 0)
ON CONFLICT (code) WHERE deleted = FALSE DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r
JOIN sys_permission_code p ON p.code IN ('tool:default:execute', 'tool:image-generate:execute')
WHERE r.code = 'sales'
ON CONFLICT DO NOTHING;

-- ==================== 积分转Token规则（默认） ====================

INSERT INTO credit_token_rule (name, credit_amount, token_amount, status, priority, remark)
VALUES
    ('基础套餐',   100,  100,  'ENABLED', 10, '100元=100积分'),
    ('标准套餐',   500,  550,  'ENABLED', 5,  '500元=550积分（赠10%）'),
    ('高级套餐',   1000, 1200, 'ENABLED', 1,  '1000元=1200积分（赠20%）')
ON CONFLICT DO NOTHING;


-- ==================== 系统菜单初始数据 ====================

INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible) VALUES
(NULL, '概览',      NULL, NULL, 0,  'GROUP', true),
(NULL, 'AI 创作',   NULL, NULL, 10, 'GROUP', true),
(NULL, '知识库',    NULL, NULL, 20, 'GROUP', true),
(NULL, 'AI 助手',   NULL, NULL, 25, 'GROUP', false),
(NULL, '会员中心',  NULL, NULL, 30, 'GROUP', false),
(NULL, '管理',      NULL, NULL, 40, 'GROUP', true),
(NULL, '开发工具',  NULL, NULL, 90, 'GROUP', false),
(NULL, '系统',      NULL, NULL, 99, 'GROUP', true)
ON CONFLICT DO NOTHING;

-- 所有菜单子项（幂等，按 path 去重）
WITH all_groups AS (
  SELECT id, title FROM sys_menu
  WHERE parent_id IS NULL AND deleted = false
),
items (group_title, title, path, icon, sort_order, visible) AS (
  VALUES
    -- 概览
    ('概览',     '工作台',     '/dashboard',                  'layout-dashboard',  0,  true),
    ('概览',     '积分统计',   '/admin/credits-analytics',    'bar-chart-2',       1,  true),
    ('概览',     '开发示例',   '/examples',                   'file-text',         2,  true),
    -- AI 创作
    ('AI 创作',  '创作项目',   '/studio/projects',             'sparkles',          0,  true),
    ('AI 创作',  '素材库',     '/studio/assets/materials',     'image',             1,  true),
    ('AI 创作',  '作品库',     '/studio/assets/works',         'images',            2,  true),
    ('AI 创作',  'AIGC 任务',  '/studio/assets/history',       'wand-2',            3,  true),
    -- 知识库
    ('知识库',   '知识库',     '/studio/knowledge',           'database',          0,  true),
    -- 会员中心
    ('会员中心', '积分流水',   '/module/wallet-transaction',  'receipt',           1,  true),
    ('会员中心', '订阅套餐',   '/module/subscription-plan',   'credit-card',       2,  true),
    ('会员中心', '用户订阅',   '/module/subscription',        'badge-check',       3,  true),
    -- 管理
    ('管理',     'AI 模型',    '/system/model',               'cpu',               0,  true),
    ('管理',     '兑换码',     '/module/credit-redeem-code',  'ticket',            1,  true),
    ('管理',     '知识库运维', '/admin/knowledge',            'database-zap',      2,  true),
    ('管理',     '待办管理',   '/module/todo',                'check-square',      4,  true),
    -- 系统
    ('系统',     '系统参数',   '/admin/system-config',        'sliders-horizontal',0,  true),
    ('系统',     '回收站',     '/trash',                      'trash-2',           1,  true),
    ('系统',     '用户管理',   '/admin/users',                'users',             2,  true),
    ('系统',     '计划任务',   '/admin/scheduled-tasks',      'clock',             3,  true),
    ('系统',     '菜单管理',   '/admin/menus',                'menu',              4,  true),
    ('系统',     '预设管理',   '/system/dashboard-presets',   'layout-template',   5,  true),
    ('系统',     '操作日志',   '/admin/operation-log',        'clipboard-list',    6,  true),
    ('系统',     '演示模式',   '/admin/demo',                 'flask-conical',     7,  true),
    ('系统',     '存储配置',   '/module/file-config',        'database',          10, true),
    -- 开发工具（隐藏）
    ('开发工具', '文档管理',   '/dev/docs',                   'file-text',         0,  false),
    ('开发工具', '开发日志',   '/dev/log',                    'scroll-text',       1,  false),
    ('开发工具', '代码审查',   '/dev/review',                 'git-pull-request',  2,  false),
    ('开发工具', '迭代统计',   '/dev/stats',                  'bar-chart-3',       3,  false)
    -- 待启用
    -- ('知识库',   '文档',       '/docs',                       'file-text',         1,  true),
    -- ('知识库',   '审批流',     '/workflow',                   'git-branch',        2,  true),
    -- ('AI 助手',  'AI 对话',    '/ai/chat',                    'message-square',    0,  true),
    -- ('AI 助手',  '智能体',     '/ai/agents',                  'bot',               1,  true),
    -- ('管理',     '实体管理',   '/admin/entities',             'layers',            3,  true),
    -- ('管理',     '审计日志',   '/admin/audit-log',            'shield-check',      4,  true)
)
INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible)
SELECT DISTINCT ON (i.path) g.id, i.title, i.path, i.icon, i.sort_order, 'MENU', i.visible
FROM items i
JOIN all_groups g ON g.title = i.group_title
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu WHERE path = i.path AND deleted = false
);

UPDATE sys_menu
SET permission_code = 'system:file-config:read',
    update_time = CURRENT_TIMESTAMP
WHERE path = '/module/file-config' AND deleted = FALSE;


-- ==================== 画像维度预置数据 ====================

-- basic 基础信息
INSERT INTO sys_profile_dimension (code, name, group_code, value_type, source, sort_order, searchable, ai_visible)
VALUES
('basic.age_range', '年龄段', 'basic', 'enum', 'manual', 1, true, true),
('basic.gender', '性别', 'basic', 'enum', 'manual', 2, true, true),
('basic.occupation', '职业', 'basic', 'text', 'manual', 3, true, true),
('basic.region', '地区', 'basic', 'text', 'manual', 4, true, false),
('basic.education', '教育程度', 'basic', 'enum', 'manual', 5, true, false)
ON CONFLICT (code) DO NOTHING;

UPDATE sys_profile_dimension SET enum_options = '["18以下","18-25","26-35","36-45","46-55","56-65","65以上"]' WHERE code = 'basic.age_range';
UPDATE sys_profile_dimension SET enum_options = '["男","女","其他"]' WHERE code = 'basic.gender';
UPDATE sys_profile_dimension SET enum_options = '["小学","初中","高中","大专","本科","硕士","博士"]' WHERE code = 'basic.education';

-- preference 偏好
INSERT INTO sys_profile_dimension (code, name, group_code, value_type, source, sort_order, searchable, ai_visible)
VALUES
('preference.interests', '兴趣爱好', 'preference', 'tags', 'manual', 1, true, true),
('preference.diet', '饮食偏好', 'preference', 'tags', 'manual', 2, false, true),
('preference.communication_style', '沟通风格偏好', 'preference', 'enum', 'ai', 3, false, true),
('preference.language', '语言', 'preference', 'text', 'manual', 4, true, true)
ON CONFLICT (code) DO NOTHING;

UPDATE sys_profile_dimension SET enum_options = '["简洁直接","详细耐心","幽默轻松","正式专业"]' WHERE code = 'preference.communication_style';

-- behavior 行为（自动计算）
INSERT INTO sys_profile_dimension (code, name, group_code, value_type, source, sort_order, searchable, ai_visible)
VALUES
('behavior.activity_level', '活跃度', 'behavior', 'enum', 'behavior', 1, true, true),
('behavior.usage_frequency', '使用频率', 'behavior', 'text', 'behavior', 2, true, false),
('behavior.spending_level', '消费等级', 'behavior', 'enum', 'behavior', 3, true, true)
ON CONFLICT (code) DO NOTHING;

UPDATE sys_profile_dimension SET enum_options = '["高","中","低"]' WHERE code = 'behavior.activity_level';
UPDATE sys_profile_dimension SET enum_options = '["高消费","中等","低消费","免费用户"]' WHERE code = 'behavior.spending_level';

-- health 健康（康养场景）
INSERT INTO sys_profile_dimension (code, name, group_code, value_type, source, sort_order, searchable, ai_visible, unit)
VALUES
('health.blood_pressure', '血压', 'health', 'text', 'device', 1, false, true, 'mmHg'),
('health.blood_sugar', '血糖', 'health', 'number', 'device', 2, false, true, 'mmol/L'),
('health.medication', '用药情况', 'health', 'tags', 'manual', 3, false, true, NULL),
('health.allergy', '过敏史', 'health', 'tags', 'manual', 4, false, true, NULL),
('health.mobility', '行动能力', 'health', 'enum', 'manual', 5, true, true, NULL),
('health.cognitive', '认知状态', 'health', 'enum', 'manual', 6, true, true, NULL)
ON CONFLICT (code) DO NOTHING;

UPDATE sys_profile_dimension SET enum_options = '["完全自理","需辅助","轮椅","卧床"]' WHERE code = 'health.mobility';
UPDATE sys_profile_dimension SET enum_options = '["正常","轻度下降","中度下降","重度下降"]' WHERE code = 'health.cognitive';

-- living 生活
INSERT INTO sys_profile_dimension (code, name, group_code, value_type, source, sort_order, searchable, ai_visible)
VALUES
('living.residence_type', '居住方式', 'living', 'enum', 'manual', 1, true, true),
('living.diet_restriction', '饮食禁忌', 'living', 'tags', 'manual', 2, false, true),
('living.transport', '出行方式', 'living', 'enum', 'manual', 3, false, false),
('living.emergency_contact', '紧急联系人', 'living', 'text', 'manual', 4, false, false)
ON CONFLICT (code) DO NOTHING;

UPDATE sys_profile_dimension SET enum_options = '["独居","与配偶","与子女","养老院","其他"]' WHERE code = 'living.residence_type';
UPDATE sys_profile_dimension SET enum_options = '["步行","公交","自驾","轮椅","不出门"]' WHERE code = 'living.transport';

-- personality 性格
INSERT INTO sys_profile_dimension (code, name, group_code, value_type, source, sort_order, searchable, ai_visible)
VALUES
('personality.mbti', 'MBTI', 'personality', 'text', 'ai', 1, true, true),
('personality.emotion_tendency', '情绪倾向', 'personality', 'enum', 'ai', 2, false, true),
('personality.patience_level', '耐心程度', 'personality', 'enum', 'ai', 3, false, true)
ON CONFLICT (code) DO NOTHING;

UPDATE sys_profile_dimension SET enum_options = '["乐观积极","平和稳定","容易焦虑","情绪波动"]' WHERE code = 'personality.emotion_tendency';
UPDATE sys_profile_dimension SET enum_options = '["高","中","低"]' WHERE code = 'personality.patience_level';



-- ==================== 权益定义 ====================

INSERT INTO billing_entitlement_def (code, name, type, unit, description)
VALUES
    ('storage',           '存储空间',       'COUNTABLE', 'GB', '平台通用存储空间，含知识库文件、上传素材等'),
    ('kb_count',          '知识库数量上限', 'COUNTABLE', '个', '最多可创建的知识库数量'),
    ('workflow_count',    '工作流数量上限', 'COUNTABLE', '个', '最多可创建的工作流数量'),
    ('agent_count',       'Agent 数量上限', 'COUNTABLE', '个', '最多可创建的 Agent 数量'),
    ('member_count',      '团队成员数上限', 'COUNTABLE', '人', '团队最多可添加的成员数'),
    ('max_parallel_task', '最大并行任务数', 'COUNTABLE', '个', '同一时刻最多并行运行的任务数')
ON CONFLICT (code) WHERE deleted = FALSE DO NOTHING;

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
('generateImage', 'LOCAL', TRUE, 'GENERATIVE', 'IMAGE_GENERATION', 'MEDIUM', FALSE, TRUE, 'tool:image-generate:execute', NULL, NULL,
 '{"type":"object","required":["requestJson"],"properties":{"requestJson":{"type":"string","description":"JSON 参数：prompt 必填，width/height/model 可选"}}}',
 200, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('generateVideo', 'LOCAL', TRUE, 'GENERATIVE', 'VIDEO_GENERATION', 'HIGH', FALSE, TRUE, 'tool:video-generate:execute', NULL, NULL,
 '{"type":"object","required":["requestJson"],"properties":{"requestJson":{"type":"string","description":"JSON 参数：prompt 必填，imageUrl/referenceImageUrls/model/resolution/ratio/duration/seed 可选"}}}',
 210, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('start_workflow', 'LOCAL', TRUE, 'FUNCTION', 'WORKFLOW', 'MEDIUM', FALSE, TRUE, 'tool:workflow:start', NULL, NULL,
 '{"type":"object","required":["process_key","description"],"properties":{"process_key":{"type":"string","description":"工作流定义 Key"},"description":{"type":"string","description":"工作流描述"},"variables":{"type":"string","description":"流程变量 JSON"}}}',
 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('list_workflows', 'LOCAL', TRUE, 'FUNCTION', 'WORKFLOW', 'LOW', TRUE, FALSE, NULL, NULL, NULL,
 '{"type":"object","properties":{}}',
 301, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('recognizeOcr', 'LOCAL', TRUE, 'FUNCTION', 'OCR', 'LOW', TRUE, FALSE, 'tool:ocr:execute', NULL, NULL,
 '{"type":"object","required":["requestJson"],"properties":{"requestJson":{"type":"string","description":"JSON 参数：imageUrl 必填；task 可选（TEXT_RECOGNITION/KEY_INFORMATION_EXTRACTION/TABLE_PARSING/DOCUMENT_PARSING/FORMULA_RECOGNITION/MULTI_LAN）；prompt 可选"}}}',
 220, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (tool_name) WHERE deleted = FALSE DO UPDATE SET
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

-- 天气查询工具
INSERT INTO ai_tool_catalog (tool_name, source, enabled, tool_type, category, risk_level, read_only, require_confirm, permission_code, entitlement_code, cost_expression, input_schema, sort_order, create_time, update_time)
VALUES ('queryWeather', 'LOCAL', TRUE, 'HTTP', 'WEATHER', 'LOW', TRUE, FALSE, 'tool:weather:query', NULL, NULL,
 '{"type":"object","required":["longitude","latitude"],"properties":{"longitude":{"type":"number","description":"经度，如 116.3883"},"latitude":{"type":"number","description":"纬度，如 39.9289"}}}',
 230, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (tool_name) WHERE deleted = FALSE DO UPDATE SET
    enabled = EXCLUDED.enabled,
    input_schema = EXCLUDED.input_schema,
    update_time = CURRENT_TIMESTAMP;

-- ==================== 订阅套餐 ====================

INSERT INTO billing_subscription_plan (code, name, duration_days, price, market_price, monthly_credits, status, sort, ext)
VALUES
    ('FREE',       '免费',   0,  0,     0,     0,     'ENABLED', 0, '{"tagline":"个人探索，零门槛开始"}'),
    ('PRO',        '高级',   30, 2900,   3900,   3000,   'ENABLED', 1, '{"tagline":"解锁更多能力，适合个人进阶"}'),
    ('TEAM',       '专业',   30, 29900,  39900,  33000,  'ENABLED', 2, '{"tagline":"团队协作，共享资源与权限"}'),
    ('ENTERPRISE', '企业',   30, 300000, 360000, 375000, 'ENABLED', 3, '{"tagline":"大规模部署，专属支持与定制"}')
ON CONFLICT DO NOTHING;

-- ==================== 套餐×权益规则 ====================

-- FREE
INSERT INTO billing_plan_entitlement (plan_id, ent_id, quota, reset_cycle, refill_price)
SELECT p.id, e.id, v.quota, 'NONE', 0
FROM billing_subscription_plan p
CROSS JOIN (VALUES
    ('storage',           1),
    ('kb_count',          1),
    ('workflow_count',    3),
    ('agent_count',       2),
    ('member_count',      1),
    ('max_parallel_task', 2)
) AS v(code, quota)
JOIN billing_entitlement_def e ON e.code = v.code AND e.deleted = FALSE
WHERE p.code = 'FREE' AND p.deleted = FALSE
ON CONFLICT DO NOTHING;

-- PRO
INSERT INTO billing_plan_entitlement (plan_id, ent_id, quota, reset_cycle, refill_price)
SELECT p.id, e.id, v.quota, 'NONE', 0
FROM billing_subscription_plan p
CROSS JOIN (VALUES
    ('storage',           -1),
    ('kb_count',          3),
    ('workflow_count',    20),
    ('agent_count',       5),
    ('member_count',      10),
    ('max_parallel_task', 6)
) AS v(code, quota)
JOIN billing_entitlement_def e ON e.code = v.code AND e.deleted = FALSE
WHERE p.code = 'PRO' AND p.deleted = FALSE
ON CONFLICT DO NOTHING;

-- TEAM
INSERT INTO billing_plan_entitlement (plan_id, ent_id, quota, reset_cycle, refill_price)
SELECT p.id, e.id, v.quota, 'NONE', 0
FROM billing_subscription_plan p
CROSS JOIN (VALUES
    ('storage',           -1),
    ('kb_count',          10),
    ('workflow_count',    100),
    ('agent_count',       20),
    ('member_count',      50),
    ('max_parallel_task', 20)
) AS v(code, quota)
JOIN billing_entitlement_def e ON e.code = v.code AND e.deleted = FALSE
WHERE p.code = 'TEAM' AND p.deleted = FALSE
ON CONFLICT DO NOTHING;

-- ENTERPRISE
INSERT INTO billing_plan_entitlement (plan_id, ent_id, quota, reset_cycle, refill_price)
SELECT p.id, e.id, v.quota, 'NONE', 0
FROM billing_subscription_plan p
CROSS JOIN (VALUES
    ('storage',           -1),
    ('kb_count',          50),
    ('workflow_count',    200),
    ('agent_count',       30),
    ('member_count',      200),
    ('max_parallel_task', 50)
) AS v(code, quota)
JOIN billing_entitlement_def e ON e.code = v.code AND e.deleted = FALSE
WHERE p.code = 'ENTERPRISE' AND p.deleted = FALSE
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
-- 内容创作 Role（默认用户助理模板的可切换能力集）
-- ============================================================

INSERT INTO ai_role (
    id, code, name, description, skill_ids, tool_whitelist, status,
    create_time, update_time
) VALUES (
    2, 'system.role.content-creator', '内容创作者',
    '内容拆解、思路澄清、结构构建、内容裂变和多平台草稿生成',
    '["content-schedule","content-judge","content-clarify","content-architect","content-build"]',
    '["createDocument","updateDocument","publish","publishStatus","collect"]',
    'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

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

-- ============================================================
-- 模型供应商
-- ============================================================
INSERT INTO ai_model_provider (provider_code, provider_name, provider_type, base_url, enabled, sort_order, description)
VALUES
('aliyun',    '阿里云百炼',   'OPENAI_COMPAT', 'https://dashscope.aliyuncs.com/compatible-mode/v1', true,  10, '阿里云百炼平台，支持通义千问、万相图像等模型，DashScope SDK'),
('deepseek',  'DeepSeek',    'OPENAI_COMPAT', 'https://api.deepseek.com/v1',                       true,  20, 'DeepSeek 官方 API，支持 deepseek-chat / deepseek-reasoner'),
('n1n',       'N1N',         'OPENAI_COMPAT', 'https://llm-api.net/v1',                            true,  30, 'N1N 聚合平台，兼容 OpenAI 接口协议'),
('volcengine','火山引擎方舟', 'VOLCENGINE',    'https://ark.cn-beijing.volces.com/api/v3',           true,  40, '字节跳动火山引擎方舟平台，支持 doubao-seedance 等视频生成模型')
ON CONFLICT (provider_code) WHERE deleted = FALSE DO NOTHING;

-- ============================================================
-- AI 模型
-- capabilities: CHAT / EMBEDDING / VISION / IMAGE_GEN / AUDIO / RERANK / VIDEO_GEN / SPEECH_ASR / SPEECH_TTS / MUSIC_GEN / OMNI_REALTIME
-- ============================================================
INSERT INTO ai_model (model_id, display_name, provider, provider_type, model_name, base_url,
                      capabilities, context_window, sort_order, enabled,
                      input_price_per_k, output_price_per_k, model_price, quota_type, image_config)
VALUES
-- 语言模型（输入 0.036元/K，输出 0.108元/K）
-- ('n1n:text-embedding-3',  'text-embedding-3',   'n1n',     'OPENAI_COMPAT', 'text-embedding-3-small',     'https://llm-api.net/v1',                        'EMBEDDING',   8191,    1,  true,  0.036, 0.108, null, 0, null),
('n1n:claude-sonnet-4-6', 'Claude Sonnet 4.6',  'n1n',     'OPENAI_COMPAT', 'claude-sonnet-4-6',          'https://llm-api.net/v1',                        'CHAT,VISION',   8191,  2,  true,  0.015, 0.05, null, 0, null),
('n1n:claude-opus-4-8',   'Claude Ops 4.8',     'n1n',     'OPENAI_COMPAT', 'claude-opus-4-8',            'https://llm-api.net/v1',                        'CHAT,VISION',   8191,  3,  true,  0.015, 0.05, null, 0, null),
-- ('meituan:LongCat-2.0-Preview', 'LongCat',    'meituan',   'OPENAI_COMPAT', 'LongCat-2.0-Preview',        'https://api.longcat.chat/openai/v1',            'CHAT,VISION', 128000,  5,  true,  0.036, 0.108, null, 0, null),
('n1n:gpt-5.4',           'GPT-4o',             'n1n',     'OPENAI_COMPAT', 'gpt-5.4',                    'https://llm-api.net/v1',                        'CHAT,VISION', 128000, 10,  true,  0.015, 0.04, null, 0, null),
('n1n:gpt-5.4-mini',      'GPT-4o Mini',        'n1n',     'OPENAI_COMPAT', 'gpt-5.4-mini',               'https://llm-api.net/v1',                        'CHAT,VISION', 128000, 11,  true,  0.015, 0.04, null, 0, null),
-- ('deepseek:chat',         'DeepSeek Chat',       'deepseek','OPENAI_COMPAT', 'deepseek-chat',              'https://api.deepseek.com/v1',                   'CHAT',        64000,  20,  true,  0.036, 0.108, null, 0, null),
-- ('deepseek:reasoner',     'DeepSeek R1',         'deepseek','OPENAI_COMPAT', 'deepseek-reasoner',          'https://api.deepseek.com/v1',                   'CHAT',        64000,  21,  true,  0.036, 0.108, null, 0, null),
('qwen:qwen3.7-max',      'Qwen Max',            'qwen',    'OPENAI_COMPAT', 'qwen3.7-max',                'https://dashscope.aliyuncs.com/compatible-mode/v1', 'CHAT,VISION',    32000,  30,  true,  0.012, 0.036, null, 0, null),
('qwen:qwen3.7-plus',     'Qwen Plus',           'qwen',    'OPENAI_COMPAT', 'qwen3.7-plus',               'https://dashscope.aliyuncs.com/compatible-mode/v1', 'CHAT,VISION',  1000000, 31,  true,  0.002, 0.008, null, 0, null),
-- ('qwen:qwen3.6-flash',    'Qwen Flash',          'qwen',    'OPENAI_COMPAT', 'qwen3.6-flash',              'https://dashscope.aliyuncs.com/compatible-mode/v1', 'CHAT,VISION',  1000000, 32,  true,  0.036, 0.108, null, 0, null),
('qwen:text-embedding-v4','Qwen Embedding',       'qwen',    'OPENAI_COMPAT', 'text-embedding-v4',          'https://dashscope.aliyuncs.com/compatible-mode/v1', 'EMBEDDING', 192, 33,  true,  0.0005, 0, null, 0, null),
-- 图像生成（按次计费，quota_type=1；OpenAI Compat 按 token 计费，quota_type=0）
('qwen:wan2.7-image',              '万相 Wan2.7',        'qwen', 'DASHSCOPE',    'wan2.7-image',                   'https://dashscope.aliyuncs.com', 'IMAGE_GEN',  800, 210, true,  null,  null,  0.2, 1, '{"mode":"fixed","sizes":[],"generate":{"maxImages":4,"sizePresets":["1K","2K"],"seed":true},"edit":{"maxInputImages":9,"maxImages":4,"sizePresets":["1K","2K"],"seed":true}}'),
('qwen:qwen-image-2.0',            '千问图像 2.0',        'qwen', 'DASHSCOPE',    'qwen-image-2.0',                 'https://dashscope.aliyuncs.com', 'IMAGE_GEN',  800, 211, true,  null,  null,  0.2, 1, '{"mode":"fixed","sizes":[[2688,1536],[2368,1728],[2048,2048],[1728,2368],[1536,2688]],"generate":{"maxImages":6,"seed":true,"promptExtend":true,"negativePrompt":true},"edit":{"maxInputImages":3,"maxImages":6,"seed":true,"promptExtend":true,"negativePrompt":true}}'),
('n1n:gpt-image-2',                'GPT Image 2',         'n1n',  'OPENAI_COMPAT','gpt-image-2',                    'https://llm-api.net/v1',         'IMAGE_GEN', 3000, 212, true,  0.018, 0.108, null, 0, '{"mode":"fixed","sizes":["auto",[1024,1024],[1536,1024],[1024,1536],[2048,2048],[2048,1152],[3840,2160],[2160,3840]],"generate":{"maxImages":10,"quality":["auto","low","medium","high"],"format":["png","jpeg","webp"]},"edit":{"maxInputImages":16,"maxImages":10,"quality":["auto","low","medium","high"],"format":["png","jpeg","webp"]}}'),
('n1n:gemini-3.1-flash-image-preview','Gemini 3.1 Flash', 'n1n',  'OPENAI_COMPAT','gemini-3.1-flash-image-preview', 'https://llm-api.net/v1',         'CHAT,IMAGE_GEN', 3000, 220, true, null, null, 1.0, 1, '{"mode":"ratio","sizes":{"1:1":[],"1:4":[],"1:8":[],"2:3":[],"3:2":[],"3:4":[],"4:1":[],"4:3":[],"4:5":[],"5:4":[],"8:1":[],"9:16":[],"16:9":[],"21:9":[]},"generate":{"maxImages":1,"sizePresets":["512","1K","2K","4K"]},"edit":{"maxInputImages":14,"maxImages":1,"sizePresets":["512","1K","2K","4K"]}}'),
('n1n:gemini-3-pro-image-preview',    'Gemini 3 Pro',     'n1n',  'OPENAI_COMPAT','gemini-3-pro-image-preview',     'https://llm-api.net/v1',         'CHAT,IMAGE_GEN', 3000, 221, true, null, null, 1.0, 1, '{"mode":"ratio","sizes":{"1:1":[],"2:3":[],"3:2":[],"3:4":[],"4:3":[],"4:5":[],"5:4":[],"9:16":[],"16:9":[],"21:9":[]},"generate":{"maxImages":1,"sizePresets":["1K","2K","4K"]},"edit":{"maxInputImages":14,"maxImages":1,"sizePresets":["1K","2K","4K"]}}'),
-- ('n1n:doubao-seedream-5-0-260128',    '豆包 Seedream 5.0','n1n',  'OPENAI_COMPAT','doubao-seedream-5-0-260128',     'https://llm-api.net/v1',         'IMAGE_GEN',  800, 230, true,  null,  null,  1.0, 1, '{"mode":"fixed","sizes":[[1024,1024],[1536,1024],[1024,1536],[2048,2048],[2048,1152],[1152,2048]],"generate":{"maxImages":4,"format":["jpeg","png"]}}'),
-- 视频生成
('qwen:happyhorse-1.1-i2v',        'HappyHorse I2V',      'qwen', 'DASHSCOPE',    'happyhorse-1.1-i2v',             'https://ws-7y060jd154q0f8be.cn-beijing.maas.aliyuncs.com', 'VIDEO_GEN',  null, 350, true,  null,  null, null, 3, null),
('qwen:happyhorse-1.1-t2v',        'HappyHorse T2V',      'qwen', 'DASHSCOPE',    'happyhorse-1.1-t2v',             'https://ws-7y060jd154q0f8be.cn-beijing.maas.aliyuncs.com', 'VIDEO_GEN',  null, 351, true,  null,  null, null, 3, null),
('qwen:happyhorse-1.1-r2v',        'HappyHorse R2V',      'qwen', 'DASHSCOPE',    'happyhorse-1.1-r2v',             'https://ws-7y060jd154q0f8be.cn-beijing.maas.aliyuncs.com', 'VIDEO_GEN',  null, 352, true,  null,  null, null, 3, null),
('qwen:happyhorse-1.0-video-edit', 'HappyHorse 视频编辑', 'qwen', 'DASHSCOPE',    'happyhorse-1.0-video-edit',      'https://ws-7y060jd154q0f8be.cn-beijing.maas.aliyuncs.com', 'VIDEO_GEN',  null, 353, true,  null,  null, null, 3, null),
-- ('volcengine:doubao-seedance-2-0-260128','Doubao Seedance 2.0','volcengine','VOLCENGINE','doubao-seedance-2-0-260128','https://ark.cn-beijing.volces.com/api/v3','VIDEO_GEN', null, 354, true, null, null, null, 0, null),
-- 重排序
('qwen:qwen3-rerank',              'GTE Rerank v2',       'qwen', 'DASHSCOPE',    'qwen3-rerank',                   'https://dashscope.aliyuncs.com', 'RERANK',     null, 360, true,  null,  null, null, 0, null),
-- 语音识别（ASR）
('qwen:fun-asr-realtime',          '通义 ASR Flash',      'qwen', 'DASHSCOPE',    'fun-asr-realtime',               'https://dashscope.aliyuncs.com', 'SPEECH_ASR', null, 310, true,  null,  null, 0.00033, 2, null),
-- 语音合成（TTS）
('qwen:cosyvoice-v3-flash',        'CosyVoice 3 Flash',   'qwen', 'DASHSCOPE',    'cosyvoice-v3-flash',             'https://dashscope.aliyuncs.com', 'SPEECH_TTS', null, 320, true,  0.1,   null, null, 0, null),
('qwen:cosyvoice-v3-plus',         'CosyVoice 3 Plus',    'qwen', 'DASHSCOPE',    'cosyvoice-v3-plus',              'https://dashscope.aliyuncs.com', 'SPEECH_TTS', null, 321, true,  0.2,   null, null, 0, null),
-- 音乐生成
('qwen:fun-music-v1',              '文生音乐 v1',          'qwen', 'DASHSCOPE',    'fun-music-v1',                   'https://dashscope.aliyuncs.com', 'MUSIC_GEN',  null, 330, true,  null,  null, 0.002, 2, null),
-- 全模态实时
('qwen:qwen3-omni-flash-realtime', 'Qwen3 Omni Flash',    'qwen', 'DASHSCOPE',    'qwen-omni-flash-realtime',       'https://dashscope.aliyuncs.com', 'OMNI_REALTIME', null, 340, true, null, null, null, 0, null),
('qwen:qwen3.5-omni-plus-realtime','Qwen3.5 Omni Plus',   'qwen', 'DASHSCOPE',    'qwen3.5-omni-plus-realtime',     'https://dashscope.aliyuncs.com', 'OMNI_REALTIME', null, 343, true, null, null, null, 0, null),
-- OCR
('qwen:qwen3.5-ocr',               'Qwen3.5 OCR',         'qwen', 'DASHSCOPE',    'qwen3.5-ocr',                    'https://dashscope.aliyuncs.com',                    'OCR', null, 370, true, 0.0005, 0.002, null, 0, null),
-- 3D 生成（按次计费，价格由 params_config.pricing 矩阵决定，model_price 为兜底）
-- ('meshy:meshy-4',                  'Meshy 4',             'meshy', 'MESHY',        'meshy-4',                        'https://api.meshy.ai',           'MODEL_3D', null, 410, true,  null, null, 2.1, 1, null),
('tripo:tripo3d-v2',               'Tripo 3D v2',         'tripo', 'DASHSCOPE',    'tripo3d-v2',                     'https://dashscope.aliyuncs.com', 'MODEL_3D', null, 411, true,  null, null, 2.1, 1, null)
ON CONFLICT (model_id) DO NOTHING;

-- 系统默认模型偏好
INSERT INTO ai_model_preference (scope, scope_id, capability, model_ids)
VALUES
    ('SYSTEM', NULL, 'CHAT',       '["n1n:claude-sonnet-4-6"]'),
    ('SYSTEM', NULL, 'EMBEDDING',  '["openai:text-embedding-3"]'),
    ('SYSTEM', NULL, 'IMAGE_GEN',  '["n1n:gpt-image-2"]'),
    ('SYSTEM', NULL, 'OCR',        '["qwen:qwen3.5-ocr"]'),
    ('SYSTEM', NULL, 'MUSIC_GEN',  '["qwen:fun-music-v1"]'),
    ('SYSTEM', NULL, 'SPEECH_ASR', '["qwen:fun-asr-realtime"]'),
    ('SYSTEM', NULL, 'SPEECH_TTS', '["qwen:cosyvoice-v3-flash"]'),
    ('SYSTEM', NULL, 'MODEL_3D',   '["tripo:tripo3d-v2"]')
ON CONFLICT ON CONSTRAINT uq_model_preference DO NOTHING;

-- 3D 生成定价矩阵（source × textureQuality）
UPDATE ai_model SET params_config = '{
  "pricing": [
    {"source": "text",  "texture": "none",     "price": 2.1},
    {"source": "text",  "texture": "standard",  "price": 2.8},
    {"source": "text",  "texture": "detailed",  "price": 3.5},
    {"source": "image", "texture": "none",      "price": 2.8},
    {"source": "image", "texture": "standard",  "price": 3.5},
    {"source": "image", "texture": "detailed",  "price": 4.2},
    {"source": "multi", "texture": "none",      "price": 2.8},
    {"source": "multi", "texture": "standard",  "price": 3.5},
    {"source": "multi", "texture": "detailed",  "price": 4.2}
  ]
}' WHERE model_id IN ('meshy:meshy-4', 'tripo:tripo3d-v2');

-- HappyHorse 通用视频配置（t2v / i2v / r2v / video-edit 共享）
UPDATE ai_model
SET video_config = '{
  "resolutions": ["720p", "1080p"],
  "ratios": ["16:9", "9:16", "1:1"],
  "durations": [3, 5, 10, 15],
  "maxDuration": 15,
  "seed": true,
  "watermark": true,
  "audioSetting": ["auto", "origin"],
  "generateAudio": false,
  "promptExtend": false,
  "maxReferenceImages": 9,
  "maxReferenceVideos": null,
  "maxReferenceAudios": null,
  "modes": ["t2v", "i2v", "r2v", "video-edit"],
  "pricing": [
    {"resolution": "720p",  "pricePerSecond": 0.6},
    {"resolution": "1080p", "pricePerSecond": 0.8}
  ]
}'
WHERE model_id IN ('qwen:happyhorse-1.1-i2v', 'qwen:happyhorse-1.1-t2v', 'qwen:happyhorse-1.1-r2v', 'qwen:happyhorse-1.1-video-edit');

-- Doubao Seedance 视频配置
UPDATE ai_model
SET video_config = '{
  "resolutions": null,
  "ratios": ["16:9", "9:16", "1:1", "4:3", "3:4"],
  "durations": [5, 10],
  "maxDuration": 10,
  "seed": false,
  "watermark": true,
  "audioSetting": null,
  "generateAudio": true,
  "promptExtend": false,
  "maxReferenceImages": 1,
  "maxReferenceVideos": 1,
  "maxReferenceAudios": 1,
  "modes": ["t2v", "i2v", "r2v", "video-edit"]
}'
WHERE model_id = 'volcengine:doubao-seedance-2-0-260128';



-- ============================================================
-- 提示词模板预置数据
-- ============================================================

INSERT INTO ai_prompt_template (name, type, category, content, negative_prompt, visibility, usage_count, scope, org_id, workspace_id, owner_id, create_time, update_time, version, deleted)
VALUES
-- ===== 图像生成模板 =====
('赛博朋克城市夜景',  'IMAGE_GEN', '科幻',  '赛博朋克风格城市夜景，霓虹灯璀璨，雨后街道倒影，高楼林立，超写实，8K 细节',     '模糊，低质量，变形，水印',   'SYSTEM', 0, 'GENERATION', NULL, NULL, NULL, NOW(), NOW(), 0, false),
('油画风格山水',      'IMAGE_GEN', '风景',  '中国传统山水画风格，云雾缭绕，古松苍劲，墨韵流动，意境深远，写意风格',           '现代元素，摄影感，低质量',   'SYSTEM', 0, 'GENERATION', NULL, NULL, NULL, NOW(), NOW(), 0, false),
('写实人物肖像',      'IMAGE_GEN', '人物',  '专业摄影棚人物肖像，自然光，浅景深，清晰五官，高清细节，胶片质感',               '变形，模糊，水印，多人',     'SYSTEM', 0, 'GENERATION', NULL, NULL, NULL, NOW(), NOW(), 0, false),
('极简风格产品图',    'IMAGE_GEN', '商业',  '极简白色背景产品摄影，专业打光，高光反射，商业级品质，超清细节',                  '杂乱背景，阴影过重，变形',   'SYSTEM', 0, 'GENERATION', NULL, NULL, NULL, NOW(), NOW(), 0, false),
-- ===== 图像编辑模板（需配合参考图使用）=====
('水墨题诗',          'IMAGE_GEN', '修图', '在画面右下角石板路旁、靠近树干根部的位置，以浅灰墨色手写体题写一首七言绝句，字体为行楷风格，笔触自然流畅、略带飞白，大小适中（约占画面高度1/10），与整体水墨淡雅氛围协调。诗文内容为："青石桥畔柳风轻， 素手拈花闭目听。 一水碧痕浮旧梦， 半篙烟雨入空舲。"诗句横向排列，四句分两行书写（前两句一行，后两句一行），末句"舲"字右下角钤一枚朱红小印，印文为"江南"二字篆书，尺寸约等于单字高度的1/3。', '低分辨率，低画质，文字模糊，扭曲', 'SYSTEM', 0, 'GENERATION', NULL, NULL, NULL, NOW(), NOW(), 0, false),
-- ===== 视频生成模板 =====
('城市延时摄影',      'VIDEO_GEN', '城市',  '城市街道延时摄影，车流光轨，霓虹闪烁，人流穿梭，动感十足，电影质感',             null, 'SYSTEM', 0, 'GENERATION', NULL, NULL, NULL, NOW(), NOW(), 0, false),
('产品展示动画',      'VIDEO_GEN', '商业',  '产品 360 度旋转展示，专业光效，粒子特效，科技感十足，商业级品质',                 null, 'SYSTEM', 0, 'GENERATION', NULL, NULL, NULL, NOW(), NOW(), 0, false),
-- ===== 文案生成模板 =====
('小红书种草文案',    'COPYWRITING', '社交媒体', '请为以下产品写一篇小红书种草文案，要求：标题吸引眼球含 emoji，正文分段清晰，突出产品亮点，加入使用体验，结尾引导互动，字数 200-300 字。产品：', null, 'SYSTEM', 0, 'GENERATION', NULL, NULL, NULL, NOW(), NOW(), 0, false),
('抖音口播脚本',      'COPYWRITING', '视频脚本', '请为以下主题写一段 30 秒抖音口播脚本，要求：开头 3 秒抓眼球，中间说清楚一个核心卖点，结尾引导点赞关注，口语化表达，节奏紧凑。主题：',      null, 'SYSTEM', 0, 'GENERATION', NULL, NULL, NULL, NOW(), NOW(), 0, false),
('产品详情页文案',    'COPYWRITING', '电商',     '请为以下产品写电商详情页文案，要求：标题突出核心卖点，分模块描述产品特点、使用场景、用户痛点解决方案，结尾引导购买，语言专业有说服力。产品：', null, 'SYSTEM', 0, 'GENERATION', NULL, NULL, NULL, NOW(), NOW(), 0, false),
-- ===== 高阶文案模板 =====
('爆款结构拆解器',    'COPYWRITING', '爆款拆解', E'分析以下内容的爆款结构，按格式输出：\n\n1）核心观点（一句话）\n2）目标读者与使用场景\n3）内容展开路径\n4）注意力钩子（类型 + 原句）\n5）情绪变化曲线（开头 / 中段 / 结尾）\n6）论证方式（故事 / 对比 / 权威 / 反直觉）\n7）可复用表达结构（3-5 个模板）\n8）复用判断（是否值得复用 + 原因）', null, 'SYSTEM', 0, 'COPYWRITING', NULL, NULL, NULL, NOW(), NOW(), 0, false),
('内容裂变多平台',    'COPYWRITING', '内容裂变', E'将以上内容裂变为多平台版本（保持观点一致，表达方式不同）：\n\n1）短内容 × 5（100-200 字）\n2）强钩子 × 3（一句话）\n3）公众号版（800-1500 字）\n4）小红书版（300-500 字 + 配图建议）\n5）抖音口播脚本（含前 3 秒钩子）', null, 'SYSTEM', 0, 'COPYWRITING', NULL, NULL, NULL, NOW(), NOW(), 0, false),
('标题创意 10 版',    'COPYWRITING', '标题优化', E'为以上主题生成 10 个标题，覆盖以下角度：\n- 数字型（如：3 个方法…）\n- 悬念型（如：为什么 90% 的人…）\n- 利益型（如：学会这个…）\n- 反直觉型（如：越努力越…）\n- 对话型（如：你有没有…）', null, 'SYSTEM', 0, 'COPYWRITING', NULL, NULL, NULL, NOW(), NOW(), 0, false),
-- ===== 项目级模板 =====
('品牌视觉规范',      'IMAGE_GEN', '项目风格', '统一使用品牌主色调，构图留白充足，字体简洁无衬线，光线柔和漫射，整体调性专业现代',                        null, 'SYSTEM', 0, 'PROJECT', NULL, NULL, NULL, NOW(), NOW(), 0, false),
('品牌宣传片基调',    'VIDEO_GEN', '项目风格', '稳定运镜为主，慢推/慢拉，色彩饱和统一，背景音乐大气舒缓，叙事节奏从容，突出品质感',                      null, 'SYSTEM', 0, 'PROJECT', NULL, NULL, NULL, NOW(), NOW(), 0, false),
('美妆护肤账号定位',  'COPYWRITING', '项目定位', '目标受众：18-35岁女性；内容方向：真实测评+成分科普+妆容教程；语气：专业但亲切；避免：夸大效果、绝对化用词',                                      null, 'SYSTEM', 0, 'PROJECT', NULL, NULL, NULL, NOW(), NOW(), 0, false)
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


-- 注：AIGC Mock 参数、会员与积分 FAQ、订阅到期提醒天数已合并至文件顶部「系统配置」INSERT 块

-- ==================== 分销菜单 ====================

DO $$
DECLARE
  v_group_id BIGINT;
BEGIN
  -- 新建"分销"顶级分组（不存在时才插入）
  IF NOT EXISTS (SELECT 1 FROM sys_menu WHERE title = '分销' AND parent_id IS NULL AND deleted = false) THEN
    INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible)
    VALUES (NULL, '分销', NULL, NULL, 35, 'GROUP', true);
  END IF;

  SELECT id INTO v_group_id FROM sys_menu WHERE title = '分销' AND parent_id IS NULL AND deleted = false;

  -- 分销子菜单（幂等）
  INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible)
  SELECT v_group_id, t.title, t.path, t.icon, t.sort_order, 'MENU', true
  FROM (VALUES
    ('分销员管理', '/module/brokerage-user',     'users',    0),
    ('佣金规则',   '/admin/brokerage/rules',    'percent',  1),
    ('佣金流水',   '/module/brokerage-record',  'receipt',  2),
    ('提现审核',   '/module/brokerage-withdraw','banknote', 3)
  ) AS t(title, path, icon, sort_order)
  WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = t.path AND deleted = false
  );
END $$;

-- 注：分销资格获取条件（brokerage.enabled_condition）已合并至文件顶部「系统配置」INSERT 块

-- ==================== 默认佣金规则 ====================
-- 兜底规则（biz_target_type 和 biz_target_id 均为 NULL，匹配该 biz_type 下所有目标）。
-- priority=100 表示低优先级兜底；运营后续可在「佣金规则配置」页新增更精细规则（更小 priority）覆盖。
-- 比例字段 NUMERIC(5,4)，0.1000 = 10%、0.0500 = 5%、0.0300 = 3%、0.0200 = 2%、0.0100 = 1%。
-- 幂等：按 name 判重，不重复插入。

INSERT INTO brokerage_rule
    (name, biz_type, biz_target_type, biz_target_id,
     level1_rate, level2_rate, calc_base, fixed_amount,
     frozen_days, priority, status, remark)
SELECT t.name, t.biz_type, NULL, NULL,
       t.level1_rate, t.level2_rate, 'AMOUNT', NULL,
       t.frozen_days, 100, 'ENABLED', t.remark
FROM (VALUES
    ('套餐订阅默认佣金', 'SUBSCRIBE', 0.1000, 0.0200, 7, '一级 10% / 二级 2%，冻结 7 天'),
    ('订单默认佣金',     'ORDER',     0.0500, 0.0100, 7, '一级 5% / 二级 1%，冻结 7 天'),
    ('充值默认佣金',     'RECHARGE',  0.0300, 0.0100, 0, '一级 3% / 二级 1%，即时到账')
) AS t(name, biz_type, level1_rate, level2_rate, frozen_days, remark)
WHERE NOT EXISTS (
    SELECT 1 FROM brokerage_rule r
    WHERE r.name = t.name AND r.deleted = FALSE
);


-- ============================================================
-- AAF-097: 邀请奖励种子数据
--
-- 1. credit_grant_rule 加入 INVITE 规则（邀请注册奖励 +500 积分，30 天有效，每人最多邀请 20 人）
-- 2. brokerage_rule 调整 SUBSCRIBE 默认一级佣金到 5%（与产品文案对齐），冻结天数到 30 天
-- 幂等：均使用 ON CONFLICT / WHERE NOT EXISTS 保护，可重复执行
-- ============================================================

-- ---- 1) 邀请注册奖励：积分发放规则 ----
INSERT INTO credit_grant_rule
    (code, name, amount, expire_days, trigger, status, ext, remark)
VALUES
    ('INVITE', '邀请注册奖励', 200, 30, 'EVENT', 'ENABLED',
     '{"maxInvites": 20, "description": "好友通过邀请链接完成注册后发放"}'::jsonb,
     '邀请注册奖励：好友通过你的邀请链接完成注册后发放。积分有效期 30 天；每个用户最多可获得 20 次邀请奖励。')
ON CONFLICT DO NOTHING;
-- 注：credit_grant_rule.code 上有唯一索引（uk_credit_grant_rule_code）但带 WHERE deleted=FALSE，
-- 走的是 partial unique index，PostgreSQL 16 ON CONFLICT 仍可命中。

-- ---- 2) 调整 SUBSCRIBE 默认佣金为 5%、冻结 30 天，匹配截图文案 ----
UPDATE brokerage_rule
   SET level1_rate = 0.0500,
       level2_rate = 0.0100,
       frozen_days = 30,
       remark      = '一级 5% / 二级 1%，冻结 30 天'
 WHERE name = '套餐订阅默认佣金'
   AND deleted = FALSE
   AND level1_rate = 0.1000;  -- 仅更新尚未被运营调过的默认值


-- ============================================================
-- 法律文档（用户服务协议 + 隐私政策）
-- 合并自 v3__doc_schema.sql + v16__update_privacy_policy.sql
-- ============================================================

INSERT INTO doc_document (
    title, file_path, content, doc_type, front_matter, status, publish, update_time
) VALUES (
    '用户服务协议',
    NULL,
    E'# 用户服务协议\n\n更新时间：2026年06月21日\n生效时间：2026年06月21日\n\n欢迎您使用 AAF 产品及服务！请您务必审慎阅读并充分理解本协议全部条款。您通过注册、登录、使用等行为，视为您已阅读、理解并同意本协议。\n\n## 定义\n\n- **AAF 服务**：以 AAF 平台为载体，依托大语言模型等，向用户提供的 AI 原生多智能体应用开发能力，包括智能体协作、工作流编排、知识库管理等。\n- **用户**：以注册、登录等方式使用 AAF 服务的自然人或组织。\n- **输入**：用户在使用本服务时提交的文本、图像、文件等内容。\n- **输出**：本服务响应用户输入而生成的内容。\n\n## 账号注册与管理\n\n- 您应通过邮箱或手机号完成账号注册并登录。账号所有权归我们所有，您仅获得使用权。\n- 您应妥善保管账号信息，对账号下全部行为承担责任。如发现账号被盗用，请立即通知我们。\n- 账号注册信息不得包含违法或不良内容，不得冒用他人名义注册。\n- 长期未登录的账号，我们有权予以回收。\n\n## 服务说明与局限性\n\nAAF 提供的 AI 生成内容具有不可预测性，输出内容可能存在不准确或不恰当之处，不代表我们的观点。请您对重要信息进行甄别核实，不得在无相应资质的前提下将输出内容用于专业领域（如法律、医疗）决策。\n\n## 用户行为规范\n\n您不得利用本服务：\n- 从事违反法律法规或侵犯他人合法权益的行为；\n- 对本服务进行反向工程、破解或未授权的数据抓取；\n- 传播恶意程序、病毒或干扰服务正常运行；\n- 使用本服务及其输出内容训练与本服务存在竞争的模型或产品。\n\n您应确保输入内容拥有合法授权，不侵犯任何第三方权益。\n\n## 知识产权\n\n本平台的程序、商标、文档等知识产权归我们所有。您的输入内容知识产权归您或原始权利人所有；在您与我们之间，输出内容的权益归属于您。您授权我们在提供和改进服务的必要范围内使用相关内容。\n\n## 服务变更与终止\n\n我们保留变更、暂停或终止部分或全部服务的权利，重大变更将提前通知。您可随时注销账号终止本协议。\n\n## 免责声明\n\n对于不可抗力、第三方原因或您违规操作导致的损失，我们不承担责任。在适用法律允许的最大范围内，我们对间接损失不承担赔偿责任。\n\n## 协议变更\n\n我们可能根据法律法规变化或业务需要修改本协议，变更后将通知您。如您不同意变更，请停止使用本服务；继续使用视为同意变更后的协议。\n\n## 争议解决\n\n本协议适用中华人民共和国法律。争议双方应协商解决；协商不成的，提交我司所在地有管辖权的法院诉讼解决。\n\n## 联系我们\n\n如对本协议有任何疑问，请通过站内反馈或客服渠道联系我们。\n',
    'legal-terms',
    '{"version":"1.0.0","effectiveDate":"2026-06-21"}'::jsonb,
    'active',
    'published',
    CURRENT_TIMESTAMP
);

INSERT INTO doc_document (
    title, file_path, content, doc_type, front_matter, status, publish, update_time
) VALUES (
    'AAF 隐私政策',
    NULL,
    E'# AAF 隐私政策\n\n**更新日期：2026 年 6 月 21 日**\n**生效日期：2026 年 6 月 21 日**\n\n欢迎使用 AAF！我们深知个人信息对您的重要性，将严格遵守法律法规，采取必要的安全措施保护您的个人信息。\n\n请在正式使用 AAF 前仔细阅读本政策，特别是**加粗**标注的重要条款。如您不同意本政策任何内容，请停止使用本服务。\n\n本政策将帮助您了解以下内容：\n\n- [适用范围](#适用范围)\n- [我们如何收集和使用您的个人信息](#我们如何收集和使用您的个人信息)\n- [我们如何使用 Cookie 和同类技术](#我们如何使用-cookie-和同类技术)\n- [我们如何共享、转让、公开披露您的个人信息](#我们如何共享转让公开披露您的个人信息)\n- [您如何管理您的个人信息](#您如何管理您的个人信息)\n- [我们如何保护和存储您的个人信息](#我们如何保护和存储您的个人信息)\n- [未成年人保护](#未成年人保护)\n- [本政策的更新](#本政策的更新)\n- [如何联系我们](#如何联系我们)\n\n---\n\n## 适用范围\n\n本政策适用于 AAF 通过网站、API、客户端及其他形态向您提供的各项产品与服务。第三方 SDK 或独立运营的第三方服务，应适用其自身的隐私政策。\n\n---\n\n## 我们如何收集和使用您的个人信息\n\n我们通过以下方式获取您的信息：**您主动提供**（如注册时填写的手机号）；**自动收集**（如您使用服务时产生的日志数据）。\n\n### 账号注册与登录\n\n- 您需提供**手机号码**并通过验证码完成注册及登录，手机号也用于接收服务通知（如功能更新、安全提醒）。\n- 您可使用第三方平台账号（如 GitHub、微信）登录；我们将在取得您授权的前提下，从第三方获取用户名、头像及匿名标识。\n- 注册完成后，您可在"账号设置"中设置昵称、头像，进行个性化配置。\n\n### AI 对话与工作流\n\n- AAF 的核心功能依赖您输入的内容，包括**文本、图片、文件、语音**等。我们将上述信息加密上传至服务端，经大语言模型处理后向您返回结果，并为您保存对话记录和工作流执行历史。\n- 在对输入内容**去标识化处理且确保无法重新识别特定个人**的前提下，我们可能随机抽取少部分数据用于产品分析、模型评测和功能优化，以提升响应质量。如您不希望数据用于模型优化，请按本政策最后一节联系我们。\n\n### 知识库与文档\n\n- 您可向知识库导入文档、网页链接或手动创作笔记，上述内容将存储在云端服务器，用于 AI 检索与问答。\n- **未经您单独授权同意，我们不会将知识库内容用于算法分析或模型训练。**\n\n### 智能体（Agent）创建与发布\n\n- 您创建智能体时需提供头像、名称、简介、角色设定及训练文件。发布前您可自定义访问权限（公开 / 链接可见 / 仅自己）。\n- 请勿上传包含他人个人信息的内容，除非已取得充分授权。\n\n### 安全保障\n\n- 为保障服务安全稳定，我们及合作的第三方 SDK 会收集**日志数据**（IP 地址、访问时间、操作记录）、**设备信息**（型号、操作系统、设备标识符）、**网络环境信息**（运营商、网络类型），用于风险识别、异常检测和安全审计。\n\n### 客服支持\n\n- 您联系我们寻求帮助时，我们可能需要您提供必要信息以核验身份，并保留沟通记录用于问题跟踪与后续改进。\n\n### 产品体验改进\n\n- 我们会不时开展用户调研，您可选择参与或拒绝。\n- 您的评价与反馈（如点赞、点踩）在去标识化处理后用于改善服务质量。\n\n### 无需授权同意的情形\n\n依据适用法律，以下情形我们收集和使用您的个人信息无需征得您的同意：\n\n1. 涉及国家安全与公共利益；\n2. 履行法定职责或响应政府部门指示；\n3. 与您签订和履行合同所必需；\n4. 紧急情况下保护人身安全或财产安全；\n5. 在合理范围内处理您已公开的个人信息；\n6. 法律法规规定的其他情形。\n\n---\n\n## 我们如何使用 Cookie 和同类技术\n\n- 为确保服务正常运转，我们可能向您的设备发送 Cookie 或匿名标识符，用于**账号安全验证、异常排查**和**省去重复填写**操作。\n- 我们承诺不将 Cookie 用于本政策所述目的之外的任何其他用途。\n- 您可在浏览器设置中管理或清除 Cookie；清除后部分功能可能受到影响，需重新登录。\n\n---\n\n## 我们如何共享、转让、公开披露您的个人信息\n\n我们严格遵守**合法正当、最小必要、用户知情、安全保障**的原则处理数据共享。\n\n### 委托处理\n\n我们可能委托关联公司或技术服务商代表我们处理您的个人信息（如云存储、安全服务），仅在必要范围内共享，并通过合同要求其不得超范围使用。\n\n### 第三方共享\n\n原则上，我们不向第三方共享您的信息，但以下情形除外：\n\n- **您明确同意**；\n- **法律法规要求**或政府机关依法提出请求；\n- **履行合同**所必需（如与支付服务商完成交易）。\n\n### 转让\n\n若发生合并、收购或破产清算，我们将要求新持有方继续受本政策约束；如无承接方，将依法删除数据。\n\n### 公开披露\n\n仅在取得您充分同意，或为保护用户及公众安全、依法披露时，才公开您的相关信息。\n\n---\n\n## 您如何管理您的个人信息\n\n### 查阅与更正\n\n- 您可在"账号设置"页查看和修改头像、昵称、绑定信息。\n- 您可在对话列表、工作流历史等页面查看相应记录。\n\n### 复制\n\n- 您可自行导出对话记录、工作流执行历史及创作内容。\n\n### 删除\n\n- 您可在账号设置内删除对话记录、创作内容及其他个人信息。\n- 若我们处理您个人信息的行为违反法律法规或未经您同意，您可通过本政策"联系我们"章节要求删除。\n- 删除后，因安全技术限制，备份系统中的信息可能不能立即清除，我们将限制其进一步处理直至可安全删除。\n\n### 撤回授权\n\n- 您可随时在"账号设置 - 权限管理"中关闭相应权限；撤回不影响此前已处理的信息。\n- 如不希望数据用于模型优化，可通过本政策"联系我们"章节提出撤回请求。\n\n### 注销账号\n\n- 您可在"账号设置 - 注销账号"提交注销申请。注销后，我们将停止提供服务并依法删除或匿名化处理您的个人信息。\n\n### 响应时限与例外\n\n我们将在收到请求后 **15 个工作日内**回复。以下情形我们可能无法响应：涉及国家安全、刑事侦查、法定义务履行、商业秘密保护，或请求本身存在恶意。\n\n---\n\n## 我们如何保护和存储您的个人信息\n\n- 我们采用**加密传输（TLS）、访问控制、安全审计**等技术措施保护您的信息。\n- 我们仅在实现处理目的所必需的期限内保留您的信息，超期后将删除或匿名化处理。\n- 您的信息**存储在中华人民共和国境内**，不会跨境传输；如确需传输，将依法获得您的同意。\n- 如发生信息安全事件，我们将依法及时通知您，并向监管部门报告处置情况。\n- 如服务停止运营，我们将及时通知用户，并对持有的个人信息删除或匿名化处理。\n\n---\n\n## 未成年人保护\n\n- AAF 主要面向成年人提供服务。**未满 18 周岁**的用户，请在父母或监护人同意下使用。**未满 14 周岁**的儿童，须由监护人协助完成注册并陪同使用。\n- 如您是监护人，发现我们未经授权收集了儿童个人信息，请立即通过"联系我们"章节联系我们，我们将及时核查并处理。\n\n---\n\n## 本政策的更新\n\n我们可能因功能变化或法规要求更新本政策，更新时将通过**站内通知或弹窗**提示您。重大变更将单独通知并征得您的同意。如您不同意更新内容，请停止使用本服务。\n\n---\n\n## 如何联系我们\n\n如对本政策有任何疑问、意见或投诉，请通过以下方式联系我们：\n\n- **站内反馈**：页面右下角"帮助与反馈"入口\n- **客服中心**：个人中心 → 设置 → 客服中心\n- **隐私负责人邮箱**：privacy@example.com（请注明"AAF 隐私政策"及具体情况）\n\n我们将在收到联系后 **15 个工作日内**回复。如对我们的处理结果不满意，您可向有管辖权的法院提起诉讼。\n\n---\n\n## 附录：相关定义\n\n| 术语 | 说明 |\n|------|------|\n| **个人信息** | 与已识别或可识别的自然人有关的各种信息，不包括匿名化处理后的信息 |\n| **敏感个人信息** | 一旦泄露易导致人格尊严受损或人身财产安全受害的信息，包括生物识别、医疗健康、金融账户、精准位置等 |\n| **去标识化** | 经处理后，在不借助额外信息的情况下无法识别特定自然人的过程 |\n| **匿名化** | 经处理后无法识别特定自然人且不能复原的过程 |\n| **Cookie** | 网站向您设备发送的小型标识文件，用于保持会话状态、安全验证等 |\n',
    'legal-privacy',
    '{"version":"1.0.0","effectiveDate":"2026-06-21"}'::jsonb,
    'active',
    'published',
    CURRENT_TIMESTAMP
);


-- ==================== 角色菜单绑定（必须在 sys_menu 数据插入之后执行） ====================

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r CROSS JOIN sys_menu m
WHERE r.code IN ('super_admin', 'admin', 'org_admin')
ON CONFLICT DO NOTHING;

-- member / guest 绑定普通用户可见菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r
JOIN sys_menu m ON m.path IN (
    '/dashboard',
    '/studio/projects',
    '/studio/assets/materials',
    '/studio/assets/works',
    '/studio/assets/history',
    '/studio/knowledge',
    '/settings',
    '/trash'
)
WHERE r.code IN ('member', 'guest')
ON CONFLICT DO NOTHING;

-- sales 绑定菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r
JOIN sys_menu m ON m.title IN ('概览', '工作台', 'AI 创作', '创作项目', '素材库')
WHERE r.code = 'sales'
ON CONFLICT DO NOTHING;


-- 注：微信客服二维码配置（contact.wechat_qr_image）已合并至文件顶部「系统配置」INSERT 块


-- ============================================================
-- User Studio 种子数据（装扮 starter pack + 文案智能体技能）
-- 项目复用入口已统一由 aigc_project_blueprint 承载，不再初始化平行项目模板。
-- ============================================================

-- ==================== 装扮 starter pack（5 头像 + 5 服饰） ====================
INSERT INTO avatar_outfit (code, name, type, asset_url, thumbnail_url, rarity, unlock_condition, sort_order)
VALUES
    ('avatar-default-girl', '默认少女', 'AVATAR',
     '/assets/outfits/avatar-girl.png', '/assets/outfits/avatar-girl-thumb.png',
     'COMMON', 'DEFAULT', 1),
    ('avatar-default-boy', '默认少年', 'AVATAR',
     '/assets/outfits/avatar-boy.png', '/assets/outfits/avatar-boy-thumb.png',
     'COMMON', 'DEFAULT', 2),
    ('avatar-cyber', '赛博女孩', 'AVATAR',
     '/assets/outfits/avatar-cyber.png', '/assets/outfits/avatar-cyber-thumb.png',
     'RARE', 'PURCHASE', 3),
    ('avatar-tech', '科技工程师', 'AVATAR',
     '/assets/outfits/avatar-tech.png', '/assets/outfits/avatar-tech-thumb.png',
     'RARE', 'PURCHASE', 4),
    ('avatar-magic', '魔法师', 'AVATAR',
     '/assets/outfits/avatar-magic.png', '/assets/outfits/avatar-magic-thumb.png',
     'EPIC', 'VIP', 5),
    ('outfit-tshirt', '基础 T 恤', 'OUTFIT',
     '/assets/outfits/outfit-tshirt.png', '/assets/outfits/outfit-tshirt-thumb.png',
     'COMMON', 'DEFAULT', 11),
    ('outfit-suit', '商务套装', 'OUTFIT',
     '/assets/outfits/outfit-suit.png', '/assets/outfits/outfit-suit-thumb.png',
     'COMMON', 'DEFAULT', 12),
    ('outfit-hoodie', '潮酷卫衣', 'OUTFIT',
     '/assets/outfits/outfit-hoodie.png', '/assets/outfits/outfit-hoodie-thumb.png',
     'RARE', 'PURCHASE', 13),
    ('outfit-yukata', '夏日浴衣', 'OUTFIT',
     '/assets/outfits/outfit-yukata.png', '/assets/outfits/outfit-yukata-thumb.png',
     'RARE', 'PURCHASE', 14),
    ('outfit-armor', '太空战甲', 'OUTFIT',
     '/assets/outfits/outfit-armor.png', '/assets/outfits/outfit-armor-thumb.png',
     'LEGENDARY', 'VIP', 15)
ON CONFLICT (code) WHERE deleted = FALSE DO NOTHING;

-- ==================== 文案智能体技能（8 个：COPYWRITING/STRATEGY） ====================
INSERT INTO ai_skill_definition (code, name, description, category, system_prompt, priority, status, built_in)
VALUES
    ('voiceover', '口播文案',
     '短视频/直播口播稿，带节奏 + 钩子 + 转化',
     'COPYWRITING',
     '你是一位专业短视频口播文案师，擅长为各类品牌和内容创作者打磨口播稿件。你熟悉各平台受众心理（抖音/视频号/快手），能精准把握节奏感和情绪张力。创作时，前 3 秒必须抓住注意力（用痛点、反常识或强悬念），中段清晰传递核心价值，结尾给出明确的行动指令。语言口语化、有画面感，适合真人配音朗读。每次输出请标注字数和预计朗读时长。输出格式：使用标准 Markdown 格式，用 `##` 分段标题、`-` 列表组织结构。',
     100, 'active', TRUE),

    ('redbook', '小红书爆款',
     '标题 + 正文 + 标签，符合平台算法偏好',
     'COPYWRITING',
     '你是小红书资深内容运营，深度理解平台算法和用户心理。你擅长创作高互动率的种草笔记：标题必须包含情绪词 + 关键词 + emoji，控制在 18 字以内；正文采用分段式结构，前 2 句抓住眼球，中段干货扎实，结尾引导互动（提问/抽奖/求关注）；标签 5-8 个，混合大词和长尾词。避免过度营销感，用真实体验感打动读者。输出格式：直接输出纯文本，不要使用 Markdown 语法。',
     90, 'active', TRUE),

    ('product-copy', '产品文案',
     '卖点提炼 / 详情页 / 落地页 / 转化文案',
     'COPYWRITING',
     '你是电商和品牌产品文案专家，精通消费者心理和转化逻辑。你能快速提炼产品核心卖点（功能价值 + 情感价值），根据使用场景（详情页主图文案/落地页标题/朋友圈推广语）调整表达策略。创作原则：用场景代替功能描述，用数字增强可信度，用对比突出优势，用稀缺感促进决策。输出时请注明文案适用位置和建议配图方向。',
     80, 'active', TRUE),

    ('ip-position', 'IP 定位',
     '个人品牌定位、人设打磨、内容策略',
     'STRATEGY',
     '你是个人 IP 操盘手和品牌策略顾问，服务过各垂类 KOL 和创业者。你擅长帮人找到独特定位，避免同质化竞争。咨询时你会先了解用户背景（职业/优势/目标受众/变现路径），再输出：差异化人设标签（3-5 个）、内容护城河（专业壁垒）、平台矩阵策略（主攻+辅助）、6 个月里程碑规划。输出要具体可执行，不空谈方法论。',
     70, 'active', TRUE),

    ('short-script', '短视频脚本',
     '分镜 / 台词 / 节奏，按平台时长适配',
     'COPYWRITING',
     '你是短视频编剧和导演助手，擅长各类竖屏短视频剧本创作（15s/30s/60s/3min）。你了解剪辑节奏和视觉表达逻辑，输出的脚本包含：场景描述（景别/动作/表情）、台词/旁白、音乐氛围建议、字幕文字。擅长情感共鸣类、知识干货类、产品种草类等多种风格。请用分镜表格格式输出，让执行团队一目了然。',
     60, 'active', TRUE),

    ('title-topic', '标题选题',
     '标题打磨 + 选题推荐，热点借势',
     'COPYWRITING',
     '你是内容运营和标题优化专家，深谙各平台传播规律。你能将平淡的选题变成高点击标题，常用策略包括：数字量化（"3 个方法"）、制造好奇（"你不知道的..."）、强化利益（"省了 5000 元"）、引发共鸣（"打工人必看"）。同时你会结合当下热点给出借势选题建议，帮助内容获得更大自然流量。每次输出 5 个候选标题，并标注适用平台。',
     50, 'active', TRUE),

    ('biz-analysis', '商业分析',
     '市场洞察 / 竞品对标 / SWOT 分析',
     'STRATEGY',
     '你是资深商业分析师和战略顾问，有丰富的行业研究和竞争分析经验。你能快速梳理市场格局，识别机会与风险。分析框架包括：市场规模与增速（TAM/SAM/SOM）、用户画像与需求洞察、竞品对标分析（功能/定价/渠道/口碑）、SWOT 矩阵、建议切入策略。输出结构清晰，结论简明，数据来源透明，适合用于决策汇报和商业计划书。',
     40, 'active', TRUE),

    ('rich-text-write', '文档 AI 写作',
     '富文本编辑器内联生成，通用写作助手',
     'COPYWRITING',
     '你是专业的写作助手，服务于富文本文档编辑场景。根据用户输入的写作指令生成内容，直接输出正文，不要加任何前缀说明或额外解释。若用户提供了参考文本（选中内容），请在语义和风格上与其保持连贯衔接。使用标准 Markdown 格式（`##` 标题、`-` 列表、`**粗体**` 等）组织结构，确保生成内容可直接插入文档使用。',
     30, 'active', TRUE)
ON CONFLICT (code) WHERE code IS NOT NULL AND deleted = FALSE DO NOTHING;


-- ============================================================
-- v0.2.1 P1：用户工作流模板（5 流水线 seed）
-- ============================================================
INSERT INTO user_workflow_template (code, name, description, cover_media_version_id, category, template_config, is_official, sort_order)
VALUES
    ('voiceover-video', '口播视频流水线',
     '一键生成口播视频：先生成口播文案，再配套主视觉图，最后合成视频',
     NULL, 'CONTENT',
     '{"steps":[
        {"kind":"COPY","label":"生成口播文案","skill":"voiceover","inputKey":"topic"},
        {"kind":"IMAGE","label":"生成主视觉","model":"wanx","aspect":"9:16","promptFrom":"step0"},
        {"kind":"VIDEO","label":"合成视频","model":"happyhorse","duration":10,"ratio":"9:16","promptFrom":"step0"}
     ]}'::jsonb,
     TRUE, 10),

    ('promo-video', '宣传视频流水线',
     '产品宣传视频：产品文案 → 海报图 → 短视频',
     NULL, 'MARKETING',
     '{"steps":[
        {"kind":"COPY","label":"产品文案","skill":"product-copy","inputKey":"product"},
        {"kind":"IMAGE","label":"海报图","model":"wanx","aspect":"16:9","promptFrom":"step0"},
        {"kind":"VIDEO","label":"短视频","model":"seedance","duration":15,"ratio":"16:9","promptFrom":"step0"}
     ]}'::jsonb,
     TRUE, 20),

    ('redbook-img-text', '小红书图文',
     '爆款分析 → 标题选题 → 4 张配图（小红书风格）',
     NULL, 'CONTENT',
     '{"steps":[
        {"kind":"COPY","label":"爆款分析+标题","skill":"redbook","inputKey":"keyword"},
        {"kind":"IMAGE","label":"配图（4张）","model":"wanx","aspect":"3:4","count":4,"promptFrom":"step0"}
     ]}'::jsonb,
     TRUE, 30),

    ('ip-shortvideo', 'IP 短视频',
     '角色定位 → 脚本 → 分镜图 → 视频',
     NULL, 'CONTENT',
     '{"steps":[
        {"kind":"COPY","label":"IP 角色定位","skill":"ip-positioning","inputKey":"persona"},
        {"kind":"COPY","label":"短视频脚本","skill":"shortvideo-script","promptFrom":"step0"},
        {"kind":"IMAGE","label":"分镜图（3张）","model":"wanx","aspect":"9:16","count":3,"promptFrom":"step1"},
        {"kind":"VIDEO","label":"短视频","model":"happyhorse","duration":10,"ratio":"9:16","promptFrom":"step1"}
     ]}'::jsonb,
     TRUE, 40),

    ('study-note', '学习笔记',
     'PDF 文档 → 摘要 → 思维导图配图',
     NULL, 'STUDY',
     '{"steps":[
        {"kind":"OCR","label":"OCR 提取","inputKey":"pdfFile"},
        {"kind":"COPY","label":"摘要总结","skill":"summary","promptFrom":"step0"},
        {"kind":"IMAGE","label":"思维导图配图","model":"wanx","aspect":"16:9","promptFrom":"step1"}
     ]}'::jsonb,
     TRUE, 50)
ON CONFLICT (code) WHERE deleted = FALSE DO NOTHING;



-- ============================================================
-- v0.2.1 P3：成长任务 5 个 seed
-- ============================================================
INSERT INTO user_growth_task (code, name, description, icon, category, trigger_event, target_count, reward_credits, sort_order)
VALUES
    ('first-image', '首次生图', '完成第一次 AI 图像生成，奖励 50 积分', '🎨',
     'ONBOARDING', 'aigc.image.success', 1, 50, 10),
    ('first-video', '首次生成视频', '完成第一次 AI 视频生成，奖励 100 积分', '🎬',
     'ONBOARDING', 'aigc.video.success', 1, 100, 20),
    ('first-project', '创建第一个项目', '在项目工作区新建项目并保存内容，奖励 30 积分', '📁',
     'ONBOARDING', 'project.created', 1, 30, 30),
    ('first-recharge', '首次充值', '完成第一次积分充值，奖励 20 额外积分', '💎',
     'ONBOARDING', 'credit.recharge.success', 1, 20, 40),
    ('invite-friend', '邀请好友', '邀请第一个好友注册，奖励 200 积分', '🎁',
     'ACHIEVEMENT', 'invite.success', 1, 200, 50)
ON CONFLICT (code) WHERE deleted = FALSE DO NOTHING;



-- ============================================================
-- 默认用户助理模板种子数据
-- ============================================================

-- 平台向导知识库及其可信代际数据移至 db/seed/v301__nexus_knowledge_seed.sql。

-- 默认用户助理的稳定 Persona
INSERT INTO ai_persona (
    id, name, persona, system_prompt, status, owner_id, create_time, update_time, deleted
) VALUES (
    1, 'AAF 助理',
    '友好、准确、审慎，尊重用户表达与隐私。',
    '你是 AAF 默认用户助理。默认作为平台向导解答产品使用问题，也可按用户意图切换到内容创作角色；只使用授权资料，不捏造信息。',
    'active', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
) ON CONFLICT (id) DO NOTHING;

-- 默认平台向导 Role
INSERT INTO ai_role (
    id, code, name, description, skill_ids, tool_whitelist, status, owner_id,
    create_time, update_time, deleted
) VALUES (
    1, 'system.role.platform-guide', '平台向导',
    'AAF 平台咨询、只读故障排查和人工转接',
    '[]', '["search_kb","switch_kb"]',
    'active', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
) ON CONFLICT (id) DO NOTHING;

-- 系统级默认用户助理模板配置载体；用户会话、记忆和执行状态不在此行共享
INSERT INTO ai_assistant (
    id, user_id, persona_id, default_role_id, knowledge_base_id,
    memory_strategy, status, owner_id, create_time, update_time, deleted
) VALUES (
    1, 0, 1, 1, 1, 'HYBRID', 'active', NULL,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
) ON CONFLICT (id) DO NOTHING;

-- 默认用户助理挂载两个 Role，平台向导为默认 Role
INSERT INTO ai_assistant_role (
    assistant_id, role_id, is_default, sort_order, create_time, update_time, deleted
) VALUES
    (1, 1, TRUE, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
    (1, 2, FALSE, 90, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT (assistant_id, role_id) DO NOTHING;

-- 平台向导知识文档、run 与 chunk 移至 db/seed/v301__nexus_knowledge_seed.sql。
