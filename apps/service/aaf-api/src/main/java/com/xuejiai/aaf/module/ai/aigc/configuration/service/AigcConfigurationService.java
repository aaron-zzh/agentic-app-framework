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
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintObjectSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintRelationSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcConfigurationApi;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcConfigurationResolveCommand;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcResolvedConfiguration;
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

        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("package", packageSnapshot(packageEntity));
        snapshot.put("projectType", projectTypeSnapshot(projectType));
        snapshot.put("blueprint", blueprintSnapshot(blueprint));
        snapshot.put(
                "domainExtension",
                domainExtension == null ? null : domainExtensionSnapshot(domainExtension));
        snapshot.put("channels", channels.stream().map(this::channelSnapshot).toList());
        snapshot.put("executionBindingVersionIds", packageEntity.getExecutionBindingIds());
        snapshot.put("productionMode", packageEntity.getProductionMode());

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
                packageEntity.getExecutionBindingIds(),
                packageEntity.getProductionMode(),
                JsonUtils.toJsonString(snapshot),
                objectSpecs(blueprint),
                relationSpecs(blueprint));
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

    private List<AigcBlueprintObjectSpec> objectSpecs(AigcProjectBlueprint blueprint) {
        var result = new ArrayList<AigcBlueprintObjectSpec>();
        for (var item : mapList(blueprint.getObjectSpec(), "objects")) {
            var key = text(item, "key");
            var type = text(item, "type");
            if (key == null || type == null) {
                throw exception(CONFIGURATION_INCOMPATIBLE, "Blueprint 对象缺少 key/type");
            }
            result.add(
                    new AigcBlueprintObjectSpec(
                            key,
                            type,
                            text(item, "parentKey"),
                            integer(item.get("sortOrder")),
                            JsonUtils.toJsonString(item)));
        }
        return List.copyOf(result);
    }

    private List<AigcBlueprintRelationSpec> relationSpecs(AigcProjectBlueprint blueprint) {
        var result = new ArrayList<AigcBlueprintRelationSpec>();
        for (var item : mapList(blueprint.getRelationSpec(), "relations")) {
            var source = text(item, "sourceKey");
            var target = text(item, "targetKey");
            var type = text(item, "type");
            if (source == null || target == null || type == null) {
                throw exception(CONFIGURATION_INCOMPATIBLE, "Blueprint 关系缺少 source/target/type");
            }
            result.add(
                    new AigcBlueprintRelationSpec(
                            source, target, type, JsonUtils.toJsonString(item)));
        }
        return List.copyOf(result);
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
        result.put("objectSpec", value.getObjectSpec());
        result.put("relationSpec", value.getRelationSpec());
        result.put("deliverableSpec", value.getDeliverableSpec());
        result.put("actionKeys", value.getActionKeys());
        result.put("confirmationGates", value.getConfirmationGates());
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
}
