package com.xuejiai.aaf.framework.engine.knowledge.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.engine.knowledge.graph.GraphSearchService;
import com.xuejiai.aaf.framework.engine.knowledge.graph.KnowledgeEntity;
import com.xuejiai.aaf.framework.engine.knowledge.graph.KnowledgeEntityRepository;
import com.xuejiai.aaf.framework.engine.knowledge.search.SearchRequest;
import com.xuejiai.aaf.framework.engine.knowledge.search.SearchResult;
import com.xuejiai.aaf.framework.engine.knowledge.search.SimilaritySearchService;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

class HybridSearchServiceTest extends BaseMockitoUnitTest {

    @Mock private SimilaritySearchService similaritySearchService;
    @Mock private GraphSearchService graphSearchService;
    @Mock private KnowledgeEntityRepository entityRepository;
    @Mock private EntityManager entityManager;
    @Mock private Query nativeQuery;
    @InjectMocks private HybridSearchService hybridSearchService;

    @Test
    @DisplayName("Given 向量模式阈值 When 检索 Then 将知识库和阈值传递给相似度服务")
    void should_pass_threshold_and_knowledge_base_when_vector_searches() {
        // 准备参数
        when(similaritySearchService.search(any(SearchRequest.class)))
                .thenReturn(
                        List.of(
                                new SearchResult(
                                        "向量内容", 0.82, Map.of("chunk_id", 21L), "21", "11")));
        var requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);

        // 调用
        var results = hybridSearchService.vectorSearch("查询", 3L, 8, 0.75);

        // 断言
        verify(similaritySearchService).search(requestCaptor.capture());
        assertThat(requestCaptor.getValue().knowledgeBaseId()).isEqualTo(3L);
        assertThat(requestCaptor.getValue().topK()).isEqualTo(8);
        assertThat(requestCaptor.getValue().similarityThreshold()).isEqualTo(0.75);
        assertThat(results)
                .singleElement()
                .satisfies(
                        result -> {
                            assertThat(result.source()).isEqualTo("vector");
                            assertThat(result.content()).isEqualTo("向量内容");
                        });
    }

    @Test
    @DisplayName("Given 关键词模式 When 检索 Then 返回真实块标识")
    void should_return_chunk_identity_when_keyword_searches() {
        // 准备参数
        when(entityManager.createNativeQuery(any(String.class))).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(any(String.class), any())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList())
                .thenReturn(
                        List.of(
                                new Object[] {21L, 11L, "高相关", 0.9},
                                new Object[] {22L, 12L, "低相关", 0.2}));

        // 调用
        var results = hybridSearchService.keywordSearch("查询", 3L, 10);

        // 断言
        var sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("to_tsvector('simple', content)")
                .contains("plainto_tsquery('simple', :query)")
                .doesNotContain("'chinese'");
        assertThat(results).hasSize(2);
        assertThat(results.getFirst())
                .satisfies(
                        result -> {
                            assertThat(result.source()).isEqualTo("keyword");
                            assertThat(result.metadata())
                                    .containsEntry("chunk_id", 21L)
                                    .containsEntry("document_id", 11L);
                        });
    }

    @Test
    @DisplayName("Given 抽取生成的无描述实体 When 混合检索 Then 返回真实图谱实体标识和名称")
    void should_return_real_graph_entity_when_hybrid_searches() {
        // 准备参数
        var matched = new KnowledgeEntity();
        matched.setId("entity-1");
        matched.setName("AAF");
        matched.setKnowledgeBaseId(3L);
        var neighbor = new KnowledgeEntity();
        neighbor.setId("entity-2");
        neighbor.setName("知识图谱");
        neighbor.setType("Concept");
        neighbor.setKnowledgeBaseId(3L);
        when(entityRepository.findByNameContaining("AAF")).thenReturn(List.of(matched));
        when(graphSearchService.subgraphSearch("entity-1", 2)).thenReturn(List.of(neighbor));
        when(similaritySearchService.search(any(SearchRequest.class))).thenReturn(List.of());
        when(entityManager.createNativeQuery(any(String.class))).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(any(String.class), any())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of());

        // 调用
        var results =
                hybridSearchService.hybridSearch(
                        "AAF", 3L, new HybridSearchConfig(0.0, 0.0, 1.0, 5), 0.5);

        // 断言
        assertThat(results)
                .singleElement()
                .satisfies(
                        result -> {
                            assertThat(result.content()).isEqualTo("知识图谱");
                            assertThat(result.source()).isEqualTo("graph");
                            assertThat(result.metadata())
                                    .containsEntry("entity_id", "entity-2")
                                    .containsEntry("entityName", "知识图谱");
                        });
    }
}
