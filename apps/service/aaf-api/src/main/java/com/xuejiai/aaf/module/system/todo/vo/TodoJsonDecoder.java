package com.xuejiai.aaf.module.system.todo.vo;

import java.util.ArrayList;
import java.util.List;

import com.xuejiai.aaf.framework.crud.reference.ResourceReference;

import tools.jackson.databind.JsonNode;

/** Todo 请求的共享 JSON 字段解码器。 */
final class TodoJsonDecoder {

    private TodoJsonDecoder() {}

    static ResourceReference decodeReference(JsonNode node) {
        if (!node.isObject()
                || !node.path("resource").isString()
                || !node.path("id").isIntegralNumber()) {
            throw new IllegalArgumentException("来源引用格式非法");
        }
        return new ResourceReference(node.path("resource").asString(), node.path("id").longValue());
    }

    static List<Long> decodeIds(JsonNode node) {
        if (!node.isArray()) {
            throw new IllegalArgumentException("参与人必须是用户 ID 数组");
        }
        var ids = new ArrayList<Long>();
        node.forEach(
                item -> {
                    if (item == null || item.isNull() || !item.isIntegralNumber()) {
                        throw new IllegalArgumentException("参与人 ID 必须是非空整数");
                    }
                    ids.add(item.longValue());
                });
        return List.copyOf(ids);
    }
}
