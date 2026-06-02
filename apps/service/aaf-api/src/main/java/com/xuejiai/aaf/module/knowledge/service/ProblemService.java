package com.xuejiai.aaf.module.knowledge.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.knowledge.domain.Problem;
import com.xuejiai.aaf.module.knowledge.domain.ProblemParagraph;
import com.xuejiai.aaf.module.knowledge.repository.ProblemParagraphRepository;
import com.xuejiai.aaf.module.knowledge.repository.ProblemRepository;

import lombok.RequiredArgsConstructor;

/** QA 问题管理服务——手动维护问答对，检索时优先匹配。 */
@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepo;
    private final ProblemParagraphRepository ppRepo;

    public Page<Problem> list(Long knowledgeBaseId, Pageable pageable) {
        return problemRepo.findByKnowledgeBaseIdAndActiveTrue(knowledgeBaseId, pageable);
    }

    public List<Problem> search(Long knowledgeBaseId, String keyword) {
        return problemRepo.findByKnowledgeBaseIdAndContentContaining(knowledgeBaseId, keyword);
    }

    @Transactional
    public Problem create(Long knowledgeBaseId, String content, List<Long> segmentIds) {
        var problem = new Problem();
        problem.setKnowledgeBaseId(knowledgeBaseId);
        problem.setContent(content);
        problemRepo.save(problem);
        if (segmentIds != null) {
            segmentIds.forEach(
                    sid -> {
                        var pp = new ProblemParagraph();
                        pp.setProblemId(problem.getId());
                        pp.setSegmentId(sid);
                        ppRepo.save(pp);
                    });
        }
        return problem;
    }

    @Transactional
    public void delete(Long problemId) {
        ppRepo.deleteByProblemId(problemId);
        problemRepo.deleteById(problemId);
    }

    public List<ProblemParagraph> getLinkedSegments(Long problemId) {
        return ppRepo.findByProblemId(problemId);
    }

    @Transactional
    public void linkSegment(Long problemId, Long segmentId) {
        var pp = new ProblemParagraph();
        pp.setProblemId(problemId);
        pp.setSegmentId(segmentId);
        ppRepo.save(pp);
    }
}
