package com.xuejiai.aaf.module.ai.aigc.image.repository;

import java.util.List;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.image.domain.GenerationTemplate;

/** 生成参数模板仓储。 */
public interface GenerationTemplateRepository extends CrudEntityRepository<GenerationTemplate> {

    List<GenerationTemplate> findByUserIdOrIsPublicTrue(Long userId);
}
