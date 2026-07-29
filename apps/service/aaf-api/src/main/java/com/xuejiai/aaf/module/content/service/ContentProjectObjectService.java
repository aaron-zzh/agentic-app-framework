package com.xuejiai.aaf.module.content.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.content.domain.ContentProjectObject;
import com.xuejiai.aaf.module.content.mapper.ContentProjectObjectConvert;
import com.xuejiai.aaf.module.content.repository.ContentProjectObjectRepository;
import com.xuejiai.aaf.module.content.vo.ContentProjectObjectCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectObjectPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectObjectUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectObjectVO;

import lombok.RequiredArgsConstructor;

/**
 * 项目对象 CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentProjectObjectService
        extends BaseCrudService<
                ContentProjectObject,
                ContentProjectObjectVO,
                ContentProjectObjectCreateDTO,
                ContentProjectObjectUpdateDTO,
                ContentProjectObjectPageDTO> {

    private final ContentProjectObjectRepository repository;

    @Override
    protected ContentProjectObjectRepository getRepository() {
        return repository;
    }

    @Override
    protected ContentProjectObjectVO toVO(ContentProjectObject entity) {
        return new ContentProjectObjectVO(
                entity.getId(),
                entity.getVersion(),
                entity.getProjectId(),
                entity.getObjectType(),
                entity.getObjectKey(),
                entity.getBlueprintNodeKey(),
                entity.getParentId(),
                entity.getSortOrder(),
                entity.getTitle(),
                entity.getStatus(),
                entity.getSource(),
                entity.getSchemaVersion(),
                entity.getEntityResource(),
                entity.getEntityId(),
                entity.getAdoptedVersionRef(),
                entity.getSummary(),
                entity.getPayload());
    }

    @Override
    protected ContentProjectObject toEntity(ContentProjectObjectCreateDTO dto) {
        var entity = ContentProjectObjectConvert.INSTANCE.toEntity(dto);
        return entity;
    }

    @Override
    protected void updateEntity(ContentProjectObject entity, ContentProjectObjectUpdateDTO dto) {
        entity.setVersion(
                ContentPatchSupport.requireVersion(entity.getVersion(), dto.expectedVersion()));
        ContentPatchSupport.required(dto.projectId(), "projectId", entity::setProjectId);
        ContentPatchSupport.required(dto.objectType(), "objectType", entity::setObjectType);
        ContentPatchSupport.required(dto.objectKey(), "objectKey", entity::setObjectKey);
        ContentPatchSupport.nullable(dto.blueprintNodeKey(), entity::setBlueprintNodeKey);
        ContentPatchSupport.nullable(dto.parentId(), entity::setParentId);
        ContentPatchSupport.required(dto.sortOrder(), "sortOrder", entity::setSortOrder);
        ContentPatchSupport.nullable(dto.title(), entity::setTitle);
        ContentPatchSupport.required(dto.status(), "status", entity::setStatus);
        ContentPatchSupport.required(dto.source(), "source", entity::setSource);
        ContentPatchSupport.nullable(dto.schemaVersion(), entity::setSchemaVersion);
        ContentPatchSupport.nullable(dto.entityResource(), entity::setEntityResource);
        ContentPatchSupport.nullable(dto.entityId(), entity::setEntityId);
        ContentPatchSupport.nullable(dto.adoptedVersionRef(), entity::setAdoptedVersionRef);
        ContentPatchSupport.nullable(dto.summary(), entity::setSummary);
        ContentPatchSupport.nullable(dto.payload(), entity::setPayload);
    }

    @Override
    protected Specification<ContentProjectObject> buildSpec(ContentProjectObjectPageDTO request) {
        return SpecificationBuilder.<ContentProjectObject>builder()
                .eqIfPresent("projectId", request.getProjectId())
                .eqIfPresent("objectType", request.getObjectType())
                .eqIfPresent("status", request.getStatus())
                .build();
    }
}
