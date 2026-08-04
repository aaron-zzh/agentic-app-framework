package com.xuejiai.aaf.module.ai.aigc.configuration.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.ai.aigc.configuration.AigcConfigurationErrorCode.PUBLISH_STATE_INVALID;
import static com.xuejiai.aaf.module.ai.aigc.configuration.AigcConfigurationErrorCode.VERSION_IMMUTABLE;

import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcDomainExtension;
import com.xuejiai.aaf.module.ai.aigc.configuration.enums.AigcConfigStatus;
import com.xuejiai.aaf.module.ai.aigc.configuration.mapper.AigcDomainExtensionConvert;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcDomainExtensionRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcConfigurationPublishDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcDomainExtensionCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcDomainExtensionPageDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcDomainExtensionUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcDomainExtensionVO;

import lombok.RequiredArgsConstructor;

/** AIGC 领域扩展 CRUD 与发布服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcDomainExtensionService
        extends BaseCrudService<
                AigcDomainExtension,
                AigcDomainExtensionVO,
                AigcDomainExtensionCreateDTO,
                AigcDomainExtensionUpdateDTO,
                AigcDomainExtensionPageDTO> {

    private final AigcDomainExtensionRepository repository;

    @Override
    protected AigcDomainExtensionRepository getRepository() {
        return repository;
    }

    @Override
    protected AigcDomainExtensionVO toVO(AigcDomainExtension entity) {
        return new AigcDomainExtensionVO(
                entity.getId(),
                entity.getVersion(),
                entity.getCode(),
                entity.getName(),
                entity.getExtensionVersion(),
                entity.getIndustry(),
                entity.getRegion(),
                entity.getLanguage(),
                entity.getStatus(),
                entity.getProfileSchemaExt(),
                entity.getObjectDefinitions(),
                entity.getKnowledgeRequirements(),
                entity.getRuleSets(),
                entity.getValidators(),
                entity.getRoleRecommendations(),
                entity.getActionConstraints(),
                entity.getChannelOverrides(),
                entity.getMigrationDeclaration());
    }

    @Override
    protected AigcDomainExtension toEntity(AigcDomainExtensionCreateDTO request) {
        var entity = AigcDomainExtensionConvert.INSTANCE.toEntity(request);
        entity.setStatus(AigcConfigStatus.DRAFT.getCode());
        return entity;
    }

    @Override
    protected void beforeUpdate(AigcDomainExtension entity, AigcDomainExtensionUpdateDTO request) {
        requireDraft(entity.getStatus());
    }

    @Override
    protected void beforeDelete(AigcDomainExtension entity) {
        requireDraft(entity.getStatus());
    }

    @Override
    protected void updateEntity(AigcDomainExtension entity, AigcDomainExtensionUpdateDTO request) {
        entity.setVersion(
                AigcConfigurationPatchSupport.requireVersion(
                        entity.getVersion(), request.expectedVersion()));
        AigcConfigurationPatchSupport.required(request.code(), "code", entity::setCode);
        AigcConfigurationPatchSupport.required(request.name(), "name", entity::setName);
        AigcConfigurationPatchSupport.required(
                request.extensionVersion(), "extensionVersion", entity::setExtensionVersion);
        AigcConfigurationPatchSupport.nullable(request.industry(), entity::setIndustry);
        AigcConfigurationPatchSupport.nullable(request.region(), entity::setRegion);
        AigcConfigurationPatchSupport.nullable(request.language(), entity::setLanguage);
        AigcConfigurationPatchSupport.nullable(
                request.profileSchemaExt(), entity::setProfileSchemaExt);
        AigcConfigurationPatchSupport.nullable(
                request.objectDefinitions(), entity::setObjectDefinitions);
        AigcConfigurationPatchSupport.nullable(
                request.knowledgeRequirements(), entity::setKnowledgeRequirements);
        AigcConfigurationPatchSupport.nullable(request.ruleSets(), entity::setRuleSets);
        AigcConfigurationPatchSupport.nullable(request.validators(), entity::setValidators);
        AigcConfigurationPatchSupport.nullable(
                request.roleRecommendations(), entity::setRoleRecommendations);
        AigcConfigurationPatchSupport.nullable(
                request.actionConstraints(), entity::setActionConstraints);
        AigcConfigurationPatchSupport.nullable(
                request.channelOverrides(), entity::setChannelOverrides);
        AigcConfigurationPatchSupport.nullable(
                request.migrationDeclaration(), entity::setMigrationDeclaration);
    }

    @Transactional
    public AigcDomainExtensionVO publish(Long id, AigcConfigurationPublishDTO command) {
        return executeCustomUpdateCommand(
                id,
                command,
                new CustomUpdatePlan<>(
                        "PUBLISH",
                        Set.of("status"),
                        (entity, request) -> {
                            requireDraftForPublish(entity.getStatus());
                            AigcConfigurationPatchSupport.requireVersion(
                                    entity.getVersion(), request.expectedVersion());
                        },
                        (entity, request) -> entity.setStatus(AigcConfigStatus.PUBLISHED.getCode()),
                        (entity, request) -> null,
                        true,
                        (entity, request, ignored) -> {},
                        (entity, request, ignored) -> toVO(entity)));
    }

    @Override
    protected Specification<AigcDomainExtension> buildSpec(AigcDomainExtensionPageDTO request) {
        return SpecificationBuilder.<AigcDomainExtension>builder()
                .eqIfPresent("status", request.getStatus())
                .build();
    }

    private void requireDraft(String status) {
        if (!AigcConfigStatus.DRAFT.getCode().equals(status)) throw exception(VERSION_IMMUTABLE);
    }

    private void requireDraftForPublish(String status) {
        if (!AigcConfigStatus.DRAFT.getCode().equals(status))
            throw exception(PUBLISH_STATE_INVALID);
    }
}
