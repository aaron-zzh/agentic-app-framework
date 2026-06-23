package com.xuejiai.aaf.module.ai.aigc.media.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.ai.aigc.media.domain.MediaAsset;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaAssetType;

/** 素材库仓储。 */
public interface MediaAssetRepository
        extends JpaRepository<MediaAsset, Long>, JpaSpecificationExecutor<MediaAsset> {

    Page<MediaAsset> findByUserId(Long userId, Pageable pageable);

    Page<MediaAsset> findByUserIdAndProjectId(Long userId, Long projectId, Pageable pageable);

    Page<MediaAsset> findByUserIdAndType(Long userId, MediaAssetType type, Pageable pageable);

    Page<MediaAsset> findByUserIdAndCategoryId(Long userId, Long categoryId, Pageable pageable);

    List<MediaAsset> findByCategoryId(Long categoryId);

    List<MediaAsset> findByGroupId(Long groupId);

    /** 统计用户 AI 生成素材数量 */
    long countByUserIdAndAiGenerated(Long userId, boolean aiGenerated);

    @Query(
            "SELECT a FROM MediaAsset a WHERE a.userId = :userId AND a.deleted = false"
                    + " AND (LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))"
                    + " OR LOWER(a.tags) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<MediaAsset> searchByKeyword(
            @Param("userId") Long userId, @Param("keyword") String keyword);
}
