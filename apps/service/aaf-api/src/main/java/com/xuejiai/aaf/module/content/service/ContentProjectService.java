package com.xuejiai.aaf.module.content.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.content.domain.ContentProject;
import com.xuejiai.aaf.module.content.mapper.ContentProjectConvert;
import com.xuejiai.aaf.module.content.repository.ContentProjectRepository;
import com.xuejiai.aaf.module.content.vo.ContentProjectCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectVO;

import lombok.RequiredArgsConstructor;

/**
 * 内容项目 CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentProjectService
        extends BaseCrudService<
                ContentProject,
                ContentProjectVO,
                ContentProjectCreateDTO,
                ContentProjectUpdateDTO,
                ContentProjectPageDTO> {

    private final ContentProjectRepository repository;
    private final OperatorContext operatorContext;

    @Override
    protected ContentProjectRepository getRepository() {
        return repository;
    }

    @Override
    protected ContentProjectVO toVO(ContentProject entity) {
        return new ContentProjectVO(
                entity.getId(),
                entity.getVersion(),
                entity.getName(),
                entity.getProjectTypeCode(),
                entity.getBlueprintCode(),
                entity.getBlueprintVersion(),
                entity.getDomainExtensionCode(),
                entity.getDomainExtensionVersion(),
                entity.getProductionMode(),
                entity.getGenerationMode(),
                entity.getStatus(),
                entity.getBrief(),
                entity.getCoverUrl(),
                entity.getChannels(),
                entity.getGraphRevision(),
                entity.getPrimaryBrandProfileId(),
                null,
                entity.getBudgetLimit(),
                entity.getCostUsed(),
                entity.getLastActiveTime(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    @Override
    protected ContentProject toEntity(ContentProjectCreateDTO dto) {
        var entity = ContentProjectConvert.INSTANCE.toEntity(dto);
        entity.setUserId(
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(() -> exception(GlobalErrorCode.UNAUTHORIZED)));
        return entity;
    }

    @Override
    protected void updateEntity(ContentProject entity, ContentProjectUpdateDTO dto) {
        entity.setVersion(
                ContentPatchSupport.requireVersion(entity.getVersion(), dto.expectedVersion()));
        ContentPatchSupport.required(dto.name(), "name", entity::setName);
        ContentPatchSupport.required(
                dto.projectTypeCode(), "projectTypeCode", entity::setProjectTypeCode);
        ContentPatchSupport.nullable(dto.blueprintCode(), entity::setBlueprintCode);
        ContentPatchSupport.nullable(dto.blueprintVersion(), entity::setBlueprintVersion);
        ContentPatchSupport.nullable(dto.domainExtensionCode(), entity::setDomainExtensionCode);
        ContentPatchSupport.nullable(
                dto.domainExtensionVersion(), entity::setDomainExtensionVersion);
        ContentPatchSupport.required(
                dto.productionMode(), "productionMode", entity::setProductionMode);
        ContentPatchSupport.required(
                dto.generationMode(), "generationMode", entity::setGenerationMode);
        ContentPatchSupport.required(dto.status(), "status", entity::setStatus);
        ContentPatchSupport.nullable(dto.brief(), entity::setBrief);
        ContentPatchSupport.nullable(dto.coverUrl(), entity::setCoverUrl);
        ContentPatchSupport.nullable(dto.channels(), entity::setChannels);
        ContentPatchSupport.nullable(dto.configSnapshot(), entity::setConfigSnapshot);
        ContentPatchSupport.required(
                dto.graphRevision(), "graphRevision", entity::setGraphRevision);
        ContentPatchSupport.nullable(dto.primaryBrandProfileId(), entity::setPrimaryBrandProfileId);
        ContentPatchSupport.nullable(dto.assistantId(), entity::setAssistantId);
        ContentPatchSupport.nullable(dto.budgetLimit(), entity::setBudgetLimit);
        ContentPatchSupport.required(dto.costUsed(), "costUsed", entity::setCostUsed);
        ContentPatchSupport.nullable(dto.lastActiveTime(), entity::setLastActiveTime);
    }

    @Override
    protected Specification<ContentProject> buildSpec(ContentProjectPageDTO request) {
        return SpecificationBuilder.<ContentProject>builder()
                .eqIfPresent("status", request.getStatus())
                .eqIfPresent("projectTypeCode", request.getProjectTypeCode())
                .eqIfPresent("productionMode", request.getProductionMode())
                .build();
    }
}
