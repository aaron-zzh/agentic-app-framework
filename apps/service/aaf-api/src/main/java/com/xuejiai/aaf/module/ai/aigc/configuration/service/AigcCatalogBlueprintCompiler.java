package com.xuejiai.aaf.module.ai.aigc.configuration.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.ai.aigc.configuration.AigcConfigurationErrorCode.CONFIGURATION_INCOMPATIBLE;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintActionSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintProcessPolicy;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintSlotTemplateSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcCatalogBlueprintDefinition;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcCatalogBlueprintDefinition.AigcBlueprintRelationSpecDraft;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcDeliverableSetSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcProjectInitializationSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectBlueprint;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectRelationSemantics;

/**
 * 类型化 Catalog Blueprint Definition 编译与发布期静态校验。
 *
 * <p>把 {@link AigcProjectBlueprint} 五组 JSONB（+projectInitialization）编译为单一类型化聚合，供 {@link
 * AigcProjectBlueprintService#publish} 和后续运行解析复用，JSONB 仅作存储载体。
 */
@Component
public class AigcCatalogBlueprintCompiler {

    /** 将蓝图当前 JSONB 编译为类型化 Definition，不做发布语义校验。 */
    public AigcCatalogBlueprintDefinition compile(AigcProjectBlueprint blueprint) {
        var templates = slotTemplates(blueprint);
        var relations = relationDrafts(blueprint);
        var actions = actionSpecs(blueprint);
        var deliverableSets = deliverableSets(blueprint);
        var processPolicy = processPolicy(blueprint);
        var projectInitialization = projectInitialization(blueprint);
        return new AigcCatalogBlueprintDefinition(
                blueprint.getProjectTypeCode(),
                blueprint.getBlueprintVersion(),
                blueprint.getProductionMode(),
                projectInitialization,
                templates,
                relations,
                actions,
                deliverableSets,
                processPolicy);
    }

    /** 发布前静态校验：模板引用完整性、动作目标存在、DeliverableSet 结构、payloadSchema 与 initialPayload 一致。 */
    public void validateForPublish(AigcCatalogBlueprintDefinition definition) {
        if (definition.slotTemplates().isEmpty()) {
            throw exception(CONFIGURATION_INCOMPATIBLE, "Blueprint 必须至少定义一个槽位模板");
        }
        var templatesByKey = new LinkedHashMap<String, AigcBlueprintSlotTemplateSpec>();
        for (var template : definition.slotTemplates()) {
            requireTemplate(template);
            if (templatesByKey.putIfAbsent(template.templateKey(), template) != null) {
                throw exception(
                        CONFIGURATION_INCOMPATIBLE,
                        "Blueprint templateKey 重复: " + template.templateKey());
            }
            validatePayloadAgainstSchema(template);
        }
        validateParentTemplates(templatesByKey);
        for (var relation : definition.relations()) {
            var source = templatesByKey.get(relation.sourceTemplateKey());
            var target = templatesByKey.get(relation.targetTemplateKey());
            if (source == null
                    || target == null
                    || source.templateKey().equals(target.templateKey())) {
                throw exception(CONFIGURATION_INCOMPATIBLE, "关系引用了未知或相同模板");
            }
            var semantics = AigcProjectRelationSemantics.require(relation.relationType());
            if (!semantics.configurationAllowed()
                    || !semantics.allowedSourceSemanticRoles().contains(source.semanticRole())
                    || !semantics.allowedTargetSemanticRoles().contains(target.semanticRole())) {
                throw exception(
                        CONFIGURATION_INCOMPATIBLE, "关系语义不符合规范: " + relation.relationType());
            }
        }
        var contextKeys =
                java.util.stream.Stream.concat(
                                definition.projectInitialization().fields().stream()
                                        .map(AigcProjectInitializationSpec.FieldSpec::key),
                                definition.projectInitialization().documents().stream()
                                        .map(AigcProjectInitializationSpec.DocumentSpec::key))
                        .collect(Collectors.toSet());
        var defaultTargets = new java.util.HashSet<String>();
        for (var action : definition.actions()) {
            if (action.actionKey() == null || action.actionKey().isBlank()) {
                throw exception(CONFIGURATION_INCOMPATIBLE, "动作 actionKey 不能为空");
            }
            if (action.targetTemplateKey() != null
                    && !templatesByKey.containsKey(action.targetTemplateKey())) {
                throw exception(
                        CONFIGURATION_INCOMPATIBLE,
                        "动作 targetTemplateKey 指向未知模板: " + action.targetTemplateKey());
            }
            if (!templatesByKey.keySet().containsAll(action.dependencyTemplateKeys())) {
                throw exception(CONFIGURATION_INCOMPATIBLE, "动作依赖引用了未知模板: " + action.actionKey());
            }
            if (!contextKeys.containsAll(action.requiredContextKeys())) {
                throw exception(
                        CONFIGURATION_INCOMPATIBLE, "动作上下文引用未被初始化定义声明: " + action.actionKey());
            }
            if (action.isDefault()
                    && (action.targetTemplateKey() == null
                            || !defaultTargets.add(action.targetTemplateKey()))) {
                throw exception(CONFIGURATION_INCOMPATIBLE, "同一模板最多只能定义一个默认动作");
            }
        }
        for (var deliverableSet : definition.deliverableSets()) {
            var root = templatesByKey.get(deliverableSet.setTemplateKey());
            if (root == null || !"COMPOSITE_MANIFEST".equals(root.outputContract())) {
                throw exception(
                        CONFIGURATION_INCOMPATIBLE,
                        "DeliverableSet 根必须存在且使用 COMPOSITE_MANIFEST: "
                                + deliverableSet.setTemplateKey());
            }
            for (var slotTemplateKey : deliverableSet.allowedSlotTemplateKeys()) {
                var slot = templatesByKey.get(slotTemplateKey);
                if (slot == null || !root.templateKey().equals(slot.parentTemplateKey())) {
                    throw exception(
                            CONFIGURATION_INCOMPATIBLE,
                            "DeliverableSet 槽位必须以根模板为直接父级: " + slotTemplateKey);
                }
            }
        }
    }

    private void requireTemplate(AigcBlueprintSlotTemplateSpec template) {
        if (template.templateKey() == null
                || template.templateKey().isBlank()
                || template.stableKeyPattern() == null
                || template.stableKeyPattern().isBlank()
                || template.objectType() == null
                || template.objectType().isBlank()) {
            throw exception(
                    CONFIGURATION_INCOMPATIBLE, "槽位模板缺少 templateKey/stableKeyPattern/objectType");
        }
        if (template.defaultCount() < 0
                || template.minCount() < 0
                || template.maxCount() < template.minCount()
                || template.defaultCount() > template.maxCount()) {
            throw exception(CONFIGURATION_INCOMPATIBLE, "槽位模板数量约束非法: " + template.templateKey());
        }
        if (!java.util.Set.of(
                        "STRUCTURE", "WORKING_ARTIFACT", "DELIVERABLE", "EVIDENCE", "REFERENCE")
                .contains(template.semanticRole())) {
            throw exception(
                    CONFIGURATION_INCOMPATIBLE, "槽位模板 semanticRole 非法: " + template.templateKey());
        }
        if (!java.util.Set.of(
                        "INLINE_OBJECT_VERSION",
                        "DOCUMENT_VERSION",
                        "MEDIA_VERSION",
                        "COMPOSITE_MANIFEST",
                        "REFERENCE_ONLY")
                .contains(template.outputContract())) {
            throw exception(
                    CONFIGURATION_INCOMPATIBLE,
                    "槽位模板 outputContract 非法: " + template.templateKey());
        }
        if (template.countByProductionMode().values().stream()
                .anyMatch(count -> count == null || count < 0)) {
            throw exception(CONFIGURATION_INCOMPATIBLE, "槽位模板动态数量非法: " + template.templateKey());
        }
    }

    private void validateParentTemplates(
            Map<String, AigcBlueprintSlotTemplateSpec> templatesByKey) {
        for (var template : templatesByKey.values()) {
            var visited = new java.util.HashSet<String>();
            var current = template;
            while (current.parentTemplateKey() != null) {
                if (!visited.add(current.templateKey())) {
                    throw exception(CONFIGURATION_INCOMPATIBLE, "槽位模板父级存在环");
                }
                current = templatesByKey.get(current.parentTemplateKey());
                if (current == null) {
                    throw exception(
                            CONFIGURATION_INCOMPATIBLE,
                            "槽位模板 parentTemplateKey 指向未知模板: " + template.parentTemplateKey());
                }
            }
        }
    }

    private void validatePayloadAgainstSchema(AigcBlueprintSlotTemplateSpec template) {
        if (template.payloadSchemaJson() == null || template.initialPayloadJson() == null) {
            return;
        }
        var schema = parseMap(template.payloadSchemaJson());
        var payload = parseMap(template.initialPayloadJson());
        if (!(schema.get("requiredKeys") instanceof List<?> requiredKeys)) {
            return;
        }
        for (var key : requiredKeys) {
            if (!payload.containsKey(String.valueOf(key))) {
                throw exception(
                        CONFIGURATION_INCOMPATIBLE,
                        "initialPayload 缺少 payloadSchema 要求字段: %s.%s"
                                .formatted(template.templateKey(), key));
            }
        }
    }

    private List<AigcBlueprintSlotTemplateSpec> slotTemplates(AigcProjectBlueprint blueprint) {
        return mapList(blueprint.getSlotTemplateSpec(), "slotTemplates").stream()
                .map(
                        item ->
                                new AigcBlueprintSlotTemplateSpec(
                                        text(item, "templateKey"),
                                        text(item, "stableKeyPattern"),
                                        text(item, "objectType"),
                                        text(item, "displayNamePattern"),
                                        text(item, "description"),
                                        text(item, "parentTemplateKey"),
                                        integer(item.get("orderNo"), 0),
                                        integer(item.get("defaultCount"), 1),
                                        integer(item.get("minCount"), 0),
                                        integer(item.get("maxCount"), 1),
                                        bool(item.get("userAddable")),
                                        bool(item.get("userRemovable")),
                                        defaultText(item, "defaultContractRole", "REQUIRED"),
                                        text(item, "semanticRole"),
                                        text(item, "outputContract"),
                                        defaultText(item, "adoptionPolicy", "SINGLE_ADOPTED"),
                                        countByProductionMode(item.get("countByProductionMode")),
                                        activationCondition(item.get("activationCondition")),
                                        json(item.get("userInstruction")),
                                        json(item.get("initialPayload")),
                                        json(item.get("payloadSchema"))))
                .toList();
    }

    private List<AigcBlueprintRelationSpecDraft> relationDrafts(AigcProjectBlueprint blueprint) {
        return mapList(blueprint.getRelationSpec(), "relations").stream()
                .map(
                        item ->
                                new AigcBlueprintRelationSpecDraft(
                                        text(item, "sourceTemplateKey"),
                                        text(item, "targetTemplateKey"),
                                        text(item, "type")))
                .toList();
    }

    private List<AigcBlueprintActionSpec> actionSpecs(AigcProjectBlueprint blueprint) {
        return mapList(blueprint.getActionSpec(), "actions").stream()
                .map(
                        item ->
                                new AigcBlueprintActionSpec(
                                        text(item, "actionKey"),
                                        text(item, "targetTemplateKey"),
                                        stringList(item.get("dependencyTemplateKeys")),
                                        stringList(item.get("requiredContextKeys")),
                                        defaultText(item, "confirmationPolicy", "NONE"),
                                        bool(item.get("isDefault")),
                                        json(item.get("inputPreset"))))
                .toList();
    }

    private List<AigcDeliverableSetSpec> deliverableSets(AigcProjectBlueprint blueprint) {
        return mapList(blueprint.getDeliverableSpec(), "sets").stream()
                .map(
                        item ->
                                new AigcDeliverableSetSpec(
                                        text(item, "setTemplateKey"),
                                        stringList(item.get("allowedSlotTemplateKeys")),
                                        stringList(item.get("allowedCustomObjectTypes")),
                                        defaultText(
                                                item, "defaultUserAddedContractRole", "OPTIONAL"),
                                        defaultText(item, "completionMode", "ALL_REQUIRED"),
                                        defaultText(item, "reviewMode", "PROJECT"),
                                        defaultText(item, "publicationPolicy", "OPTIONAL")))
                .toList();
    }

    private AigcBlueprintProcessPolicy processPolicy(AigcProjectBlueprint blueprint) {
        var policy = blueprint.getProcessPolicy();
        return new AigcBlueprintProcessPolicy(
                policy != null && bool(policy.get("reviewRequired")),
                policy == null ? "OPTIONAL" : defaultText(policy, "publicationPolicy", "OPTIONAL"),
                policy == null ? List.of() : stringList(policy.get("autoActionKeys")),
                policy == null ? List.of() : stringList(policy.get("confirmationGateKeys")));
    }

    private AigcProjectInitializationSpec projectInitialization(AigcProjectBlueprint blueprint) {
        var value = blueprint.getProjectInitialization();
        if (value == null || value.isEmpty()) {
            return AigcProjectInitializationSpec.empty();
        }
        try {
            return JsonUtils.parseObject(
                    JsonUtils.toJsonString(value), AigcProjectInitializationSpec.class);
        } catch (IllegalArgumentException error) {
            throw exception(
                    CONFIGURATION_INCOMPATIBLE,
                    "Blueprint projectInitialization 非法: " + error.getMessage());
        }
    }

    private List<Map<String, Object>> mapList(Map<String, Object> source, String key) {
        if (source == null || !(source.get(key) instanceof List<?> values)) {
            return List.of();
        }
        var result = new ArrayList<Map<String, Object>>();
        for (var value : values) {
            if (!(value instanceof Map<?, ?> raw)) {
                throw exception(CONFIGURATION_INCOMPATIBLE, key + " 必须是对象数组");
            }
            var item = new LinkedHashMap<String, Object>();
            raw.forEach((itemKey, itemValue) -> item.put(String.valueOf(itemKey), itemValue));
            result.add(java.util.Collections.unmodifiableMap(item));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return JsonUtils.parseObject(
                json, new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    private String text(Map<String, Object> source, String key) {
        var value = source.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String defaultText(Map<String, Object> source, String key, String fallback) {
        var value = text(source, key);
        return value == null ? fallback : value;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private Map<String, Integer> countByProductionMode(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        var counts = new LinkedHashMap<String, Integer>();
        raw.forEach(
                (mode, count) -> {
                    if (!(count instanceof Number number)) {
                        throw exception(
                                CONFIGURATION_INCOMPATIBLE, "countByProductionMode 必须使用整数值");
                    }
                    counts.put(String.valueOf(mode), number.intValue());
                });
        return Map.copyOf(counts);
    }

    private AigcBlueprintSlotTemplateSpec.ActivationCondition activationCondition(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return AigcBlueprintSlotTemplateSpec.ActivationCondition.empty();
        }
        return new AigcBlueprintSlotTemplateSpec.ActivationCondition(
                stringList(raw.get("productionModes")), stringList(raw.get("channelsAny")));
    }

    private String json(Object value) {
        return value == null ? null : JsonUtils.toJsonString(value);
    }

    private boolean bool(Object value) {
        return Boolean.TRUE.equals(value);
    }

    private Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private int integer(Object value, int fallback) {
        var parsed = integer(value);
        return parsed == null ? fallback : parsed;
    }
}
