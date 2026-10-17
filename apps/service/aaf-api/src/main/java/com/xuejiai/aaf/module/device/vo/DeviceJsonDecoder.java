package com.xuejiai.aaf.module.device.vo;

import com.xuejiai.aaf.framework.crud.reference.ResourceReference;

import tools.jackson.databind.JsonNode;

/** 设备 Patch 请求的严格 JSON 值解码器。 */
final class DeviceJsonDecoder {

    private DeviceJsonDecoder() {}

    static String text(JsonNode node, String fieldName) {
        if (!node.isString()) {
            throw new IllegalArgumentException(fieldName + "必须是字符串");
        }
        return node.asString();
    }

    static ResourceReference reference(JsonNode node) {
        if (!node.isObject()
                || !node.path("resource").isString()
                || !node.path("id").isIntegralNumber()) {
            throw new IllegalArgumentException("场景主体引用格式非法");
        }
        return new ResourceReference(node.path("resource").asString(), node.path("id").longValue());
    }
}
