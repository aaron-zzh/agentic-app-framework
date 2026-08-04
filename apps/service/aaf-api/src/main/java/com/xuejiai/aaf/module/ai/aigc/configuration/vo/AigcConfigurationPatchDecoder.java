package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.common.util.JsonUtils;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

/** AIGC 配置 Patch JSON 解码器。 */
public final class AigcConfigurationPatchDecoder {

    private AigcConfigurationPatchDecoder() {}

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

    public static List<String> stringList(JsonNode node) {
        return JsonUtils.convertValue(node, new TypeReference<List<String>>() {});
    }

    public static List<Long> longList(JsonNode node) {
        return JsonUtils.convertValue(node, new TypeReference<List<Long>>() {});
    }

    public static Map<String, Object> objectMap(JsonNode node) {
        return JsonUtils.convertValue(node, new TypeReference<Map<String, Object>>() {});
    }
}
