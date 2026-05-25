package com.xuejiai.aaf.module.aigc.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.aigc.domain.VideoTemplate;

public interface VideoTemplateRepository extends JpaRepository<VideoTemplate, Long> {

    List<VideoTemplate> findByType(String type);
}
