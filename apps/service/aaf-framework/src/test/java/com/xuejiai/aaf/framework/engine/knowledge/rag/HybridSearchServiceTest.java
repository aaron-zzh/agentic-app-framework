package com.xuejiai.aaf.framework.engine.knowledge.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xuejiai.aaf.framework.engine.knowledge.search.SimilaritySearchService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeAccessScopePort;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeGraphProjectionService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.AuthorizedKnowledgeBase;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.AuthorizedQuery;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.AuthorizedScope;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.ChannelWeights;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.SourceFilters;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.SourceRef;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.Visibility;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.SearchCandidate;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationSubject;

@ExtendWith(MockitoExtension.class)
class HybridSearchServiceTest {

    private static final SourceFilters NO_SOURCE_FILTERS =
            new SourceFilters(Set.of(), Set.of(), Set.of());

    @Mock private KnowledgeAccessScopePort accessScopePort;
    @Mock private SimilaritySearchService similaritySearchService;
    @Mock private TrustedKnowledgeStore truthStore;
    @Mock private KnowledgeGraphProjectionService graphProjectionService;

    private HybridSearchService service;

    @BeforeEach
    void setUp() {
        service =
                new HybridSearchService(
                        accessScopePort,
                        similaritySearchService,
                        truthStore,
                        graphProjectionService);
    }

    @Test
    @DisplayName("Given 两个已授权知识库包含相同正文 When 多库检索 Then 按稳定 chunkId 保留两条并先解析 ACL")
    void should_keep_same_content_with_distinct_chunk_ids_after_acl_resolution() {
        var base1 = UUID.randomUUID();
        var base2 = UUID.randomUUID();
        var chunk1 = UUID.randomUUID();
        var chunk2 = UUID.randomUUID();
        var query = query(Set.of(base1, base2), Map.of());
        var bases = new LinkedHashMap<UUID, AuthorizedKnowledgeBase>();
        bases.put(base1, new AuthorizedKnowledgeBase(base1, Visibility.PRIVATE, 1.0));
        bases.put(base2, new AuthorizedKnowledgeBase(base2, Visibility.SYSTEM_PUBLIC, 1.0));
        when(accessScopePort.resolve(query)).thenReturn(new AuthorizedScope(bases, "v1"));
        when(truthStore.keywordSearch("query", Set.of(base1), NO_SOURCE_FILTERS, 30))
                .thenReturn(List.of(new SearchCandidate(chunk1, "相同正文", 0.9)));
        when(truthStore.keywordSearch("query", Set.of(base2), NO_SOURCE_FILTERS, 30))
                .thenReturn(List.of(new SearchCandidate(chunk2, "相同正文", 0.9)));
        stubSource(base1, chunk1, "private");
        stubSource(base2, chunk2, "public");

        var response = service.search(query);

        assertThat(response.hits()).hasSize(2);
        assertThat(response.hits())
                .extracting(hit -> hit.candidateKey())
                .containsExactlyInAnyOrder("CHUNK:" + chunk1, "CHUNK:" + chunk2);
        var ordered = inOrder(accessScopePort, truthStore);
        ordered.verify(accessScopePort).resolve(query);
        ordered.verify(truthStore).keywordSearch("query", Set.of(base1), NO_SOURCE_FILTERS, 30);
    }

    @Test
    @DisplayName("Given 库权重不同且通道同分 When weighted RRF 融合 Then 高权重库优先")
    void should_rank_higher_base_weight_first() {
        var base1 = UUID.randomUUID();
        var base2 = UUID.randomUUID();
        var chunk1 = UUID.randomUUID();
        var chunk2 = UUID.randomUUID();
        var query = query(Set.of(base1, base2), Map.of(base1, 0.2, base2, 2.0));
        var bases =
                Map.of(
                        base1,
                        new AuthorizedKnowledgeBase(base1, Visibility.PRIVATE, 1.0),
                        base2,
                        new AuthorizedKnowledgeBase(base2, Visibility.PRIVATE, 1.0));
        when(accessScopePort.resolve(query)).thenReturn(new AuthorizedScope(bases, "v1"));
        when(truthStore.keywordSearch("query", Set.of(base1), NO_SOURCE_FILTERS, 30))
                .thenReturn(List.of(new SearchCandidate(chunk1, "低权重", 0.9)));
        when(truthStore.keywordSearch("query", Set.of(base2), NO_SOURCE_FILTERS, 30))
                .thenReturn(List.of(new SearchCandidate(chunk2, "高权重", 0.9)));
        stubSource(base1, chunk1, "one");
        stubSource(base2, chunk2, "two");

        var response = service.search(query);

        assertThat(response.hits())
                .extracting(hit -> hit.candidateKey())
                .containsExactly("CHUNK:" + chunk2, "CHUNK:" + chunk1);
    }

    @Test
    @DisplayName("Given 候选 weighted RRF 分数相同 When 排序 Then 按 candidateKey 稳定升序")
    void should_sort_by_candidate_key_when_scores_tie() {
        var base1 = UUID.randomUUID();
        var base2 = UUID.randomUUID();
        var lower = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var higher = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var query = query(Set.of(base1, base2), Map.of());
        when(accessScopePort.resolve(query))
                .thenReturn(
                        new AuthorizedScope(
                                Map.of(
                                        base1,
                                        new AuthorizedKnowledgeBase(base1, Visibility.PRIVATE, 1.0),
                                        base2,
                                        new AuthorizedKnowledgeBase(
                                                base2, Visibility.PRIVATE, 1.0)),
                                "v1"));
        when(truthStore.keywordSearch("query", Set.of(base1), NO_SOURCE_FILTERS, 30))
                .thenReturn(List.of(new SearchCandidate(higher, "B", 0.9)));
        when(truthStore.keywordSearch("query", Set.of(base2), NO_SOURCE_FILTERS, 30))
                .thenReturn(List.of(new SearchCandidate(lower, "A", 0.9)));
        stubSource(base1, higher, "b");
        stubSource(base2, lower, "a");

        var response = service.search(query);

        assertThat(response.hits())
                .extracting(hit -> hit.candidateKey())
                .containsExactly("CHUNK:" + lower, "CHUNK:" + higher);
    }

    @Test
    @DisplayName("Given 检索期间知识库权限被撤销 When 最终命中复核 Then 不返回已撤权候选")
    void should_drop_hit_when_acl_is_revoked_before_final_check() {
        var baseId = UUID.randomUUID();
        var chunkId = UUID.randomUUID();
        var query = query(Set.of(baseId), Map.of());
        var initialScope =
                new AuthorizedScope(
                        Map.of(
                                baseId,
                                new AuthorizedKnowledgeBase(baseId, Visibility.PRIVATE, 1.0)),
                        "v1");
        var revokedScope = new AuthorizedScope(Map.of(), "v2");
        when(accessScopePort.resolve(query)).thenReturn(initialScope, revokedScope);
        when(truthStore.keywordSearch("query", Set.of(baseId), NO_SOURCE_FILTERS, 30))
                .thenReturn(List.of(new SearchCandidate(chunkId, "已撤权正文", 0.9)));
        when(truthStore.sourceRef(chunkId, Set.of(), Set.of(), Set.of(), NO_SOURCE_FILTERS))
                .thenReturn(Optional.empty());

        var response = service.search(query);

        assertThat(response.hits()).isEmpty();
        assertThat(response.searchedKnowledgeBaseIds()).isEmpty();
        org.mockito.Mockito.verify(accessScopePort, org.mockito.Mockito.times(2)).resolve(query);
        org.mockito.Mockito.verify(truthStore)
                .sourceRef(chunkId, Set.of(), Set.of(), Set.of(), NO_SOURCE_FILTERS);
    }

    @Test
    @DisplayName("Given 图候选证据已撤销 When 最终来源复核 Then 不回显 fact/evidence 且丢弃命中")
    void should_drop_graph_hit_when_evidence_is_revoked() {
        var baseId = UUID.randomUUID();
        var chunkId = UUID.randomUUID();
        var factId = UUID.randomUUID();
        var evidenceId = UUID.randomUUID();
        var query =
                new AuthorizedQuery(
                        AuthorizationSubject.unresolved(),
                        "query",
                        Set.of(baseId),
                        true,
                        Map.of(),
                        new ChannelWeights(0.0, 0.0, 1.0),
                        10,
                        0.0,
                        Map.of());
        when(accessScopePort.resolve(query))
                .thenReturn(
                        new AuthorizedScope(
                                Map.of(
                                        baseId,
                                        new AuthorizedKnowledgeBase(
                                                baseId, Visibility.PRIVATE, 1.0)),
                                "v1"));
        when(truthStore.isProjectionReady(baseId, "NEO4J")).thenReturn(true);
        when(graphProjectionService.searchFactKeys("query", Set.of(baseId), NO_SOURCE_FILTERS, 30))
                .thenReturn(Set.of("fact-key"));
        when(truthStore.graphCandidates(Set.of("fact-key"), Set.of(baseId), NO_SOURCE_FILTERS, 30))
                .thenReturn(
                        List.of(
                                new com.xuejiai.aaf.framework.engine.knowledge.trusted
                                        .TrustedKnowledgeStore.GraphCandidate(
                                        chunkId, "已撤销证据", factId, evidenceId)));
        when(truthStore.sourceRef(
                        chunkId,
                        Set.of(factId),
                        Set.of(evidenceId),
                        Set.of(baseId),
                        NO_SOURCE_FILTERS))
                .thenReturn(Optional.empty());

        var response = service.search(query);

        assertThat(response.hits()).isEmpty();
    }

    @Test
    @DisplayName("Given graph checkpoint 非 READY When 图通道搜索 Then 跳过 Neo4j 并报告降级")
    void should_skip_graph_and_report_degraded_when_projection_not_ready() {
        var baseId = UUID.randomUUID();
        var query =
                new AuthorizedQuery(
                        AuthorizationSubject.unresolved(),
                        "query",
                        Set.of(baseId),
                        true,
                        Map.of(),
                        new ChannelWeights(0.0, 0.0, 1.0),
                        10,
                        0.0,
                        Map.of());
        when(accessScopePort.resolve(query))
                .thenReturn(
                        new AuthorizedScope(
                                Map.of(
                                        baseId,
                                        new AuthorizedKnowledgeBase(
                                                baseId, Visibility.PRIVATE, 1.0)),
                                "v1"));
        when(truthStore.isProjectionReady(baseId, "NEO4J")).thenReturn(false);

        var response = service.search(query);

        assertThat(response.hits()).isEmpty();
        assertThat(response.degradedChannels())
                .containsExactly(
                        com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts
                                .Channel.GRAPH);
        org.mockito.Mockito.verifyNoInteractions(graphProjectionService);
    }

    private AuthorizedQuery query(Set<UUID> baseIds, Map<UUID, Double> weights) {
        return new AuthorizedQuery(
                AuthorizationSubject.unresolved(),
                "query",
                baseIds,
                true,
                weights,
                new ChannelWeights(0.0, 1.0, 0.0),
                10,
                0.0,
                Map.of());
    }

    private void stubSource(UUID baseId, UUID chunkId, String sourceKey) {
        when(truthStore.sourceRef(
                        org.mockito.ArgumentMatchers.eq(chunkId),
                        org.mockito.ArgumentMatchers.eq(Set.of()),
                        org.mockito.ArgumentMatchers.eq(Set.of()),
                        anySet(),
                        org.mockito.ArgumentMatchers.eq(NO_SOURCE_FILTERS)))
                .thenReturn(
                        Optional.of(
                                new SourceRef(
                                        baseId,
                                        sourceKey,
                                        Visibility.PRIVATE,
                                        UUID.randomUUID(),
                                        "FILE",
                                        sourceKey,
                                        null,
                                        UUID.randomUUID(),
                                        chunkId,
                                        Set.of(),
                                        Set.of())));
    }
}
