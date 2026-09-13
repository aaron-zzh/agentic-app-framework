package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.core.skill.SkillReference;

public interface SkillReferenceCatalogPort {

    Map<Long, List<SkillReference>> findByVersionIds(Set<Long> skillVersionIds);

    Optional<SkillReference> findByVersionIdAndKey(Long skillVersionId, String referenceKey);

    boolean versionBelongsToSkillCode(Long skillVersionId, String skillCode);

    /** 批量取文档正文；权限与技能正文同级信任，不对调用者单独校验文档可见性。 */
    Map<Long, String> readContents(Set<Long> documentIds);

    /** 取单份文档正文；权限与技能正文同级信任，不对调用者单独校验文档可见性。 */
    Optional<String> readContent(Long documentId, Long documentVersionId);
}
