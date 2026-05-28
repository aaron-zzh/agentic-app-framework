package com.xuejiai.aaf.module.ai.aigc.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.domain.GenerationTemplate;

/** 生成参数模板仓储。 */
public interface GenerationTemplateRepository extends JpaRepository<GenerationTemplate, Long> {

    Page<GenerationTemplate> findByUserId(Long userId, Pageable pageable);

    Page<GenerationTemplate> findByCategory(String category, Pageable pageable);

    Page<GenerationTemplate> findByIsPublicTrue(Pageable pageable);

    List<GenerationTemplate> findByUserIdOrIsPublicTrue(Long userId);
}
