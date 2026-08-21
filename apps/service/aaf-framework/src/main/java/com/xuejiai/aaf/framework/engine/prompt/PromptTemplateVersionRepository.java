package com.xuejiai.aaf.framework.engine.prompt;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 提示词不可变内容版本数据访问。 */
public interface PromptTemplateVersionRepository
        extends JpaRepository<PromptTemplateVersion, Long> {

    Optional<PromptTemplateVersion> findByTemplateCodeAndTemplateVersionAndStatusAndDeletedFalse(
            String code, Integer templateVersion, PromptVersionStatus status);

    Optional<PromptTemplateVersion> findByTemplateCodeAndTemplateVersionAndDeletedFalse(
            String code, Integer templateVersion);

    Optional<PromptTemplateVersion> findTopByTemplateCodeAndDeletedFalseOrderByTemplateVersionDesc(
            String code);

    List<PromptTemplateVersion> findByTemplateIdAndDeletedFalseOrderByTemplateVersionDesc(Long id);

    Optional<PromptTemplateVersion>
            findTopByTemplateIdAndStatusAndDeletedFalseOrderByTemplateVersionDesc(
                    Long id, PromptVersionStatus status);

    Optional<PromptTemplateVersion> findByTemplateIdAndTemplateVersionAndStatusAndDeletedFalse(
            Long id, Integer templateVersion, PromptVersionStatus status);
}
