package com.xuejiai.aaf.module.ai.aigc.video.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.ai.aigc.video.domain.VideoTemplate;

/** 视频模板仓储。 */
public interface VideoTemplateRepository
        extends JpaRepository<VideoTemplate, Long>, JpaSpecificationExecutor<VideoTemplate> {}
