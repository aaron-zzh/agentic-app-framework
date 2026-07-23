package com.xuejiai.aaf.module.ai.aigc.video.service;

import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.ai.aigc.video.domain.VideoTemplate;
import com.xuejiai.aaf.module.ai.aigc.video.repository.VideoTemplateRepository;
import com.xuejiai.aaf.module.ai.aigc.video.vo.VideoTemplateCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.video.vo.VideoTemplatePageDTO;
import com.xuejiai.aaf.module.ai.aigc.video.vo.VideoTemplateUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.video.vo.VideoTemplateVO;

import lombok.RequiredArgsConstructor;

/**
 * 视频模板 CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VideoTemplateService
        extends BaseCrudService<
                VideoTemplate,
                VideoTemplateVO,
                VideoTemplateCreateDTO,
                VideoTemplateUpdateDTO,
                VideoTemplatePageDTO> {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "name", "type", "createTime", "updateTime");

    private final VideoTemplateRepository templateRepository;

    @Override
    protected VideoTemplateRepository getRepository() {
        return templateRepository;
    }

    @Override
    protected VideoTemplateVO toVO(VideoTemplate t) {
        return new VideoTemplateVO(
                t.getId(),
                t.getName(),
                t.getType(),
                t.getParams(),
                t.getPreviewUrl(),
                t.getThumbnailUrl());
    }

    @Override
    protected VideoTemplate toEntity(VideoTemplateCreateDTO dto) {
        var entity = new VideoTemplate();
        entity.setName(dto.name());
        entity.setType(dto.type());
        entity.setParams(dto.params());
        entity.setPreviewUrl(dto.previewUrl());
        entity.setThumbnailUrl(dto.thumbnailUrl());
        return entity;
    }

    @Override
    protected void updateEntity(VideoTemplate entity, VideoTemplateUpdateDTO dto) {
        if (dto.name() != null) entity.setName(dto.name());
        if (dto.type() != null) entity.setType(dto.type());
        if (dto.params() != null) entity.setParams(dto.params());
        if (dto.previewUrl() != null) entity.setPreviewUrl(dto.previewUrl());
        if (dto.thumbnailUrl() != null) entity.setThumbnailUrl(dto.thumbnailUrl());
    }

    @Override
    protected Specification<VideoTemplate> buildSpec(VideoTemplatePageDTO query) {
        return SpecificationBuilder.<VideoTemplate>builder()
                .eqIfPresent("type", query.getType())
                .build();
    }
}
