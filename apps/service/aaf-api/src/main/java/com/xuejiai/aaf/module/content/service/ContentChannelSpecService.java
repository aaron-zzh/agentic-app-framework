package com.xuejiai.aaf.module.content.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.content.domain.ContentChannelSpec;
import com.xuejiai.aaf.module.content.mapper.ContentChannelSpecConvert;
import com.xuejiai.aaf.module.content.repository.ContentChannelSpecRepository;
import com.xuejiai.aaf.module.content.vo.ContentChannelSpecCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentChannelSpecPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentChannelSpecUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentChannelSpecVO;

import lombok.RequiredArgsConstructor;

/**
 * 渠道规格 CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentChannelSpecService
        extends BaseCrudService<
                ContentChannelSpec,
                ContentChannelSpecVO,
                ContentChannelSpecCreateDTO,
                ContentChannelSpecUpdateDTO,
                ContentChannelSpecPageDTO> {

    private final ContentChannelSpecRepository repository;

    @Override
    protected ContentChannelSpecRepository getRepository() {
        return repository;
    }

    @Override
    protected ContentChannelSpecVO toVO(ContentChannelSpec entity) {
        return new ContentChannelSpecVO(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getSpecVersion(),
                entity.getAspectRatio(),
                entity.getWidth(),
                entity.getHeight(),
                entity.getMaxDurationSeconds(),
                entity.getRequiredDisclaimers(),
                entity.getExportFormat(),
                entity.getSortOrder(),
                entity.getStatus());
    }

    @Override
    protected ContentChannelSpec toEntity(ContentChannelSpecCreateDTO dto) {
        var entity = ContentChannelSpecConvert.INSTANCE.toEntity(dto);
        return entity;
    }

    @Override
    protected void updateEntity(ContentChannelSpec entity, ContentChannelSpecUpdateDTO dto) {
        entity.setVersion(
                ContentPatchSupport.requireVersion(entity.getVersion(), dto.expectedVersion()));
        ContentPatchSupport.required(dto.code(), "code", entity::setCode);
        ContentPatchSupport.required(dto.name(), "name", entity::setName);
        ContentPatchSupport.required(dto.specVersion(), "specVersion", entity::setSpecVersion);
        ContentPatchSupport.nullable(dto.aspectRatio(), entity::setAspectRatio);
        ContentPatchSupport.nullable(dto.width(), entity::setWidth);
        ContentPatchSupport.nullable(dto.height(), entity::setHeight);
        ContentPatchSupport.nullable(dto.maxDurationSeconds(), entity::setMaxDurationSeconds);
        ContentPatchSupport.nullable(dto.copyStructure(), entity::setCopyStructure);
        ContentPatchSupport.nullable(dto.requiredDisclaimers(), entity::setRequiredDisclaimers);
        ContentPatchSupport.nullable(dto.exportFormat(), entity::setExportFormat);
        ContentPatchSupport.required(dto.sortOrder(), "sortOrder", entity::setSortOrder);
        ContentPatchSupport.required(dto.status(), "status", entity::setStatus);
    }

    @Override
    protected Specification<ContentChannelSpec> buildSpec(ContentChannelSpecPageDTO request) {
        return SpecificationBuilder.<ContentChannelSpec>builder()
                .eqIfPresent("status", request.getStatus())
                .build();
    }
}
