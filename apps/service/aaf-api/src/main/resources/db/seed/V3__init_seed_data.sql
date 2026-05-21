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
('ai',       'ai.default_model',             'gpt-4o-mini', 'gpt-4o-mini', 'string',  'AI 默认模型',         '未指定模型时使用的默认 LLM',           TRUE,  TRUE),
('ai',       'ai.token_quota_per_user',      '100000',      '100000',      'integer', '用户 Token 配额',     '每用户每月 Token 使用上限，0=不限制',  TRUE,  TRUE),
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


-- ==================== 字典类型 ====================

INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('用户性别',     'sys_user_sex',          0, NULL),
('通用状态',     'sys_common_status',     0, '0=正常 1=禁用'),
('是否',         'sys_boolean',           0, 'true/false 转是否'),
('用户类型',     'sys_user_type',         0, NULL),
('登录方式',     'sys_login_type',        0, NULL),
('登录结果',     'sys_login_result',      0, NULL),
('短信渠道',     'sys_sms_channel',       0, NULL),
('短信模板类型', 'sys_sms_template_type', 0, NULL),
('短信发送状态', 'sys_sms_send_status',   0, NULL),
('通知渠道',     'sys_notify_channel',    0, NULL),
('操作类型',     'sys_operate_type',      0, '操作日志的操作类型'),
('文件存储类型', 'sys_file_storage',      0, NULL),
('OAuth 提供商', 'sys_oauth_provider',    0, NULL)
ON CONFLICT DO NOTHING;

-- ==================== 字典数据 ====================

-- sys_user_sex 用户性别
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_user_sex', '男',   '1', 1, 'default'),
('sys_user_sex', '女',   '2', 2, 'success'),
('sys_user_sex', '未知', '0', 0, 'info')
ON CONFLICT DO NOTHING;

-- sys_common_status 通用状态
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_common_status', '正常', '0', 1, 'success'),
('sys_common_status', '禁用', '1', 2, 'danger')
ON CONFLICT DO NOTHING;

-- sys_boolean 是否
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_boolean', '是', 'true',  1, 'success'),
('sys_boolean', '否', 'false', 2, 'info')
ON CONFLICT DO NOTHING;

-- sys_user_type 用户类型
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_user_type', '普通用户', '1', 1, 'primary'),
('sys_user_type', '管理员',   '2', 2, 'success')
ON CONFLICT DO NOTHING;

-- sys_login_type 登录方式
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_login_type', '账号密码登录', '100', 1, 'primary'),
('sys_login_type', '邮箱验证码登录', '102', 2, 'info'),
('sys_login_type', 'OAuth 登录',   '101', 3, 'warning'),
('sys_login_type', '主动登出',     '200', 4, 'default'),
('sys_login_type', '强制登出',     '202', 5, 'danger')
ON CONFLICT DO NOTHING;

-- sys_login_result 登录结果
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_login_result', '成功',         '0',   1, 'success'),
('sys_login_result', '账号或密码错误', '10', 2, 'danger'),
('sys_login_result', '账号被禁用',   '20',  3, 'warning'),
('sys_login_result', '验证码无效',   '30',  4, 'info'),
('sys_login_result', '账号已锁定',   '40',  5, 'danger'),
('sys_login_result', '未知异常',     '100', 6, 'danger')
ON CONFLICT DO NOTHING;

-- sys_sms_channel 短信渠道
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_sms_channel', '阿里云', 'ALIYUN',  1, 'primary'),
('sys_sms_channel', '腾讯云', 'TENCENT', 2, 'info')
ON CONFLICT DO NOTHING;

-- sys_sms_template_type 短信模板类型
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_sms_template_type', '验证码', '1', 1, 'warning'),
('sys_sms_template_type', '通知',   '2', 2, 'primary'),
('sys_sms_template_type', '营销',   '3', 3, 'danger')
ON CONFLICT DO NOTHING;

-- sys_sms_send_status 短信发送状态
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_sms_send_status', '初始化',   '0',  1, 'info'),
('sys_sms_send_status', '发送成功', '10', 2, 'success'),
('sys_sms_send_status', '发送失败', '20', 3, 'danger'),
('sys_sms_send_status', '不发送',   '30', 4, 'default')
ON CONFLICT DO NOTHING;

-- sys_notify_channel 通知渠道
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_notify_channel', '站内信', 'INTERNAL', 1, 'primary'),
('sys_notify_channel', '邮件',   'EMAIL',    2, 'info'),
('sys_notify_channel', '短信',   'SMS',      3, 'warning')
ON CONFLICT DO NOTHING;

-- sys_operate_type 操作类型
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_operate_type', '其它', '0', 0, 'default'),
('sys_operate_type', '查询', '1', 1, 'info'),
('sys_operate_type', '新增', '2', 2, 'primary'),
('sys_operate_type', '修改', '3', 3, 'warning'),
('sys_operate_type', '删除', '4', 4, 'danger'),
('sys_operate_type', '导出', '5', 5, 'default'),
('sys_operate_type', '导入', '6', 6, 'default')
ON CONFLICT DO NOTHING;

-- sys_file_storage 文件存储类型
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_file_storage', '本地',   'LOCAL',  1, 'default'),
('sys_file_storage', 'MinIO',  'MINIO',  2, 'primary'),
('sys_file_storage', '阿里云 OSS', 'ALIYUN_OSS', 3, 'warning'),
('sys_file_storage', 'AWS S3', 'AWS_S3', 4, 'info')
ON CONFLICT DO NOTHING;

-- sys_oauth_provider OAuth 提供商
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_oauth_provider', 'GitHub',  'github',  1, 'default'),
('sys_oauth_provider', 'Google',  'google',  2, 'danger'),
('sys_oauth_provider', '微信',    'wechat',  3, 'success'),
('sys_oauth_provider', '钉钉',    'dingtalk', 4, 'primary')
ON CONFLICT DO NOTHING;


-- ==================== 支付 & 订单字典（参考 kids-service）====================

INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('支付渠道',     'pay_channel_code',    0, '支付渠道编码'),
('支付订单状态', 'pay_order_status',    0, NULL),
('支付回调状态', 'pay_notify_status',   0, '支付/退款回调通知状态'),
('支付通知类型', 'pay_notify_type',     0, NULL),
('退款订单状态', 'pay_refund_status',   0, NULL),
('转账类型',     'pay_transfer_type',   0, NULL),
('转账订单状态', 'pay_transfer_status', 0, NULL)
ON CONFLICT DO NOTHING;

-- pay_channel_code 支付渠道
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('pay_channel_code', '微信公众号支付',   'wx_pub',    1,  'success'),
('pay_channel_code', '微信小程序支付',   'wx_lite',   2,  'success'),
('pay_channel_code', '微信 App 支付',    'wx_app',    3,  'success'),
('pay_channel_code', '微信扫码支付',     'wx_native', 4,  'success'),
('pay_channel_code', '支付宝 PC 网站',   'alipay_pc', 10, 'primary'),
('pay_channel_code', '支付宝 Wap 网站',  'alipay_wap',11, 'primary'),
('pay_channel_code', '支付宝 App 支付',  'alipay_app',12, 'primary'),
('pay_channel_code', '支付宝扫码支付',   'alipay_qr', 14, 'primary')
ON CONFLICT DO NOTHING;

-- pay_order_status 支付订单状态
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('pay_order_status', '等待支付', '0',  1, 'info'),
('pay_order_status', '支付成功', '10', 2, 'success'),
('pay_order_status', '支付关闭', '30', 3, 'default')
ON CONFLICT DO NOTHING;

-- pay_notify_status 支付回调状态
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('pay_notify_status', '等待通知', '0',  1, 'info'),
('pay_notify_status', '通知成功', '10', 2, 'success'),
('pay_notify_status', '通知失败', '20', 3, 'danger')
ON CONFLICT DO NOTHING;

-- pay_notify_type 支付通知类型
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('pay_notify_type', '支付单', '1', 1, 'primary'),
('pay_notify_type', '退款单', '2', 2, 'danger')
ON CONFLICT DO NOTHING;

-- pay_refund_status 退款订单状态
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('pay_refund_status', '等待退款', '0',  1, 'info'),
('pay_refund_status', '退款成功', '10', 2, 'success'),
('pay_refund_status', '退款失败', '20', 3, 'danger')
ON CONFLICT DO NOTHING;

-- pay_transfer_type 转账类型
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('pay_transfer_type', '支付宝余额', '1', 1, 'primary'),
('pay_transfer_type', '微信余额',   '2', 2, 'success'),
('pay_transfer_type', '银行卡',     '3', 3, 'default'),
('pay_transfer_type', '钱包余额',   '4', 4, 'info')
ON CONFLICT DO NOTHING;

-- pay_transfer_status 转账订单状态
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('pay_transfer_status', '等待转账',   '0',  1, 'default'),
('pay_transfer_status', '转账进行中', '10', 2, 'info'),
('pay_transfer_status', '转账成功',   '20', 3, 'success'),
('pay_transfer_status', '转账失败',   '30', 4, 'warning')
ON CONFLICT DO NOTHING;
