package com.xuejiai.aaf.module.document.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.document.domain.DocumentVersionSemanticMetadata;

import jakarta.persistence.LockModeType;

public interface DocumentVersionSemanticMetadataRepository
        extends JpaRepository<DocumentVersionSemanticMetadata, Long> {

    Optional<DocumentVersionSemanticMetadata> findByDocumentVersionId(Long documentVersionId);

    List<DocumentVersionSemanticMetadata> findAllByDocumentVersionIdIn(
            Collection<Long> documentVersionIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select metadata from DocumentVersionSemanticMetadata metadata where metadata.documentVersionId = :documentVersionId")
    Optional<DocumentVersionSemanticMetadata> findLockedByDocumentVersionId(
            @Param("documentVersionId") Long documentVersionId);
}
