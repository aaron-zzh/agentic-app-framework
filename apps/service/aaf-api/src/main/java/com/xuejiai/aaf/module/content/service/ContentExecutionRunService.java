package com.xuejiai.aaf.module.content.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.content.domain.ContentExecutionRun;
import com.xuejiai.aaf.module.content.mapper.ContentExecutionRunConvert;
import com.xuejiai.aaf.module.content.repository.ContentExecutionRunRepository;
import com.xuejiai.aaf.module.content.vo.ContentExecutionRunCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentExecutionRunPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentExecutionRunUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentExecutionRunVO;

import lombok.RequiredArgsConstructor;

/**
 * 执行记录 CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentExecutionRunService
        extends BaseCrudService<
                ContentExecutionRun,
                ContentExecutionRunVO,
                ContentExecutionRunCreateDTO,
                ContentExecutionRunUpdateDTO,
                ContentExecutionRunPageDTO> {

    private final ContentExecutionRunRepository repository;

    @Override
    protected ContentExecutionRunRepository getRepository() {
        return repository;
    }

    @Override
    protected ContentExecutionRunVO toVO(ContentExecutionRun entity) {
        return new ContentExecutionRunVO(
                entity.getId(),
                entity.getProjectId(),
                entity.getObjectId(),
                entity.getActionKey(),
                entity.getTargetType(),
                entity.getTargetRef(),
                entity.getStatus(),
                entity.getGenerationMode(),
                entity.getRoleProfileCode(),
                entity.getSelectedModelVersion(),
                entity.getCostCredits(),
                entity.getErrorMessage(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getCreateTime());
    }

    public ContentExecutionRunVO toView(ContentExecutionRun entity) {
        return toVO(entity);
    }

    @Override
    protected ContentExecutionRun toEntity(ContentExecutionRunCreateDTO dto) {
        var entity = ContentExecutionRunConvert.INSTANCE.toEntity(dto);
        return entity;
    }

    @Override
    protected void updateEntity(ContentExecutionRun entity, ContentExecutionRunUpdateDTO dto) {
        entity.setVersion(
                ContentPatchSupport.requireVersion(entity.getVersion(), dto.expectedVersion()));
        ContentPatchSupport.required(dto.projectId(), "projectId", entity::setProjectId);
        ContentPatchSupport.nullable(dto.objectId(), entity::setObjectId);
        ContentPatchSupport.required(dto.actionKey(), "actionKey", entity::setActionKey);
        ContentPatchSupport.required(dto.targetType(), "targetType", entity::setTargetType);
        ContentPatchSupport.nullable(dto.targetRef(), entity::setTargetRef);
        ContentPatchSupport.required(dto.status(), "status", entity::setStatus);
        ContentPatchSupport.nullable(dto.generationMode(), entity::setGenerationMode);
        ContentPatchSupport.nullable(dto.roleProfileCode(), entity::setRoleProfileCode);
        ContentPatchSupport.nullable(dto.modelPolicyVersion(), entity::setModelPolicyVersion);
        ContentPatchSupport.nullable(dto.selectedModelVersion(), entity::setSelectedModelVersion);
        ContentPatchSupport.nullable(
                dto.skillDefinitionVersionId(), entity::setSkillDefinitionVersionId);
        ContentPatchSupport.nullable(dto.promptText(), entity::setPromptText);
        ContentPatchSupport.nullable(dto.snippetRefs(), entity::setSnippetRefs);
        ContentPatchSupport.nullable(dto.attachmentRefs(), entity::setAttachmentRefs);
        ContentPatchSupport.nullable(dto.toolCalls(), entity::setToolCalls);
        ContentPatchSupport.nullable(dto.inputPayload(), entity::setInputPayload);
        ContentPatchSupport.nullable(dto.outputPayload(), entity::setOutputPayload);
        ContentPatchSupport.nullable(dto.costCredits(), entity::setCostCredits);
        ContentPatchSupport.nullable(dto.errorMessage(), entity::setErrorMessage);
        ContentPatchSupport.nullable(dto.startTime(), entity::setStartTime);
        ContentPatchSupport.nullable(dto.endTime(), entity::setEndTime);
        ContentPatchSupport.nullable(dto.aigcTaskId(), entity::setAigcTaskId);
    }

    @Override
    protected Specification<ContentExecutionRun> buildSpec(ContentExecutionRunPageDTO request) {
        return SpecificationBuilder.<ContentExecutionRun>builder()
                .eqIfPresent("projectId", request.getProjectId())
                .eqIfPresent("objectId", request.getObjectId())
                .eqIfPresent("status", request.getStatus())
                .build();
    }
}
