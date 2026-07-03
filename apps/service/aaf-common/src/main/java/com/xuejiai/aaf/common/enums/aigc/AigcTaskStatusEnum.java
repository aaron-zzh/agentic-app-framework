package com.xuejiai.aaf.common.enums.aigc;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** AIGC 任务状态枚举，对应字典 aigc_task_status。 */
@Getter
@AllArgsConstructor
public enum AigcTaskStatusEnum implements ArrayValuable<String> {
    PENDING("PENDING", "等待中"),
    RUNNING("RUNNING", "运行中"),
    SUCCESS("SUCCESS", "成功"),
    FAIL("FAIL", "失败");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(AigcTaskStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
