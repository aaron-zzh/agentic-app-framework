
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



-- ==================== AIGC 创作项目字典 ====================

INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('文档类型',        'doc_type',             0, 'doc_document.doc_type 枚举值'),
('AIGC 项目类型',   'aigc_project_type',    0, NULL),
('AIGC 项目状态',   'aigc_project_status',  0, NULL),
('AIGC 内容类型',   'aigc_content_type',    0, NULL),
('AIGC 发布状态',   'aigc_publish_status',  0, NULL),
('AIGC 发布平台',   'aigc_platform',        0, NULL),
('时间轴轨道类型',  'aigc_track_type',      0, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('doc_type', '规范文档', 'spec',         1, 'default'),
('doc_type', '指南',     'guide',        2, 'info'),
('doc_type', '说明',     'explanation',  3, 'info'),
('doc_type', '教程',     'tutorial',     4, 'primary'),
('doc_type', 'AIGC 脚本','aigc_script',  5, 'warning'),
('doc_type', 'AIGC 文案','aigc_post',    6, 'success')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_project_type', '短剧',   'VIDEO_DRAMA',  1, 'primary'),
('aigc_project_type', '图文',   'IMAGE_POST',   2, 'success'),
('aigc_project_type', '短视频', 'SHORT_VIDEO',  3, 'warning'),
('aigc_project_type', '混合',   'MIXED',        4, 'default')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_project_status', '草稿',   'DRAFT',       1, 'default'),
('aigc_project_status', '进行中', 'IN_PROGRESS', 2, 'primary'),
('aigc_project_status', '已完成', 'COMPLETED',   3, 'success'),
('aigc_project_status', '已归档', 'ARCHIVED',    4, 'info')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_content_type', '富文本', 'RICH_TEXT',   1, 'info'),
('aigc_content_type', '图文',   'IMAGE_POST',  2, 'success'),
('aigc_content_type', '短视频', 'SHORT_VIDEO', 3, 'primary')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('分镜景别', 'aigc_shot_scene_type', 0, '分镜 properties.sceneType'),
('分镜素材角色', 'aigc_shot_asset_role', 0, 'aigc_shot_asset.role')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_shot_asset_role', '最终视频', 'FINAL_VIDEO', 1, 'primary'),
('aigc_shot_asset_role', '最终音频', 'FINAL_AUDIO', 2, 'success'),
('aigc_shot_asset_role', '参考图',   'REFERENCE',   3, 'default')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('内容素材角色', 'aigc_content_asset_role', 0, 'aigc_content_asset.role')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_content_asset_role', '正片',   'MAIN',     1, 'primary'),
('aigc_content_asset_role', '封面',   'COVER',    2, 'success'),
('aigc_content_asset_role', '背景音乐','BGM',     3, 'info'),
('aigc_content_asset_role', '字幕',   'SUBTITLE', 4, 'default')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_shot_scene_type', '远景', 'ELS',  1, 'default'),
('aigc_shot_scene_type', '全景', 'LS',   2, 'default'),
('aigc_shot_scene_type', '中景', 'MS',   3, 'default'),
('aigc_shot_scene_type', '近景', 'MCU',  4, 'default'),
('aigc_shot_scene_type', '特写', 'CU',   5, 'default'),
('aigc_shot_scene_type', '大特写','ECU', 6, 'default')
ON CONFLICT DO NOTHING;
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_publish_status', '草稿',   'DRAFT',      1, 'default'),
('aigc_publish_status', '审核中', 'REVIEWING',  2, 'warning'),
('aigc_publish_status', '已发布', 'PUBLISHED',  3, 'success'),
('aigc_publish_status', '发布失败', 'FAILED',   4, 'danger')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_platform', '微信公众号', 'WECHAT',       1, 'success'),
('aigc_platform', '抖音',       'DOUYIN',       2, 'default'),
('aigc_platform', '小红书',     'XIAOHONGSHU',  3, 'danger'),
('aigc_platform', 'B站',        'BILIBILI',     4, 'info'),
('aigc_platform', '视频号',     'CHANNELS',     5, 'success')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('aigc_track_type', '视频轨', 'VIDEO',    1, 'primary'),
('aigc_track_type', '音频轨', 'AUDIO',    2, 'success'),
('aigc_track_type', '字幕轨', 'SUBTITLE', 3, 'info'),
('aigc_track_type', '贴图轨', 'STICKER',  4, 'warning')
ON CONFLICT DO NOTHING;

-- ==================== Chat 会话域字典 ====================

INSERT INTO sys_dict_type (name, type, status, version, deleted, create_time, update_time) VALUES
    ('会话类型',         'conversation_type',           0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('会话状态',         'conversation_status',          0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('参与方类型',       'participant_type',             0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('参与方角色',       'participant_role',             0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('参与方离开原因',   'participant_left_reason',      0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('消息发送方类型',   'message_sender_type',          0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('消息内容类型',     'message_content_type',         0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('坐席类型',         'livechat_seat_type',           0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, status, version, deleted, create_time, update_time) VALUES
    -- 会话类型
    ('conversation_type', 'AI对话',   'AI',       1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('conversation_type', '客服会话', 'LIVECHAT',  2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('conversation_type', 'IM消息',   'IM',        3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- 会话状态
    ('conversation_status', '进行中',     'ACTIVE',   1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('conversation_status', '已归档',     'ARCHIVED', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('conversation_status', '机器人服务', 'BOT',      3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('conversation_status', '等待人工',   'WAITING',  4, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('conversation_status', '已关闭',     'CLOSED',   5, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- 参与方类型
    ('participant_type', '用户',     'HUMAN',     1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('participant_type', 'AI助理',   'ASSISTANT', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('participant_type', '智能体',   'AGENT',     3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('participant_type', '人工坐席', 'STAFF',     4, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('participant_type', '机器人',   'BOT',       5, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- 参与方角色
    ('participant_role', '发起方', 'OWNER',    1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('participant_role', '参与方', 'MEMBER',   2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('participant_role', '旁观者', 'OBSERVER', 3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- 参与方离开原因
    ('participant_left_reason', '转接',   'TRANSFER', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('participant_left_reason', '会话关闭', 'CLOSED',  2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('participant_left_reason', '主动离开', 'LEFT',    3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- 消息发送方类型
    ('message_sender_type', '用户',     'HUMAN',     1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('message_sender_type', 'AI助理',   'ASSISTANT', 2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('message_sender_type', '智能体',   'AGENT',     3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('message_sender_type', '人工坐席', 'STAFF',     4, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('message_sender_type', '机器人',   'BOT',       5, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('message_sender_type', '系统',     'SYSTEM',    6, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- 消息内容类型
    ('message_content_type', '文本',     'TEXT',         1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('message_content_type', '图片',     'IMAGE',        2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('message_content_type', '文件',     'FILE',         3, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('message_content_type', '工具调用', 'TOOL_CALL',    4, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('message_content_type', '工具结果', 'TOOL_RESULT',  5, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('message_content_type', '任务',     'TASK',         6, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('message_content_type', '系统事件', 'SYSTEM_EVENT', 7, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- 坐席类型
    ('livechat_seat_type', '人工坐席', 'HUMAN', 1, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('livechat_seat_type', 'AI坐席',   'AI',    2, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;


-- ai_model_provider_type AI 模型协议类型
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('AI 模型协议类型', 'ai_model_provider_type', 0, '决定运行时 SDK 选择')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('ai_model_provider_type', 'OpenAI 兼容', 'OPENAI_COMPAT', 1, 'primary'),
('ai_model_provider_type', 'Anthropic',   'ANTHROPIC',     2, 'success'),
('ai_model_provider_type', 'Ollama',      'OLLAMA',        3, 'info'),
('ai_model_provider_type', '阿里云百炼',  'DASHSCOPE',     4, 'warning')
ON CONFLICT DO NOTHING;


-- ==================== 联系人字典 ====================

INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('联系人类型',     'sys_contact_type',    0, 'PERSON=个人 / ORG=组织'),
('联系人来源',     'sys_contact_source',  0, '联系人数据来源'),
('联系人状态',     'sys_contact_status',  0, '联系人当前状态')
ON CONFLICT DO NOTHING;

-- sys_contact_type 联系人类型（与 ContactType 枚举一致）
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_contact_type', '个人', 'PERSON', 1, 'primary'),
('sys_contact_type', '组织', 'ORG',    2, 'default')
ON CONFLICT DO NOTHING;

-- sys_contact_source 联系人来源
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_contact_source', '注册',     'REGISTER', 1, 'success'),
('sys_contact_source', '导入',     'IMPORT',   2, 'default'),
('sys_contact_source', '渠道接入', 'CHANNEL',  3, 'primary'),
('sys_contact_source', '访客',     'VISITOR',  4, 'info')
ON CONFLICT DO NOTHING;

-- sys_contact_status 联系人状态
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('sys_contact_status', '活跃',   'ACTIVE',   1, 'success'),
('sys_contact_status', '线索',   'LEAD',     2, 'warning'),
('sys_contact_status', '访客',   'VISITOR',  3, 'info'),
('sys_contact_status', '已归档', 'ARCHIVED', 4, 'default')
ON CONFLICT DO NOTHING;


-- credit_transaction_source 积分流水来源
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('积分流水来源', 'credit_transaction_source', 0, '标记积分变动的业务来源')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('credit_transaction_source', '用户充值',     'recharge',           1,  'success'),
('credit_transaction_source', '订阅套餐',     'subscribe',          2,  'primary'),
('credit_transaction_source', '注册赠送',     'register_gift',      3,  'info'),
('credit_transaction_source', '兑换码',       'redeem_code',        4,  'info'),
('credit_transaction_source', '权益补充',     'entitlement_refill', 5,  'warning'),
('credit_transaction_source', '管理员调整',   'admin_adjust',       6,  'danger'),
('credit_transaction_source', '周期奖励',     'periodic_reward',    7,  'info'),
('credit_transaction_source', 'AI 能力消费',  'ai_consume',         8,  'default'),
('credit_transaction_source', '工具调用消费', 'tool_consume',       9,  'default'),
('credit_transaction_source', '其他',         'other',              10, 'default')
ON CONFLICT DO NOTHING;

-- credit_transaction_category 积分消费分类（AI 能力维度）
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('积分消费分类', 'credit_transaction_category', 0, '标记积分花在哪种 AI 能力，仅 SPEND 类型有意义')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('credit_transaction_category', '文本对话',     'chat',        1,  'primary'),
('credit_transaction_category', '图像生成',     'image_gen',   2,  'success'),
('credit_transaction_category', '图像编辑',     'image_edit',  3,  'success'),
('credit_transaction_category', 'OCR 识别',     'ocr',         4,  'info'),
('credit_transaction_category', '视频生成',     'video',       5,  'warning'),
('credit_transaction_category', '语音合成',     'speech_tts',  6,  'info'),
('credit_transaction_category', '语音识别',     'speech_asr',  7,  'info'),
('credit_transaction_category', '向量嵌入',     'embedding',   8,  'default'),
('credit_transaction_category', '3D 生成',      'model_3d',    9,  'default'),
('credit_transaction_category', '数字人视频',   'avatar',      10, 'default'),
('credit_transaction_category', '工具调用',     'tool',        11, 'default'),
('credit_transaction_category', '权益补充',     'entitlement', 12, 'default'),
('credit_transaction_category', '其他',         'other',       13, 'default')
ON CONFLICT DO NOTHING;


-- ai_ocr_document_type OCR 支持的证件/票据类型
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('OCR证件类型', 'ai_ocr_document_type', 0, 'Qwen-OCR KEY_INFORMATION_EXTRACTION 任务支持的常见证件与票据类型')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('ai_ocr_document_type', '中国护照',       '中国护照',       1,  'default'),
('ai_ocr_document_type', '往来港澳通行证', '往来港澳通行证', 2,  'default'),
('ai_ocr_document_type', '机动车驾驶证',   '机动车驾驶证',   3,  'default'),
('ai_ocr_document_type', '机动车行驶证',   '机动车行驶证',   4,  'default'),
('ai_ocr_document_type', '增值税普通发票', '增值税普通发票', 5,  'default'),
('ai_ocr_document_type', '火车票',         '火车票',         6,  'default'),
('ai_ocr_document_type', '12306高铁票',    '12306高铁票',    7,  'default'),
('ai_ocr_document_type', '营业执照',       '营业执照',       8,  'default'),
('ai_ocr_document_type', '社会保障卡',     '社会保障卡',     9,  'default'),
('ai_ocr_document_type', '不动产权证书',   '不动产权证书',   10, 'default')
ON CONFLICT DO NOTHING;
