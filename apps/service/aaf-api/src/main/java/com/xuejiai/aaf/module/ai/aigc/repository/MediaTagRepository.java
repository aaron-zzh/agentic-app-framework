package com.xuejiai.aaf.module.ai.aigc.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.domain.MediaTag;

/** 素材标签仓储。 */
public interface MediaTagRepository extends JpaRepository<MediaTag, Long> {

    List<MediaTag> findByNameIn(List<String> names);
}
