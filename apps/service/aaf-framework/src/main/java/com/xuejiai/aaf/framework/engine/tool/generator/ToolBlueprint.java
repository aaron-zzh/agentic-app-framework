package com.xuejiai.aaf.framework.engine.tool.generator;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/** 工具蓝图——LLM 生成的工具定义（待确认后注册）。 */
@Slf4j
@Getter
@Setter
public class ToolBlueprint {

    private String name;
    private String description;
    private Map<String, String> parameters;
    private String code;
    private Long creatorUserId;

    /** 可见性：PRIVATE（仅创建者）/ SHARED（所有人可用） */
    private Visibility visibility = Visibility.PRIVATE;

    public enum Visibility {
        PRIVATE,
        SHARED
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 从 LLM JSON 输出解析。 */
    @SuppressWarnings("unchecked")
    public static ToolBlueprint parse(String json) {
        try {
            var cleaned = json.strip();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("```json?\\s*", "").replaceAll("```\\s*$", "").strip();
            }
            var map = MAPPER.readValue(cleaned, Map.class);
            var blueprint = new ToolBlueprint();
            blueprint.setName((String) map.get("name"));
            blueprint.setDescription((String) map.get("description"));
            blueprint.setParameters((Map<String, String>) map.get("parameters"));
            blueprint.setCode((String) map.get("code"));
            if (blueprint.getName() == null || blueprint.getCode() == null) return null;
            return blueprint;
        } catch (Exception e) {
            log.debug("ToolBlueprint 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
