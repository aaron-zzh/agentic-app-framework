package com.xuejiai.aaf.framework.engine.knowledge.rag;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.AuthorizedQuery;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.ChannelWeights;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationSubject;

import lombok.RequiredArgsConstructor;

/** RAG 评估服务——批量评估置信度、延迟与通过率。 */
@Service
@RequiredArgsConstructor
public class RagEvaluationService {

    private static final double PASS_THRESHOLD = 0.6;

    private final RagGenerationService ragGenerationService;
    private final ConfidenceScorer confidenceScorer;
    private final HybridSearchService hybridSearchService;

    public RagEvaluationReport evaluate(List<EvalCase> testCases) {
        double totalConfidence = 0;
        long totalLatency = 0;
        int passCount = 0;

        for (var testCase : testCases) {
            var query = authorizedQuery(testCase);
            long start = System.currentTimeMillis();
            var response = ragGenerationService.generate(query);
            long latency = System.currentTimeMillis() - start;
            var sources = hybridSearchService.search(query).hits();
            double confidence = confidenceScorer.score(response.answer(), sources);

            totalConfidence += confidence;
            totalLatency += latency;
            if (confidence > PASS_THRESHOLD) {
                passCount++;
            }
        }

        int total = testCases.size();
        return new RagEvaluationReport(
                total,
                total > 0 ? totalConfidence / total : 0,
                total > 0 ? totalLatency / total : 0,
                total > 0 ? (double) passCount / total : 0);
    }

    private AuthorizedQuery authorizedQuery(EvalCase testCase) {
        return new AuthorizedQuery(
                AuthorizationSubject.unresolved(),
                testCase.question(),
                testCase.knowledgeBaseIds(),
                true,
                Map.of(),
                ChannelWeights.defaults(),
                5,
                0.0,
                Map.of());
    }
}
