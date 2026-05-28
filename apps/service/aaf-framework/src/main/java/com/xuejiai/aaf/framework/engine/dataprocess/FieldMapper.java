package com.xuejiai.aaf.framework.engine.dataprocess;

import java.util.HashMap;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 字段映射步骤——将平台原始字段映射为统一业务字段。
 *
 * <p>支持嵌套路径（如 "author.nickname" → "author_name"）。
 */
@Component
@Order(10)
public class FieldMapper implements ProcessingStep {

    @Override
    public String name() {
        return "FieldMapper";
    }

    @Override
    public ProcessingContext execute(ProcessingContext context) {
        var mappings = context.getConfig().getFieldMappings();
        if (mappings == null || mappings.isEmpty()) {
            return context;
        }

        var mapped = context.getItems().stream().map(item -> mapItem(item, mappings)).toList();
        context.setItems(new java.util.ArrayList<>(mapped));
        return context;
    }

    private Map<String, Object> mapItem(Map<String, Object> source, Map<String, String> mappings) {
        var result = new HashMap<String, Object>();
        for (var entry : mappings.entrySet()) {
            var value = resolveNestedPath(source, entry.getKey());
            if (value != null) {
                result.put(entry.getValue(), value);
            }
        }
        // 保留未映射的原始字段（以 _raw_ 前缀）
        result.put("_raw_", source);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object resolveNestedPath(Map<String, Object> source, String path) {
        var parts = path.split("\\.");
        Object current = source;
        for (var part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
        }
        return current;
    }
}
