package com.xuejiai.aaf.module.aigc.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.aigc.domain.MediaAssetVariant;

/** 素材变体关联仓储。 */
public interface MediaAssetVariantRepository extends JpaRepository<MediaAssetVariant, Long> {

    List<MediaAssetVariant> findByOriginalAssetId(Long originalAssetId);
}
