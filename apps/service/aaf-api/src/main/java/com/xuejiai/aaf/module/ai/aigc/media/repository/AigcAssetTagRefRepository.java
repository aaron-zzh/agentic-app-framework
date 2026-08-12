package com.xuejiai.aaf.module.ai.aigc.media.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAssetTagRef;

/** Asset 聚合内部标签关系仓储。 */
public interface AigcAssetTagRefRepository
        extends JpaRepository<AigcAssetTagRef, AigcAssetTagRef.Id> {

    List<AigcAssetTagRef> findByAssetIdOrderByTagId(Long assetId);

    List<AigcAssetTagRef> findByAssetIdInAndDeletedFalseOrderByAssetIdAscTagIdAsc(
            Collection<Long> assetIds);

    long countByTagIdAndDeletedFalse(Long tagId);
}
