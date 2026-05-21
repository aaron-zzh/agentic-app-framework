package com.xuejiai.aaf.enums.knowledge;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档处理状态枚举。
 *
 * <p>0=待处理 1=处理中 2=已完成 3=失败
 */
@Getter
@AllArgsConstructor
public enum DocumentStatusEnum implements ArrayValuable<Integer> {
    PENDING(0, "待处理"),
    PROCESSING(1, "处理中"),
    COMPLETED(2, "已完成"),
    FAILED(3, "失败");

    private final Integer code;
    private final String message;

    public static final Integer[] ARRAYS =
            Arrays.stream(values()).map(DocumentStatusEnum::getCode).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
