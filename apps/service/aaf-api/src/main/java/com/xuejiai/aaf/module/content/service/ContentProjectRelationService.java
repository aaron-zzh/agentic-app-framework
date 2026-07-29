package com.xuejiai.aaf.module.content.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.content.domain.ContentProjectRelation;
import com.xuejiai.aaf.module.content.mapper.ContentProjectRelationConvert;
import com.xuejiai.aaf.module.content.repository.ContentProjectRelationRepository;
import com.xuejiai.aaf.module.content.vo.ContentProjectRelationCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectRelationPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectRelationUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectRelationVO;

import lombok.RequiredArgsConstructor;

/**
 * 项目关系 CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentProjectRelationService
        extends BaseCrudService<
                ContentProjectRelation,
                ContentProjectRelationVO,
                ContentProjectRelationCreateDTO,
                ContentProjectRelationUpdateDTO,
                ContentProjectRelationPageDTO> {

    private final ContentProjectRelationRepository repository;

    @Override
    protected ContentProjectRelationRepository getRepository() {
        return repository;
    }

    @Override
    protected ContentProjectRelationVO toVO(ContentProjectRelation entity) {
        return new ContentProjectRelationVO(
                entity.getId(),
                entity.getProjectId(),
                entity.getRelationType(),
                entity.getLayer(),
                entity.getSourceObjectId(),
                entity.getTargetObjectId(),
                entity.getRelationMeta());
    }

    @Override
    protected ContentProjectRelation toEntity(ContentProjectRelationCreateDTO dto) {
        var entity = ContentProjectRelationConvert.INSTANCE.toEntity(dto);
        return entity;
    }

    @Override
    protected void updateEntity(
            ContentProjectRelation entity, ContentProjectRelationUpdateDTO dto) {
        entity.setVersion(
                ContentPatchSupport.requireVersion(entity.getVersion(), dto.expectedVersion()));
        ContentPatchSupport.required(dto.projectId(), "projectId", entity::setProjectId);
        ContentPatchSupport.required(dto.relationType(), "relationType", entity::setRelationType);
        ContentPatchSupport.required(dto.layer(), "layer", entity::setLayer);
        ContentPatchSupport.required(
                dto.sourceObjectId(), "sourceObjectId", entity::setSourceObjectId);
        ContentPatchSupport.required(
                dto.targetObjectId(), "targetObjectId", entity::setTargetObjectId);
        ContentPatchSupport.nullable(dto.relationMeta(), entity::setRelationMeta);
    }

    @Override
    protected Specification<ContentProjectRelation> buildSpec(
            ContentProjectRelationPageDTO request) {
        return SpecificationBuilder.<ContentProjectRelation>builder()
                .eqIfPresent("projectId", request.getProjectId())
                .eqIfPresent("relationType", request.getRelationType())
                .eqIfPresent("layer", request.getLayer())
                .build();
    }
}
