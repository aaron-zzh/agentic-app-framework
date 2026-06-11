package com.xuejiai.aaf.module.ai.aigc.image.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.ai.aigc.image.domain.GenerationTemplate;

/** 生成参数模板仓储。 */
public interface GenerationTemplateRepository
        extends JpaRepository<GenerationTemplate, Long>,
                JpaSpecificationExecutor<GenerationTemplate> {

    List<GenerationTemplate> findByUserIdOrIsPublicTrue(Long userId);
}
