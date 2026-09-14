package com.xuejiai.aaf.module.document.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.document.domain.DocumentVersion;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    List<DocumentVersion> findAllByIdIn(Collection<Long> ids);

    Optional<DocumentVersion> findByIdAndDocumentIdAndDeletedFalse(Long id, Long documentId);

    List<DocumentVersion> findAllByDocumentIdAndDeletedFalseOrderByVersionNoDesc(Long documentId);

    @Query(
            value =
                    "SELECT d.id FROM doc_document d "
                            + "JOIN doc_document_version v ON v.id = d.current_version_id AND v.document_id = d.id "
                            + "WHERE d.deleted = false "
                            + "AND to_tsvector('simple', coalesce(d.title, '') || ' ' || coalesce(v.content, '')) "
                            + "@@ plainto_tsquery('simple', :query)",
            nativeQuery = true)
    List<Long> findCurrentDocumentIdsMatching(@Param("query") String query);
}
