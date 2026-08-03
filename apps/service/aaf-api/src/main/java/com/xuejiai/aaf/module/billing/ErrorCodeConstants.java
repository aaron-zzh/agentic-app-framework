package com.xuejiai.aaf.module.billing;

import com.xuejiai.aaf.common.exception.ErrorCode;

/** Billing 模块错误码，使用 9_000_000 ~ 9_999_999 段。 */
public interface ErrorCodeConstants {

    // ========== SUBSCRIPTION 子模块 9_000_000 ==========
    ErrorCode SUBSCRIPTION_PLAN_NOT_FOUND = ErrorCode.of(9_000_000, "套餐不存在: {0}");
    ErrorCode SUBSCRIPTION_CURRENT_PLAN_NOT_FOUND = ErrorCode.of(9_000_001, "当前套餐不存在");
    ErrorCode SUBSCRIPTION_PAID_PLAN_REQUIRED = ErrorCode.of(9_000_002, "只能开通已启用的付费会员套餐");
    ErrorCode SUBSCRIPTION_SAME_LEVEL_RENEW_UNSUPPORTED = ErrorCode.of(9_000_003, "不支持同级会员续期");
    ErrorCode SUBSCRIPTION_UPGRADE_ONLY = ErrorCode.of(9_000_004, "只能切换至更高级会员套餐");
    ErrorCode SUBSCRIPTION_PLAN_DISABLED = ErrorCode.of(9_000_005, "套餐已禁用，无法开通");
}
