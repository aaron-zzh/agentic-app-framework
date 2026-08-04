package com.xuejiai.aaf.module.ai.aigc.brand.vo;

import tools.jackson.databind.JsonNode;

/** AIGC brand Patch JSON 解码器。 */
public final class AigcBrandPatchDecoder {

    private AigcBrandPatchDecoder() {}

    public static String text(JsonNode node) {
        if (!node.isTextual()) throw new IllegalArgumentException("字段必须是字符串");
        return node.asText();
    }

    public static Long longValue(JsonNode node) {
        if (!node.isIntegralNumber()) throw new IllegalArgumentException("字段必须是整数");
        return node.longValue();
    }
}
