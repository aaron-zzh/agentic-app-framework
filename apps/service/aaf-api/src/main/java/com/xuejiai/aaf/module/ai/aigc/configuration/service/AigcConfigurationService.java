package com.xuejiai.aaf.module.ai.aigc.configuration.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.ai.aigc.configuration.AigcConfigurationErrorCode.CONFIGURATION_INCOMPATIBLE;
import static com.xuejiai.aaf.module.ai.aigc.configuration.AigcConfigurationErrorCode.CONFIGURATION_NOT_FOUND;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintActionSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintProcessPolicy;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintRelationSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintSlotTemplateSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcConfigurationApi;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcConfigurationResolveCommand;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcDeliverableSetSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcExecutionBindingVersionRef;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcResolvedConfiguration;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcResolvedObjectSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcChannelSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcDomainExtension;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectBlueprint;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectType;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectTypePackage;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcChannelSpecRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcDomainExtensionRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcProjectBlueprintRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcProjectTypePackageRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcProjectTypeRepository;

import lombok.RequiredArgsConstructor;

/** 从已发布兼容包解析并固定项目配置组合。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcConfigurationService implements AigcConfigurationApi {

    private static final String PUBLISHED = "published";

    private final AigcProjectTypePackageRepository packageRepository;
    private final AigcProjectTypeRepository projectTypeRepository;
    private final AigcProjectBlueprintRepository blueprintRepository;
    private final AigcDomainExtensionRepository domainExtensionRepository;
    private final AigcChannelSpecRepository channelSpecRepository;

    @Override
    public AigcResolvedConfiguration resolve(AigcConfigurationResolveCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        var packageEntity = requireCompatiblePackage(command);
        var projectType = requireProjectType(packageEntity.getProjectTypeId());
        var blueprint = requireBlueprint(packageEntity.getBlueprintId());
        var domainExtension = requireDomainExtension(packageEntity.getDomainExtensionId());
        var channelIds = selectedChannelIds(command.channelSpecVersionIds(), packageEntity);
        var channels = requireChannels(channelIds);
        var templates = slotTemplates(blueprint);
        var objects =
                resolvedObjects(
                        blueprint,
                        templates,
                        command,
                        packageEntity.getProductionMode(),
                        domainExtension,
                        channels);
        var relations = relationSpecs(blueprint, objects);
        var actions = actionSpecs(blueprint);
        var deliverableSets = deliverableSets(blueprint);
        var processPolicy = processPolicy(blueprint);
        var bindings = executionBindings(packageEntity);

        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("package", packageSnapshot(packageEntity));
        snapshot.put("projectType", projectTypeSnapshot(projectType));
        snapshot.put("blueprint", blueprintSnapshot(blueprint));
        snapshot.put(
                "domainExtension",
                domainExtension == null ? null : domainExtensionSnapshot(domainExtension));
        snapshot.put("channels", channels.stream().map(this::channelSnapshot).toList());
        snapshot.put("executionBindings", bindings);
        snapshot.put("productionMode", packageEntity.getProductionMode());
        snapshot.put("budgetTier", command.budgetTier());
        snapshot.put("qualityTier", command.qualityTier());
        snapshot.put("slotOverrides", command.slotOverrides());
        snapshot.put("slotTemplates", templates);
        snapshot.put("resolvedObjects", objects);
        snapshot.put("relations", relations);
        snapshot.put("actions", actions);
        snapshot.put("deliverableSets", deliverableSets);
        snapshot.put("processPolicy", processPolicy);

        return new AigcResolvedConfiguration(
                packageEntity.getId(),
                packageEntity.getPackageVersion(),
                projectType.getId(),
                projectType.getDefinitionVersion(),
                blueprint.getId(),
                blueprint.getCode(),
                blueprint.getBlueprintVersion(),
                domainExtension == null ? null : domainExtension.getId(),
                domainExtension == null ? null : domainExtension.getCode(),
                domainExtension == null ? null : domainExtension.getExtensionVersion(),
                channelIds,
                bindings,
                packageEntity.getProductionMode(),
                command.budgetTier(),
                command.qualityTier(),
                templates,
                objects,
                relations,
                actions,
                deliverableSets,
                processPolicy,
                JsonUtils.toJsonString(snapshot));
    }

    private AigcProjectTypePackage requireCompatiblePackage(
            AigcConfigurationResolveCommand command) {
        if (command.projectTypeCode() == null || command.projectTypeCode().isBlank()) {
            throw exception(CONFIGURATION_NOT_FOUND, "projectTypeCode");
        }
        var requestedChannels = distinct(command.channelSpecVersionIds());
        return packageRepository
                .findPublishedByProjectTypeCode(command.projectTypeCode(), PUBLISHED)
                .stream()
                .filter(value -> compatible(value, command, requestedChannels))
                .findFirst()
                .orElseThrow(() -> exception(CONFIGURATION_INCOMPATIBLE, "不存在匹配请求的已发布项目类型兼容包"));
    }

    private boolean compatible(
            AigcProjectTypePackage packageEntity,
            AigcConfigurationResolveCommand command,
            List<Long> requestedChannels) {
        if (packageEntity.getCompatibilityResult() == null
                || !Boolean.TRUE.equals(packageEntity.getCompatibilityResult().get("compatible"))) {
            return false;
        }
        if (command.productionMode() != null
                && !command.productionMode().isBlank()
                && !command.productionMode().equals(packageEntity.getProductionMode())) {
            return false;
        }
        if (command.blueprintVersionId() != null
                && !command.blueprintVersionId().equals(packageEntity.getBlueprintId())) {
            return false;
        }
        if (!Objects.equals(
                command.domainExtensionVersionId(), packageEntity.getDomainExtensionId())) {
            return false;
        }
        return packageEntity.getChannelSpecIds().containsAll(requestedChannels);
    }

    private List<Long> selectedChannelIds(
            List<Long> requested, AigcProjectTypePackage packageEntity) {
        var channelIds = distinct(requested);
        return channelIds.isEmpty() ? List.copyOf(packageEntity.getChannelSpecIds()) : channelIds;
    }

    private AigcProjectType requireProjectType(Long id) {
        return projectTypeRepository
                .findByIdAndStatusAndDeletedFalse(id, PUBLISHED)
                .orElseThrow(() -> exception(CONFIGURATION_NOT_FOUND, "projectType:" + id));
    }

    private AigcProjectBlueprint requireBlueprint(Long id) {
        return blueprintRepository
                .findByIdAndStatusAndDeletedFalse(id, PUBLISHED)
                .orElseThrow(() -> exception(CONFIGURATION_NOT_FOUND, "blueprint:" + id));
    }

    private AigcDomainExtension requireDomainExtension(Long id) {
        if (id == null) return null;
        return domainExtensionRepository
                .findByIdAndStatusAndDeletedFalse(id, PUBLISHED)
                .orElseThrow(() -> exception(CONFIGURATION_NOT_FOUND, "domainExtension:" + id));
    }

    private List<AigcChannelSpec> requireChannels(List<Long> channelIds) {
        if (channelIds.isEmpty()) return List.of();
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

    private List<AigcBlueprintSlotTemplateSpec> slotTemplates(AigcProjectBlueprint blueprint) {
        return mapList(blueprint.getSlotTemplateSpec(), "slotTemplates").stream()
                .map(
                        item -> {
                            var templateKey = text(item, "templateKey");
                            var stableKeyPattern = text(item, "stableKeyPattern");
                            var objectType = text(item, "objectType");
                            if (templateKey == null || stableKeyPattern == null || objectType == null) {
                                throw exception(
                                        CONFIGURATION_INCOMPATIBLE,
                                        "Blueprint 槽位模板缺少 templateKey/stableKeyPattern/objectType");
                            }
                            return new AigcBlueprintSlotTemplateSpec(
                                    templateKey,
                                    stableKeyPattern,
                                    objectType,
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
                                    defaultText(item, "adoptionPolicy", "SINGLE_ADOPTED"),
                                    json(item.get("activationCondition")),
                                    json(item.get("userInstruction")),
                                    JsonUtils.toJsonString(item));
                        })
                .toList();
    }

    private List<AigcResolvedObjectSpec> resolvedObjects(
            AigcProjectBlueprint blueprint,
            List<AigcBlueprintSlotTemplateSpec> templates,
            AigcConfigurationResolveCommand command,
            String productionMode,
            AigcDomainExtension domainExtension,
            List<AigcChannelSpec> channels) {
        var rawByTemplate =
                mapList(blueprint.getSlotTemplateSpec(), "slotTemplates").stream()
                        .collect(
                                Collectors.toMap(
                                        item -> text(item, "templateKey"),
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
        if (!rawByTemplate.keySet().containsAll(overrideByTemplate.keySet())) {
            throw exception(CONFIGURATION_INCOMPATIBLE, "slotOverrides 包含未知模板");
        }

        var seeds = new ArrayList<ResolvedSeed>();
        for (var template : templates) {
            var raw = rawByTemplate.get(template.templateKey());
            var count = template.defaultCount();
            count = tierCount(raw, "countByProductionMode", productionMode, count);
            count = tierCount(raw, "countByBudgetTier", command.budgetTier(), count);
            count = tierCount(raw, "countByQualityTier", command.qualityTier(), count);
            count = overrideByTemplate.getOrDefault(template.templateKey(), count);
            count =
                    constrainCount(
                            count,
                            template,
                            domainExtension,
                            channels,
                            overrideByTemplate.containsKey(template.templateKey()));
            if (!isActive(
                    raw.get("activationCondition"), command, productionMode, channels)) {
                count = 0;
            }
            if (count < 0 || count > template.maxCount() || (count > 0 && count < template.minCount())) {
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
                                        template.stableKeyPattern(), instanceNo, count, "stableKey"),
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
                seeds.stream().collect(Collectors.groupingBy(seed -> seed.template().templateKey()));
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
                                    seed.template().adoptionPolicy(),
                                    seed.template().userInstructionJson(),
                                    seed.template().schemaJson());
                        })
                .toList();
    }

    private List<AigcBlueprintRelationSpec> relationSpecs(
            AigcProjectBlueprint blueprint, List<AigcResolvedObjectSpec> objects) {
        var stableByTemplate =
                objects.stream()
                        .collect(
                                Collectors.groupingBy(
                                        AigcResolvedObjectSpec::blueprintTemplateKey,
                                        LinkedHashMap::new,
                                        Collectors.mapping(
                                                AigcResolvedObjectSpec::stableKey,
                                                Collectors.toList())));
        var result = new ArrayList<AigcBlueprintRelationSpec>();
        for (var item : mapList(blueprint.getRelationSpec(), "relations")) {
            var source = text(item, "sourceTemplateKey");
            var target = text(item, "targetTemplateKey");
            var type = text(item, "type");
            if (source == null || target == null || type == null) {
                throw exception(
                        CONFIGURATION_INCOMPATIBLE,
                        "Blueprint 关系缺少 sourceTemplateKey/targetTemplateKey/type");
            }
            var sources = stableByTemplate.getOrDefault(source, List.of());
            var targets = stableByTemplate.getOrDefault(target, List.of());
            for (var sourceKey : sources) {
                for (var targetKey : targets) {
                    result.add(
                            new AigcBlueprintRelationSpec(
                                    sourceKey, targetKey, type, JsonUtils.toJsonString(item)));
                }
            }
        }
        return List.copyOf(result);
    }

    private List<AigcBlueprintActionSpec> actionSpecs(AigcProjectBlueprint blueprint) {
        return mapList(blueprint.getActionSpec(), "actions").stream()
                .map(
                        item -> {
                            var actionKey = text(item, "actionKey");
                            if (actionKey == null) {
                                throw exception(CONFIGURATION_INCOMPATIBLE, "Blueprint action 缺少 actionKey");
                            }
                            return new AigcBlueprintActionSpec(
                                    actionKey,
                                    text(item, "targetTemplateKey"),
                                    stringList(item.get("dependencyTemplateKeys")),
                                    defaultText(item, "confirmationPolicy", "NONE"),
                                    json(item.get("inputPreset")));
                        })
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
                                                item,
                                                "defaultUserAddedContractRole",
                                                "OPTIONAL"),
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

    private List<AigcExecutionBindingVersionRef> executionBindings(
            AigcProjectTypePackage packageEntity) {
        var actionKeys =
                stringList(
                        packageEntity.getCompatibilityResult() == null
                                ? null
                                : packageEntity.getCompatibilityResult().get("coveredActionKeys"));
        if (actionKeys.size() != packageEntity.getExecutionBindingIds().size()) {
            throw exception(CONFIGURATION_INCOMPATIBLE, "兼容包动作与执行绑定数量不一致");
        }
        var result = new ArrayList<AigcExecutionBindingVersionRef>();
        for (var index = 0; index < actionKeys.size(); index++) {
            result.add(
                    new AigcExecutionBindingVersionRef(
                            actionKeys.get(index), packageEntity.getExecutionBindingIds().get(index)));
        }
        return List.copyOf(result);
    }

    private int tierCount(
            Map<String, Object> item, String policyKey, String tier, int currentCount) {
        if (tier == null || !(item.get(policyKey) instanceof Map<?, ?> policy)) {
            return currentCount;
        }
        var value = policy.get(tier);
        return value instanceof Number number ? number.intValue() : currentCount;
    }

    private int constrainCount(
            int requested,
            AigcBlueprintSlotTemplateSpec template,
            AigcDomainExtension extension,
            List<AigcChannelSpec> channels,
            boolean rejectAdjustment) {
        var count = requested;
        if (extension != null) {
            count =
                    applyConstraint(
                            count,
                            extension.getActionConstraints(),
                            template.templateKey(),
                            rejectAdjustment);
            count =
                    applyConstraint(
                            count,
                            extension.getChannelOverrides(),
                            template.templateKey(),
                            rejectAdjustment);
        }
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
            Map<String, Object> source,
            String templateKey,
            boolean rejectAdjustment) {
        if (source == null || !(source.get("slotConstraints") instanceof List<?> constraints)) {
            return value;
        }
        var result = value;
        for (var constraint : constraints) {
            if (!(constraint instanceof Map<?, ?> map)
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
            Object value,
            AigcConfigurationResolveCommand command,
            String productionMode,
            List<AigcChannelSpec> channels) {
        if (!(value instanceof Map<?, ?> condition)) {
            return true;
        }
        if (condition.get("productionModes") instanceof List<?> modes
                && !modes.contains(productionMode)) {
            return false;
        }
        if (condition.get("budgetTiers") instanceof List<?> tiers
                && !tiers.contains(command.budgetTier())) {
            return false;
        }
        if (condition.get("qualityTiers") instanceof List<?> tiers
                && !tiers.contains(command.qualityTier())) {
            return false;
        }
        if (condition.get("channelsAny") instanceof List<?> expectedChannels) {
            var channelCodes = channels.stream().map(AigcChannelSpec::getCode).toList();
            return expectedChannels.stream().anyMatch(channelCodes::contains);
        }
        return true;
    }

    private String resolveParentKey(
            String parentTemplateKey,
            int instanceNo,
            Map<String, List<ResolvedSeed>> seedsByTemplate) {
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

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private String json(Object value) {
        return value == null ? null : JsonUtils.toJsonString(value);
    }

    private String defaultText(Map<String, Object> source, String key, String fallback) {
        var value = text(source, key);
        return value == null ? fallback : value;
    }

    private boolean bool(Object value) {
        return Boolean.TRUE.equals(value);
    }

    private record ResolvedSeed(
            AigcBlueprintSlotTemplateSpec template,
            int instanceNo,
            String stableKey,
            String displayName) {}

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

    private Map<String, Object> packageSnapshot(AigcProjectTypePackage value) {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", value.getId());
        result.put("version", value.getPackageVersion());
        result.put("executionBindingIds", value.getExecutionBindingIds());
        result.put("compatibilityResult", value.getCompatibilityResult());
        return result;
    }

    private Map<String, Object> projectTypeSnapshot(AigcProjectType value) {
        return Map.of(
                "id", value.getId(),
                "code", value.getCode(),
                "definitionVersion", value.getDefinitionVersion());
    }

    private Map<String, Object> blueprintSnapshot(AigcProjectBlueprint value) {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", value.getId());
        result.put("code", value.getCode());
        result.put("version", value.getBlueprintVersion());
        result.put("slotTemplateSpec", value.getSlotTemplateSpec());
        result.put("relationSpec", value.getRelationSpec());
        result.put("actionSpec", value.getActionSpec());
        result.put("deliverableSpec", value.getDeliverableSpec());
        result.put("processPolicy", value.getProcessPolicy());
        return result;
    }

    private Map<String, Object> domainExtensionSnapshot(AigcDomainExtension value) {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", value.getId());
        result.put("code", value.getCode());
        result.put("version", value.getExtensionVersion());
        result.put("ruleSets", value.getRuleSets());
        result.put("validators", value.getValidators());
        result.put("actionConstraints", value.getActionConstraints());
        result.put("channelOverrides", value.getChannelOverrides());
        result.put("migrationDeclaration", value.getMigrationDeclaration());
        return result;
    }

    private Map<String, Object> channelSnapshot(AigcChannelSpec value) {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", value.getId());
        result.put("code", value.getCode());
        result.put("version", value.getSpecVersion());
        result.put("aspectRatio", value.getAspectRatio());
        result.put("width", value.getWidth());
        result.put("height", value.getHeight());
        result.put("maxDurationSeconds", value.getMaxDurationSeconds());
        result.put("copyStructure", value.getCopyStructure());
        result.put("requiredDisclaimers", value.getRequiredDisclaimers());
        result.put("exportFormat", value.getExportFormat());
        return result;
    }

    private String text(Map<String, Object> source, String key) {
        var value = source.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private int integer(Object value, int fallback) {
        var parsed = integer(value);
        return parsed == null ? fallback : parsed;
    }
}
