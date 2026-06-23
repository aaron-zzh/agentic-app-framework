package com.xuejiai.aaf.module.ai.aigc.media.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.media.domain.MediaCategory;

/** 素材分类仓储。 */
public interface MediaCategoryRepository extends JpaRepository<MediaCategory, Long> {

    List<MediaCategory> findByParentIdOrderBySortOrder(Long parentId);

    List<MediaCategory> findAllByOrderBySortOrder();

    List<MediaCategory> findByOwnerIdOrderBySortOrder(Long ownerId);

    Optional<MediaCategory> findByName(String name);
}
