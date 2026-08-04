package com.xuejiai.aaf.module.ai.aigc.media.service;

import java.util.Objects;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAssetCategory;
import com.xuejiai.aaf.module.ai.aigc.media.repository.AigcAssetCategoryRepository;
import com.xuejiai.aaf.module.ai.aigc.media.repository.AigcAssetRepository;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCategoryCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCategoryPageDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCategoryUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCategoryVO;

import lombok.RequiredArgsConstructor;

/** 资产分类管理。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcAssetCategoryService
        extends BaseCrudService<
                AigcAssetCategory,
                AigcAssetCategoryVO,
                AigcAssetCategoryCreateDTO,
                AigcAssetCategoryUpdateDTO,
                AigcAssetCategoryPageDTO> {

    private final AigcAssetCategoryRepository repository;
    private final AigcAssetRepository assetRepository;

    @Override
    protected AigcAssetCategoryRepository getRepository() {
        return repository;
    }

    @Override
    protected AigcAssetCategoryVO toVO(AigcAssetCategory entity) {
        return new AigcAssetCategoryVO(
                entity.getId(),
                entity.getVersion(),
                entity.getName(),
                entity.getParentId(),
                entity.getSortOrder(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    @Override
    protected AigcAssetCategory toEntity(AigcAssetCategoryCreateDTO request) {
        requireParent(null, request.parentId());
        var entity = new AigcAssetCategory();
        entity.setName(request.name());
        entity.setParentId(request.parentId());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        return entity;
    }

    @Override
    protected void updateEntity(AigcAssetCategory entity, AigcAssetCategoryUpdateDTO request) {
        requireParent(entity.getId(), request.parentId());
        entity.setName(request.name());
        entity.setParentId(request.parentId());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    @Override
    protected void beforeDelete(AigcAssetCategory entity) {
        if (repository.existsByParentIdAndDeletedFalse(entity.getId())
                || assetRepository.existsByCategoryIdAndDeletedFalse(entity.getId())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "分类仍被子分类或资产引用");
        }
    }

    @Override
    protected Specification<AigcAssetCategory> buildSpec(AigcAssetCategoryPageDTO request) {
        return SpecificationBuilder.<AigcAssetCategory>builder()
                .likeIfPresent("name", request.getName())
                .eqIfPresent("parentId", request.getParentId())
                .build();
    }

    private void requireParent(Long categoryId, Long parentId) {
        if (parentId == null) return;
        if (Objects.equals(categoryId, parentId)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "分类不能以自身为父级");
        }
        var cursor = requireEntity(parentId, CrudOperation.GET, AccessMode.DEFAULT);
        var visited = new java.util.HashSet<Long>();
        while (cursor != null) {
            if (!visited.add(cursor.getId()) || Objects.equals(cursor.getParentId(), categoryId)) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "分类层级不能形成环");
            }
            cursor =
                    cursor.getParentId() == null
                            ? null
                            : requireEntity(
                                    cursor.getParentId(), CrudOperation.GET, AccessMode.DEFAULT);
        }
    }
}
