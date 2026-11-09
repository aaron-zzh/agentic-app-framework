package com.xuejiai.aaf.framework.engine.knowledge.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.knowledge.search.SearchRequest;
import com.xuejiai.aaf.framework.engine.knowledge.search.SimilaritySearchService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeAccessScopePort;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeGraphProjectionService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.AuthorizedKnowledgeBase;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.AuthorizedQuery;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.Channel;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.Hit;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.Response;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.SourceFilters;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore;
import com.xuejiai.aaf.framework.intelligent.ai.rerank.RerankService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 授权多库向量、全文和图检索：weighted RRF 融合召回，再经重排模型精排。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private static final int RRF_K = 60;
    private static final int OVERSAMPLE = 3;

    /** 每个通道每个知识库至少召回的候选数，为精排提供足够的候选池。 */
    private static final int MIN_RECALL_PER_CHANNEL = 30;

    /** 交给重排模型的融合候选上限。 */
    private static final int RERANK_POOL = 40;

    private final KnowledgeAccessScopePort accessScopePort;
    private final SimilaritySearchService similaritySearchService;
    private final TrustedKnowledgeStore truthStore;
    private final KnowledgeGraphProjectionService graphProjectionService;

    /** 重排服务为可选 Bean：未配置百炼 key 时不存在，检索保持融合排序。 */
    private final ObjectProvider<RerankService> rerankServiceProvider;

    public Response search(AuthorizedQuery query) {
        var startedAt = System.nanoTime();
        var scope = accessScopePort.resolve(query);
        var authorizedAt = System.nanoTime();
        if (scope.knowledgeBases().isEmpty()) {
            log.info(
                    "知识检索耗时: baseCount=0, authMs={}, totalMs={}, hitCount=0",
                    millisBetween(startedAt, authorizedAt),
                    millisBetween(startedAt, authorizedAt));
            return new Response(List.of(), Set.of(), Set.of());
        }
        var degraded = ConcurrentHashMap.<Channel>newKeySet();
        // 各库并行检索，同一通道取各库中最慢的一次，即该通道对总耗时的贡献
        var channelMillis = new ConcurrentHashMap<Channel, Long>();
        var byBase = new LinkedHashMap<UUID, List<Candidate>>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new LinkedHashMap<UUID, java.util.concurrent.Future<List<Candidate>>>();
            scope.knowledgeBases()
                    .forEach(
                            (baseId, base) ->
                                    futures.put(
                                            baseId,
                                            executor.submit(
                                                    () ->
                                                            searchBase(
                                                                    query,
                                                                    base,
                                                                    degraded,
                                                                    channelMillis))));
            for (var entry : futures.entrySet()) {
                byBase.put(entry.getKey(), entry.getValue().get());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("知识检索被中断", interrupted);
        } catch (java.util.concurrent.ExecutionException failure) {
            throw new IllegalStateException("授权知识库检索失败", failure.getCause());
        }
        var retrievedAt = System.nanoTime();

        var fused = new HashMap<String, Aggregate>();
        for (var entry : byBase.entrySet()) {
            var baseId = entry.getKey();
            var base = scope.knowledgeBases().get(baseId);
            var baseWeight =
                    query.knowledgeBaseWeights().getOrDefault(baseId, base.defaultWeight());
            var byChannel = groupByChannel(entry.getValue());
            for (var channelEntry : byChannel.entrySet()) {
                var channel = channelEntry.getKey();
                var channelWeight = query.channelWeights().weight(channel);
                var ranked = channelEntry.getValue();
                for (var rank = 0; rank < ranked.size(); rank++) {
                    var candidate = ranked.get(rank);
                    var aggregate =
                            fused.computeIfAbsent(
                                    candidate.candidateKey(),
                                    ignored ->
                                            new Aggregate(
                                                    candidate.chunkId(), candidate.content()));
                    aggregate.score += baseWeight * channelWeight / (RRF_K + rank + 1.0);
                    aggregate.channels.add(channel);
                    aggregate.factIds.addAll(candidate.factIds());
                    aggregate.evidenceIds.addAll(candidate.evidenceIds());
                }
            }
        }

        var finalScope = accessScopePort.resolve(query);
        var sourceFilters = SourceFilters.from(query.sourceFilters());
        var ranked =
                fused.entrySet().stream()
                        .sorted(
                                Map.Entry.<String, Aggregate>comparingByValue(
                                                Comparator.comparingDouble(
                                                                (Aggregate item) -> item.score)
                                                        .reversed())
                                        .thenComparing(Map.Entry::getKey))
                        .toList();
        var hits =
                rerank(query, ranked).stream()
                        .map(
                                entry ->
                                        toHit(
                                                entry.getKey(),
                                                entry.getValue(),
                                                finalScope.knowledgeBases().keySet(),
                                                sourceFilters))
                        .flatMap(java.util.Optional::stream)
                        .limit(query.topK())
                        .toList();
        var finishedAt = System.nanoTime();
        log.info(
                "知识检索耗时: baseCount={}, authMs={}, retrieveMs={}, vectorMs={}, keywordMs={}, graphMs={}, fuseMs={}, totalMs={}, hitCount={}, degraded={}",
                scope.knowledgeBases().size(),
                millisBetween(startedAt, authorizedAt),
                millisBetween(authorizedAt, retrievedAt),
                channelMillis.getOrDefault(Channel.VECTOR, 0L),
                channelMillis.getOrDefault(Channel.KEYWORD, 0L),
                channelMillis.getOrDefault(Channel.GRAPH, 0L),
                millisBetween(retrievedAt, finishedAt),
                millisBetween(startedAt, finishedAt),
                hits.size(),
                degraded);
        return new Response(hits, finalScope.knowledgeBases().keySet(), Set.copyOf(degraded));
    }

    private static long millisBetween(long startNanos, long endNanos) {
        return (endNanos - startNanos) / 1_000_000;
    }

    /**
     * 两阶段检索的精排：融合排序前 {@link #RERANK_POOL} 个候选交给重排模型重新排序，其余候选按融合顺序排在后面。
     *
     * <p>未配置重排模型、候选不足两个或重排失败（含积分不足）时保持融合顺序，检索本身不因精排失败而失败。 命中分数仍为融合分数，只调整顺序。
     */
    private List<Map.Entry<String, Aggregate>> rerank(
            AuthorizedQuery query, List<Map.Entry<String, Aggregate>> ranked) {
        var reranker = rerankServiceProvider.getIfAvailable();
        var userId = query.subject().subjectId();
        if (reranker == null || userId == null || ranked.size() < 2) {
            return ranked;
        }
        var pool = ranked.subList(0, Math.min(RERANK_POOL, ranked.size()));
        try {
            var startedAt = System.nanoTime();
            var results =
                    reranker.rerank(
                            userId,
                            query.query(),
                            pool.stream().map(entry -> entry.getValue().content).toList(),
                            pool.size());
            var ordered = new ArrayList<Map.Entry<String, Aggregate>>(ranked.size());
            var placed = new HashSet<Integer>();
            for (var result : results) {
                var index = result.index();
                if (index >= 0 && index < pool.size() && placed.add(index)) {
                    ordered.add(pool.get(index));
                }
            }
            for (var index = 0; index < ranked.size(); index++) {
                if (!placed.contains(index)) {
                    ordered.add(ranked.get(index));
                }
            }
            log.info(
                    "知识检索重排: candidates={}, rerankMs={}",
                    pool.size(),
                    millisBetween(startedAt, System.nanoTime()));
            return ordered;
        } catch (RuntimeException failure) {
            log.warn("知识检索重排失败，保持融合排序: {}", failure.getMessage());
            return ranked;
        }
    }

    private static void recordChannel(
            Map<Channel, Long> channelMillis, Channel channel, long startNanos) {
        channelMillis.merge(channel, millisBetween(startNanos, System.nanoTime()), Math::max);
    }

    private List<Candidate> searchBase(
            AuthorizedQuery query,
            AuthorizedKnowledgeBase base,
            Set<Channel> degraded,
            Map<Channel, Long> channelMillis) {
        var candidates = new ArrayList<Candidate>();
        var limit = Math.max(query.topK() * OVERSAMPLE, MIN_RECALL_PER_CHANNEL);
        var sourceFilters = SourceFilters.from(query.sourceFilters());
        if (query.channelWeights().vector() > 0) {
            var channelStartedAt = System.nanoTime();
            try {
                var request =
                        new SearchRequest(
                                query.query(),
                                limit,
                                query.vectorThreshold(),
                                base.knowledgeBaseId(),
                                sourceFilters,
                                query.subject().subjectId());
                similaritySearchService.search(request).stream()
                        .map(
                                result ->
                                        new Candidate(
                                                "CHUNK:" + result.chunkId(),
                                                UUID.fromString(result.chunkId()),
                                                result.content(),
                                                result.score(),
                                                Channel.VECTOR,
                                                Set.of(),
                                                Set.of()))
                        .forEach(candidates::add);
            } catch (RuntimeException failure) {
                degraded.add(Channel.VECTOR);
            } finally {
                recordChannel(channelMillis, Channel.VECTOR, channelStartedAt);
            }
        }
        if (query.channelWeights().keyword() > 0) {
            var channelStartedAt = System.nanoTime();
            try {
                truthStore
                        .keywordSearch(
                                query.query(), Set.of(base.knowledgeBaseId()), sourceFilters, limit)
                        .stream()
                        .map(
                                result ->
                                        new Candidate(
                                                "CHUNK:" + result.chunkId(),
                                                result.chunkId(),
                                                result.content(),
                                                result.score(),
                                                Channel.KEYWORD,
                                                Set.of(),
                                                Set.of()))
                        .forEach(candidates::add);
            } catch (RuntimeException failure) {
                degraded.add(Channel.KEYWORD);
            } finally {
                recordChannel(channelMillis, Channel.KEYWORD, channelStartedAt);
            }
        }
        if (query.channelWeights().graph() > 0) {
            var channelStartedAt = System.nanoTime();
            if (!truthStore.isProjectionReady(base.knowledgeBaseId(), "NEO4J")) {
                degraded.add(Channel.GRAPH);
            } else {
                try {
                    var factKeys =
                            graphProjectionService.searchFactKeys(
                                    query.query(),
                                    Set.of(base.knowledgeBaseId()),
                                    sourceFilters,
                                    limit);
                    truthStore
                            .graphCandidates(
                                    factKeys, Set.of(base.knowledgeBaseId()), sourceFilters, limit)
                            .stream()
                            .map(
                                    result ->
                                            new Candidate(
                                                    "CHUNK:" + result.chunkId(),
                                                    result.chunkId(),
                                                    result.content(),
                                                    1.0,
                                                    Channel.GRAPH,
                                                    Set.of(result.factId()),
                                                    Set.of(result.evidenceId())))
                            .forEach(candidates::add);
                } catch (RuntimeException failure) {
                    degraded.add(Channel.GRAPH);
                }
            }
            recordChannel(channelMillis, Channel.GRAPH, channelStartedAt);
        }
        return List.copyOf(candidates);
    }

    private Map<Channel, List<Candidate>> groupByChannel(List<Candidate> candidates) {
        var grouped = new LinkedHashMap<Channel, List<Candidate>>();
        for (var channel : Channel.values()) {
            var channelResults =
                    candidates.stream()
                            .filter(candidate -> candidate.channel() == channel)
                            .sorted(
                                    Comparator.comparingDouble(Candidate::rawScore)
                                            .reversed()
                                            .thenComparing(Candidate::candidateKey))
                            .toList();
            grouped.put(channel, channelResults);
        }
        return grouped;
    }

    private java.util.Optional<Hit> toHit(
            String candidateKey,
            Aggregate aggregate,
            Set<UUID> finalAuthorizedBaseIds,
            SourceFilters sourceFilters) {
        var source =
                truthStore.sourceRef(
                        aggregate.chunkId,
                        aggregate.factIds,
                        aggregate.evidenceIds,
                        finalAuthorizedBaseIds,
                        sourceFilters);
        return source.flatMap(
                value -> {
                    var channels = EnumSet.copyOf(aggregate.channels);
                    if (channels.contains(Channel.GRAPH) && value.factIds().isEmpty()) {
                        channels.remove(Channel.GRAPH);
                    }
                    if (channels.isEmpty()) {
                        return java.util.Optional.empty();
                    }
                    return java.util.Optional.of(
                            new Hit(
                                    candidateKey,
                                    aggregate.content,
                                    aggregate.score,
                                    Set.copyOf(channels),
                                    value));
                });
    }

    private record Candidate(
            String candidateKey,
            UUID chunkId,
            String content,
            double rawScore,
            Channel channel,
            Set<UUID> factIds,
            Set<UUID> evidenceIds) {}

    private static final class Aggregate {
        private final UUID chunkId;
        private final String content;
        private final Set<Channel> channels = EnumSet.noneOf(Channel.class);
        private final Set<UUID> factIds = new LinkedHashSet<>();
        private final Set<UUID> evidenceIds = new LinkedHashSet<>();
        private double score;

        private Aggregate(UUID chunkId, String content) {
            this.chunkId = chunkId;
            this.content = content;
        }
    }
}
