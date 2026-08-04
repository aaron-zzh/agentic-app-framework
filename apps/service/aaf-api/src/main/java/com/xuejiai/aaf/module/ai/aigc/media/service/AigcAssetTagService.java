package com.xuejiai.aaf.module.ai.aigc.media.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAssetTag;
import com.xuejiai.aaf.module.ai.aigc.media.repository.AigcAssetTagRepository;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetTagCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetTagPageDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetTagUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetTagVO;

import lombok.RequiredArgsConstructor;

/** 资产标签管理。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcAssetTagService
        extends BaseCrudService<
                AigcAssetTag,
                AigcAssetTagVO,
                AigcAssetTagCreateDTO,
                AigcAssetTagUpdateDTO,
                AigcAssetTagPageDTO> {

    private final AigcAssetTagRepository repository;

    @Override
    protected AigcAssetTagRepository getRepository() {
        return repository;
    }

    @Override
    protected AigcAssetTagVO toVO(AigcAssetTag entity) {
        return new AigcAssetTagVO(
                entity.getId(),
                entity.getVersion(),
                entity.getName(),
                entity.getColor(),
                entity.getUsageCount(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    @Override
    protected AigcAssetTag toEntity(AigcAssetTagCreateDTO request) {
        var entity = new AigcAssetTag();
        entity.setName(request.name());
        entity.setColor(request.color());
        entity.setUsageCount(0);
        return entity;
    }

    @Override
    protected void updateEntity(AigcAssetTag entity, AigcAssetTagUpdateDTO request) {
        entity.setName(request.name());
        entity.setColor(request.color());
    }

    @Override
    protected void beforeDelete(AigcAssetTag entity) {
        if (repository.existsActiveAssetReference(entity.getId())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "标签仍被资产引用");
        }
    }

    @Override
    protected Specification<AigcAssetTag> buildSpec(AigcAssetTagPageDTO request) {
        return SpecificationBuilder.<AigcAssetTag>builder()
                .likeIfPresent("name", request.getName())
                .build();
    }
}
