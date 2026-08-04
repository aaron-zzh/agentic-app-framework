package com.xuejiai.aaf.module.ai.aigc.media.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAssetCollectionItem;

/** 资产集合成员仓储，仅由集合聚合服务使用。 */
public interface AigcAssetCollectionItemRepository
        extends JpaRepository<AigcAssetCollectionItem, Long> {

    List<AigcAssetCollectionItem> findByCollectionIdAndDeletedFalseOrderBySortOrderAscIdAsc(
            Long collectionId);

    Optional<AigcAssetCollectionItem> findByIdAndCollectionIdAndDeletedFalse(
            Long id, Long collectionId);

    Optional<AigcAssetCollectionItem> findByCollectionIdAndAssetIdAndDeletedFalse(
            Long collectionId, Long assetId);

    boolean existsByCollectionIdAndDeletedFalse(Long collectionId);
}
