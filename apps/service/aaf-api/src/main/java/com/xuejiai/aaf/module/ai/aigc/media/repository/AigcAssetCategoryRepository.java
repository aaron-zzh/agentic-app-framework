package com.xuejiai.aaf.module.ai.aigc.media.repository;

import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAssetCategory;

/** 资产分类仓储。 */
public interface AigcAssetCategoryRepository extends CrudEntityRepository<AigcAssetCategory> {
    Optional<AigcAssetCategory> findByIdAndDeletedFalse(Long id);

    boolean existsByParentIdAndDeletedFalse(Long parentId);
}
