package com.xuejiai.aaf.module.ai.aigc.configuration.service;

import java.util.List;
import java.util.Objects;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcSnippet;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcSnippetRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcSnippetCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcSnippetPageDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcSnippetUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcSnippetVO;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;

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
    private final AigcMediaApi mediaApi;
    private final OperatorContext operatorContext;

    @Override
    protected AigcSnippetRepository getRepository() {
        return repository;
    }

    @Override
    protected AigcSnippetVO toVO(AigcSnippet entity) {
        var currentOwnerId = operatorContext.currentOwnerId().orElse(null);
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
                entity.getIsPublic(),
                entity.getOwnerId() == null,
                Objects.equals(entity.getOwnerId(), currentOwnerId));
    }

    @Override
    protected AigcSnippet toEntity(AigcSnippetCreateDTO dto) {
        var mediaVersionIds =
                dto.referenceMediaVersionIds() == null
                        ? List.<Long>of()
                        : List.copyOf(dto.referenceMediaVersionIds());
        lockMediaVersions(mediaVersionIds);
        var entity = new AigcSnippet();
        entity.setName(dto.name());
        entity.setCategory(dto.category());
        entity.setContent(dto.content());
        entity.setReferenceMediaVersionIds(mediaVersionIds);
        entity.setVariableSlots(dto.variableSlots());
        entity.setProjectTypeCode(dto.projectTypeCode());
        entity.setBrandProfileId(dto.brandProfileId());
        entity.setUseCount(dto.useCount() == null ? 0 : dto.useCount());
        entity.setIsPublic(Boolean.TRUE.equals(dto.isPublic()));
        return entity;
    }

    @Override
    protected void beforeUpdate(AigcSnippet entity, AigcSnippetUpdateDTO dto) {
        requireOwned(entity);
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
                value -> {
                    var mediaVersionIds = List.copyOf(value);
                    lockMediaVersions(mediaVersionIds);
                    entity.setReferenceMediaVersionIds(mediaVersionIds);
                });
        AigcConfigurationPatchSupport.nullable(dto.variableSlots(), entity::setVariableSlots);
        AigcConfigurationPatchSupport.nullable(dto.projectTypeCode(), entity::setProjectTypeCode);
        AigcConfigurationPatchSupport.nullable(dto.brandProfileId(), entity::setBrandProfileId);
        AigcConfigurationPatchSupport.required(dto.useCount(), "useCount", entity::setUseCount);
        AigcConfigurationPatchSupport.required(dto.isPublic(), "isPublic", entity::setIsPublic);
    }

    @Override
    protected void beforeDelete(AigcSnippet entity) {
        requireOwned(entity);
    }

    @Override
    protected Specification<AigcSnippet> buildSpec(AigcSnippetPageDTO request) {
        var builder =
                SpecificationBuilder.<AigcSnippet>builder()
                        .eqIfPresent("category", request.getCategory())
                        .eqIfPresent("projectTypeCode", request.getProjectTypeCode());
        if (Boolean.TRUE.equals(request.getOwnerOnly())) {
            builder.eqIfPresent("ownerId", operatorContext.currentOwnerId().orElse(-1L));
        }
        if (Boolean.TRUE.equals(request.getPublicOnly())) {
            builder.eqIfPresent("isPublic", true);
        }
        return builder.build();
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of("name", "content");
    }

    private void lockMediaVersions(List<Long> mediaVersionIds) {
        var userId = operatorContext.currentOwnerId().orElseThrow();
        mediaVersionIds.forEach(mediaVersionId -> mediaApi.getByVersionId(mediaVersionId, userId));
    }

    private void requireOwned(AigcSnippet entity) {
        var currentOwnerId = operatorContext.currentOwnerId().orElse(null);
        if (currentOwnerId == null || !Objects.equals(entity.getOwnerId(), currentOwnerId)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "创作片段不存在或无权操作");
        }
    }
}
