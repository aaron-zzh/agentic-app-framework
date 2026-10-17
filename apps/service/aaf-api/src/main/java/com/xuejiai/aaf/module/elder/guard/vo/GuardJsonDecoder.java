package com.xuejiai.aaf.module.elder.guard.vo;

import tools.jackson.databind.JsonNode;

/** guard Patch 请求的严格 JSON 值解码器。 */
final class GuardJsonDecoder {

    private GuardJsonDecoder() {}

    static String text(JsonNode node, String fieldName) {
        if (!node.isString()) {
            throw new IllegalArgumentException(fieldName + "必须是字符串");
        }
        return node.asString();
    }
}
