package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;

/** 事实抽取允许使用的实体类型。 */
public enum ExtractedEntityType {
    PERSON,
    ORGANIZATION,
    LOCATION,
    EVENT,
    PRODUCT,
    TECHNOLOGY,
    SYSTEM,
    DOCUMENT,
    CONCEPT,
    METRIC,
    ROLE,
    OTHER;

    /** 大小写不敏感地解析模型输出，未知非空类型收敛为 OTHER。 */
    @JsonCreator
    public static ExtractedEntityType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return OTHER;
        }
    }
}
