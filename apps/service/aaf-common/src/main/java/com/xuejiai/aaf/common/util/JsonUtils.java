package com.xuejiai.aaf.common.util;

import java.util.List;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * JSON 工具类——统一 JsonMapper 实例，避免散落 new JsonMapper()。
 *
 * <p>启动时由 {@code JacksonAutoConfiguration} 调用 {@link #init} 注入 Spring 管理的 Bean，
 * 复用统一的序列化配置（JavaTimeModule 等）。
 *
 * @author AaronZZH
 */
@Slf4j
@UtilityClass
public class JsonUtils {

    private static JsonMapper jsonMapper = JsonMapper.builder().build();

    /** 由 Spring 启动时注入，复用统一配置的 JsonMapper Bean */
    public static void init(JsonMapper mapper) {
        jsonMapper = mapper;
    }

    public static String toJsonString(Object obj) {
        try {
            return jsonMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON 序列化失败: {}", obj, e);
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseObject(String text, Class<T> clazz) {
        if (text == null || text.isBlank()) return null;
        try {
            return jsonMapper.readValue(text, clazz);
        } catch (Exception e) {
            log.warn("JSON 反序列化失败: {}", text, e);
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseObject(String text, TypeReference<T> typeRef) {
        if (text == null || text.isBlank()) return null;
        try {
            return jsonMapper.readValue(text, typeRef);
        } catch (Exception e) {
            log.warn("JSON 反序列化失败: {}", text, e);
            throw new RuntimeException(e);
        }
    }

    /** 解析失败返回 null，不抛异常 */
    public static <T> T parseObjectQuietly(String text, Class<T> clazz) {
        if (text == null || text.isBlank()) return null;
        try {
            return jsonMapper.readValue(text, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> List<T> parseArray(String text, Class<T> clazz) {
        if (text == null || text.isBlank()) return List.of();
        try {
            return jsonMapper.readValue(
                    text, jsonMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            log.error("JSON 数组反序列化失败: {}", text, e);
            throw new RuntimeException(e);
        }
    }

    /** 解析 JSON 树（用于处理结构不固定的 HTTP 响应） */
    public static JsonNode readTree(String json) {
        try {
            return jsonMapper.readTree(json);
        } catch (Exception e) {
            log.error("JSON readTree 失败: {}", json, e);
            throw new RuntimeException(e);
        }
    }

    public static ObjectNode createObjectNode() {
        return jsonMapper.createObjectNode();
    }

    /** 对象类型转换（如 Map → POJO） */
    public static <T> T convertValue(Object value, Class<T> clazz) {
        return jsonMapper.convertValue(value, clazz);
    }

    /** 对象类型转换（泛型版） */
    public static <T> T convertValue(Object value, TypeReference<T> typeRef) {
        return jsonMapper.convertValue(value, typeRef);
    }
}
