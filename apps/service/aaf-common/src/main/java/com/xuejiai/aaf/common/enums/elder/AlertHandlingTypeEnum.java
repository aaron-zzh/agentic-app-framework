package com.xuejiai.aaf.common.enums.elder;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 预警响应记录类型。 */
@Getter
@AllArgsConstructor
public enum AlertHandlingTypeEnum implements ArrayValuable<String> {
    NOTIFICATION("NOTIFICATION", "通知"),
    CLAIM("CLAIM", "认领"),
    ACKNOWLEDGEMENT("ACKNOWLEDGEMENT", "代记已知晓"),
    RESOLUTION("RESOLUTION", "处理结论");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(AlertHandlingTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
