package com.xuejiai.aaf.framework.engine.credit;

import com.xuejiai.aaf.common.enums.pay.CreditTransactionTypeEnum;

/**
 * 积分流水类型——直接复用 common 枚举值，保证单一来源。
 *
 * <p>值与 {@link CreditTransactionTypeEnum} 的 code 完全一致。
 */
public enum CreditTransactionType {
    EARN,
    SPEND,
    FREEZE,
    UNFREEZE,
    /** 积分批次过期清零 */
    EXPIRE;

    /** 转换为 common 枚举 */
    public CreditTransactionTypeEnum toEnum() {
        return CreditTransactionTypeEnum.valueOf(this.name());
    }

    /** 从 common 枚举转换 */
    public static CreditTransactionType from(CreditTransactionTypeEnum e) {
        return valueOf(e.getCode());
    }
}
