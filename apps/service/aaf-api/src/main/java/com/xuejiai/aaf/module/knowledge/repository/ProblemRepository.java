package com.xuejiai.aaf.module.knowledge.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.xuejiai.aaf.module.knowledge.domain.Problem;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
    Page<Problem> findByKnowledgeBaseIdAndActiveTrue(Long knowledgeBaseId, Pageable pageable);
    List<Problem> findByKnowledgeBaseIdAndActiveTrue(Long knowledgeBaseId);
    List<Problem> findByKnowledgeBaseIdAndContentContaining(Long knowledgeBaseId, String keyword);
}
