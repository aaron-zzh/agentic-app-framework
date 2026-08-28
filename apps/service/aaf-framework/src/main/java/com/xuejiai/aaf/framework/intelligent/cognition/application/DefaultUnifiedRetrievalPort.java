package com.xuejiai.aaf.framework.intelligent.cognition.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.AuthorizedQuery;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.ChannelWeights;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.Hit;
import com.xuejiai.aaf.framework.engine.memory.MemoryAtom;
import com.xuejiai.aaf.framework.engine.memory.MemoryBundle;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryRerankerService;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryRetrievalPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryRetrievalPort.MemoryRetrievalQuery;
import com.xuejiai.aaf.framework.intelligent.cognition.port.UnifiedRetrievalPort;

import lombok.extern.slf4j.Slf4j;

/**
 * 混合检索统一门面的默认实现。
 *
 * <p>迁移自旧 {@code MemoryRetrievalService}（意图分类规则、预算分配公式）与旧 {@code
 * UnifiedRetrievalService}（RRF 融合结构、并行调用结构），并修正两处已知缺陷：
 *
 * <ul>
 *   <li>知识检索改为使用调用方传入的已解析授权主体，不再使用 {@code AuthorizationSubject.unresolved()}
 *   <li>重排调用时机改为融合之后作用于最终跨源候选，不再只作用于独立 memory 列表
 * </ul>
 */
@Slf4j
public final class DefaultUnifiedRetrievalPort implements UnifiedRetrievalPort {

    private static final int RRF_K = 60;

    /** 默认预算分配（借鉴 M-FLOW OrchestratorConfig，迁移自 MemoryRetrievalService）。 */
    private static final int ATOMIC_TOP_K = 6;

    private static final int EPISODIC_TOP_K = 4;
    private static final int PROCEDURAL_TOP_K = 2;

    private final MemoryRetrievalPort memoryRetrieval;
    private final HybridSearchService knowledgeSearch;
    private final EmbeddingService embeddingService;
    private final MemoryRerankerService reranker;

    public DefaultUnifiedRetrievalPort(
            MemoryRetrievalPort memoryRetrieval,
            HybridSearchService knowledgeSearch,
            EmbeddingService embeddingService,
            MemoryRerankerService reranker) {
        this.memoryRetrieval = Objects.requireNonNull(memoryRetrieval, "memoryRetrieval 不能为空");
        this.knowledgeSearch = Objects.requireNonNull(knowledgeSearch, "knowledgeSearch 不能为空");
        this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService 不能为空");
        this.reranker = Objects.requireNonNull(reranker, "reranker 不能为空");
    }

    @Override
    public UnifiedRetrievalResult retrieve(UnifiedRetrievalRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        var wantMemory = request.userId() != null;
        var wantKnowledge = !request.knowledgeBaseIds().isEmpty() || request.includePublic();
        if (!wantMemory && !wantKnowledge) {
            return UnifiedRetrievalResult.empty();
        }

        var intent = classifyIntent(request.query());
        var budget = allocateBudget(intent);

        float[] queryEmbedding = null;
        if (wantMemory && !request.query().isBlank()) {
            queryEmbedding = embeddingService.embed(request.query(), request.userId());
        }
        final float[] resolvedEmbedding = queryEmbedding;

        MemoryRetrievalPort.MemoryRetrievalResult memoryResult =
                MemoryRetrievalPort.MemoryRetrievalResult.empty();
        List<Hit> knowledgeHits = List.of();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<MemoryRetrievalPort.MemoryRetrievalResult> memoryFuture =
                    wantMemory
                            ? executor.submit(
                                    () ->
                                            memoryRetrieval.retrieve(
                                                    new MemoryRetrievalQuery(
                                                            request.userId(),
                                                            request.query(),
                                                            resolvedEmbedding,
                                                            budget.atomicTopK(),
                                                            budget.episodicTopK(),
                                                            budget.proceduralTopK(),
                                                            Instant.now())))
                            : null;
            Future<List<Hit>> knowledgeFuture =
                    wantKnowledge
                            ? executor.submit(() -> searchKnowledge(request))
                            : null;

            if (memoryFuture != null) {
                memoryResult = awaitMemory(memoryFuture);
            }
            if (knowledgeFuture != null) {
                knowledgeHits = awaitKnowledge(knowledgeFuture);
            }
        }

        var fused = fuse(memoryResult, knowledgeHits, request.maxItems());
        var reranked = rerank(request.query(), fused, request.maxItems());
        return new UnifiedRetrievalResult(reranked, knowledgeHits);
    }

    private List<Hit> searchKnowledge(UnifiedRetrievalRequest request) {
        var authorizedQuery =
                new AuthorizedQuery(
                        request.subject(),
                        request.query(),
                        request.knowledgeBaseIds(),
                        request.includePublic(),
                        Map.of(),
                        ChannelWeights.defaults(),
                        request.maxItems(),
                        0.0,
                        Map.of());
        return knowledgeSearch.search(authorizedQuery).hits();
    }

    private static MemoryRetrievalPort.MemoryRetrievalResult awaitMemory(
            Future<MemoryRetrievalPort.MemoryRetrievalResult> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            log.warn("[混合检索] 记忆通道检索失败，本轮按空结果继续：{}", exception.getMessage());
            return MemoryRetrievalPort.MemoryRetrievalResult.empty();
        }
    }

    private static List<Hit> awaitKnowledge(Future<List<Hit>> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            log.warn("[混合检索] 知识库通道检索失败，本轮按空结果继续：{}", exception.getMessage());
            return List.of();
        }
    }

    /** 跨通道 RRF 融合：先按稳定候选键去重，再加权求和；不同通道权重均等，对齐 retrieval.md 的 k=60。 */
    private List<FusedCandidate> fuse(
            MemoryRetrievalPort.MemoryRetrievalResult memoryResult,
            List<Hit> knowledgeHits,
            int maxItems) {
        Map<String, Double> scoreByKey = new LinkedHashMap<>();
        Map<String, FusedCandidate> candidateByKey = new LinkedHashMap<>();

        fuseAtomic(memoryResult.atomicMemories(), scoreByKey, candidateByKey);
        fuseEpisodic(memoryResult.episodicBundles(), scoreByKey, candidateByKey);
        fuseProcedural(memoryResult.proceduralMemories(), scoreByKey, candidateByKey);
        fuseKnowledge(knowledgeHits, scoreByKey, candidateByKey);

        return scoreByKey.entrySet().stream()
                .sorted(
                        (left, right) -> {
                            int byScore = Double.compare(right.getValue(), left.getValue());
                            return byScore != 0 ? byScore : left.getKey().compareTo(right.getKey());
                        })
                .limit(maxItems)
                .map(entry -> candidateByKey.get(entry.getKey()).withScore(entry.getValue()))
                .toList();
    }

    private static void fuseAtomic(
            List<MemoryAtom> atoms,
            Map<String, Double> scoreByKey,
            Map<String, FusedCandidate> candidateByKey) {
        IntStream.range(0, atoms.size())
                .forEach(
                        rank -> {
                            var atom = atoms.get(rank);
                            var key = "MEMORY:ATOMIC:" + atom.getId();
                            accumulate(key, rank, scoreByKey);
                            candidateByKey.putIfAbsent(
                                    key, new FusedCandidate(key, atom.getContent(), "atomic", 0));
                        });
    }

    private static void fuseEpisodic(
            List<MemoryBundle> bundles,
            Map<String, Double> scoreByKey,
            Map<String, FusedCandidate> candidateByKey) {
        IntStream.range(0, bundles.size())
                .forEach(
                        rank -> {
                            var bundle = bundles.get(rank);
                            var key = "MEMORY:EPISODIC:" + rank + ":" + bundle.hashCode();
                            accumulate(key, rank, scoreByKey);
                            var content =
                                    bundle.atoms().stream()
                                            .map(MemoryAtom::getContent)
                                            .reduce("", (left, right) -> left + "\n" + right);
                            candidateByKey.putIfAbsent(
                                    key, new FusedCandidate(key, content, "episodic", 0));
                        });
    }

    private static void fuseProcedural(
            List<MemoryAtom> atoms,
            Map<String, Double> scoreByKey,
            Map<String, FusedCandidate> candidateByKey) {
        IntStream.range(0, atoms.size())
                .forEach(
                        rank -> {
                            var atom = atoms.get(rank);
                            var key = "MEMORY:PROCEDURAL:" + atom.getId();
                            accumulate(key, rank, scoreByKey);
                            candidateByKey.putIfAbsent(
                                    key,
                                    new FusedCandidate(key, atom.getContent(), "procedural", 0));
                        });
    }

    private static void fuseKnowledge(
            List<Hit> hits, Map<String, Double> scoreByKey, Map<String, FusedCandidate> candidateByKey) {
        IntStream.range(0, hits.size())
                .forEach(
                        rank -> {
                            var hit = hits.get(rank);
                            var key = "KNOWLEDGE:" + hit.candidateKey();
                            accumulate(key, rank, scoreByKey);
                            candidateByKey.putIfAbsent(
                                    key, new FusedCandidate(key, hit.content(), "knowledge", 0));
                        });
    }

    private static void accumulate(String key, int rank, Map<String, Double> scoreByKey) {
        scoreByKey.merge(key, 1.0 / (RRF_K + rank + 1), Double::sum);
    }

    /** 融合后重排：候选数达门控下限才调用专用模型，否则维持融合序；作用于最终跨源候选。 */
    private List<FusedCandidate> rerank(String query, List<FusedCandidate> fused, int topK) {
        if (query.isBlank() || fused.size() <= 1) {
            return fused;
        }
        var contents = fused.stream().map(FusedCandidate::content).toList();
        var orderedIndexes = reranker.rerankContents(query, contents, topK);
        var result = new ArrayList<FusedCandidate>(orderedIndexes.size());
        for (var index : orderedIndexes) {
            result.add(fused.get(index));
        }
        return result.isEmpty() ? fused : result;
    }

    /** 查询意图分类（轻量规则，不调 LLM），迁移自旧 MemoryRetrievalService。 */
    private static QueryIntent classifyIntent(String query) {
        if (query == null || query.isBlank()) return QueryIntent.GENERAL;
        var lower = query.toLowerCase();
        if (lower.contains("怎么")
                || lower.contains("如何")
                || lower.contains("步骤")
                || lower.contains("流程")
                || lower.contains("方法")
                || lower.contains("how to")) {
            return QueryIntent.PROCEDURAL;
        }
        if (lower.contains("什么时候")
                || lower.contains("上次")
                || lower.contains("之前")
                || lower.contains("昨天")
                || lower.contains("上周")
                || lower.contains("when")) {
            return QueryIntent.TEMPORAL;
        }
        if (lower.contains("为什么") || lower.contains("原因") || lower.contains("why")) {
            return QueryIntent.CAUSAL;
        }
        return QueryIntent.GENERAL;
    }

    /** 预算分配（借鉴 M-FLOW 软路由），迁移自旧 MemoryRetrievalService。 */
    private static RetrievalBudget allocateBudget(QueryIntent intent) {
        return switch (intent) {
            case PROCEDURAL -> new RetrievalBudget(3, 2, 5);
            case TEMPORAL -> new RetrievalBudget(3, 6, 1);
            case CAUSAL -> new RetrievalBudget(4, 5, 1);
            case GENERAL -> new RetrievalBudget(ATOMIC_TOP_K, EPISODIC_TOP_K, PROCEDURAL_TOP_K);
        };
    }

    private enum QueryIntent {
        GENERAL,
        PROCEDURAL,
        TEMPORAL,
        CAUSAL
    }

    private record RetrievalBudget(int atomicTopK, int episodicTopK, int proceduralTopK) {}
}
