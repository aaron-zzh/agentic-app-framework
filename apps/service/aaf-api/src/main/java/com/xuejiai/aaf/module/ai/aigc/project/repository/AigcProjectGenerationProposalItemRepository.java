package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectGenerationProposalItem;

public interface AigcProjectGenerationProposalItemRepository
        extends JpaRepository<AigcProjectGenerationProposalItem, Long> {

    List<AigcProjectGenerationProposalItem> findByProposalIdAndProposalVersionOrderByItemKeyAsc(
            String proposalId, Integer proposalVersion);

    Optional<AigcProjectGenerationProposalItem> findByProposalRecordIdAndItemKey(
            Long proposalRecordId, String itemKey);
}
