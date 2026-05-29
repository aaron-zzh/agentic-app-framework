package com.xuejiai.aaf.module.ai.output.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.ai.output.domain.AiOutput;

public interface AiOutputRepository extends JpaRepository<AiOutput, Long> {

    Page<AiOutput> findByCreatorIdAndDeletedFalseOrderByCreateTimeDesc(Long creatorId, Pageable pageable);

    @Query("""
            SELECT o FROM AiOutput o
            WHERE o.creatorId = :creatorId AND o.deleted = false
              AND (:category IS NULL OR o.category = :category)
              AND (:riskLevel IS NULL OR o.riskLevel = :riskLevel)
              AND (:sourceType IS NULL OR o.sourceType = :sourceType)
            ORDER BY o.createTime DESC""")
    Page<AiOutput> findFiltered(Long creatorId, String category, String riskLevel, String sourceType, Pageable pageable);

    /** 最近 N 条高风险产出 */
    List<AiOutput> findByCreatorIdAndRiskLevelAndDeletedFalseOrderByCreateTimeDesc(
            Long creatorId, String riskLevel, Pageable pageable);

    /** 统计各风险级别数量 */
    long countByCreatorIdAndRiskLevelAndStatusAndDeletedFalse(Long creatorId, String riskLevel, String status);
}
