package com.xuejiai.aaf.module.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.engine.knowledge.graph.GraphSearchService;
import com.xuejiai.aaf.framework.engine.knowledge.graph.KnowledgeEntityRepository;
import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchConfig;
import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchService;
import com.xuejiai.aaf.framework.engine.knowledge.search.SearchRequest;
import com.xuejiai.aaf.framework.engine.knowledge.search.SimilaritySearchService;

@SpringBootTest(properties = "aaf.task.queue.enabled=false")
@ActiveProfiles("test")
@Transactional
class HybridSearchPostgresIT {

    @Autowired private HybridSearchService hybridSearchService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private SimilaritySearchService similaritySearchService;
    @MockitoBean private GraphSearchService graphSearchService;
    @MockitoBean private KnowledgeEntityRepository entityRepository;

    private Long knowledgeBaseId;

    @BeforeEach
    void setUp() {
        knowledgeBaseId =
                jdbcTemplate.queryForObject(
                        "INSERT INTO ai_knowledge_base(name) VALUES (?) RETURNING id",
                        Long.class,
                        "检索集成测试");
        var documentId =
                jdbcTemplate.queryForObject(
                        "INSERT INTO ai_knowledge_document(knowledge_base_id, title) VALUES (?, ?) RETURNING id",
                        Long.class,
                        knowledgeBaseId,
                        "guide.md");
        jdbcTemplate.update(
                "INSERT INTO ai_knowledge_chunk(document_id, knowledge_base_id, content) VALUES (?, ?, ?)",
                documentId,
                knowledgeBaseId,
                "Agentic framework knowledge search");
        when(similaritySearchService.search(any(SearchRequest.class))).thenReturn(List.of());
        when(entityRepository.findByNameContaining(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Given PostgreSQL 现有 schema When 关键词检索 Then simple 配置返回真实知识块")
    void should_execute_keyword_search_against_postgresql() {
        // 调用
        var results = hybridSearchService.keywordSearch("Agentic", knowledgeBaseId, 5);

        // 断言
        assertThat(results)
                .singleElement()
                .satisfies(
                        result -> {
                            assertThat(result.content())
                                    .isEqualTo("Agentic framework knowledge search");
                            assertThat(result.source()).isEqualTo("keyword");
                            assertThat(result.metadata()).containsKeys("chunk_id", "document_id");
                        });
    }

    @Test
    @DisplayName("Given PostgreSQL 关键词知识块 When 混合检索 Then 融合结果包含 keyword 分支")
    void should_execute_hybrid_search_against_postgresql() {
        // 调用
        var results =
                hybridSearchService.hybridSearch(
                        "Agentic", knowledgeBaseId, new HybridSearchConfig(0.0, 1.0, 0.0, 5), 0.0);

        // 断言
        assertThat(results)
                .singleElement()
                .satisfies(result -> assertThat(result.source()).isEqualTo("keyword"));
    }
}
