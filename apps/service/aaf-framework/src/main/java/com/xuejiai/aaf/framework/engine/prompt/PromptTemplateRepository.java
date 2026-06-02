/**
 * Prompt 模板仓库。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.engine.prompt;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Prompt 模板数据访问。 */
public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    Optional<PromptTemplate> findByNameAndActiveTrue(String name);

    Optional<PromptTemplate> findByNameAndTemplateVersion(String name, Integer templateVersion);

    List<PromptTemplate> findByNameOrderByTemplateVersionDesc(String name);

    List<PromptTemplate> findByCategory(String category);
}
