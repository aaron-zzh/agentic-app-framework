package com.xuejiai.aaf.framework.engine.knowledge.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

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

import lombok.RequiredArgsConstructor;

/** 授权多库向量、全文和图检索的 weighted RRF 入口。 */
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private static final int RRF_K = 60;
    private static final int OVERSAMPLE = 3;

    private final KnowledgeAccessScopePort accessScopePort;
    private final SimilaritySearchService similaritySearchService;
    private final TrustedKnowledgeStore truthStore;
    private final KnowledgeGraphProjectionService graphProjectionService;

    public Response search(AuthorizedQuery query) {
        var scope = accessScopePort.resolve(query);
        if (scope.knowledgeBases().isEmpty()) {
            return new Response(List.of(), Set.of(), Set.of());
        }
        var degraded = ConcurrentHashMap.<Channel>newKeySet();
        var byBase = new LinkedHashMap<UUID, List<Candidate>>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new LinkedHashMap<UUID, java.util.concurrent.Future<List<Candidate>>>();
            scope.knowledgeBases()
                    .forEach(
                            (baseId, base) ->
                                    futures.put(
                                            baseId,
                                            executor.submit(
                                                    () -> searchBase(query, base, degraded))));
            for (var entry : futures.entrySet()) {
                byBase.put(entry.getKey(), entry.getValue().get());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("知识检索被中断", interrupted);
        } catch (java.util.concurrent.ExecutionException failure) {
            throw new IllegalStateException("授权知识库检索失败", failure.getCause());
        }

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
        var hits =
                fused.entrySet().stream()
                        .sorted(
                                Map.Entry.<String, Aggregate>comparingByValue(
                                                Comparator.comparingDouble(
                                                                (Aggregate item) -> item.score)
                                                        .reversed())
                                        .thenComparing(Map.Entry::getKey))
                        .limit(query.topK())
                        .map(
                                entry ->
                                        toHit(
                                                entry.getKey(),
                                                entry.getValue(),
                                                finalScope.knowledgeBases().keySet(),
                                                sourceFilters))
                        .flatMap(java.util.Optional::stream)
                        .toList();
        return new Response(hits, finalScope.knowledgeBases().keySet(), Set.copyOf(degraded));
    }

    private List<Candidate> searchBase(
            AuthorizedQuery query, AuthorizedKnowledgeBase base, Set<Channel> degraded) {
        var candidates = new ArrayList<Candidate>();
        var limit = query.topK() * OVERSAMPLE;
        var sourceFilters = SourceFilters.from(query.sourceFilters());
        if (query.channelWeights().vector() > 0) {
            try {
                var request =
                        new SearchRequest(
                                query.query(),
                                limit,
                                query.vectorThreshold(),
                                base.knowledgeBaseId(),
                                sourceFilters);
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
            }
        }
        if (query.channelWeights().keyword() > 0) {
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
            }
        }
        if (query.channelWeights().graph() > 0) {
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
