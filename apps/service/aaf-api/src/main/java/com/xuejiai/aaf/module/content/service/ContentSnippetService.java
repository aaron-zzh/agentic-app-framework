package com.xuejiai.aaf.module.content.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.content.domain.ContentSnippet;
import com.xuejiai.aaf.module.content.mapper.ContentSnippetConvert;
import com.xuejiai.aaf.module.content.repository.ContentSnippetRepository;
import com.xuejiai.aaf.module.content.vo.ContentSnippetCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentSnippetPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentSnippetUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentSnippetVO;

import lombok.RequiredArgsConstructor;

/**
 * 创作片段 CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentSnippetService
        extends BaseCrudService<
                ContentSnippet,
                ContentSnippetVO,
                ContentSnippetCreateDTO,
                ContentSnippetUpdateDTO,
                ContentSnippetPageDTO> {

    private final ContentSnippetRepository repository;

    @Override
    protected ContentSnippetRepository getRepository() {
        return repository;
    }

    @Override
    protected ContentSnippetVO toVO(ContentSnippet entity) {
        return new ContentSnippetVO(
                entity.getId(),
                entity.getName(),
                entity.getCategory(),
                entity.getContent(),
                entity.getReferenceImageUrls(),
                entity.getVariableSlots(),
                entity.getProjectTypeCode(),
                entity.getBrandProfileId(),
                entity.getUseCount(),
                entity.getIsPublic());
    }

    @Override
    protected ContentSnippet toEntity(ContentSnippetCreateDTO dto) {
        var entity = ContentSnippetConvert.INSTANCE.toEntity(dto);
        return entity;
    }

    @Override
    protected void updateEntity(ContentSnippet entity, ContentSnippetUpdateDTO dto) {
        entity.setVersion(
                ContentPatchSupport.requireVersion(entity.getVersion(), dto.expectedVersion()));
        ContentPatchSupport.required(dto.name(), "name", entity::setName);
        ContentPatchSupport.nullable(dto.category(), entity::setCategory);
        ContentPatchSupport.nullable(dto.content(), entity::setContent);
        ContentPatchSupport.nullable(dto.referenceImageUrls(), entity::setReferenceImageUrls);
        ContentPatchSupport.nullable(dto.variableSlots(), entity::setVariableSlots);
        ContentPatchSupport.nullable(dto.projectTypeCode(), entity::setProjectTypeCode);
        ContentPatchSupport.nullable(dto.brandProfileId(), entity::setBrandProfileId);
        ContentPatchSupport.required(dto.useCount(), "useCount", entity::setUseCount);
        ContentPatchSupport.required(dto.isPublic(), "isPublic", entity::setIsPublic);
    }

    @Override
    protected Specification<ContentSnippet> buildSpec(ContentSnippetPageDTO request) {
        return SpecificationBuilder.<ContentSnippet>builder()
                .eqIfPresent("category", request.getCategory())
                .eqIfPresent("projectTypeCode", request.getProjectTypeCode())
                .build();
    }
}
