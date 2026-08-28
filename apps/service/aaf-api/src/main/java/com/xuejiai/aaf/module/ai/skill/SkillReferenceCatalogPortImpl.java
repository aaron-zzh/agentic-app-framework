package com.xuejiai.aaf.module.ai.skill;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import com.xuejiai.aaf.framework.engine.skill.SkillReferenceEntity;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillReferenceCatalogPort;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillReference;
import com.xuejiai.aaf.module.document.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;

/**
 * SkillReferenceCatalogPort 的 JPA 桥接。
 *
 * <p>{@code document_version_id} 不参与校验——{@code doc_document} 当前无内容版本历史，只按 {@code document_id} 取当前
 * {@code content}；一旦文档模块引入版本能力需补上对应校验。
 */
@Component
@RequiredArgsConstructor
public class SkillReferenceCatalogPortImpl implements SkillReferenceCatalogPort {

    private final SkillReferenceRepository repository;
    private final DocumentRepository documents;

    @Override
    public List<SkillReference> findByVersionId(Long skillVersionId) {
        return repository.findBySkillVersionIdOrderBySortOrderAscIdAsc(skillVersionId).stream()
                .map(SkillReferenceCatalogPortImpl::toDomain)
                .toList();
    }

    @Override
    public Optional<SkillReference> findByVersionIdAndKey(
            Long skillVersionId, String referenceKey) {
        return repository
                .findBySkillVersionIdAndReferenceKey(skillVersionId, referenceKey)
                .map(SkillReferenceCatalogPortImpl::toDomain);
    }

    @Override
    public Optional<String> readContent(Long documentId, Long documentVersionId) {
        return documents.findById(documentId).map(document -> document.getContent());
    }

    private static SkillReference toDomain(SkillReferenceEntity entity) {
        return new SkillReference(
                entity.getReferenceKey(),
                entity.getTitle(),
                entity.getDocumentId(),
                entity.getDocumentVersionId(),
                Boolean.TRUE.equals(entity.getRequired()),
                entity.getMaxTokens());
    }
}

@Repository
interface SkillReferenceRepository extends JpaRepository<SkillReferenceEntity, Long> {
    List<SkillReferenceEntity> findBySkillVersionIdOrderBySortOrderAscIdAsc(Long skillVersionId);

    Optional<SkillReferenceEntity> findBySkillVersionIdAndReferenceKey(
            Long skillVersionId, String referenceKey);
}
