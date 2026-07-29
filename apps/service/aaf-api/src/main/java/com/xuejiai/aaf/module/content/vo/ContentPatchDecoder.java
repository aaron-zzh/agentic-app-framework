package com.xuejiai.aaf.module.content.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.common.util.JsonUtils;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

/**
 * Content Studio Patch JSON 解码器。
 *
 * @author AaronZZH & Kiro
 */
public final class ContentPatchDecoder {

    private ContentPatchDecoder() {}

    public static String text(JsonNode node) {
        if (!node.isTextual()) throw new IllegalArgumentException("字段必须是字符串");
        return node.asText();
    }

    public static Integer integer(JsonNode node) {
        if (!node.isIntegralNumber()) throw new IllegalArgumentException("字段必须是整数");
        return node.intValue();
    }

    public static Long longValue(JsonNode node) {
        if (!node.isIntegralNumber()) throw new IllegalArgumentException("字段必须是整数");
        return node.longValue();
    }

    public static Boolean bool(JsonNode node) {
        if (!node.isBoolean()) throw new IllegalArgumentException("字段必须是布尔值");
        return node.booleanValue();
    }

    public static BigDecimal decimal(JsonNode node) {
        if (!node.isNumber()) throw new IllegalArgumentException("字段必须是数值");
        return node.decimalValue();
    }

    public static LocalDateTime dateTime(JsonNode node) {
        return LocalDateTime.parse(text(node));
    }

    public static List<String> stringList(JsonNode node) {
        return JsonUtils.convertValue(node, new TypeReference<List<String>>() {});
    }

    public static List<Map<String, Object>> objectList(JsonNode node) {
        return JsonUtils.convertValue(node, new TypeReference<List<Map<String, Object>>>() {});
    }

    public static Map<String, Object> objectMap(JsonNode node) {
        return JsonUtils.convertValue(node, new TypeReference<Map<String, Object>>() {});
    }
}
