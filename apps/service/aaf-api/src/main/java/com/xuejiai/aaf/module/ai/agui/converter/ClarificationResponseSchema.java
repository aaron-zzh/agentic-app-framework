package com.xuejiai.aaf.module.ai.agui.converter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 {@code CLARIFICATION_REQUESTED} 事件 payload 里的 {@code questions} 转换为标准 JSON Schema， 供 {@link
 * AguiEvent.Interrupt#responseSchema()} 使用（AAF-114 #11408 第二版）。
 *
 * <p>输入是 {@code DelegatedTaskCoordinator.clarificationRequestedEvent} 构造的 {@code
 * List<Map<String,Object>>}，每项含 {@code field}/{@code question}/{@code options}。 首批只覆盖 text
 * 和单选枚举两种（对齐 {@code ClarificationRequest.values: Map<String,String>} 的现有能力， 不超前设计模型尚不支持的
 * number/checkbox/multiple——那些需要先扩展 {@code ClarificationRequest.Question} 领域模型才有意义，留作后续迭代）。
 */
final class ClarificationResponseSchema {

    private ClarificationResponseSchema() {}

    /**
     * @param questions 每项含 {@code field}(String)/{@code question}(String)/{@code
     *     options}(List&lt;String&gt;)
     * @param requiredFields 必填字段名列表
     * @return 标准 JSON Schema：{@code {"type":"object","properties":{...},"required":[...]}}
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> fromQuestions(List<?> questions, List<String> requiredFields) {
        var properties = new LinkedHashMap<String, Object>();
        for (var raw : questions) {
            if (!(raw instanceof Map<?, ?> map)) continue;
            var question = (Map<String, Object>) map;
            var field = String.valueOf(question.get("field"));
            var title = String.valueOf(question.get("question"));
            var options = question.get("options");
            properties.put(field, fieldSchema(title, options));
        }
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.copyOf(requiredFields));
        return schema;
    }

    private static Map<String, Object> fieldSchema(String title, Object options) {
        var schema = new LinkedHashMap<String, Object>();
        schema.put("title", title);
        if (options instanceof List<?> list && !list.isEmpty()) {
            schema.put("type", "string");
            schema.put("enum", List.copyOf(list));
        } else {
            schema.put("type", "string");
        }
        return schema;
    }
}
