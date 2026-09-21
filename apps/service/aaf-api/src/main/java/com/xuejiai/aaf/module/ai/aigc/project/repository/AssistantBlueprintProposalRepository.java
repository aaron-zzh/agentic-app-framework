package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AssistantBlueprintProposal;

import jakarta.persistence.LockModeType;

/** Assistant Blueprint Proposal 的独立持久化边界。 */
public interface AssistantBlueprintProposalRepository
        extends JpaRepository<AssistantBlueprintProposal, Long> {

    List<AssistantBlueprintProposal>
            findByOwnerIdAndOrgIdAndWorkspaceIdOrderByCreateTimeDescProposalVersionDesc(
                    Long ownerId, Long orgId, Long workspaceId);

    Optional<AssistantBlueprintProposal> findBySourceTaskId(String sourceTaskId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select proposal from AssistantBlueprintProposal proposal
            where proposal.sourceTaskId = :sourceTaskId
            """)
    Optional<AssistantBlueprintProposal> findLockedBySourceTaskId(String sourceTaskId);

    Optional<AssistantBlueprintProposal> findByProposalIdAndProposalVersion(
            String proposalId, Integer proposalVersion);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select proposal from AssistantBlueprintProposal proposal
            where proposal.proposalId = :proposalId
              and proposal.proposalVersion = :proposalVersion
            """)
    Optional<AssistantBlueprintProposal> findLocked(String proposalId, Integer proposalVersion);
}
