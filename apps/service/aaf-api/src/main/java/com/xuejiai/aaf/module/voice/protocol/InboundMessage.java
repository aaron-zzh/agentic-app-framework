package com.xuejiai.aaf.module.voice.protocol;

import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.common.util.JsonUtils;

/** 入站文本帧：{@code type} + 其余字段。未知 {@code type} 由调用方忽略，字段缺失或类型不符时取默认值，不抛异常。 */
public record InboundMessage(String type, Map<String, Object> fields) {

    /** 解析 JSON 对象文本；非 JSON 对象或缺少 {@code type} 返回 null。 */
    public static InboundMessage parse(String json) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = JsonUtils.parseObjectQuietly(json, Map.class);
        if (map == null || !(map.get("type") instanceof String type) || type.isBlank()) {
            return null;
        }
        return new InboundMessage(type, map);
    }

    public int intOf(String key, int fallback) {
        return fields.get(key) instanceof Number number ? number.intValue() : fallback;
    }

    public String strOf(String key, String fallback) {
        return fields.get(key) instanceof String value && !value.isBlank() ? value : fallback;
    }

    public boolean boolOf(String key, boolean fallback) {
        return fields.get(key) instanceof Boolean value ? value : fallback;
    }

    /** 读取嵌套对象字段，缺失返回空 Map。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> mapOf(String key) {
        return fields.get(key) instanceof Map<?, ?> value ? (Map<String, Object>) value : Map.of();
    }

    /** 读取字符串数组字段，缺失或元素非字符串时忽略。 */
    public List<String> stringsOf(String key) {
        if (!(fields.get(key) instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }
}
