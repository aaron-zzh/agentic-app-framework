package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectDocumentDecision;

public interface AigcProjectDocumentDecisionRepository
        extends JpaRepository<AigcProjectDocumentDecision, Long> {

    List<AigcProjectDocumentDecision> findByProjectDocumentRefIdOrderByIdDesc(
            Long projectDocumentRefId);

    Optional<AigcProjectDocumentDecision> findByProjectDocumentRefIdAndCandidateIdempotencyKey(
            Long projectDocumentRefId, String candidateIdempotencyKey);

    Optional<AigcProjectDocumentDecision> findByProjectDocumentRefIdAndDecisionIdempotencyKey(
            Long projectDocumentRefId, String decisionIdempotencyKey);

    Optional<AigcProjectDocumentDecision> findByProjectDocumentRefIdAndDocumentVersionId(
            Long projectDocumentRefId, Long documentVersionId);
}
