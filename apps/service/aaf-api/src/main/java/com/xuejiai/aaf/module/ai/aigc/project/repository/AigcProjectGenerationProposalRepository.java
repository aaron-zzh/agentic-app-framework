package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectGenerationProposal;

import jakarta.persistence.LockModeType;

/**
 * Proposal 读写边界。
 *
 * <p>{@code proposalId} 全局唯一（`(proposal_id, proposal_version)` 唯一索引），因此定位统一按 proposalId + 版本进行，
 * 作用域归属由服务层显式比对，避免 PROJECT/CREATION_DRAFT 各写一套查询。
 */
public interface AigcProjectGenerationProposalRepository
        extends JpaRepository<AigcProjectGenerationProposal, Long> {

    List<AigcProjectGenerationProposal> findByProjectIdOrderByProposalIdAscProposalVersionDesc(
            Long projectId);

    List<AigcProjectGenerationProposal> findByDraftIdOrderByProposalIdAscProposalVersionDesc(
            String draftId);

    Optional<AigcProjectGenerationProposal> findByProposalIdAndProposalVersion(
            String proposalId, Integer proposalVersion);

    Optional<AigcProjectGenerationProposal> findByProjectIdAndApprovalIdempotencyKey(
            Long projectId, String approvalIdempotencyKey);

    Optional<AigcProjectGenerationProposal> findByDraftIdAndApprovalIdempotencyKey(
            String draftId, String approvalIdempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select proposal from AigcProjectGenerationProposal proposal
            where proposal.proposalId = :proposalId
              and proposal.proposalVersion = :proposalVersion
            """)
    Optional<AigcProjectGenerationProposal> findForApproval(
            String proposalId, Integer proposalVersion);
}
