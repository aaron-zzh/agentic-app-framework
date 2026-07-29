package com.xuejiai.aaf.common.enums.content;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内容项目状态。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@AllArgsConstructor
public enum ContentProjectStatusEnum implements ArrayValuable<String> {
    DRAFT("draft", "草稿"),
    IN_PROGRESS("in_progress", "进行中"),
    REVIEWING("reviewing", "审核中"),
    COMPLETED("completed", "已完成"),
    ARCHIVED("archived", "已归档");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ContentProjectStatusEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
