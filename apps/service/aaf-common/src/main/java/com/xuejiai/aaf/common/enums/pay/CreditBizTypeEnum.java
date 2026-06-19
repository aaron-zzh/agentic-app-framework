package com.xuejiai.aaf.common.enums.pay;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 积分流水业务表标识枚举，对应字典 credit_biz_type。与 biz_id 组合定位具体业务记录。 */
@Getter
@AllArgsConstructor
public enum CreditBizTypeEnum implements ArrayValuable<String> {
    AIGC_TASK("AIGC_TASK", "AIGC 任务"),
    TOOL_CALL("TOOL_CALL", "工具调用"),
    ENTITLEMENT("ENTITLEMENT", "权益补充"),
    AI_USAGE("AI_USAGE", "AI 用量");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(CreditBizTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
