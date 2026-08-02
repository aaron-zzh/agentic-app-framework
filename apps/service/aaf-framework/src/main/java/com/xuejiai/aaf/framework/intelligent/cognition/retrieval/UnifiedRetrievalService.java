package com.xuejiai.aaf.framework.intelligent.cognition.retrieval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.AuthorizedQuery;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.ChannelWeights;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.Hit;
import com.xuejiai.aaf.framework.engine.memory.AtomMemoryEngine;
import com.xuejiai.aaf.framework.engine.memory.MemoryAtom;
import com.xuejiai.aaf.framework.engine.memory.MemoryBundle;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryRerankerService;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationSubject;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 跨 Memory 与 Knowledge 的授权融合检索入口。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedRetrievalService {

    private static final int RRF_K = 60;

    private final AtomMemoryEngine memoryEngine;
    private final HybridSearchService knowledgeSearch;
    private final EmbeddingService embeddingService;
    private final MemoryRerankerService reranker;

    public RetrievalResult retrieve(RetrievalRequest request) {
        var queryEmbedding = embeddingService.embed(request.query(), request.userId());
        var route = decideRoute(request);

        List<MemoryAtom> memoryResults = List.of();
        List<MemoryBundle> bundles = List.of();
        List<Hit> knowledgeResults = List.of();

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
            Future<List<Hit>> knowledgeFuture =
                    route.searchKnowledge()
                            ? executor.submit(() -> searchKnowledge(request, route.knowledgeTopK()))
                            : executor.submit(() -> List.<Hit>of());

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
            if (route.searchKnowledge()) {
                knowledgeResults = searchKnowledge(request, route.knowledgeTopK());
            }
        }

        var fused = fuseResults(memoryResults, knowledgeResults, request.topK());
        if (memoryResults.size() > 1) {
            memoryResults = reranker.rerank(request.query(), memoryResults, route.memoryTopK());
        }
        return new RetrievalResult(memoryResults, bundles, knowledgeResults, fused);
    }

    private List<Hit> searchKnowledge(RetrievalRequest request, int topK) {
        var query =
                new AuthorizedQuery(
                        AuthorizationSubject.unresolved(),
                        request.query(),
                        request.knowledgeBaseIds(),
                        request.includePublic(),
                        Map.of(),
                        ChannelWeights.defaults(),
                        topK,
                        request.similarityThreshold(),
                        Map.of());
        return knowledgeSearch.search(query).hits();
    }

    private RouteDecision decideRoute(RetrievalRequest request) {
        boolean hasKnowledge = !request.knowledgeBaseIds().isEmpty() || request.includePublic();
        boolean hasUser = request.userId() != null;
        if (hasKnowledge && hasUser) {
            return new RouteDecision(true, true, true, 6, 3, 6);
        }
        if (hasKnowledge) {
            return new RouteDecision(false, false, true, 0, 0, 10);
        }
        return new RouteDecision(true, true, false, 8, 4, 0);
    }

    private List<FusedItem> fuseResults(List<MemoryAtom> memory, List<Hit> knowledge, int topK) {
        Map<String, double[]> scoreMap = new LinkedHashMap<>();
        Map<String, FusedItem> itemMap = new LinkedHashMap<>();

        IntStream.range(0, memory.size())
                .forEach(
                        rank -> {
                            var atom = memory.get(rank);
                            var key = "MEMORY:" + atom.getId();
                            scoreMap.computeIfAbsent(key, ignored -> new double[] {0.0})[0] +=
                                    0.5 / (RRF_K + rank + 1);
                            itemMap.putIfAbsent(
                                    key,
                                    new FusedItem(
                                            key,
                                            atom.getContent(),
                                            "memory",
                                            scoreMap.get(key)[0]));
                        });

        IntStream.range(0, knowledge.size())
                .forEach(
                        rank -> {
                            var item = knowledge.get(rank);
                            var key = item.candidateKey();
                            scoreMap.computeIfAbsent(key, ignored -> new double[] {0.0})[0] +=
                                    0.5 / (RRF_K + rank + 1);
                            itemMap.putIfAbsent(
                                    key,
                                    new FusedItem(
                                            key,
                                            item.content(),
                                            item.source().knowledgeBaseName(),
                                            scoreMap.get(key)[0]));
                        });

        return scoreMap.entrySet().stream()
                .sorted(
                        (left, right) -> {
                            int score = Double.compare(right.getValue()[0], left.getValue()[0]);
                            return score != 0 ? score : left.getKey().compareTo(right.getKey());
                        })
                .limit(topK)
                .map(
                        entry -> {
                            var item = itemMap.get(entry.getKey());
                            return new FusedItem(
                                    item.candidateKey(),
                                    item.content(),
                                    item.source(),
                                    entry.getValue()[0]);
                        })
                .toList();
    }

    private record RouteDecision(
            boolean searchMemory,
            boolean searchBundles,
            boolean searchKnowledge,
            int memoryTopK,
            int bundleTopK,
            int knowledgeTopK) {}

    public record RetrievalRequest(
            String query,
            Long userId,
            Set<UUID> knowledgeBaseIds,
            boolean includePublic,
            double similarityThreshold,
            int topK) {
        public RetrievalRequest {
            knowledgeBaseIds = knowledgeBaseIds == null ? Set.of() : Set.copyOf(knowledgeBaseIds);
            if (topK <= 0) {
                topK = 10;
            }
        }
    }

    public record RetrievalResult(
            List<MemoryAtom> memoryResults,
            List<MemoryBundle> bundles,
            List<Hit> knowledgeResults,
            List<FusedItem> fused) {}

    public record FusedItem(String candidateKey, String content, String source, double score) {}
}
