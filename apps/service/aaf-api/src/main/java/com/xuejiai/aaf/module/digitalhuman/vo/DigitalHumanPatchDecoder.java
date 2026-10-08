package com.xuejiai.aaf.module.digitalhuman.vo;

import java.util.List;

import com.xuejiai.aaf.common.util.JsonUtils;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

/** 数字人 Patch JSON 解码器。 */
public final class DigitalHumanPatchDecoder {

    private DigitalHumanPatchDecoder() {}

    public static String text(JsonNode node) {
        if (!node.isString()) throw new IllegalArgumentException("字段必须是字符串");
        return node.asString();
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

    public static DigitalHumanAppearance appearance(JsonNode node) {
        return object(node, DigitalHumanAppearance.class);
    }

    public static DigitalHumanVoiceConfig voiceConfig(JsonNode node) {
        return object(node, DigitalHumanVoiceConfig.class);
    }

    public static DigitalHumanModelConfig modelConfig(JsonNode node) {
        return object(node, DigitalHumanModelConfig.class);
    }

    private static <T> T object(JsonNode node, Class<T> type) {
        if (!node.isObject()) throw new IllegalArgumentException("字段必须是对象");
        return JsonUtils.convertValue(node, type);
    }
}
