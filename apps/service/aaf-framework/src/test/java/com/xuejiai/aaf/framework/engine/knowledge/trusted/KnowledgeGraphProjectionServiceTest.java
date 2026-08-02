package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.SourceFilters;

@ExtendWith(MockitoExtension.class)
class KnowledgeGraphProjectionServiceTest {

    @Mock private Driver driver;
    @Mock private Session session;
    @Mock private Result directResult;
    @Mock private Result relatedResult;
    @Mock private TrustedKnowledgeStore store;

    private KnowledgeGraphProjectionService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeGraphProjectionService(driver, store);
    }

    @Test
    @SuppressWarnings("rawtypes")
    @DisplayName("Given 直接事实未占满额度 When 图检索 Then 在授权与来源范围内扩展相邻事实")
    void should_expand_related_facts_with_remaining_limit() {
        var knowledgeBaseId = UUID.randomUUID();
        var documentId = UUID.randomUUID();
        var filters = new SourceFilters(Set.of("FILE"), Set.of("source-key"), Set.of(documentId));
        when(driver.session()).thenReturn(session);
        when(session.run(anyString(), anyMap())).thenReturn(directResult, relatedResult);
        doReturn(List.of("direct-fact")).when(directResult).list(any());
        doReturn(List.of("related-a", "related-b")).when(relatedResult).list(any());

        var factKeys = service.searchFactKeys("AAF", Set.of(knowledgeBaseId), filters, 3);

        assertThat(factKeys).containsExactlyInAnyOrder("direct-fact", "related-a", "related-b");
        var cypher = ArgumentCaptor.forClass(String.class);
        var parameters = ArgumentCaptor.forClass(Map.class);
        verify(session, times(2)).run(cypher.capture(), parameters.capture());
        assertThat(cypher.getAllValues().get(1))
                .contains("direct.factKey IN $directFactKeys")
                .contains("MATCH (anchor)-[related:FACT]-(neighbor:KnowledgeEntity)")
                .contains("anchor.knowledgeBaseId IN $knowledgeBaseIds")
                .contains("neighbor.knowledgeBaseId IN $knowledgeBaseIds")
                .contains("related.sourceTypes")
                .contains("related.sourceKeys")
                .contains("related.documentIds");
        assertThat(parameters.getAllValues().get(1))
                .containsEntry("knowledgeBaseIds", List.of(knowledgeBaseId.toString()))
                .containsEntry("directFactKeys", List.of("direct-fact"))
                .containsEntry("sourceTypes", List.of("FILE"))
                .containsEntry("sourceKeys", List.of("source-key"))
                .containsEntry("documentIds", List.of(documentId.toString()))
                .containsEntry("limit", 2);
    }

    @Test
    @DisplayName("Given 直接事实已占满额度 When 图检索 Then 不执行二跳扩展")
    void should_skip_expansion_when_direct_facts_fill_limit() {
        when(driver.session()).thenReturn(session);
        when(session.run(anyString(), anyMap())).thenReturn(directResult);
        doReturn(List.of("direct-a", "direct-b")).when(directResult).list(any());

        var factKeys =
                service.searchFactKeys(
                        "AAF", Set.of(UUID.randomUUID()), SourceFilters.from(Map.of()), 2);

        assertThat(factKeys).containsExactlyInAnyOrder("direct-a", "direct-b");
        verify(session, times(1)).run(anyString(), anyMap());
        verify(session, never())
                .run(org.mockito.ArgumentMatchers.contains("related:FACT"), anyMap());
    }

    @Test
    @DisplayName("Given 无授权知识库或额度非正数 When 图检索 Then 不访问 Neo4j")
    void should_not_query_neo4j_without_scope_or_limit() {
        var noFilters = SourceFilters.from(Map.of());
        assertThat(service.searchFactKeys("AAF", Set.of(), noFilters, 3)).isEmpty();
        assertThat(service.searchFactKeys("AAF", Set.of(UUID.randomUUID()), noFilters, 0))
                .isEmpty();

        verifyNoInteractions(driver);
    }
}
