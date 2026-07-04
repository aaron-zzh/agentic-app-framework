package com.xuejiai.aaf.module.pay;

import com.xuejiai.aaf.common.exception.ErrorCode;

/**
 * Pay 模块错误码，使用 6_000_000 ~ 6_999_999 段。
 *
 * <p>子模块分段：
 *
 * <ul>
 *   <li>PAY_ORDER：6_000_000 ~ 6_000_999
 *   <li>REFUND：6_001_000 ~ 6_001_999
 *   <li>BIZ_ORDER：6_002_000 ~ 6_002_999
 * </ul>
 */
public interface ErrorCodeConstants {

    // ========== PAY_ORDER 模块 6_000_000 ==========
    ErrorCode PAY_ORDER_NOT_FOUND = ErrorCode.of(6_000_000, "支付单不存在");
    ErrorCode PAY_ORDER_USER_NOT_LINKED_CONTACT = ErrorCode.of(6_000_001, "用户未关联联系人，无法使用余额支付");
    ErrorCode PAY_ORDER_BALANCE_INSUFFICIENT = ErrorCode.of(6_000_002, "分销余额不足，当前余额: {0} 分");
    ErrorCode PAY_ORDER_BALANCE_DEDUCT_FAILED = ErrorCode.of(6_000_003, "余额扣减失败，请重试");
    ErrorCode PAY_ORDER_NOTIFY_UNSIGNED_FORBIDDEN = ErrorCode.of(6_000_004, "真实渠道回调须经验签，禁止未签名通知");
    ErrorCode PAY_ORDER_CHANNEL_MISMATCH = ErrorCode.of(6_000_005, "支付单渠道与请求渠道不匹配");
    ErrorCode PAY_ORDER_ALREADY_FINISHED = ErrorCode.of(6_000_006, "支付单已处理，无法重新生成跳转表单");
    ErrorCode PAY_ORDER_CHANNEL_NOT_CONFIGURED = ErrorCode.of(6_000_007, "支付渠道未启用或未配置");
    ErrorCode PAY_ORDER_CHANNEL_ERROR = ErrorCode.of(6_000_008, "支付渠道调用失败，请重试");

    // ========== REFUND 模块 6_001_000 ==========
    ErrorCode REFUND_ORDER_NOT_FOUND = ErrorCode.of(6_001_000, "退款单不存在");
    ErrorCode REFUND_AMOUNT_EXCEEDED = ErrorCode.of(6_001_001, "退款金额超过可退金额");

    // ========== BIZ_ORDER 模块 6_002_000 ==========
    ErrorCode BIZ_ORDER_NOT_FOUND = ErrorCode.of(6_002_000, "业务订单不存在");
}
