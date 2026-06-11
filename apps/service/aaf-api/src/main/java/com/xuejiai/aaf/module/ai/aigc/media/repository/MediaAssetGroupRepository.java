package com.xuejiai.aaf.module.ai.aigc.media.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.media.domain.MediaAssetGroup;

/** 素材组 Repository。 */
public interface MediaAssetGroupRepository extends JpaRepository<MediaAssetGroup, Long> {

    Page<MediaAssetGroup> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    List<MediaAssetGroup> findByUserIdOrderByCreateTimeDesc(Long userId);
}
