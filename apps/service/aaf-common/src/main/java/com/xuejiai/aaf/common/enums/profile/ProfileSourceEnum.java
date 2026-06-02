package com.xuejiai.aaf.common.enums.profile;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 画像数据来源枚举。 */
@Getter
@AllArgsConstructor
public enum ProfileSourceEnum implements ArrayValuable<String> {
    MANUAL("manual", "手动填写"),
    BEHAVIOR("behavior", "行为推断"),
    AI("ai", "AI分析"),
    DEVICE("device", "设备采集"),
    IMPORT("import", "批量导入");

    private final String code;
    private final String label;

    @Override
    public String[] array() {
        return java.util.Arrays.stream(values())
                .map(ProfileSourceEnum::getCode)
                .toArray(String[]::new);
    }
}
