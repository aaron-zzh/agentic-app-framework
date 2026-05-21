package com.xuejiai.aaf.framework.engine.knowledge.rag;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** RAG 评估服务 — 批量评估 RAG 质量（置信度、延迟、通过率） */
@Service
@RequiredArgsConstructor
public class RagEvaluationService {

    private static final double PASS_THRESHOLD = 0.6;

    private final RagGenerationService ragGenerationService;
    private final ConfidenceScorer confidenceScorer;
    private final HybridSearchService hybridSearchService;

    /** 批量评估 RAG 质量 */
    public RagEvaluationReport evaluate(List<EvalCase> testCases) {
        double totalConfidence = 0;
        long totalLatency = 0;
        int passCount = 0;

        for (var testCase : testCases) {
            long start = System.currentTimeMillis();
            var response =
                    ragGenerationService.generate(testCase.question(), testCase.knowledgeBaseId());
            long latency = System.currentTimeMillis() - start;

            var sources =
                    hybridSearchService.search(
                            testCase.question(),
                            testCase.knowledgeBaseId(),
                            new HybridSearchConfig());
            double confidence = confidenceScorer.score(response.answer(), sources);

            totalConfidence += confidence;
            totalLatency += latency;
            if (confidence > PASS_THRESHOLD) passCount++;
        }

        int total = testCases.size();
        return new RagEvaluationReport(
                total,
                total > 0 ? totalConfidence / total : 0,
                total > 0 ? totalLatency / total : 0,
                total > 0 ? (double) passCount / total : 0);
    }
}
