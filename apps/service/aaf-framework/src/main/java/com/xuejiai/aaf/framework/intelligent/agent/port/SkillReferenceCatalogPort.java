package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.core.skill.SkillReference;

/** Skill 版本挂载的参考文档只读目录。 */
public interface SkillReferenceCatalogPort {

    List<SkillReference> findByVersionId(Long skillVersionId);

    Optional<SkillReference> findByVersionIdAndKey(Long skillVersionId, String referenceKey);

    /** 取文档正文；权限与技能正文同级信任，不对调用者单独校验文档可见性。 */
    Optional<String> readContent(Long documentId, Long documentVersionId);
}
