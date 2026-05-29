package com.xuejiai.aaf.module.ai.output.domain.enums;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** AI 产出状态。 */
@Getter
@AllArgsConstructor
public enum AiOutputStatus implements ArrayValuable<Integer> {
    EFFECTIVE(0, "生效中"),
    ADJUSTED(1, "已调整"),
    REVERTED(2, "已回退");

    private final Integer status;
    private final String name;

    public static final Integer[] ARRAYS =
            Arrays.stream(values()).map(AiOutputStatus::getStatus).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
