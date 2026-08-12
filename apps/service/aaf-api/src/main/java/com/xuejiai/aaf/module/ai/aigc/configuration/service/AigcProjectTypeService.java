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
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectType;
import com.xuejiai.aaf.module.ai.aigc.configuration.enums.AigcConfigStatus;
import com.xuejiai.aaf.module.ai.aigc.configuration.mapper.AigcProjectTypeConvert;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcProjectTypeRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcConfigurationPublishDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectTypeCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectTypePageDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectTypeUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectTypeVO;

import lombok.RequiredArgsConstructor;

/** AIGC 项目类型 CRUD 服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcProjectTypeService
        extends BaseCrudService<
                AigcProjectType,
                AigcProjectTypeVO,
                AigcProjectTypeCreateDTO,
                AigcProjectTypeUpdateDTO,
                AigcProjectTypePageDTO> {

    private final AigcProjectTypeRepository repository;

    @Override
    protected AigcProjectTypeRepository getRepository() {
        return repository;
    }

    @Override
    protected AigcProjectTypeVO toVO(AigcProjectType entity) {
        return new AigcProjectTypeVO(
                entity.getId(),
                entity.getVersion(),
                entity.getCode(),
                entity.getName(),
                entity.getIcon(),
                entity.getDescription(),
                entity.getBriefPlaceholder(),
                entity.getDefinitionVersion(),
                entity.getDefaultChannels(),
                entity.getDefaultProductionMode(),
                entity.getQuickEntry(),
                entity.getBuiltin(),
                entity.getSortOrder(),
                entity.getStatus());
    }

    @Override
    protected AigcProjectType toEntity(AigcProjectTypeCreateDTO request) {
        var entity = AigcProjectTypeConvert.INSTANCE.toEntity(request);
        entity.setStatus(AigcConfigStatus.DRAFT.getCode());
        return entity;
    }

    @Override
    protected void beforeUpdate(AigcProjectType entity, AigcProjectTypeUpdateDTO request) {
        requireDraft(entity.getStatus());
    }

    @Override
    protected void beforeDelete(AigcProjectType entity) {
        requireDraft(entity.getStatus());
    }

    @Override
    protected void updateEntity(AigcProjectType entity, AigcProjectTypeUpdateDTO request) {
        entity.setVersion(
                AigcConfigurationPatchSupport.requireVersion(
                        entity.getVersion(), request.expectedVersion()));
        AigcConfigurationPatchSupport.required(request.code(), "code", entity::setCode);
        AigcConfigurationPatchSupport.required(request.name(), "name", entity::setName);
        AigcConfigurationPatchSupport.nullable(request.icon(), entity::setIcon);
        AigcConfigurationPatchSupport.nullable(request.description(), entity::setDescription);
        AigcConfigurationPatchSupport.nullable(
                request.briefPlaceholder(), entity::setBriefPlaceholder);
        AigcConfigurationPatchSupport.required(
                request.definitionVersion(), "definitionVersion", entity::setDefinitionVersion);
        AigcConfigurationPatchSupport.nullable(
                request.defaultChannels(), entity::setDefaultChannels);
        AigcConfigurationPatchSupport.nullable(
                request.defaultProductionMode(), entity::setDefaultProductionMode);
        AigcConfigurationPatchSupport.required(
                request.quickEntry(), "quickEntry", entity::setQuickEntry);
        AigcConfigurationPatchSupport.required(
                request.sortOrder(), "sortOrder", entity::setSortOrder);
    }

    @Transactional
    public AigcProjectTypeVO publish(Long id, AigcConfigurationPublishDTO command) {
        return executeCustomUpdateCommand(
                id,
                command,
                new CustomUpdatePlan<>(
                        "PUBLISH",
                        Set.of("status"),
                        (entity, request) -> {
                            if (!AigcConfigStatus.DRAFT.getCode().equals(entity.getStatus())) {
                                throw exception(PUBLISH_STATE_INVALID);
                            }
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
    protected Specification<AigcProjectType> buildSpec(AigcProjectTypePageDTO request) {
        return SpecificationBuilder.<AigcProjectType>builder().build();
    }

    private void requireDraft(String status) {
        if (!AigcConfigStatus.DRAFT.getCode().equals(status)) {
            throw exception(VERSION_IMMUTABLE);
        }
    }
}
