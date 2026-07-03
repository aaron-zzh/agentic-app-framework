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
        public static final String OPERATOR_TYPE = "sys_operator_type"; // 操作者类型，见 OperatorType
        public static final String RISK_LEVEL = "sys_risk_level"; // 风险等级，见 RiskLevel
        public static final String ROLE_CODE = "sys_role_code"; // 角色编码，见 RoleCodeEnum
        public static final String PERMISSION_ACTION =
                "sys_permission_action"; // 权限操作，见 PermissionActionEnum
        public static final String ACCESS_POLICY_EFFECT =
                "sys_access_policy_effect"; // 访问策略效果，见 AccessPolicyEffectEnum
        public static final String DATA_RULE_EFFECT =
                "sys_data_rule_effect"; // 数据规则效果，见 DataRuleEffectEnum
        public static final String OVER_LIMIT_ACTION =
                "sys_over_limit_action"; // 超限操作，见 OverLimitAction
        public static final String CONTACT_SOURCE =
                "sys_contact_source"; // 联系人来源，见 ContactSourceEnum
        public static final String CONTACT_STATUS =
                "sys_contact_status"; // 联系人状态，见 ContactStatusEnum
        public static final String CONTACT_TYPE = "sys_contact_type"; // 联系人类型，见 ContactTypeEnum
        public static final String TODO_CATEGORY = "sys_todo_category"; // 待办分类，见 TodoCategoryEnum
        public static final String TODO_STATUS = "sys_todo_status"; // 待办状态，见 TodoStatusEnum
    }

    /** AI 模块 */
    public static final class Ai {

        private Ai() {}

        public static final String OCR_DOCUMENT_TYPE = "ai_ocr_document_type"; // OCR 证件/票据类型
    }

    /** AIGC 模块 */
    public static final class Aigc {

        private Aigc() {}

        public static final String CONTENT_ASSET_ROLE =
                "aigc_content_asset_role"; // 内容素材角色，见 AigcContentAssetRoleEnum
        public static final String SHOT_ASSET_ROLE =
                "aigc_shot_asset_role"; // 镜头素材角色，见 AigcShotAssetRoleEnum
        public static final String SHOT_SCENE_TYPE =
                "aigc_shot_scene_type"; // 镜头景别类型，见 AigcShotSceneTypeEnum
        public static final String TRACK_TYPE = "aigc_track_type"; // 轨道类型，见 AigcTrackTypeEnum
        public static final String TASK_TYPE = "aigc_task_type"; // 任务类型，见 AigcTaskTypeEnum
        public static final String TASK_STATUS = "aigc_task_status"; // 任务状态，见 AigcTaskStatusEnum
    }

    /** 对话/聊天模块 */
    public static final class Chat {

        private Chat() {}

        public static final String CHANNEL_TYPE = "channel_type"; // 渠道类型，见 ChannelTypeEnum
        public static final String CHANNEL_MESSAGE_TYPE = "channel_message_type"; // 渠道消息类型（无独立枚举）
        public static final String CONVERSATION_TYPE =
                "conversation_type"; // 会话类型，见 ConversationTypeEnum
        public static final String CONVERSATION_STATUS =
                "conversation_status"; // 会话状态，见 ConversationStatusEnum
        public static final String MESSAGE_CONTENT_TYPE =
                "message_content_type"; // 消息内容类型，见 MessageContentTypeEnum
        public static final String MESSAGE_SENDER_TYPE =
                "message_sender_type"; // 消息发送者类型，见 MessageSenderTypeEnum
        public static final String PARTICIPANT_TYPE =
                "participant_type"; // 参与者类型，见 ParticipantTypeEnum
        public static final String PARTICIPANT_ROLE =
                "participant_role"; // 参与者角色，见 ParticipantRoleEnum
        public static final String PARTICIPANT_LEFT_REASON =
                "participant_left_reason"; // 参与者离开原因，见 ParticipantLeftReasonEnum
    }

    /** 在线客服模块 */
    public static final class LiveChat {

        private LiveChat() {}

        public static final String SEAT_TYPE = "livechat_seat_type"; // 坐席类型
        public static final String SEAT_STATUS = "livechat_seat_status"; // 坐席状态
        public static final String SESSION_STATUS = "livechat_session_status"; // 会话状态
        public static final String TICKET_TYPE = "livechat_ticket_type"; // 工单类型
        public static final String TICKET_STATUS = "livechat_ticket_status"; // 工单状态
        public static final String TICKET_PRIORITY = "livechat_ticket_priority"; // 工单优先级
        public static final String TRANSFER_REASON = "livechat_transfer_reason"; // 转接原因
    }

    /** 开发者模块 */
    public static final class Developer {

        private Developer() {}

        public static final String ACCOUNT_STATUS =
                "developer_account_status"; // 账号状态，见 DeveloperAccountStatusEnum
        public static final String PROXY_STATUS =
                "developer_proxy_status"; // 代理状态，见 DeveloperProxyStatusEnum
        public static final String REDEEM_CODE_STATUS =
                "developer_redeem_code_status"; // 兑换码状态，见 DeveloperRedeemCodeStatusEnum
        public static final String SUBSCRIPTION_STATUS =
                "developer_subscription_status"; // 订阅状态，见 DeveloperSubscriptionStatusEnum
    }

    /** 文档模块 */
    public static final class Doc {

        private Doc() {}

        public static final String DOC_TYPE = "doc_type"; // 文档类型，见 DocTypeEnum
    }

    /** 统计模块 */
    public static final class Stats {

        private Stats() {}

        public static final String EVENT_TYPE = "stats_event_type"; // 统计事件类型，见 UserEventTypeEnum
    }

    /** 支付模块 */
    public static final class Pay {

        private Pay() {}

        public static final String CHANNEL_CODE = "pay_channel_code"; // 支付渠道，见 PayChannelEnum
        public static final String ORDER_STATUS = "pay_order_status"; // 支付订单状态，见 PayOrderStatusEnum
        public static final String NOTIFY_STATUS =
                "pay_notify_status"; // 支付回调状态，见 PayNotifyStatusEnum
        public static final String NOTIFY_TYPE = "pay_notify_type"; // 支付通知类型，见 PayNotifyTypeEnum
        public static final String REFUND_STATUS =
                "pay_refund_status"; // 退款订单状态，见 PayRefundStatusEnum
        public static final String TRANSFER_TYPE =
                "pay_transfer_type"; // 转账类型，见 PayTransferTypeEnum
        public static final String TRANSFER_STATUS =
                "pay_transfer_status"; // 转账订单状态，见 PayTransferStatusEnum
        public static final String CREDIT_TRANSACTION_TYPE =
                "credit_transaction_type"; // 积分流水类型，见 CreditTransactionTypeEnum
        public static final String CREDIT_TRANSACTION_SOURCE =
                "credit_transaction_source"; // 积分流水来源，见 CreditTransactionSourceEnum
        public static final String CREDIT_TRANSACTION_CATEGORY =
                "credit_transaction_category"; // 积分消费分类，见 CreditTransactionCategoryEnum
        public static final String CREDIT_BIZ_TYPE =
                "credit_biz_type"; // 积分流水业务表标识，见 CreditBizTypeEnum
        public static final String BIZ_ORDER_TYPE = "biz_order_type"; // 业务订单类型，见 BizOrderTypeEnum
        public static final String BIZ_ORDER_STATUS =
                "biz_order_status"; // 业务订单状态，见 BizOrderStatusEnum
        public static final String CREDIT_RULE_STATUS =
                "credit_rule_status"; // 积分规则状态，见 CreditRuleStatusEnum
        public static final String PRODUCT_TYPE = "product_type"; // 订单明细产品类型，见 ProductTypeEnum
    }
}
