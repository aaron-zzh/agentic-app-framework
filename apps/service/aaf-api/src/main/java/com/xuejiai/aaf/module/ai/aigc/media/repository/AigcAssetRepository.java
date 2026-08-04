package com.xuejiai.aaf.module.ai.aigc.media.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAsset;

/** AIGC 资产仓储。 */
public interface AigcAssetRepository extends CrudEntityRepository<AigcAsset> {
    Optional<AigcAsset> findByMediaId(Long mediaId);

    boolean existsByCategoryIdAndDeletedFalse(Long categoryId);

    @Query(
            value =
                    """
                    SELECT COUNT(*)
                    FROM aigc_asset_category
                    WHERE id = :categoryId
                      AND deleted = FALSE
                      AND (
                        owner_id = :userId
                        OR (:workspaceId IS NOT NULL AND workspace_id = :workspaceId)
                      )
                    """,
            nativeQuery = true)
    long countAccessibleCategory(
            @Param("categoryId") Long categoryId,
            @Param("userId") Long userId,
            @Param("workspaceId") Long workspaceId);
}
