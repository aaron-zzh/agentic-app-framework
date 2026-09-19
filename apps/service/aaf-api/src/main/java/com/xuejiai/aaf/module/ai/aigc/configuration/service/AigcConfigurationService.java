package com.xuejiai.aaf.module.ai.aigc.configuration.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.ai.aigc.configuration.AigcConfigurationErrorCode.CONFIGURATION_INCOMPATIBLE;
import static com.xuejiai.aaf.module.ai.aigc.configuration.AigcConfigurationErrorCode.CONFIGURATION_NOT_FOUND;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintActionSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintRelationSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintSlotTemplateSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcCatalogBlueprintDefinition;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcCatalogBlueprintDefinition.AigcBlueprintRelationSpecDraft;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcConfigurationApi;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcConfigurationResolveCommand;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcDeliverableSetSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcExecutionBindingVersionRef;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcResolvedConfiguration;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcResolvedObjectSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcChannelSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectBlueprint;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectType;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcChannelSpecRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcProjectBlueprintRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcProjectTypeRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionBindingRepository;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectRelationSemantics;

import lombok.RequiredArgsConstructor;

/** 从已发布的类型化 Catalog Blueprint Definition 解析并冻结项目配置组合。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcConfigurationService implements AigcConfigurationApi {

    private static final String PUBLISHED = "published";

    private final AigcProjectTypeRepository projectTypeRepository;
    private final AigcProjectBlueprintRepository blueprintRepository;
    private final AigcChannelSpecRepository channelSpecRepository;
    private final AigcExecutionBindingRepository executionBindingRepository;
    private final AigcCatalogBlueprintCompiler blueprintCompiler;

    @Override
    public AigcResolvedConfiguration resolve(AigcConfigurationResolveCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        if (command.projectTypeCode() == null || command.projectTypeCode().isBlank()) {
            throw exception(CONFIGURATION_NOT_FOUND, "projectTypeCode");
        }
        var projectType = requireProjectTypeByCode(command.projectTypeCode());
        var blueprint = requireBlueprint(command.blueprintVersionId());
        var definition = blueprintCompiler.compile(blueprint);
        requireCompatible(projectType, blueprint, definition, command);
        var channelPool = channelPool(projectType);
        var channelIds = selectedChannelIds(command.channelSpecVersionIds(), channelPool);
        var channels = requireChannels(channelIds);
        var objects =
                resolvedObjects(
                        definition.slotTemplates(),
                        command,
                        definition.productionMode(),
                        channels,
                        definition.actions());
        var relations = relationSpecs(definition.relations(), objects);
        validateDeliverableSets(definition.slotTemplates(), definition.deliverableSets());
        var bindings = executionBindings(definition.actions());

        return new AigcResolvedConfiguration(
                projectType.getDefinitionVersion(),
                blueprint.getId(),
                blueprint.getCode(),
                definition.blueprintVersion(),
                channelIds,
                bindings,
                definition.productionMode(),
                definition.projectInitialization(),
                definition.slotTemplates(),
                objects,
                relations,
                definition.actions(),
                definition.deliverableSets(),
                definition.processPolicy());
    }

    private AigcProjectType requireProjectTypeByCode(String code) {
        return projectTypeRepository
                .findFirstByCodeAndStatusAndDeletedFalseOrderByIdDesc(code, PUBLISHED)
                .orElseThrow(() -> exception(CONFIGURATION_NOT_FOUND, "projectType:" + code));
    }

    private void requireCompatible(
            AigcProjectType projectType,
            AigcProjectBlueprint blueprint,
            AigcCatalogBlueprintDefinition definition,
            AigcConfigurationResolveCommand command) {
        if (!projectType.getCode().equals(definition.projectTypeCode())) {
            throw exception(CONFIGURATION_INCOMPATIBLE, "项目类型与蓝图不匹配");
        }
        if (command.productionMode() != null
                && !command.productionMode().isBlank()
                && !command.productionMode().equals(definition.productionMode())) {
            throw exception(CONFIGURATION_INCOMPATIBLE, "生产模式与蓝图不匹配");
        }
        if (!blueprint.getBlueprintVersion().equals(definition.blueprintVersion())) {
            throw exception(CONFIGURATION_INCOMPATIBLE, "Blueprint Definition 版本与目录记录不一致");
        }
    }

    private List<Long> channelPool(AigcProjectType projectType) {
        var defaultChannels = projectType.getDefaultChannels();
        if (defaultChannels == null || defaultChannels.isEmpty()) {
            return List.of();
        }
        return channelSpecRepository
                .findByCodeInAndStatusAndDeletedFalse(defaultChannels, PUBLISHED)
                .stream()
                .map(AigcChannelSpec::getId)
                .toList();
    }

    private List<Long> selectedChannelIds(List<Long> requested, List<Long> channelPool) {
        var channelIds = distinct(requested);
        return channelIds.isEmpty() ? List.copyOf(channelPool) : channelIds;
    }

    private AigcProjectBlueprint requireBlueprint(Long id) {
        return blueprintRepository
                .findByIdAndStatusAndDeletedFalse(id, PUBLISHED)
                .orElseThrow(() -> exception(CONFIGURATION_NOT_FOUND, "blueprint:" + id));
    }

    private List<AigcChannelSpec> requireChannels(List<Long> channelIds) {
        if (channelIds.isEmpty()) {
            return List.of();
        }
        var byId =
                channelSpecRepository
                        .findByIdInAndStatusAndDeletedFalse(channelIds, PUBLISHED)
                        .stream()
                        .collect(Collectors.toMap(AigcChannelSpec::getId, Function.identity()));
        if (byId.size() != channelIds.size()) {
            throw exception(CONFIGURATION_NOT_FOUND, "channelSpec");
        }
        return channelIds.stream().map(byId::get).toList();
    }

    private List<Long> distinct(List<Long> values) {
        return values == null ? List.of() : values.stream().distinct().toList();
    }

    private List<AigcResolvedObjectSpec> resolvedObjects(
            List<AigcBlueprintSlotTemplateSpec> templates,
            AigcConfigurationResolveCommand command,
            String productionMode,
            List<AigcChannelSpec> channels,
            List<AigcBlueprintActionSpec> actions) {
        var defaultActionByTemplate =
                actions.stream()
                        .filter(AigcBlueprintActionSpec::isDefault)
                        .collect(
                                Collectors.toMap(
                                        AigcBlueprintActionSpec::targetTemplateKey,
                                        AigcBlueprintActionSpec::actionKey));
        var templatesByKey =
                templates.stream()
                        .collect(
                                Collectors.toMap(
                                        AigcBlueprintSlotTemplateSpec::templateKey,
                                        Function.identity(),
                                        (left, ignored) -> left,
                                        LinkedHashMap::new));
        var overrideByTemplate = new LinkedHashMap<String, Integer>();
        for (var override : command.slotOverrides()) {
            if (override == null
                    || override.templateKey() == null
                    || override.requestedCount() == null
                    || override.requestedCount() < 0
                    || overrideByTemplate.putIfAbsent(
                                    override.templateKey(), override.requestedCount())
                            != null) {
                throw exception(CONFIGURATION_INCOMPATIBLE, "slotOverrides 非法或重复");
            }
        }
        if (!templatesByKey.keySet().containsAll(overrideByTemplate.keySet())) {
            throw exception(CONFIGURATION_INCOMPATIBLE, "slotOverrides 包含未知模板");
        }

        var seeds = new ArrayList<ResolvedSeed>();
        for (var template : templates) {
            var count =
                    template.countByProductionMode()
                            .getOrDefault(productionMode, template.defaultCount());
            count = overrideByTemplate.getOrDefault(template.templateKey(), count);
            count =
                    constrainCount(
                            count,
                            template,
                            channels,
                            overrideByTemplate.containsKey(template.templateKey()));
            if (!isActive(template.activationCondition(), productionMode, channels)) {
                count = 0;
            }
            if (count < 0
                    || count > template.maxCount()
                    || (count > 0 && count < template.minCount())) {
                throw exception(
                        CONFIGURATION_INCOMPATIBLE,
                        "槽位数量越界: %s=%s".formatted(template.templateKey(), count));
            }
            for (var instanceNo = 1; instanceNo <= count; instanceNo++) {
                seeds.add(
                        new ResolvedSeed(
                                template,
                                instanceNo,
                                formatPattern(
                                        template.stableKeyPattern(),
                                        instanceNo,
                                        count,
                                        "stableKey"),
                                formatPattern(
                                        template.displayNamePattern() == null
                                                ? template.templateKey()
                                                : template.displayNamePattern(),
                                        instanceNo,
                                        count,
                                        "displayName")));
            }
        }
        var stableKeys = new java.util.HashSet<String>();
        seeds.forEach(
                seed -> {
                    if (!stableKeys.add(seed.stableKey())) {
                        throw exception(CONFIGURATION_INCOMPATIBLE, "项目对象 stableKey 不唯一");
                    }
                });
        var seedsByTemplate =
                seeds.stream()
                        .collect(Collectors.groupingBy(seed -> seed.template().templateKey()));
        return seeds.stream()
                .map(
                        seed -> {
                            var parentKey =
                                    resolveParentKey(
                                            seed.template().parentTemplateKey(),
                                            seed.instanceNo(),
                                            seedsByTemplate);
                            return new AigcResolvedObjectSpec(
                                    seed.stableKey(),
                                    seed.template().templateKey(),
                                    seed.instanceNo(),
                                    seed.template().objectType(),
                                    seed.displayName(),
                                    parentKey,
                                    seed.template().orderNo() + seed.instanceNo() - 1,
                                    seed.template().defaultContractRole(),
                                    seed.template().semanticRole(),
                                    seed.template().outputContract(),
                                    seed.template().adoptionPolicy(),
                                    defaultActionByTemplate.get(seed.template().templateKey()),
                                    seed.template().userInstructionJson(),
                                    seed.template().initialPayloadJson(),
                                    seed.template().payloadSchemaJson());
                        })
                .toList();
    }

    private List<AigcBlueprintRelationSpec> relationSpecs(
            List<AigcBlueprintRelationSpecDraft> drafts, List<AigcResolvedObjectSpec> objects) {
        var stableByTemplate =
                objects.stream()
                        .collect(
                                Collectors.groupingBy(
                                        AigcResolvedObjectSpec::blueprintTemplateKey,
                                        LinkedHashMap::new,
                                        Collectors.mapping(
                                                AigcResolvedObjectSpec::stableKey,
                                                Collectors.toList())));
        var objectByStableKey =
                objects.stream()
                        .collect(
                                Collectors.toMap(
                                        AigcResolvedObjectSpec::stableKey, Function.identity()));
        var result = new ArrayList<AigcBlueprintRelationSpec>();
        for (var draft : drafts) {
            var semantics = AigcProjectRelationSemantics.require(draft.relationType());
            if (!semantics.configurationAllowed()) {
                throw exception(
                        CONFIGURATION_INCOMPATIBLE,
                        "contains 只能由 parentTemplateKey 派生，不能写入 relationSpec");
            }
            var sources = stableByTemplate.getOrDefault(draft.sourceTemplateKey(), List.of());
            var targets = stableByTemplate.getOrDefault(draft.targetTemplateKey(), List.of());
            for (var sourceKey : sources) {
                for (var targetKey : targets) {
                    var sourceObject = objectByStableKey.get(sourceKey);
                    var targetObject = objectByStableKey.get(targetKey);
                    if (sourceKey.equals(targetKey)) {
                        throw exception(
                                CONFIGURATION_INCOMPATIBLE, "关系不能指向自身: " + draft.relationType());
                    }
                    if (!semantics
                                    .allowedSourceSemanticRoles()
                                    .contains(sourceObject.semanticRole())
                            || !semantics
                                    .allowedTargetSemanticRoles()
                                    .contains(targetObject.semanticRole())) {
                        throw exception(
                                CONFIGURATION_INCOMPATIBLE,
                                "关系端点 semanticRole 不符合规范: " + draft.relationType());
                    }
                    result.add(
                            new AigcBlueprintRelationSpec(
                                    sourceKey, targetKey, draft.relationType(), "{}"));
                }
            }
        }
        validateAcyclicRelations(result);
        return List.copyOf(result);
    }

    private void validateAcyclicRelations(List<AigcBlueprintRelationSpec> relations) {
        var indegree = new LinkedHashMap<String, Integer>();
        var outgoing = new LinkedHashMap<String, List<String>>();
        for (var relation : relations) {
            if (!relation.acyclic()) {
                continue;
            }
            indegree.putIfAbsent(relation.sourceKey(), 0);
            indegree.merge(relation.targetKey(), 1, Integer::sum);
            outgoing.computeIfAbsent(relation.sourceKey(), ignored -> new ArrayList<>())
                    .add(relation.targetKey());
        }
        var queue = new java.util.ArrayDeque<String>();
        indegree.forEach(
                (key, value) -> {
                    if (value == 0) {
                        queue.add(key);
                    }
                });
        var visited = 0;
        while (!queue.isEmpty()) {
            var source = queue.removeFirst();
            visited++;
            for (var target : outgoing.getOrDefault(source, List.of())) {
                var remaining = indegree.computeIfPresent(target, (key, value) -> value - 1);
                if (remaining != null && remaining == 0) {
                    queue.addLast(target);
                }
            }
        }
        if (visited != indegree.size()) {
            throw exception(CONFIGURATION_INCOMPATIBLE, "Blueprint 关系图包含环");
        }
    }

    private void validateDeliverableSets(
            List<AigcBlueprintSlotTemplateSpec> templates,
            List<AigcDeliverableSetSpec> deliverableSets) {
        var byKey =
                templates.stream()
                        .collect(
                                Collectors.toMap(
                                        AigcBlueprintSlotTemplateSpec::templateKey,
                                        Function.identity()));
        for (var deliverableSet : deliverableSets) {
            var root = byKey.get(deliverableSet.setTemplateKey());
            if (root == null || !"COMPOSITE_MANIFEST".equals(root.outputContract())) {
                throw exception(
                        CONFIGURATION_INCOMPATIBLE,
                        "DeliverableSet 根必须存在且使用 COMPOSITE_MANIFEST: "
                                + deliverableSet.setTemplateKey());
            }
            for (var slotKey : deliverableSet.allowedSlotTemplateKeys()) {
                var slot = byKey.get(slotKey);
                if (slot == null || !Objects.equals(root.templateKey(), slot.parentTemplateKey())) {
                    throw exception(
                            CONFIGURATION_INCOMPATIBLE,
                            "DeliverableSet 槽位必须以 setTemplateKey 为直接父级: " + slotKey);
                }
            }
        }
    }

    private List<AigcExecutionBindingVersionRef> executionBindings(
            List<AigcBlueprintActionSpec> actions) {
        var result = new ArrayList<AigcExecutionBindingVersionRef>();
        for (var action : actions) {
            var binding =
                    executionBindingRepository
                            .findByActionKeyAndStatusOrderByPriorityDesc(
                                    action.actionKey(), PUBLISHED)
                            .stream()
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            exception(
                                                    CONFIGURATION_INCOMPATIBLE,
                                                    "动作缺少已发布执行绑定: " + action.actionKey()));
            result.add(new AigcExecutionBindingVersionRef(action.actionKey(), binding.getId()));
        }
        return List.copyOf(result);
    }

    private int constrainCount(
            int requested,
            AigcBlueprintSlotTemplateSpec template,
            List<AigcChannelSpec> channels,
            boolean rejectAdjustment) {
        var count = requested;
        for (var channel : channels) {
            count =
                    applyConstraint(
                            count,
                            channel.getCopyStructure(),
                            template.templateKey(),
                            rejectAdjustment);
        }
        return count;
    }

    private int applyConstraint(
            int value,
            java.util.Map<String, Object> source,
            String templateKey,
            boolean rejectAdjustment) {
        if (source == null || !(source.get("slotConstraints") instanceof List<?> constraints)) {
            return value;
        }
        var result = value;
        for (var constraint : constraints) {
            if (!(constraint instanceof java.util.Map<?, ?> map)
                    || !templateKey.equals(String.valueOf(map.get("templateKey")))) {
                continue;
            }
            if (map.get("exactCount") instanceof Number exact) {
                if (rejectAdjustment && result != exact.intValue()) {
                    throw exception(
                            CONFIGURATION_INCOMPATIBLE,
                            "slotOverrides 违反槽位硬约束: %s=%s".formatted(templateKey, value));
                }
                result = exact.intValue();
            }
            if (map.get("minCount") instanceof Number min) {
                if (rejectAdjustment && result < min.intValue()) {
                    throw exception(
                            CONFIGURATION_INCOMPATIBLE,
                            "slotOverrides 违反槽位硬约束: %s=%s".formatted(templateKey, value));
                }
                result = Math.max(result, min.intValue());
            }
            if (map.get("maxCount") instanceof Number max) {
                if (rejectAdjustment && result > max.intValue()) {
                    throw exception(
                            CONFIGURATION_INCOMPATIBLE,
                            "slotOverrides 违反槽位硬约束: %s=%s".formatted(templateKey, value));
                }
                result = Math.min(result, max.intValue());
            }
        }
        return result;
    }

    private boolean isActive(
            AigcBlueprintSlotTemplateSpec.ActivationCondition condition,
            String productionMode,
            List<AigcChannelSpec> channels) {
        if (!condition.productionModes().isEmpty()
                && !condition.productionModes().contains(productionMode)) {
            return false;
        }
        if (!condition.channelsAny().isEmpty()) {
            var channelCodes = channels.stream().map(AigcChannelSpec::getCode).toList();
            return condition.channelsAny().stream().anyMatch(channelCodes::contains);
        }
        return true;
    }

    private String resolveParentKey(
            String parentTemplateKey,
            int instanceNo,
            java.util.Map<String, List<ResolvedSeed>> seedsByTemplate) {
        if (parentTemplateKey == null) {
            return null;
        }
        var parents = seedsByTemplate.getOrDefault(parentTemplateKey, List.of());
        if (parents.isEmpty()) {
            throw exception(CONFIGURATION_INCOMPATIBLE, "父槽位模板没有具体实例: " + parentTemplateKey);
        }
        return parents.size() >= instanceNo
                ? parents.get(instanceNo - 1).stableKey()
                : parents.getFirst().stableKey();
    }

    private String formatPattern(String pattern, int instanceNo, int count, String field) {
        if (pattern.contains("{instanceNo:02d}")) {
            return pattern.replace("{instanceNo:02d}", "%02d".formatted(instanceNo));
        }
        if (pattern.contains("{instanceNo}")) {
            return pattern.replace("{instanceNo}", String.valueOf(instanceNo));
        }
        if (pattern.contains("%")) {
            try {
                return pattern.formatted(instanceNo);
            } catch (java.util.IllegalFormatException error) {
                throw exception(CONFIGURATION_INCOMPATIBLE, field + " pattern 非法");
            }
        }
        if (count > 1) {
            throw exception(CONFIGURATION_INCOMPATIBLE, field + " 重复槽位必须包含 instanceNo");
        }
        return pattern;
    }

    private record ResolvedSeed(
            AigcBlueprintSlotTemplateSpec template,
            int instanceNo,
            String stableKey,
            String displayName) {}
}
