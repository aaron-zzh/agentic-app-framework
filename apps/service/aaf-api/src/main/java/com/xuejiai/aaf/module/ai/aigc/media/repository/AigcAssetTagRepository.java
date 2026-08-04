package com.xuejiai.aaf.module.ai.aigc.media.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAssetTag;

/** 资产标签仓储。 */
public interface AigcAssetTagRepository extends CrudEntityRepository<AigcAssetTag> {

    @Query(
            value =
                    """
                    SELECT EXISTS (
                        SELECT 1
                        FROM aigc_asset_tag_ref tag_ref
                        WHERE tag_ref.tag_id = :tagId
                          AND tag_ref.deleted = FALSE
                    )
                    """,
            nativeQuery = true)
    boolean existsActiveAssetReference(@Param("tagId") Long tagId);
}
