package com.xuejiai.aaf.framework.engine.credit;

import com.xuejiai.aaf.common.enums.pay.CreditTransactionTypeEnum;

/**
 * 积分流水类型——引擎层直接复用 common 枚举值。
 *
 * @see CreditTransactionTypeEnum
 */
public enum CreditTransactionType {
    EARN,
    SPEND,
    FREEZE,
    UNFREEZE
}
