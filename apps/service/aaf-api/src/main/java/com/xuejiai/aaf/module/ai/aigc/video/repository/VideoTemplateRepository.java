package com.xuejiai.aaf.module.ai.aigc.video.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.video.domain.VideoTemplate;

/** 视频模板仓储。 */
public interface VideoTemplateRepository extends JpaRepository<VideoTemplate, Long> {

    Page<VideoTemplate> findByType(String type, Pageable pageable);
}
