package com.xuejiai.aaf.framework.engine.prompt;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Prompt 分类字典数据访问。 */
public interface PromptCategoryRepository extends JpaRepository<PromptCategory, Long> {

    List<PromptCategory> findAllByCodeInAndEnabledTrueAndDeletedFalse(Collection<String> codes);
}
