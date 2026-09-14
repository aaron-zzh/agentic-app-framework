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

    /** 批量取冻结文档版本正文；权限与技能正文同级信任，不对调用者单独校验文档可见性。 */
    Map<DocumentReference, String> readContents(Set<DocumentReference> documentReferences);

    /** 取单份冻结文档版本正文；权限与技能正文同级信任，不对调用者单独校验文档可见性。 */
    Optional<String> readContent(Long documentId, Long documentVersionId);

    record DocumentReference(Long documentId, Long documentVersionId) {
        public DocumentReference {
            java.util.Objects.requireNonNull(documentId, "documentId 不能为空");
            java.util.Objects.requireNonNull(documentVersionId, "documentVersionId 不能为空");
        }
    }
}
