package com.xuejiai.aaf.module.elder.mall.vo;

import java.time.LocalDateTime;

import tools.jackson.databind.JsonNode;

/** mall 更新 DTO 的严格 JSON 值解码。 */
final class MallJsonDecoder {

    private MallJsonDecoder() {}

    static String text(JsonNode node, String field) {
        if (!node.isString()) {
            throw new IllegalArgumentException(field + "必须是字符串");
        }
        return node.asString();
    }

    static Long longValue(JsonNode node, String field) {
        if (!node.isIntegralNumber()) {
            throw new IllegalArgumentException(field + "必须是整数");
        }
        return node.asLong();
    }

    static Integer integer(JsonNode node, String field) {
        if (!node.isIntegralNumber()) {
            throw new IllegalArgumentException(field + "必须是整数");
        }
        return node.asInt();
    }

    static LocalDateTime dateTime(JsonNode node, String field) {
        try {
            return LocalDateTime.parse(text(node, field));
        } catch (RuntimeException cause) {
            throw new IllegalArgumentException(field + "必须是 ISO-8601 日期时间", cause);
        }
    }
}
