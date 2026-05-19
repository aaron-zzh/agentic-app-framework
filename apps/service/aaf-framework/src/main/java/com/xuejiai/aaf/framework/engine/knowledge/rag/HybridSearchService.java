package com.xuejiai.aaf.framework.engine.knowledge.rag;

import com.xuejiai.aaf.framework.engine.knowledge.graph.GraphSearchService;
import com.xuejiai.aaf.framework.engine.knowledge.graph.KnowledgeEntity;
import com.xuejiai.aaf.framework.engine.knowledge.graph.KnowledgeEntityRepository;
import com.xuejiai.aaf.framework.engine.knowledge.search.SearchRequest;
import com.xuejiai.aaf.framework.engine.knowledge.search.SearchResult;
import com.xuejiai.aaf.framework.engine.knowledge.search.SimilaritySearchService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.IntStream;

/**
 * 混合检索服务 — 向量 + BM25 + 图谱三路融合，RRF 排序
 */
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private static final int RRF_K = 60;

    private final SimilaritySearchService similaritySearchService;
    private final GraphSearchService graphSearchService;
    private final KnowledgeEntityRepository entityRepository;
    private final EntityManager entityManager;

    /**
     * 三路混合检索 + RRF 融合排序
     */
    public List<RagSearchResult> search(String query, Long knowledgeBaseId, HybridSearchConfig config) {
        // 三路并行检索
        var vectorResults = vectorSearch(query, knowledgeBaseId, config.topK());
        var bm25Results = bm25Search(query, knowledgeBaseId, config.topK());
        var graphResults = graphSearch(query, knowledgeBaseId, config.topK());

        // RRF 融合
        Map<String, double[]> scoreMap = new LinkedHashMap<>(); // content -> [rrfScore]
        Map<String, RagSearchResult> resultMap = new LinkedHashMap<>();

        accumulateRrf(vectorResults, config.vectorWeight(), scoreMap, resultMap);
        accumulateRrf(bm25Results, config.bm25Weight(), scoreMap, resultMap);
        accumulateRrf(graphResults, config.graphWeight(), scoreMap, resultMap);

        // 按融合分数排序，取 topK
        return scoreMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
                .limit(config.topK())
                .map(e -> {
                    var original = resultMap.get(e.getKey());
                    return new RagSearchResult(original.content(), e.getValue()[0], original.source(), original.metadata());
                })
                .toList();
    }

    private List<RagSearchResult> vectorSearch(String query, Long knowledgeBaseId, int topK) {
        var request = new SearchRequest(query, topK, 0.0, knowledgeBaseId, null, null);
        return similaritySearchService.search(request).stream()
                .map(r -> new RagSearchResult(r.content(), r.score(), "vector", r.metadata()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<RagSearchResult> bm25Search(String query, Long knowledgeBaseId, int topK) {
        var sql = """
                SELECT content, ts_rank(to_tsvector('chinese', content), plainto_tsquery('chinese', :query)) AS rank
                FROM knowledge_chunk
                WHERE knowledge_base_id = :kbId
                  AND to_tsvector('chinese', content) @@ plainto_tsquery('chinese', :query)
                ORDER BY rank DESC
                LIMIT :topK
                """;
        var results = entityManager.createNativeQuery(sql)
                .setParameter("query", query)
                .setParameter("kbId", knowledgeBaseId)
                .setParameter("topK", topK)
                .getResultList();

        return ((List<Object[]>) results).stream()
                .map(row -> new RagSearchResult(
                        (String) row[0],
                        ((Number) row[1]).doubleValue(),
                        "bm25",
                        Map.of()
                ))
                .toList();
    }

    private List<RagSearchResult> graphSearch(String query, Long knowledgeBaseId, int topK) {
        // 先按名称模糊匹配找到实体，再取子图
        var entities = entityRepository.findByNameContaining(query).stream()
                .filter(e -> knowledgeBaseId.equals(e.getKnowledgeBaseId()))
                .limit(3)
                .toList();

        if (entities.isEmpty()) return List.of();

        return entities.stream()
                .flatMap(entity -> graphSearchService.subgraphSearch(entity.getId(), 2).stream())
                .filter(e -> e.getDescription() != null && !e.getDescription().isBlank())
                .distinct()
                .limit(topK)
                .map(e -> new RagSearchResult(
                        e.getDescription(),
                        1.0,
                        "graph",
                        Map.of("entityName", e.getName(), "entityType", Objects.toString(e.getType(), ""))
                ))
                .toList();
    }

    /**
     * 累加 RRF 分数：score = weight * (1 / (k + rank))
     */
    private void accumulateRrf(List<RagSearchResult> results, double weight,
                               Map<String, double[]> scoreMap, Map<String, RagSearchResult> resultMap) {
        IntStream.range(0, results.size()).forEach(rank -> {
            var r = results.get(rank);
            var key = r.content();
            scoreMap.computeIfAbsent(key, k -> new double[]{0.0})[0] += weight * (1.0 / (RRF_K + rank + 1));
            resultMap.putIfAbsent(key, r);
        });
    }
}
