package com.xuejiai.aaf.module.content.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.content.domain.ContentProjectBlueprint;
import com.xuejiai.aaf.module.content.mapper.ContentProjectBlueprintConvert;
import com.xuejiai.aaf.module.content.repository.ContentProjectBlueprintRepository;
import com.xuejiai.aaf.module.content.vo.ContentBlueprintVO;
import com.xuejiai.aaf.module.content.vo.ContentProjectBlueprintCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectBlueprintPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectBlueprintUpdateDTO;

import lombok.RequiredArgsConstructor;

/**
 * 项目蓝图 CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentProjectBlueprintService
        extends BaseCrudService<
                ContentProjectBlueprint,
                ContentBlueprintVO,
                ContentProjectBlueprintCreateDTO,
                ContentProjectBlueprintUpdateDTO,
                ContentProjectBlueprintPageDTO> {

    private final ContentProjectBlueprintRepository repository;

    @Override
    protected ContentProjectBlueprintRepository getRepository() {
        return repository;
    }

    @Override
    protected ContentBlueprintVO toVO(ContentProjectBlueprint entity) {
        return new ContentBlueprintVO(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getProjectTypeCode(),
                entity.getBlueprintVersion(),
                entity.getProductionMode(),
                entity.getDescription(),
                entity.getStatus());
    }

    @Override
    protected ContentProjectBlueprint toEntity(ContentProjectBlueprintCreateDTO dto) {
        var entity = ContentProjectBlueprintConvert.INSTANCE.toEntity(dto);
        return entity;
    }

    @Override
    protected void updateEntity(
            ContentProjectBlueprint entity, ContentProjectBlueprintUpdateDTO dto) {
        entity.setVersion(
                ContentPatchSupport.requireVersion(entity.getVersion(), dto.expectedVersion()));
        ContentPatchSupport.required(dto.code(), "code", entity::setCode);
        ContentPatchSupport.required(dto.name(), "name", entity::setName);
        ContentPatchSupport.required(
                dto.projectTypeCode(), "projectTypeCode", entity::setProjectTypeCode);
        ContentPatchSupport.required(
                dto.blueprintVersion(), "blueprintVersion", entity::setBlueprintVersion);
        ContentPatchSupport.required(
                dto.productionMode(), "productionMode", entity::setProductionMode);
        ContentPatchSupport.nullable(dto.description(), entity::setDescription);
        ContentPatchSupport.required(dto.status(), "status", entity::setStatus);
        ContentPatchSupport.nullable(dto.objectSpec(), entity::setObjectSpec);
        ContentPatchSupport.nullable(dto.relationSpec(), entity::setRelationSpec);
        ContentPatchSupport.nullable(dto.deliverableSpec(), entity::setDeliverableSpec);
        ContentPatchSupport.nullable(dto.actionKeys(), entity::setActionKeys);
        ContentPatchSupport.nullable(dto.confirmationGates(), entity::setConfirmationGates);
        ContentPatchSupport.nullable(dto.briefFields(), entity::setBriefFields);
    }

    @Override
    protected Specification<ContentProjectBlueprint> buildSpec(
            ContentProjectBlueprintPageDTO request) {
        return SpecificationBuilder.<ContentProjectBlueprint>builder()
                .eqIfPresent("projectTypeCode", request.getProjectTypeCode())
                .eqIfPresent("productionMode", request.getProductionMode())
                .eqIfPresent("status", request.getStatus())
                .build();
    }
}
