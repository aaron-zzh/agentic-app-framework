package com.xuejiai.aaf.framework.engine.knowledge.rag;

import java.util.*;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.knowledge.graph.GraphSearchService;
import com.xuejiai.aaf.framework.engine.knowledge.graph.KnowledgeEntityRepository;
import com.xuejiai.aaf.framework.engine.knowledge.search.SearchRequest;
import com.xuejiai.aaf.framework.engine.knowledge.search.SimilaritySearchService;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/** 混合检索服务 — 向量 + BM25 + 图谱三路融合，RRF 排序 */
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private static final int RRF_K = 60;

    private final SimilaritySearchService similaritySearchService;
    private final GraphSearchService graphSearchService;
    private final KnowledgeEntityRepository entityRepository;
    private final EntityManager entityManager;

    /** 三路混合检索 + RRF 融合排序。 */
    public List<RagSearchResult> search(
            String query, Long knowledgeBaseId, HybridSearchConfig config) {
        return hybridSearch(query, knowledgeBaseId, config, 0.0);
    }

    /** 三路混合检索，并把阈值传递到向量检索支路。 */
    public List<RagSearchResult> hybridSearch(
            String query,
            Long knowledgeBaseId,
            HybridSearchConfig config,
            double similarityThreshold) {
        // 三路并行检索（虚拟线程）
        List<RagSearchResult> vectorResults;
        List<RagSearchResult> bm25Results;
        List<RagSearchResult> graphResults;

        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var vectorFuture =
                    executor.submit(
                            () ->
                                    vectorSearch(
                                            query,
                                            knowledgeBaseId,
                                            config.topK(),
                                            similarityThreshold));
            var bm25Future =
                    executor.submit(() -> keywordSearch(query, knowledgeBaseId, config.topK()));
            var graphFuture =
                    executor.submit(() -> graphSearch(query, knowledgeBaseId, config.topK()));

            vectorResults = vectorFuture.get();
            bm25Results = bm25Future.get();
            graphResults = graphFuture.get();
        } catch (Exception e) {
            // 降级为串行
            vectorResults =
                    vectorSearch(query, knowledgeBaseId, config.topK(), similarityThreshold);
            bm25Results = keywordSearch(query, knowledgeBaseId, config.topK());
            graphResults = graphSearch(query, knowledgeBaseId, config.topK());
        }

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
                .map(
                        e -> {
                            var original = resultMap.get(e.getKey());
                            return new RagSearchResult(
                                    original.content(),
                                    e.getValue()[0],
                                    original.source(),
                                    original.metadata());
                        })
                .toList();
    }

    /** 仅执行向量检索。 */
    public List<RagSearchResult> vectorSearch(
            String query, Long knowledgeBaseId, int topK, double similarityThreshold) {
        var request =
                new SearchRequest(query, topK, similarityThreshold, knowledgeBaseId, null, null);
        return similaritySearchService.search(request).stream()
                .map(r -> new RagSearchResult(r.content(), r.score(), "vector", r.metadata()))
                .toList();
    }

    /** 仅执行 PostgreSQL 全文关键词检索。 */
    @SuppressWarnings("unchecked")
    public List<RagSearchResult> keywordSearch(String query, Long knowledgeBaseId, int topK) {
        var sql =
                """
                SELECT id, document_id, content,
                       ts_rank(to_tsvector('simple', content), plainto_tsquery('simple', :query)) AS rank
                FROM ai_knowledge_chunk
                WHERE knowledge_base_id = :kbId
                  AND to_tsvector('simple', content) @@ plainto_tsquery('simple', :query)
                ORDER BY rank DESC
                LIMIT :topK
                """;
        var results =
                entityManager
                        .createNativeQuery(sql)
                        .setParameter("query", query)
                        .setParameter("kbId", knowledgeBaseId)
                        .setParameter("topK", topK)
                        .getResultList();

        return ((List<Object[]>) results)
                .stream()
                        .map(
                                row ->
                                        new RagSearchResult(
                                                (String) row[2],
                                                ((Number) row[3]).doubleValue(),
                                                "keyword",
                                                Map.of(
                                                        "chunk_id",
                                                        ((Number) row[0]).longValue(),
                                                        "document_id",
                                                        ((Number) row[1]).longValue())))
                        .toList();
    }

    private List<RagSearchResult> graphSearch(String query, Long knowledgeBaseId, int topK) {
        // 先按名称模糊匹配找到实体，再取子图
        var entities =
                entityRepository.findByNameContaining(query).stream()
                        .filter(e -> knowledgeBaseId.equals(e.getKnowledgeBaseId()))
                        .limit(3)
                        .toList();

        if (entities.isEmpty()) return List.of();

        return entities.stream()
                .flatMap(entity -> graphSearchService.subgraphSearch(entity.getId(), 2).stream())
                .filter(e -> knowledgeBaseId.equals(e.getKnowledgeBaseId()))
                .filter(e -> e.getName() != null && !e.getName().isBlank())
                .distinct()
                .limit(topK)
                .map(
                        e ->
                                new RagSearchResult(
                                        graphContent(e.getDescription(), e.getName()),
                                        1.0,
                                        "graph",
                                        Map.of(
                                                "entity_id",
                                                e.getId(),
                                                "entityName",
                                                e.getName(),
                                                "entityType",
                                                Objects.toString(e.getType(), ""))))
                .toList();
    }

    private String graphContent(String description, String name) {
        return description == null || description.isBlank() ? name : description;
    }

    /** 累加 RRF 分数：score = weight * (1 / (k + rank)) */
    private void accumulateRrf(
            List<RagSearchResult> results,
            double weight,
            Map<String, double[]> scoreMap,
            Map<String, RagSearchResult> resultMap) {
        IntStream.range(0, results.size())
                .forEach(
                        rank -> {
                            var r = results.get(rank);
                            var key = r.content();
                            scoreMap.computeIfAbsent(key, k -> new double[] {0.0})[0] +=
                                    weight * (1.0 / (RRF_K + rank + 1));
                            resultMap.putIfAbsent(key, r);
                        });
    }
}
