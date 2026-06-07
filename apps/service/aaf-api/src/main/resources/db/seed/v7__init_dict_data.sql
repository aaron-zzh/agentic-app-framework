
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
('pay_channel_code', '模拟支付',         'MOCK',      0,  'default'),
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

-- ==================== 积分与订单字典 ====================

-- credit_transaction_type 积分流水类型
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('积分流水类型', 'credit_transaction_type', 0, NULL),
('业务订单类型', 'biz_order_type',          0, NULL),
('业务订单状态', 'biz_order_status',        0, NULL),
('积分规则状态', 'credit_rule_status',      0, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('credit_transaction_type', '赚取',   'EARN',     1, 'success'),
('credit_transaction_type', '消费',   'SPEND',    2, 'danger'),
('credit_transaction_type', '冻结',   'FREEZE',   3, 'warning'),
('credit_transaction_type', '解冻',   'UNFREEZE', 4, 'info')
ON CONFLICT DO NOTHING;

-- biz_order_type 业务订单类型
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('biz_order_type', '充值',   'RECHARGE',     1, 'primary'),
('biz_order_type', '购买',   'PURCHASE',     2, 'success'),
('biz_order_type', '订阅',   'SUBSCRIPTION', 3, 'info')
ON CONFLICT DO NOTHING;

-- biz_order_status 业务订单状态
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('biz_order_status', '待支付', 'PENDING',   1, 'info'),
('biz_order_status', '已支付', 'PAID',      2, 'success'),
('biz_order_status', '已取消', 'CANCELLED', 3, 'default'),
('biz_order_status', '已退款', 'REFUNDED',  4, 'danger')
ON CONFLICT DO NOTHING;

-- credit_rule_status 积分规则状态
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('credit_rule_status', '启用', 'ENABLED',  1, 'success'),
('credit_rule_status', '禁用', 'DISABLED', 2, 'danger')
ON CONFLICT DO NOTHING;


-- ==================== 审批与菜单字典 ====================

INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('审批操作类型', 'approval_operation_type', 0, NULL),
('菜单类型',     'sys_menu_type',           0, NULL)
ON CONFLICT DO NOTHING;

-- approval_operation_type 审批操作类型
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('approval_operation_type', '通过',   'APPROVE',  1, 'success'),
('approval_operation_type', '拒绝',   'REJECT',   2, 'danger'),
('approval_operation_type', '委派',   'DELEGATE', 3, 'info'),
('approval_operation_type', '加签',   'ADD_SIGN', 4, 'info'),
('approval_operation_type', '转办',   'TRANSFER', 5, 'warning'),
('approval_operation_type', '撤回',   'WITHDRAW', 6, 'default'),
('approval_operation_type', '催办',   'URGE',     7, 'warning')
ON CONFLICT DO NOTHING;

-- sys_menu_type 菜单类型
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_menu_type', '分组',   'GROUP',  1, 'default'),
('sys_menu_type', '菜单',   'MENU',   2, 'primary'),
('sys_menu_type', '按钮',   'BUTTON', 3, 'info')
ON CONFLICT DO NOTHING;


-- ==================== 产品类型字典 ====================

INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('产品类型', 'product_type', 0, '订单明细关联的产品/服务类型')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('product_type', '积分套餐', 'CREDIT_PACK',   1, 'primary'),
('product_type', 'Token 套餐', 'TOKEN_PACK',  2, 'primary'),
('product_type', '订阅服务', 'SUBSCRIPTION',   3, 'info'),
('product_type', 'Agent',    'AGENT',          4, 'success'),
('product_type', '工具',     'TOOL',           5, 'success'),
('product_type', '知识库',   'KNOWLEDGE',      6, 'success')
ON CONFLICT DO NOTHING;


-- ==================== 权限功能字典 ====================

INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('系统角色码',       'sys_role_code',              0, '内置角色与演示角色'),
('权限动作',         'sys_permission_action',      0, '权限码第三段 action'),
('访问策略效果',     'sys_access_policy_effect',   0, 'ABAC 策略效果'),
('数据权限规则效果', 'sys_data_rule_effect',       0, 'L3 行级规则效果'),
('ReBAC 主体类型',   'sys_rebac_subject_type',     0, '关系元组 subject_type'),
('ReBAC 常用关系',   'sys_rebac_relation',         0, '关系元组 relation'),
('ReBAC 权限',       'sys_rebac_permission',       0, 'hasPermission 的 relationPermission'),
('操作者类型',       'sys_operator_type',          0, 'Human/AI 操作者类型'),
('风险等级',         'sys_risk_level',             0, 'AI 工具与策略风险等级'),
('越限处理动作',     'sys_over_limit_action',      0, '风险越限后的处理动作')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_role_code', '超级管理员', 'super_admin', 1, 'danger'),
('sys_role_code', '系统管理员', 'admin',       2, 'warning'),
('sys_role_code', '组织管理员', 'org_admin',   3, 'warning'),
('sys_role_code', '普通成员',   'member',      4, 'primary'),
('sys_role_code', '访客',       'guest',       5, 'info'),
('sys_role_code', 'AI 智能体',  'agent',       6, 'default'),
('sys_role_code', '销售',       'sales',       7, 'success')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_permission_action', '读取',     'read',    1, 'info'),
('sys_permission_action', '创建',     'create',  2, 'primary'),
('sys_permission_action', '更新',     'update',  3, 'warning'),
('sys_permission_action', '删除',     'delete',  4, 'danger'),
('sys_permission_action', '导出',     'export',  5, 'default'),
('sys_permission_action', '管理',     'manage',  6, 'warning'),
('sys_permission_action', '执行',     'execute', 7, 'success')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_access_policy_effect', '允许', 'ALLOW', 1, 'success'),
('sys_access_policy_effect', '拒绝', 'DENY',  2, 'danger')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_data_rule_effect', '允许', 'allow', 1, 'success'),
('sys_data_rule_effect', '拒绝', 'deny',  2, 'danger')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_rebac_subject_type', '用户',   'USER',  1, 'primary'),
('sys_rebac_subject_type', '角色',   'ROLE',  2, 'warning'),
('sys_rebac_subject_type', '组织',   'ORG',   3, 'info'),
('sys_rebac_subject_type', '团队',   'TEAM',  4, 'info'),
('sys_rebac_subject_type', '智能体', 'AGENT', 5, 'default')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_rebac_relation', '拥有者', 'OWNER',  1, 'danger'),
('sys_rebac_relation', '管理员', 'ADMIN',  2, 'warning'),
('sys_rebac_relation', '编辑者', 'EDITOR', 3, 'primary'),
('sys_rebac_relation', '查看者', 'VIEWER', 4, 'info'),
('sys_rebac_relation', '成员',   'MEMBER', 5, 'success')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_rebac_permission', '可读取', 'can_read',   1, 'info'),
('sys_rebac_permission', '可写入', 'can_write',  2, 'primary'),
('sys_rebac_permission', '可删除', 'can_delete', 3, 'danger')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_operator_type', '人类用户', 'human', 1, 'primary'),
('sys_operator_type', 'AI 助理',  'ai',    2, 'success')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_risk_level', '低风险', 'low',    1, 'success'),
('sys_risk_level', '中风险', 'medium', 2, 'warning'),
('sys_risk_level', '高风险', 'high',   3, 'danger')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_over_limit_action', '请求确认', 'ask',   1, 'warning'),
('sys_over_limit_action', '跳过操作', 'skip',  2, 'info'),
('sys_over_limit_action', '暂停任务', 'pause', 3, 'danger')
ON CONFLICT DO NOTHING;


-- ==================== 渠道 / 客服 / 统计字典 ====================

-- ============================================================
-- 字典 seed：渠道/客服/统计
-- ============================================================

INSERT INTO sys_dict_type (name, type, status, version, deleted, create_time, update_time) VALUES
    ('渠道类型', 'channel_type', 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('渠道消息类型', 'channel_message_type', 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Webhook状态', 'webhook_status', 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('客服会话状态', 'livechat_session_status', 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('坐席状态', 'livechat_seat_status', 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('转接原因', 'livechat_transfer_reason', 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('工单状态', 'livechat_ticket_status', 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('工单优先级', 'livechat_ticket_priority', 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('工单类型', 'livechat_ticket_type', 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('用户事件类型', 'stats_event_type', 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time) VALUES
    ('channel_type', '微信公众号', 'wechat_mp', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('channel_type', '微信小程序', 'wechat_mini', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('channel_type', '钉钉', 'dingtalk', 3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('channel_type', '飞书', 'feishu', 4, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('channel_type', '网页', 'web', 5, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('channel_type', 'Webhook', 'webhook', 6, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('channel_message_type', '文本', 'text', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('channel_message_type', '图片', 'image', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('channel_message_type', '语音', 'voice', 3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('channel_message_type', '事件', 'event', 4, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('channel_message_type', '模板消息', 'template', 5, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('channel_message_type', 'Markdown', 'markdown', 6, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('channel_message_type', '卡片消息', 'card', 7, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('webhook_status', '启用', 'active', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('webhook_status', '停用', 'inactive', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('webhook_status', '失败', 'failed', 3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_session_status', '机器人服务中', 'bot', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_session_status', '等待人工接入', 'waiting', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_session_status', '人工服务中', 'active', 3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_session_status', '已关闭', 'closed', 4, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_seat_status', '在线', 'online', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_seat_status', '忙碌', 'busy', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_seat_status', '离线', 'offline', 3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_transfer_reason', '技能不匹配', 'skill_mismatch', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_transfer_reason', '工作量过大', 'workload', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_transfer_reason', '用户要求', 'user_request', 3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_transfer_reason', '问题升级', 'escalation', 4, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_transfer_reason', '换班交接', 'shift_change', 5, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_ticket_status', '待处理', 'PENDING', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_ticket_status', '处理中', 'PROCESSING', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_ticket_status', '待确认', 'CONFIRMING', 3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_ticket_status', '已关闭', 'CLOSED', 4, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_ticket_priority', '低', 'LOW', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_ticket_priority', '中', 'MEDIUM', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_ticket_priority', '高', 'HIGH', 3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_ticket_priority', '紧急', 'URGENT', 4, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_ticket_type', '咨询', 'consultation', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_ticket_type', '投诉', 'complaint', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_ticket_type', '故障报告', 'bug_report', 3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_ticket_type', '功能建议', 'feature_request', 4, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_ticket_type', '退款', 'refund', 5, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_ticket_type', '其他', 'other', 6, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('stats_event_type', '页面浏览', 'page_view', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('stats_event_type', '点击', 'click', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('stats_event_type', '注册', 'register', 3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('stats_event_type', '登录', 'login', 4, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('stats_event_type', '对话', 'chat', 5, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('stats_event_type', '工具使用', 'tool_use', 6, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- ==================== 开发者商业化字典 ====================

INSERT INTO sys_dict_type (name, type, status, version, deleted, create_time, update_time) VALUES
    ('开发者账户状态', 'developer_account_status', 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('开发者订阅状态', 'developer_subscription_status', 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('开发者Token流水类型', 'developer_token_transaction_type', 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('开发者兑换码状态', 'developer_redeem_code_status', 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('开发者代理状态', 'developer_proxy_status', 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time) VALUES
    ('developer_account_status', '启用', 'ACTIVE', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('developer_account_status', '停用', 'DISABLED', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('developer_subscription_status', '有效', 'ACTIVE', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('developer_subscription_status', '已过期', 'EXPIRED', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('developer_subscription_status', '已取消', 'CANCELLED', 3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('developer_token_transaction_type', '入账', 'EARN', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('developer_token_transaction_type', '消费', 'SPEND', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('developer_redeem_code_status', '未使用', 'UNUSED', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('developer_redeem_code_status', '已兑换', 'REDEEMED', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('developer_redeem_code_status', '已过期', 'EXPIRED', 3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('developer_proxy_status', '启用', 'ACTIVE', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('developer_proxy_status', '停用', 'DISABLED', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;



-- ==================== 待办字典 ====================

INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('待办分类', 'sys_todo_category', 0, '对应 TodoCategoryEnum，用于活动流待办跟进'),
('待办状态', 'sys_todo_status',   0, '待办事项的处理状态')
ON CONFLICT DO NOTHING;

-- sys_todo_category 待办分类（与 TodoCategoryEnum 一致）
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_todo_category', '待办',   'todo',    1, 'default'),
('sys_todo_category', '电话',   'call',    2, 'primary'),
('sys_todo_category', '邮件',   'email',   3, 'info'),
('sys_todo_category', '会议',   'meeting', 4, 'success')
ON CONFLICT DO NOTHING;

-- sys_todo_status 待办状态
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_todo_status', '待处理', 'pending',  1, 'warning'),
('sys_todo_status', '已完成', 'done',     2, 'success'),
('sys_todo_status', '已忽略', 'ignored',  3, 'default')
ON CONFLICT DO NOTHING;
