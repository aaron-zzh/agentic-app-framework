/**
 * Prompt 模板仓库。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.core.prompt;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Prompt 模板数据访问。 */
public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    Optional<PromptTemplate> findByNameAndActiveTrue(String name);

    Optional<PromptTemplate> findByNameAndVersion(String name, Integer version);

    List<PromptTemplate> findByNameOrderByVersionDesc(String name);

    List<PromptTemplate> findByCategory(String category);
}
