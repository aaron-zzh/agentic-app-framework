package com.xuejiai.aaf.module.billing;

import com.xuejiai.aaf.common.exception.ErrorCode;

/** Billing 模块错误码，使用 9_000_000 ~ 9_999_999 段。 */
public interface ErrorCodeConstants {

    ErrorCode SUBSCRIPTION_PLAN_NOT_FOUND = ErrorCode.of(9_000_000, "套餐不存在: {0}");
    ErrorCode SUBSCRIPTION_CURRENT_PLAN_NOT_FOUND = ErrorCode.of(9_000_001, "当前套餐不存在");
    ErrorCode SUBSCRIPTION_PAID_PLAN_REQUIRED = ErrorCode.of(9_000_002, "只能开通已启用的付费会员 SKU");
    ErrorCode SUBSCRIPTION_SAME_LEVEL_RENEW_UNSUPPORTED = ErrorCode.of(9_000_003, "本期暂不支持套餐内换周期");
    ErrorCode SUBSCRIPTION_UPGRADE_ONLY = ErrorCode.of(9_000_004, "只能切换至更高级会员套餐");
    ErrorCode SUBSCRIPTION_PLAN_DISABLED = ErrorCode.of(9_000_005, "套餐已禁用，无法开通");
    ErrorCode SUBSCRIPTION_SKU_NOT_FOUND = ErrorCode.of(9_000_006, "订阅 SKU 不存在: {0}");
    ErrorCode SUBSCRIPTION_SKU_DISABLED = ErrorCode.of(9_000_007, "订阅 SKU 已禁用，无法购买");
    ErrorCode SUBSCRIPTION_RENEW_ACTIVE_REQUIRED = ErrorCode.of(9_000_008, 409, "当前状态不可续费");
    ErrorCode SUBSCRIPTION_DOWNGRADE_ENDPOINT_REQUIRED = ErrorCode.of(9_000_009, "降级应使用降级接口");
    ErrorCode SUBSCRIPTION_PENDING_PAYMENT_EXISTS = ErrorCode.of(9_000_010, 409, "已有待支付会员订单");
    ErrorCode SUBSCRIPTION_OPERATION_STATE_CHANGED = ErrorCode.of(9_000_011, 409, "订阅状态已变化，请刷新后重试");
    ErrorCode SUBSCRIPTION_INTERNAL_SKU_NOT_PURCHASABLE =
            ErrorCode.of(9_000_012, "FREE_DEFAULT 不可购买或兑换");
    ErrorCode SUBSCRIPTION_SKU_PLAN_INVALID = ErrorCode.of(9_000_013, "SKU 与套餐归属不合法");
    ErrorCode REDEEM_MEMBERSHIP_SKU_REQUIRED = ErrorCode.of(9_000_014, "会员码必须指定明确 SKU");
    ErrorCode SUBSCRIPTION_PAYMENT_COMPENSATION_PENDING =
            ErrorCode.of(9_000_015, 202, "收款已登记，等待人工补偿");
}
