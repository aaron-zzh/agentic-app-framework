package com.xuejiai.aaf.module.knowledge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.knowledge.domain.ProblemParagraph;

public interface ProblemParagraphRepository extends JpaRepository<ProblemParagraph, Long> {
    List<ProblemParagraph> findByProblemId(Long problemId);

    void deleteByProblemId(Long problemId);
}
