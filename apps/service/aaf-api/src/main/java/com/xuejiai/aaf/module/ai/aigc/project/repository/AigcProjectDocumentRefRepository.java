package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectDocumentRef;

public interface AigcProjectDocumentRefRepository
        extends JpaRepository<AigcProjectDocumentRef, Long> {

    List<AigcProjectDocumentRef> findByProjectIdOrderBySortOrderAscIdAsc(Long projectId);

    Optional<AigcProjectDocumentRef> findByProjectIdAndDocumentVersionIdAndRole(
            Long projectId, Long documentVersionId, String role);
}
