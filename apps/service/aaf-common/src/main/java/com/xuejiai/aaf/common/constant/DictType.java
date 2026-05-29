package com.xuejiai.aaf.common.constant;

/**
 * 字典类型常量。
 *
 * <p>每个内部类对应一个业务模块，常量值与 sys_dict_type.type 字段一致。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * DictUtils.getLabelByValue(DictType.Sys.USER_SEX, "1"); // "男"
 * }</pre>
 */
public final class DictType {

    private DictType() {}

    /** 系统模块 */
    public static final class Sys {

        private Sys() {}

        public static final String USER_SEX = "sys_user_sex"; // 用户性别
        public static final String COMMON_STATUS = "sys_common_status"; // 通用状态
        public static final String BOOLEAN = "sys_boolean"; // 是否
        public static final String USER_TYPE = "sys_user_type"; // 用户类型
        public static final String LOGIN_TYPE = "sys_login_type"; // 登录方式
        public static final String LOGIN_RESULT = "sys_login_result"; // 登录结果
        public static final String SMS_CHANNEL = "sys_sms_channel"; // 短信渠道
        public static final String SMS_TEMPLATE_TYPE = "sys_sms_template_type"; // 短信模板类型
        public static final String SMS_SEND_STATUS = "sys_sms_send_status"; // 短信发送状态
        public static final String NOTIFY_CHANNEL = "sys_notify_channel"; // 通知渠道
        public static final String OPERATE_TYPE = "sys_operate_type"; // 操作类型
        public static final String FILE_STORAGE = "sys_file_storage"; // 文件存储类型
        public static final String OAUTH_PROVIDER = "sys_oauth_provider"; // OAuth 提供商
        public static final String VERIFY_CODE_TYPE = "sys_verify_code_type"; // 验证码场景
        public static final String MENU_TYPE = "sys_menu_type"; // 菜单类型
        public static final String APPROVAL_OPERATION_TYPE = "approval_operation_type"; // 审批操作类型
    }

    /** 支付模块 */
    public static final class Pay {

        private Pay() {}

        public static final String CHANNEL_CODE = "pay_channel_code"; // 支付渠道
        public static final String ORDER_STATUS = "pay_order_status"; // 支付订单状态
        public static final String NOTIFY_STATUS = "pay_notify_status"; // 支付回调状态
        public static final String NOTIFY_TYPE = "pay_notify_type"; // 支付通知类型
        public static final String REFUND_STATUS = "pay_refund_status"; // 退款订单状态
        public static final String TRANSFER_TYPE = "pay_transfer_type"; // 转账类型
        public static final String TRANSFER_STATUS = "pay_transfer_status"; // 转账订单状态
        public static final String CREDIT_TRANSACTION_TYPE = "credit_transaction_type"; // 积分流水类型
        public static final String BIZ_ORDER_TYPE = "biz_order_type"; // 业务订单类型
        public static final String BIZ_ORDER_STATUS = "biz_order_status"; // 业务订单状态
        public static final String CREDIT_RULE_STATUS = "credit_rule_status"; // 积分规则状态
        public static final String PRODUCT_TYPE = "product_type"; // 订单明细产品类型
    }
}
