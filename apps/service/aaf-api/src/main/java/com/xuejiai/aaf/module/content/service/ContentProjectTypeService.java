package com.xuejiai.aaf.module.content.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.content.domain.ContentProjectType;
import com.xuejiai.aaf.module.content.mapper.ContentProjectTypeConvert;
import com.xuejiai.aaf.module.content.repository.ContentProjectTypeRepository;
import com.xuejiai.aaf.module.content.vo.ContentProjectTypeCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectTypePageDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectTypeUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectTypeVO;

import lombok.RequiredArgsConstructor;

/**
 * 项目类型 CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentProjectTypeService
        extends BaseCrudService<
                ContentProjectType,
                ContentProjectTypeVO,
                ContentProjectTypeCreateDTO,
                ContentProjectTypeUpdateDTO,
                ContentProjectTypePageDTO> {

    private final ContentProjectTypeRepository repository;

    @Override
    protected ContentProjectTypeRepository getRepository() {
        return repository;
    }

    @Override
    protected ContentProjectTypeVO toVO(ContentProjectType entity) {
        return new ContentProjectTypeVO(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getIcon(),
                entity.getDescription(),
                entity.getBriefPlaceholder(),
                entity.getDefaultChannels(),
                entity.getDefaultProductionMode(),
                entity.getQuickEntry(),
                entity.getBuiltin(),
                entity.getSortOrder(),
                entity.getStatus());
    }

    @Override
    protected ContentProjectType toEntity(ContentProjectTypeCreateDTO dto) {
        var entity = ContentProjectTypeConvert.INSTANCE.toEntity(dto);
        return entity;
    }

    @Override
    protected void updateEntity(ContentProjectType entity, ContentProjectTypeUpdateDTO dto) {
        entity.setVersion(
                ContentPatchSupport.requireVersion(entity.getVersion(), dto.expectedVersion()));
        ContentPatchSupport.required(dto.code(), "code", entity::setCode);
        ContentPatchSupport.required(dto.name(), "name", entity::setName);
        ContentPatchSupport.nullable(dto.icon(), entity::setIcon);
        ContentPatchSupport.nullable(dto.description(), entity::setDescription);
        ContentPatchSupport.nullable(dto.briefPlaceholder(), entity::setBriefPlaceholder);
        ContentPatchSupport.nullable(dto.defaultChannels(), entity::setDefaultChannels);
        ContentPatchSupport.nullable(dto.defaultProductionMode(), entity::setDefaultProductionMode);
        ContentPatchSupport.required(dto.quickEntry(), "quickEntry", entity::setQuickEntry);
        ContentPatchSupport.required(dto.builtin(), "builtin", entity::setBuiltin);
        ContentPatchSupport.required(dto.sortOrder(), "sortOrder", entity::setSortOrder);
        ContentPatchSupport.required(dto.status(), "status", entity::setStatus);
    }

    @Override
    protected Specification<ContentProjectType> buildSpec(ContentProjectTypePageDTO request) {
        return SpecificationBuilder.<ContentProjectType>builder().build();
    }
}
