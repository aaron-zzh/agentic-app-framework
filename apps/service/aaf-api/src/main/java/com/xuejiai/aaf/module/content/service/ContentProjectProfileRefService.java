package com.xuejiai.aaf.module.content.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.content.domain.ContentProjectProfileRef;
import com.xuejiai.aaf.module.content.mapper.ContentProjectProfileRefConvert;
import com.xuejiai.aaf.module.content.repository.ContentProjectProfileRefRepository;
import com.xuejiai.aaf.module.content.vo.ContentProjectProfileRefCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectProfileRefPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectProfileRefUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectProfileRefVO;

import lombok.RequiredArgsConstructor;

/**
 * 项目资料引用 CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentProjectProfileRefService
        extends BaseCrudService<
                ContentProjectProfileRef,
                ContentProjectProfileRefVO,
                ContentProjectProfileRefCreateDTO,
                ContentProjectProfileRefUpdateDTO,
                ContentProjectProfileRefPageDTO> {

    private final ContentProjectProfileRefRepository repository;

    @Override
    protected ContentProjectProfileRefRepository getRepository() {
        return repository;
    }

    @Override
    protected ContentProjectProfileRefVO toVO(ContentProjectProfileRef entity) {
        return new ContentProjectProfileRefVO(
                entity.getId(),
                entity.getProjectId(),
                entity.getBrandProfileId(),
                entity.getRefScope(),
                entity.getProfileVersion(),
                entity.getScopeNote(),
                null);
    }

    @Override
    protected ContentProjectProfileRef toEntity(ContentProjectProfileRefCreateDTO dto) {
        var entity = ContentProjectProfileRefConvert.INSTANCE.toEntity(dto);
        return entity;
    }

    @Override
    protected void updateEntity(
            ContentProjectProfileRef entity, ContentProjectProfileRefUpdateDTO dto) {
        entity.setVersion(
                ContentPatchSupport.requireVersion(entity.getVersion(), dto.expectedVersion()));
        ContentPatchSupport.required(dto.projectId(), "projectId", entity::setProjectId);
        ContentPatchSupport.required(
                dto.brandProfileId(), "brandProfileId", entity::setBrandProfileId);
        ContentPatchSupport.required(dto.refScope(), "refScope", entity::setRefScope);
        ContentPatchSupport.nullable(dto.profileVersion(), entity::setProfileVersion);
        ContentPatchSupport.nullable(dto.scopeNote(), entity::setScopeNote);
    }

    @Override
    protected Specification<ContentProjectProfileRef> buildSpec(
            ContentProjectProfileRefPageDTO request) {
        return SpecificationBuilder.<ContentProjectProfileRef>builder()
                .eqIfPresent("projectId", request.getProjectId())
                .build();
    }
}
