package com.xuejiai.aaf.common.enums.pay;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 积分流水来源枚举，对应字典 credit_transaction_source。标记这笔积分变动的业务来源（从哪来/为什么发生）。 */
@Getter
@AllArgsConstructor
public enum CreditTransactionSourceEnum implements ArrayValuable<String> {
    RECHARGE("recharge", "用户充值"),
    SUBSCRIBE("subscribe", "订阅套餐发放"),
    REGISTER_GIFT("register_gift", "注册赠送"),
    REDEEM_CODE("redeem_code", "兑换码兑换"),
    ENTITLEMENT_REFILL("entitlement_refill", "权益自动补充"),
    ADMIN_ADJUST("admin_adjust", "管理员手动调整"),
    PERIODIC_REWARD("periodic_reward", "周期性奖励"),
    AI_CONSUME("ai_consume", "AI 能力消费"),
    TOOL_CONSUME("tool_consume", "工具调用消费"),
    OTHER("other", "其他");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values())
                    .map(CreditTransactionSourceEnum::getCode)
                    .toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
