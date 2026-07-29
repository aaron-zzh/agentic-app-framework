package com.xuejiai.aaf.common.enums.content;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内容项目对象状态。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@AllArgsConstructor
public enum ContentObjectStatusEnum implements ArrayValuable<String> {
    EMPTY("empty", "空槽位"),
    DRAFT("draft", "草稿"),
    PENDING_CONFIRM("pending_confirm", "待确认"),
    ADOPTED("adopted", "已采用"),
    BLOCKED("blocked", "已阻断"),
    DONE("done", "已完成");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ContentObjectStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
