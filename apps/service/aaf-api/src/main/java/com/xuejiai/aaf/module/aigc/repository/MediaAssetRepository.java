package com.xuejiai.aaf.module.aigc.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.aigc.domain.MediaAsset;
import com.xuejiai.aaf.module.aigc.enums.MediaAssetType;

/** 素材库仓储。 */
public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    Page<MediaAsset> findByUserId(Long userId, Pageable pageable);

    Page<MediaAsset> findByUserIdAndType(Long userId, MediaAssetType type, Pageable pageable);

    List<MediaAsset> findByCategoryId(Long categoryId);
}
