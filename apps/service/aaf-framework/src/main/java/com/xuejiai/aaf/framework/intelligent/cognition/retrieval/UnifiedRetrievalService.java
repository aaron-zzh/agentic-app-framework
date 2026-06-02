/**
 * 融合检索服务——跨 Memory/Knowledge/Value 的统一路由与聚合。
 *
 * <p>对齐认知心理学：线索依赖提取 + 激活扩散 + 注意力资源分配。 不是又一个检索引擎，是跨认知组件的路由和聚合层。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.retrieval;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchConfig;
import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchService;
import com.xuejiai.aaf.framework.engine.knowledge.rag.RagSearchResult;
import com.xuejiai.aaf.framework.engine.memory.AtomMemoryEngine;
import com.xuejiai.aaf.framework.engine.memory.MemoryAtom;
import com.xuejiai.aaf.framework.engine.memory.MemoryBundle;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryRerankerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 统一融合检索入口：并行查 Memory + Knowledge，RRF 融合，LLM 重排。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedRetrievalService {

    private static final int RRF_K = 60;

    private final AtomMemoryEngine memoryEngine;
    private final HybridSearchService knowledgeSearch;
    private final EmbeddingService embeddingService;
    private final MemoryRerankerService reranker;

    /**
     * 统一检索入口。
     *
     * @param request 检索请求
     * @return 融合后的检索结果（已排序、已截断）
     */
    public RetrievalResult retrieve(RetrievalRequest request) {
        var queryEmbedding = embeddingService.embed(request.query());

        // 路由决策
        var route = decideRoute(request);

        // 并行检索（虚拟线程）
        List<MemoryAtom> memoryResults = List.of();
        List<MemoryBundle> bundles = List.of();
        List<RagSearchResult> knowledgeResults = List.of();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<List<MemoryAtom>> memoryFuture =
                    route.searchMemory()
                            ? executor.submit(
                                    () ->
                                            memoryEngine.searchByVector(
                                                    request.userId(),
                                                    queryEmbedding,
                                                    route.memoryTopK()))
                            : executor.submit(() -> List.<MemoryAtom>of());

            Future<List<MemoryBundle>> bundleFuture =
                    route.searchBundles()
                            ? executor.submit(
                                    () ->
                                            memoryEngine.searchBundles(
                                                    request.userId(),
                                                    queryEmbedding,
                                                    route.bundleTopK(),
                                                    null))
                            : executor.submit(() -> List.<MemoryBundle>of());

            Future<List<RagSearchResult>> knowledgeFuture =
                    route.searchKnowledge() && request.knowledgeBaseId() != null
                            ? executor.submit(
                                    () ->
                                            knowledgeSearch.search(
                                                    request.query(),
                                                    request.knowledgeBaseId(),
                                                    new HybridSearchConfig(
                                                            0.4, 0.3, 0.3, route.knowledgeTopK())))
                            : executor.submit(() -> List.<RagSearchResult>of());

            memoryResults = memoryFuture.get();
            bundles = bundleFuture.get();
            knowledgeResults = knowledgeFuture.get();
        } catch (Exception e) {
            log.warn("融合检索并行失败，降级串行: {}", e.getMessage());
            if (route.searchMemory()) {
                memoryResults =
                        memoryEngine.searchByVector(
                                request.userId(), queryEmbedding, route.memoryTopK());
            }
            if (route.searchKnowledge() && request.knowledgeBaseId() != null) {
                knowledgeResults =
                        knowledgeSearch.search(
                                request.query(),
                                request.knowledgeBaseId(),
                                new HybridSearchConfig(0.4, 0.3, 0.3, route.knowledgeTopK()));
            }
        }

        // RRF 融合排序
        var fused = fuseResults(memoryResults, knowledgeResults, request.topK());

        // 轻量重排（纯计算，仅对记忆部分；不调 chat LLM，保持读路径低延迟）
        if (memoryResults.size() > 1) {
            memoryResults = reranker.rerank(request.query(), memoryResults, route.memoryTopK());
        }

        // Value 校验过滤（P1 占位：后续接入 ValueRuleEngine 过滤不符合价值观的结果）
        // memoryResults = valueFilter.filter(memoryResults);
        // knowledgeResults = valueFilter.filter(knowledgeResults);

        return new RetrievalResult(memoryResults, bundles, knowledgeResults, fused);
    }

    /** 路由决策：根据查询特征决定检索哪些源 */
    private RouteDecision decideRoute(RetrievalRequest request) {
        boolean hasKb = request.knowledgeBaseId() != null;
        boolean hasUser = request.userId() != null;

        // 都有：混合检索
        if (hasKb && hasUser) {
            return new RouteDecision(true, true, true, 6, 3, 6);
        }
        // 仅知识库
        if (hasKb) {
            return new RouteDecision(false, false, true, 0, 0, 10);
        }
        // 仅记忆
        return new RouteDecision(true, true, false, 8, 4, 0);
    }

    /** RRF 融合：将记忆结果和知识库结果统一排序 */
    private List<FusedItem> fuseResults(
            List<MemoryAtom> memory, List<RagSearchResult> knowledge, int topK) {
        Map<String, double[]> scoreMap = new LinkedHashMap<>();
        Map<String, FusedItem> itemMap = new LinkedHashMap<>();

        // 记忆结果 RRF
        IntStream.range(0, memory.size())
                .forEach(
                        rank -> {
                            var atom = memory.get(rank);
                            var key = "mem:" + atom.getId();
                            scoreMap.computeIfAbsent(key, k -> new double[] {0.0})[0] +=
                                    0.5 * (1.0 / (RRF_K + rank + 1));
                            itemMap.putIfAbsent(
                                    key,
                                    new FusedItem(
                                            atom.getContent(), "memory", scoreMap.get(key)[0]));
                        });

        // 知识库结果 RRF
        IntStream.range(0, knowledge.size())
                .forEach(
                        rank -> {
                            var item = knowledge.get(rank);
                            var key = "kb:" + item.content().hashCode();
                            scoreMap.computeIfAbsent(key, k -> new double[] {0.0})[0] +=
                                    0.5 * (1.0 / (RRF_K + rank + 1));
                            itemMap.putIfAbsent(
                                    key,
                                    new FusedItem(
                                            item.content(), item.source(), scoreMap.get(key)[0]));
                        });

        return scoreMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
                .limit(topK)
                .map(
                        e -> {
                            var item = itemMap.get(e.getKey());
                            return new FusedItem(item.content(), item.source(), e.getValue()[0]);
                        })
                .toList();
    }

    /** 路由决策 */
    private record RouteDecision(
            boolean searchMemory,
            boolean searchBundles,
            boolean searchKnowledge,
            int memoryTopK,
            int bundleTopK,
            int knowledgeTopK) {}

    /** 检索请求 */
    public record RetrievalRequest(String query, Long userId, Long knowledgeBaseId, int topK) {
        public RetrievalRequest {
            if (topK <= 0) topK = 10;
        }
    }

    /** 融合检索结果 */
    public record RetrievalResult(
            List<MemoryAtom> memoryResults,
            List<MemoryBundle> bundles,
            List<RagSearchResult> knowledgeResults,
            List<FusedItem> fused) {}

    /** 融合后的单条结果 */
    public record FusedItem(String content, String source, double score) {}
}
