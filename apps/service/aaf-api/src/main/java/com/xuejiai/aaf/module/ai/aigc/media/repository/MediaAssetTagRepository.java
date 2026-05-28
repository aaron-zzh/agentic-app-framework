package com.xuejiai.aaf.module.ai.aigc.media.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.ai.aigc.media.domain.MediaAssetTag;
import com.xuejiai.aaf.module.ai.aigc.media.domain.MediaAssetTagId;

/** 素材-标签关联仓储。 */
public interface MediaAssetTagRepository extends JpaRepository<MediaAssetTag, MediaAssetTagId> {

    List<MediaAssetTag> findByAssetId(Long assetId);

    void deleteByAssetId(Long assetId);

    @Query("SELECT mat.assetId FROM MediaAssetTag mat WHERE mat.tagId IN :tagIds")
    List<Long> findAssetIdsByTagIds(List<Long> tagIds);
}
