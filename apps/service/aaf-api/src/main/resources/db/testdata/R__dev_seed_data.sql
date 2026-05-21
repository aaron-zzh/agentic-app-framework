-- ============================================================
-- 开发/测试环境种子数据（Flyway Repeatable Migration）
-- 每次内容变化自动重新执行
-- 生产环境通过 locations 配置排除此文件
-- ============================================================

-- ==================== 系统配置 ====================

INSERT INTO sys_config (category, config_key, value, default_value, value_type, name, description, visible, editable) VALUES
-- user 分类
('user',     'user.default_password',        '123456',  '123456',  'string',  '用户默认密码',       '管理员创建用户时的初始密码',                    FALSE, TRUE),
('user',     'user.register_enabled',        'true',    'true',    'boolean', '是否开放注册',       '关闭后禁止新用户自主注册',                      TRUE,  TRUE),
('user',     'user.login_fail_lock_count',   '5',       '5',       'integer', '登录失败锁定次数',   '连续失败超过此次数后锁定账号',                  TRUE,  TRUE),
('user',     'user.login_fail_lock_minutes', '30',      '30',      'integer', '账号锁定时长（分钟）','登录失败锁定的持续时间',                       TRUE,  TRUE),
-- security 分类
('security', 'security.captcha_enabled',     'true',    'true',    'boolean', '是否启用验证码',     '登录时是否需要图形验证码',                      TRUE,  TRUE),
('security', 'security.verify_code_expire',  '5',       '5',       'integer', '验证码有效期（分钟）','邮件/短信验证码的有效时间',                    TRUE,  TRUE),
-- storage 分类
('storage',  'storage.upload_max_size_mb',   '50',      '50',      'integer', '文件上传大小限制（MB）','单文件最大上传体积',                         TRUE,  TRUE),
('storage',  'storage.allowed_types',        'jpg,jpeg,png,gif,pdf,doc,docx,xls,xlsx,zip', NULL, 'string', '允许上传的文件类型', '逗号分隔的扩展名列表', TRUE, TRUE),
-- ai 分类
('ai',       'ai.default_model',             'gpt-4o-mini', 'gpt-4o-mini', 'string', 'AI 默认模型', '未指定模型时使用的默认 LLM',                  TRUE,  TRUE),
('ai',       'ai.token_quota_per_user',      '100000',  '100000',  'integer', '用户 Token 配额',   '每用户每月 Token 使用上限，0=不限制',           TRUE,  TRUE),
-- brand 分类
('brand',    'brand.company_name',           '学记智能', '学记智能', 'string', '公司名称',          '显示在邮件、页面标题等位置',                    TRUE,  TRUE),
('brand',    'brand.logo_url',               NULL,      NULL,      'string',  'Logo URL',          '系统 Logo 图片地址',                           TRUE,  TRUE)
ON CONFLICT (config_key) DO NOTHING;

-- ==================== 短信模板 ====================

INSERT INTO sys_sms_template (code, name, api_template_id, params, status)
VALUES ('AUTH_CODE', '登录/注册验证码', 'SMS_000000001', '["code"]', 1)
ON CONFLICT (code) DO NOTHING;

-- ==================== 消息模板 ====================

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
    subject = EXCLUDED.subject,
    content = EXCLUDED.content,
    variables = EXCLUDED.variables;

-- ==================== 用户 ====================

-- 密码均为 admin（BCrypt）
INSERT INTO sys_user (username, password, nickname, email, email_verified, status)
VALUES ('admin', '$2a$10$UyqdQK.M7V9FE4IzbbzeUeQnU.NsumDR.RCviFq4Pt04Y/F4VWLKC', '管理员', 'admin@xuejiai.com', TRUE, 0)
ON CONFLICT (username) DO NOTHING;

INSERT INTO sys_user (username, password, nickname, email, email_verified, status)
VALUES ('user1', '$2a$10$UyqdQK.M7V9FE4IzbbzeUeQnU.NsumDR.RCviFq4Pt04Y/F4VWLKC', '用户1', 'user1@xuejiai.com', TRUE, 0),
       ('user2', '$2a$10$UyqdQK.M7V9FE4IzbbzeUeQnU.NsumDR.RCviFq4Pt04Y/F4VWLKC', '用户2', 'user2@xuejiai.com', TRUE, 0)
ON CONFLICT (username) DO NOTHING;

-- ==================== 角色 ====================

INSERT INTO sys_role (code, name, description)
VALUES ('admin', '管理员', '系统管理员，拥有全部权限'),
       ('user', '普通用户', '普通用户，仅有只读权限')
ON CONFLICT (code) DO NOTHING;

-- ==================== 用户-角色关联 ====================

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.code = 'admin'
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'user1' AND r.code = 'user'
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'user2' AND r.code = 'user'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ==================== 组织（个人工作空间） ====================

INSERT INTO sys_organization (name, slug, type, owner_id, create_by)
SELECT u.nickname || '的空间', 'personal-' || u.id, 'personal', u.id, u.id
FROM sys_user u
WHERE u.deleted = FALSE
  AND NOT EXISTS (SELECT 1 FROM sys_organization o WHERE o.slug = 'personal-' || u.id);

INSERT INTO sys_org_member (org_id, user_id, role, create_by)
SELECT o.id, o.owner_id, 'owner', o.owner_id
FROM sys_organization o
WHERE o.type = 'personal'
  AND NOT EXISTS (SELECT 1 FROM sys_org_member m WHERE m.org_id = o.id AND m.user_id = o.owner_id);
