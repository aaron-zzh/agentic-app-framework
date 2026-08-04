package com.xuejiai.aaf.module.ai.aigc.configuration.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.ai.aigc.configuration.AigcConfigurationErrorCode.CONFIGURATION_INCOMPATIBLE;
import static com.xuejiai.aaf.module.ai.aigc.configuration.AigcConfigurationErrorCode.CONFIGURATION_NOT_FOUND;
import static com.xuejiai.aaf.module.ai.aigc.configuration.AigcConfigurationErrorCode.PUBLISH_STATE_INVALID;
import static com.xuejiai.aaf.module.ai.aigc.configuration.AigcConfigurationErrorCode.VERSION_IMMUTABLE;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcExecutionBindingCompatibilityPort;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcChannelSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcDomainExtension;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectBlueprint;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectType;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectTypePackage;
import com.xuejiai.aaf.module.ai.aigc.configuration.enums.AigcConfigStatus;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcChannelSpecRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcDomainExtensionRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcProjectBlueprintRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcProjectTypePackageRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcProjectTypeRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcConfigurationPublishDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectTypePackageCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectTypePackagePageDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectTypePackageUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectTypePackageVO;

import lombok.RequiredArgsConstructor;

/** 项目类型兼容包管理。发布时固定并校验所有组成版本。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcProjectTypePackageService
        extends BaseCrudService<
                AigcProjectTypePackage,
                AigcProjectTypePackageVO,
                AigcProjectTypePackageCreateDTO,
                AigcProjectTypePackageUpdateDTO,
                AigcProjectTypePackagePageDTO> {

    private static final String PUBLISHED = AigcConfigStatus.PUBLISHED.getCode();

    private final AigcProjectTypePackageRepository repository;
    private final AigcProjectTypeRepository projectTypeRepository;
    private final AigcProjectBlueprintRepository blueprintRepository;
    private final AigcDomainExtensionRepository domainExtensionRepository;
    private final AigcChannelSpecRepository channelSpecRepository;
    private final AigcExecutionBindingCompatibilityPort bindingCompatibilityPort;

    @Override
    protected AigcProjectTypePackageRepository getRepository() {
        return repository;
    }

    @Override
    protected AigcProjectTypePackageVO toVO(AigcProjectTypePackage entity) {
        return new AigcProjectTypePackageVO(
                entity.getId(),
                entity.getVersion(),
                entity.getPackageVersion(),
                entity.getProjectTypeId(),
                entity.getBlueprintId(),
                entity.getDomainExtensionId(),
                entity.getChannelSpecIds(),
                entity.getExecutionBindingIds(),
                entity.getProductionMode(),
                entity.getCompatibilityResult(),
                entity.getStatus());
    }

    @Override
    protected AigcProjectTypePackage toEntity(AigcProjectTypePackageCreateDTO request) {
        var entity = new AigcProjectTypePackage();
        entity.setPackageVersion(request.packageVersion());
        entity.setProjectTypeId(request.projectTypeId());
        entity.setBlueprintId(request.blueprintId());
        entity.setDomainExtensionId(request.domainExtensionId());
        entity.setChannelSpecIds(distinct(request.channelSpecIds()));
        entity.setExecutionBindingIds(distinct(request.executionBindingIds()));
        entity.setProductionMode(request.productionMode());
        entity.setStatus(AigcConfigStatus.DRAFT.getCode());
        return entity;
    }

    @Override
    protected void beforeUpdate(
            AigcProjectTypePackage entity, AigcProjectTypePackageUpdateDTO request) {
        requireDraft(entity.getStatus());
    }

    @Override
    protected void beforeDelete(AigcProjectTypePackage entity) {
        requireDraft(entity.getStatus());
    }

    @Override
    protected void updateEntity(
            AigcProjectTypePackage entity, AigcProjectTypePackageUpdateDTO request) {
        entity.setVersion(
                AigcConfigurationPatchSupport.requireVersion(
                        entity.getVersion(), request.expectedVersion()));
        AigcConfigurationPatchSupport.required(
                request.packageVersion(), "packageVersion", entity::setPackageVersion);
        AigcConfigurationPatchSupport.required(
                request.projectTypeId(), "projectTypeId", entity::setProjectTypeId);
        AigcConfigurationPatchSupport.required(
                request.blueprintId(), "blueprintId", entity::setBlueprintId);
        AigcConfigurationPatchSupport.nullable(
                request.domainExtensionId(), entity::setDomainExtensionId);
        if (!request.channelSpecIds().isAbsent()) {
            AigcConfigurationPatchSupport.required(
                    request.channelSpecIds(),
                    "channelSpecIds",
                    ids -> entity.setChannelSpecIds(distinct(ids)));
        }
        if (!request.executionBindingIds().isAbsent()) {
            AigcConfigurationPatchSupport.required(
                    request.executionBindingIds(),
                    "executionBindingIds",
                    ids -> entity.setExecutionBindingIds(distinct(ids)));
        }
        AigcConfigurationPatchSupport.required(
                request.productionMode(), "productionMode", entity::setProductionMode);
        entity.setCompatibilityResult(null);
    }

    @Transactional
    public AigcProjectTypePackageVO publish(Long id, AigcConfigurationPublishDTO command) {
        return executeCustomUpdateCommand(
                id,
                command,
                new CustomUpdatePlan<>(
                        "PUBLISH",
                        Set.of("status", "compatibilityResult"),
                        (entity, request) -> {
                            if (!AigcConfigStatus.DRAFT.getCode().equals(entity.getStatus())) {
                                throw exception(PUBLISH_STATE_INVALID);
                            }
                            AigcConfigurationPatchSupport.requireVersion(
                                    entity.getVersion(), request.expectedVersion());
                        },
                        (entity, request) -> entity.setStatus(PUBLISHED),
                        (entity, request) -> validateCompatibility(entity),
                        true,
                        (entity, request, result) -> entity.setCompatibilityResult(result),
                        (entity, request, result) -> toVO(entity)));
    }

    @Override
    protected Specification<AigcProjectTypePackage> buildSpec(
            AigcProjectTypePackagePageDTO request) {
        return SpecificationBuilder.<AigcProjectTypePackage>builder()
                .eqIfPresent("projectTypeId", request.getProjectTypeId())
                .eqIfPresent("productionMode", request.getProductionMode())
                .eqIfPresent("status", request.getStatus())
                .build();
    }

    private java.util.Map<String, Object> validateCompatibility(
            AigcProjectTypePackage packageEntity) {
        var projectType = requirePublishedProjectType(packageEntity.getProjectTypeId());
        var blueprint = requirePublishedBlueprint(packageEntity.getBlueprintId());
        var domainExtension = requirePublishedDomain(packageEntity.getDomainExtensionId());
        var channels = requirePublishedChannels(packageEntity.getChannelSpecIds());

        if (!projectType.getCode().equals(blueprint.getProjectTypeCode())
                || !packageEntity.getProductionMode().equals(blueprint.getProductionMode())) {
            throw exception(CONFIGURATION_INCOMPATIBLE, "项目类型、蓝图与生产模式不匹配");
        }
        var bindingIds = distinct(packageEntity.getExecutionBindingIds());
        if (bindingIds.isEmpty() && !blueprint.getActionKeys().isEmpty()) {
            throw exception(CONFIGURATION_INCOMPATIBLE, "执行绑定不能为空");
        }
        if (!bindingIds.isEmpty()) {
            var actionKeys = blueprint.getActionKeys().stream().distinct().toList();
            var validation =
                    bindingCompatibilityPort.validate(
                            bindingIds,
                            projectType.getCode(),
                            domainExtension == null ? null : domainExtension.getCode(),
                            packageEntity.getProductionMode(),
                            channels.stream().map(AigcChannelSpec::getCode).toList());
            if (!validation.allCompatible()) {
                throw exception(CONFIGURATION_INCOMPATIBLE, "存在未发布或维度不兼容的执行绑定");
            }
            if (!validation.covers(actionKeys)) {
                throw exception(CONFIGURATION_INCOMPATIBLE, "执行绑定未覆盖蓝图动作");
            }
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("compatible", true);
        result.put("projectTypeCode", projectType.getCode());
        result.put("blueprintCode", blueprint.getCode());
        result.put(
                "domainExtensionCode", domainExtension == null ? null : domainExtension.getCode());
        result.put("channelCodes", channels.stream().map(AigcChannelSpec::getCode).toList());
        result.put("coveredActionKeys", blueprint.getActionKeys());
        return java.util.Collections.unmodifiableMap(result);
    }

    private AigcProjectType requirePublishedProjectType(Long id) {
        return projectTypeRepository
                .findByIdAndStatusAndDeletedFalse(id, PUBLISHED)
                .orElseThrow(() -> exception(CONFIGURATION_NOT_FOUND, "projectType:" + id));
    }

    private AigcProjectBlueprint requirePublishedBlueprint(Long id) {
        return blueprintRepository
                .findByIdAndStatusAndDeletedFalse(id, PUBLISHED)
                .orElseThrow(() -> exception(CONFIGURATION_NOT_FOUND, "blueprint:" + id));
    }

    private AigcDomainExtension requirePublishedDomain(Long id) {
        if (id == null) return null;
        return domainExtensionRepository
                .findByIdAndStatusAndDeletedFalse(id, PUBLISHED)
                .orElseThrow(() -> exception(CONFIGURATION_NOT_FOUND, "domainExtension:" + id));
    }

    private List<AigcChannelSpec> requirePublishedChannels(List<Long> ids) {
        var requested = distinct(ids);
        if (requested.isEmpty()) return List.of();
        var entities =
                channelSpecRepository.findByIdInAndStatusAndDeletedFalse(requested, PUBLISHED);
        if (entities.size() != requested.size()) {
            throw exception(CONFIGURATION_NOT_FOUND, "channelSpec");
        }
        var byId =
                entities.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AigcChannelSpec::getId, value -> value));
        return requested.stream().map(byId::get).toList();
    }

    private List<Long> distinct(List<Long> values) {
        return values == null ? List.of() : values.stream().distinct().toList();
    }

    private void requireDraft(String status) {
        if (!AigcConfigStatus.DRAFT.getCode().equals(status)) {
            throw exception(VERSION_IMMUTABLE);
        }
    }
}
