package com.xuejiai.aaf.module.ai.skill;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import com.xuejiai.aaf.framework.engine.skill.SkillReferenceEntity;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillReferenceCatalogPort;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillReference;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillReference.LoadMode;
import com.xuejiai.aaf.module.document.api.DocumentVersionApi;

import lombok.RequiredArgsConstructor;

/**
 * SkillReferenceCatalogPort 的 JPA 桥接。
 *
 * <p>{@code document_version_id} 精确冻结 Reference 正文；读取始终通过文档模块版本 API，不回退到可变当前内容。
 */
@Component
@RequiredArgsConstructor
public class SkillReferenceCatalogPortImpl implements SkillReferenceCatalogPort {

    private final SkillReferenceRepository repository;
    private final DocumentVersionApi documentVersions;

    @Override
    public Map<Long, List<SkillReference>> findByVersionIds(Set<Long> skillVersionIds) {
        if (skillVersionIds.isEmpty()) {
            return Map.of();
        }
        var grouped =
                repository
                        .findBySkillVersionIdInOrderBySkillVersionIdAscSortOrderAscIdAsc(
                                skillVersionIds)
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        SkillReferenceEntity::getSkillVersionId,
                                        LinkedHashMap::new,
                                        Collectors.mapping(
                                                SkillReferenceCatalogPortImpl::toDomain,
                                                Collectors.toUnmodifiableList())));
        return Map.copyOf(grouped);
    }

    @Override
    public Optional<SkillReference> findByVersionIdAndKey(
            Long skillVersionId, String referenceKey) {
        return repository
                .findBySkillVersionIdAndReferenceKey(skillVersionId, referenceKey)
                .map(SkillReferenceCatalogPortImpl::toDomain);
    }

    @Override
    public boolean versionBelongsToSkillCode(Long skillVersionId, String skillCode) {
        return repository.existsVersionForSkillCode(skillVersionId, skillCode);
    }

    @Override
    public Map<DocumentReference, String> readContents(Set<DocumentReference> documentReferences) {
        if (documentReferences.isEmpty()) {
            return Map.of();
        }
        var versions =
                documentVersions.findExact(
                        documentReferences.stream()
                                .map(
                                        reference ->
                                                new DocumentVersionApi.DocumentVersionIdentity(
                                                        reference.documentId(),
                                                        reference.documentVersionId()))
                                .toList());
        return documentReferences.stream()
                .filter(
                        reference ->
                                versions.containsKey(
                                        new DocumentVersionApi.DocumentVersionIdentity(
                                                reference.documentId(),
                                                reference.documentVersionId())))
                .collect(
                        Collectors.toUnmodifiableMap(
                                reference -> reference,
                                reference ->
                                        versions.get(
                                                        new DocumentVersionApi
                                                                .DocumentVersionIdentity(
                                                                reference.documentId(),
                                                                reference.documentVersionId()))
                                                .content()));
    }

    @Override
    public Optional<String> readContent(Long documentId, Long documentVersionId) {
        return documentVersions
                .findExact(documentId, documentVersionId)
                .map(DocumentVersionApi.DocumentVersionView::content);
    }

    private static SkillReference toDomain(SkillReferenceEntity entity) {
        return new SkillReference(
                entity.getReferenceKey(),
                entity.getTitle(),
                entity.getDocumentId(),
                entity.getDocumentVersionId(),
                entity.getSelector(),
                LoadMode.valueOf(entity.getLoadMode()),
                entity.getLoadWhen(),
                Boolean.TRUE.equals(entity.getRequired()),
                entity.getMaxTokens(),
                entity.getSortOrder(),
                entity.getContentHash());
    }
}

@Repository
interface SkillReferenceRepository extends JpaRepository<SkillReferenceEntity, Long> {
    List<SkillReferenceEntity> findBySkillVersionIdInOrderBySkillVersionIdAscSortOrderAscIdAsc(
            Collection<Long> skillVersionIds);

    Optional<SkillReferenceEntity> findBySkillVersionIdAndReferenceKey(
            Long skillVersionId, String referenceKey);

    @Query(
            """
            SELECT CASE WHEN COUNT(version) > 0 THEN TRUE ELSE FALSE END
            FROM SkillVersion version, SkillDefinition definition
            WHERE version.id = :skillVersionId
              AND version.skillId = definition.id
              AND version.status IN ('APPROVED', 'RETIRED')
              AND definition.code = :skillCode
              AND definition.deleted = FALSE
            """)
    boolean existsVersionForSkillCode(
            @Param("skillVersionId") Long skillVersionId, @Param("skillCode") String skillCode);
}
