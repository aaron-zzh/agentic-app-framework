package com.xuejiai.aaf.module.ai.aigc.configuration.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Blueprint 项目初始化配置。 */
public record AigcProjectInitializationSpec(List<FieldSpec> fields, List<DocumentSpec> documents) {

    private static final Set<String> FIELD_KEYS =
            Set.of(
                    "description",
                    "background",
                    "targetAudience",
                    "requirements",
                    "constraints",
                    "keyFacts",
                    "keyMessages",
                    "styleGuidance",
                    "successCriteria",
                    "deliveryRequirements",
                    "locale",
                    "additionalContext");

    public AigcProjectInitializationSpec {
        fields = fields == null ? List.of() : List.copyOf(fields);
        documents = documents == null ? List.of() : List.copyOf(documents);
        var keys = new java.util.HashSet<String>();
        for (var field : fields) {
            if (field == null || !FIELD_KEYS.contains(field.key()) || !keys.add(field.key())) {
                throw new IllegalArgumentException("projectInitialization.fields 包含未知或重复字段");
            }
        }
    }

    public static AigcProjectInitializationSpec empty() {
        return new AigcProjectInitializationSpec(List.of(), List.of());
    }

    public static Set<String> supportedFieldKeys() {
        return FIELD_KEYS;
    }

    public record FieldSpec(
            String key,
            String label,
            String description,
            String valueType,
            boolean required,
            boolean aiAssist,
            boolean affectsStructure,
            Integer orderNo,
            Map<String, Object> validation) {

        public FieldSpec {
            if (key == null || key.isBlank() || label == null || label.isBlank()) {
                throw new IllegalArgumentException("项目初始化字段 key/label 不能为空");
            }
            valueType = valueType == null || valueType.isBlank() ? "TEXT" : valueType;
            orderNo = orderNo == null ? 0 : orderNo;
            validation = validation == null ? Map.of() : Map.copyOf(validation);
        }
    }

    public record DocumentSpec(
            String key,
            String role,
            String label,
            String description,
            String referenceType,
            boolean required,
            Integer minCount,
            Integer maxCount,
            boolean aiAssist,
            ContextPolicy contextPolicy,
            Integer orderNo) {

        public DocumentSpec {
            if (key == null
                    || key.isBlank()
                    || role == null
                    || role.isBlank()
                    || label == null
                    || label.isBlank()) {
                throw new IllegalArgumentException("项目初始化文档 key/role/label 不能为空");
            }
            referenceType =
                    referenceType == null || referenceType.isBlank()
                            ? "EXTERNAL_REFERENCE"
                            : referenceType;
            contextPolicy = contextPolicy == null ? ContextPolicy.auto() : contextPolicy;
            minCount = minCount == null ? 0 : minCount;
            maxCount = maxCount == null ? 1 : maxCount;
            orderNo = orderNo == null ? 0 : orderNo;
            if (minCount < 0 || maxCount < minCount) {
                throw new IllegalArgumentException("项目初始化文档数量范围非法");
            }
        }

        /** Blueprint 文档字段的默认上下文策略；项目引用冻结其实际 mode。 */
        public record ContextPolicy(String mode, Integer priority, Integer maxTokens) {

            public ContextPolicy {
                mode = mode == null || mode.isBlank() ? "AUTO" : mode.trim().toUpperCase();
                if (!java.util.Set.of("FULL_TEXT", "SUMMARY", "RAG", "AUTO", "NONE")
                        .contains(mode)) {
                    throw new IllegalArgumentException("项目初始化文档 contextPolicy.mode 非法: " + mode);
                }
                priority = priority == null ? 0 : priority;
                maxTokens = maxTokens == null ? 0 : maxTokens;
                if (priority < 0 || maxTokens < 0) {
                    throw new IllegalArgumentException("项目初始化文档 contextPolicy 数值非法");
                }
            }

            public static ContextPolicy auto() {
                return new ContextPolicy("AUTO", 0, 0);
            }
        }
    }
}
