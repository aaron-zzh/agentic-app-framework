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
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectBlueprint;
import com.xuejiai.aaf.module.ai.aigc.configuration.enums.AigcConfigStatus;
import com.xuejiai.aaf.module.ai.aigc.configuration.mapper.AigcProjectBlueprintConvert;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcProjectBlueprintRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcConfigurationPublishDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectBlueprintCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectBlueprintPageDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectBlueprintUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectBlueprintVO;

import lombok.RequiredArgsConstructor;

/** AIGC 项目蓝图 CRUD 与发布服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcProjectBlueprintService
        extends BaseCrudService<
                AigcProjectBlueprint,
                AigcProjectBlueprintVO,
                AigcProjectBlueprintCreateDTO,
                AigcProjectBlueprintUpdateDTO,
                AigcProjectBlueprintPageDTO> {

    private final AigcProjectBlueprintRepository repository;

    @Override
    protected AigcProjectBlueprintRepository getRepository() {
        return repository;
    }

    @Override
    protected AigcProjectBlueprintVO toVO(AigcProjectBlueprint entity) {
        return new AigcProjectBlueprintVO(
                entity.getId(),
                entity.getVersion(),
                entity.getCode(),
                entity.getName(),
                entity.getProjectTypeCode(),
                entity.getBlueprintVersion(),
                entity.getProductionMode(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getObjectSpec(),
                entity.getRelationSpec(),
                entity.getDeliverableSpec(),
                entity.getActionKeys(),
                entity.getConfirmationGates(),
                entity.getBriefFields());
    }

    @Override
    protected AigcProjectBlueprint toEntity(AigcProjectBlueprintCreateDTO request) {
        var entity = AigcProjectBlueprintConvert.INSTANCE.toEntity(request);
        entity.setStatus(AigcConfigStatus.DRAFT.getCode());
        return entity;
    }

    @Override
    protected void beforeUpdate(
            AigcProjectBlueprint entity, AigcProjectBlueprintUpdateDTO request) {
        requireDraft(entity.getStatus());
    }

    @Override
    protected void beforeDelete(AigcProjectBlueprint entity) {
        requireDraft(entity.getStatus());
    }

    @Override
    protected void updateEntity(
            AigcProjectBlueprint entity, AigcProjectBlueprintUpdateDTO request) {
        entity.setVersion(
                AigcConfigurationPatchSupport.requireVersion(
                        entity.getVersion(), request.expectedVersion()));
        AigcConfigurationPatchSupport.required(request.code(), "code", entity::setCode);
        AigcConfigurationPatchSupport.required(request.name(), "name", entity::setName);
        AigcConfigurationPatchSupport.required(
                request.projectTypeCode(), "projectTypeCode", entity::setProjectTypeCode);
        AigcConfigurationPatchSupport.required(
                request.blueprintVersion(), "blueprintVersion", entity::setBlueprintVersion);
        AigcConfigurationPatchSupport.required(
                request.productionMode(), "productionMode", entity::setProductionMode);
        AigcConfigurationPatchSupport.nullable(request.description(), entity::setDescription);
        AigcConfigurationPatchSupport.nullable(request.objectSpec(), entity::setObjectSpec);
        AigcConfigurationPatchSupport.nullable(request.relationSpec(), entity::setRelationSpec);
        AigcConfigurationPatchSupport.nullable(
                request.deliverableSpec(), entity::setDeliverableSpec);
        AigcConfigurationPatchSupport.nullable(request.actionKeys(), entity::setActionKeys);
        AigcConfigurationPatchSupport.nullable(
                request.confirmationGates(), entity::setConfirmationGates);
        AigcConfigurationPatchSupport.nullable(request.briefFields(), entity::setBriefFields);
    }

    @Transactional
    public AigcProjectBlueprintVO publish(Long id, AigcConfigurationPublishDTO command) {
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
    protected Specification<AigcProjectBlueprint> buildSpec(AigcProjectBlueprintPageDTO request) {
        return SpecificationBuilder.<AigcProjectBlueprint>builder()
                .eqIfPresent("projectTypeCode", request.getProjectTypeCode())
                .eqIfPresent("productionMode", request.getProductionMode())
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
