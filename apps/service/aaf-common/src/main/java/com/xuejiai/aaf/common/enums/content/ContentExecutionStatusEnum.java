package com.xuejiai.aaf.common.enums.content;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内容执行状态。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@AllArgsConstructor
public enum ContentExecutionStatusEnum implements ArrayValuable<String> {
    PENDING("pending", "待执行"),
    RUNNING("running", "执行中"),
    SUCCEEDED("succeeded", "已成功"),
    FAILED("failed", "已失败"),
    CANCELED("canceled", "已取消");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ContentExecutionStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
