package com.xuejiai.aaf.module.elder.profile.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;

/** 长者中心 Patch 字段 JSON 解码器。 */
final class ProfileJsonDecoder {

    private ProfileJsonDecoder() {}

    static String text(JsonNode node, String field) {
        if (!node.isString()) {
            throw new IllegalArgumentException(field + " 必须是字符串");
        }
        return node.asString();
    }

    static Long longValue(JsonNode node, String field) {
        if (!node.isIntegralNumber()) {
            throw new IllegalArgumentException(field + " 必须是整数");
        }
        return node.longValue();
    }

    static Integer integer(JsonNode node, String field) {
        if (!node.isIntegralNumber()) {
            throw new IllegalArgumentException(field + " 必须是整数");
        }
        return node.intValue();
    }

    static Boolean bool(JsonNode node, String field) {
        if (!node.isBoolean()) {
            throw new IllegalArgumentException(field + " 必须是布尔值");
        }
        return node.booleanValue();
    }

    static LocalDate date(JsonNode node, String field) {
        return LocalDate.parse(text(node, field));
    }

    static LocalDateTime dateTime(JsonNode node, String field) {
        return LocalDateTime.parse(text(node, field));
    }

    static Set<String> textSet(JsonNode node, String field) {
        if (!node.isArray()) {
            throw new IllegalArgumentException(field + " 必须是字符串数组");
        }
        var values = new LinkedHashSet<String>();
        node.forEach(
                item -> {
                    if (item == null || item.isNull() || !item.isString()) {
                        throw new IllegalArgumentException(field + " 只能包含字符串");
                    }
                    values.add(item.asString());
                });
        return Set.copyOf(values);
    }

    static List<String> textList(JsonNode node, String field) {
        if (!node.isArray()) {
            throw new IllegalArgumentException(field + " 必须是字符串数组");
        }
        var values = new ArrayList<String>();
        node.forEach(
                item -> {
                    if (item == null || item.isNull() || !item.isString()) {
                        throw new IllegalArgumentException(field + " 只能包含字符串");
                    }
                    values.add(item.asString());
                });
        return List.copyOf(values);
    }
}
