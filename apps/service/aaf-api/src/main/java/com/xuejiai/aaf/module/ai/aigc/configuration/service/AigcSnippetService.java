package com.xuejiai.aaf.module.ai.aigc.configuration.service;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcSnippet;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcSnippetRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcSnippetCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcSnippetPageDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcSnippetUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcSnippetVO;

import lombok.RequiredArgsConstructor;

/** 创作片段管理根服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcSnippetService
        extends BaseCrudService<
                AigcSnippet,
                AigcSnippetVO,
                AigcSnippetCreateDTO,
                AigcSnippetUpdateDTO,
                AigcSnippetPageDTO> {

    private final AigcSnippetRepository repository;

    @Override
    protected AigcSnippetRepository getRepository() {
        return repository;
    }

    @Override
    protected AigcSnippetVO toVO(AigcSnippet entity) {
        return new AigcSnippetVO(
                entity.getId(),
                entity.getVersion(),
                entity.getName(),
                entity.getCategory(),
                entity.getContent(),
                entity.getReferenceMediaVersionIds(),
                entity.getVariableSlots(),
                entity.getProjectTypeCode(),
                entity.getBrandProfileId(),
                entity.getUseCount(),
                entity.getIsPublic());
    }

    @Override
    protected AigcSnippet toEntity(AigcSnippetCreateDTO dto) {
        var entity = new AigcSnippet();
        entity.setName(dto.name());
        entity.setCategory(dto.category());
        entity.setContent(dto.content());
        entity.setReferenceMediaVersionIds(
                dto.referenceMediaVersionIds() == null
                        ? List.of()
                        : List.copyOf(dto.referenceMediaVersionIds()));
        entity.setVariableSlots(dto.variableSlots());
        entity.setProjectTypeCode(dto.projectTypeCode());
        entity.setBrandProfileId(dto.brandProfileId());
        entity.setUseCount(dto.useCount() == null ? 0 : dto.useCount());
        entity.setIsPublic(Boolean.TRUE.equals(dto.isPublic()));
        return entity;
    }

    @Override
    protected void updateEntity(AigcSnippet entity, AigcSnippetUpdateDTO dto) {
        entity.setVersion(
                AigcConfigurationPatchSupport.requireVersion(
                        entity.getVersion(), dto.expectedVersion()));
        AigcConfigurationPatchSupport.required(dto.name(), "name", entity::setName);
        AigcConfigurationPatchSupport.nullable(dto.category(), entity::setCategory);
        AigcConfigurationPatchSupport.nullable(dto.content(), entity::setContent);
        AigcConfigurationPatchSupport.required(
                dto.referenceMediaVersionIds(),
                "referenceMediaVersionIds",
                value -> entity.setReferenceMediaVersionIds(List.copyOf(value)));
        AigcConfigurationPatchSupport.nullable(dto.variableSlots(), entity::setVariableSlots);
        AigcConfigurationPatchSupport.nullable(dto.projectTypeCode(), entity::setProjectTypeCode);
        AigcConfigurationPatchSupport.nullable(dto.brandProfileId(), entity::setBrandProfileId);
        AigcConfigurationPatchSupport.required(dto.useCount(), "useCount", entity::setUseCount);
        AigcConfigurationPatchSupport.required(dto.isPublic(), "isPublic", entity::setIsPublic);
    }

    @Override
    protected Specification<AigcSnippet> buildSpec(AigcSnippetPageDTO request) {
        return SpecificationBuilder.<AigcSnippet>builder()
                .eqIfPresent("category", request.getCategory())
                .eqIfPresent("projectTypeCode", request.getProjectTypeCode())
                .build();
    }
}
