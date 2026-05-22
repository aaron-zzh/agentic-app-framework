
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

-- sys_sms_channel 短信渠道（与 SmsProperties.provider 值一致）
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_sms_channel', '阿里云', 'aliyun',  1, 'primary'),
('sys_sms_channel', '腾讯云', 'tencent', 2, 'info')
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

-- sys_file_storage 文件存储类型（与 StorageProperties.StorageType 枚举一致）
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_file_storage', '本地存储', 'LOCAL', 1, 'default'),
('sys_file_storage', 'S3 兼容（MinIO / 阿里云 OSS / AWS S3）', 'S3', 2, 'primary')
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

-- ==================== 验证码场景字典 ====================

INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('验证码场景', 'sys_verify_code_type', 0, '对应 SendCodeDTO.type，用于发送验证码接口')
ON CONFLICT DO NOTHING;

-- sys_verify_code_type 验证码场景（与 SendCodeDTO.type 正则约束一致）
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_verify_code_type', '注册',     'register', 1, 'primary'),
('sys_verify_code_type', '登录',     'login',    2, 'success'),
('sys_verify_code_type', '重置密码', 'reset',    3, 'warning')
ON CONFLICT DO NOTHING;
