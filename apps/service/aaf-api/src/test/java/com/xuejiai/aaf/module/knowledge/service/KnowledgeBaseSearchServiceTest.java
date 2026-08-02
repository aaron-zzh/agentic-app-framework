package com.xuejiai.aaf.module.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xuejiai.aaf.framework.engine.knowledge.graph.GraphService;
import com.xuejiai.aaf.framework.engine.knowledge.pipeline.KnowledgePipelineService;
import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.AuthorizedQuery;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.Response;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeBaseRepository;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeDocumentRepository;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeSearchDTO;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseSearchServiceTest {

    @Mock private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock private KnowledgeDocumentRepository knowledgeDocumentRepository;
    @Mock private KnowledgeDocumentUploadService uploadService;
    @Mock private KnowledgeDocumentQueueService queueService;
    @Mock private KnowledgeDocumentExecutionLeaseService executionLeaseService;
    @Mock private HybridSearchService hybridSearchService;
    @Mock private GraphService graphService;
    @Mock private KnowledgePipelineService pipelineService;
    @Mock private KnowledgeDocumentCleanupQueueService cleanupQueueService;
    @Mock private EntityManager entityManager;
    @Mock private OperatorContext operatorContext;
    @InjectMocks private KnowledgeBaseService service;

    @Test
    @DisplayName("Given 合法来源过滤 When 搜索知识库 Then 规范化后原样透传到授权查询")
    void should_normalize_and_forward_source_filters() {
        var documentId = UUID.randomUUID();
        var request =
                new KnowledgeSearchDTO(
                        "query",
                        Set.of(),
                        true,
                        Map.of(),
                        Map.of(
                                "sourceTypes", List.of(" file "),
                                "sourceKeys", List.of(" object/key "),
                                "documentIds", List.of(documentId.toString())),
                        5,
                        0.7,
                        "hybrid");
        when(operatorContext.currentOperatorId()).thenReturn(Optional.of(1L));
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(1L));
        when(hybridSearchService.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new Response(List.of(), Set.of(), Set.of()));

        service.search(request);

        var captor = ArgumentCaptor.forClass(AuthorizedQuery.class);
        verify(hybridSearchService).search(captor.capture());
        assertThat(captor.getValue().sourceFilters())
                .containsEntry("sourceTypes", List.of("FILE"))
                .containsEntry("sourceKeys", List.of("object/key"))
                .containsEntry("documentIds", List.of(documentId.toString()));
    }

    @Test
    @DisplayName("Given 纯图检索模式 When 搜索知识库 Then 仅启用图检索通道")
    void should_enable_only_graph_channel_when_graph_mode() {
        var request =
                new KnowledgeSearchDTO(
                        "query", Set.of(), true, Map.of(), Map.of(), 5, 0.7, "graph");
        when(operatorContext.currentOperatorId()).thenReturn(Optional.of(1L));
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(1L));
        when(hybridSearchService.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new Response(List.of(), Set.of(), Set.of()));

        service.search(request);

        var captor = ArgumentCaptor.forClass(AuthorizedQuery.class);
        verify(hybridSearchService).search(captor.capture());
        assertThat(captor.getValue().channelWeights().vector()).isZero();
        assertThat(captor.getValue().channelWeights().keyword()).isZero();
        assertThat(captor.getValue().channelWeights().graph()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Given 未支持或宽松转换的来源过滤 When 规范化 Then 失败关闭")
    void should_reject_unknown_or_non_string_source_filters() {
        var unknown =
                new KnowledgeSearchDTO(
                        "query",
                        Set.of(),
                        true,
                        Map.of(),
                        Map.of("ownerId", List.of("1")),
                        5,
                        0.7,
                        "hybrid");
        var nonString =
                new KnowledgeSearchDTO(
                        "query",
                        Set.of(),
                        true,
                        Map.of(),
                        Map.of("sourceKeys", List.of(123)),
                        5,
                        0.7,
                        "hybrid");

        assertThatThrownBy(unknown::effectiveSourceFilters)
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(nonString::effectiveSourceFilters)
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Given 权重 key 或 value 非法 When 构造搜索 DTO Then 统一拒绝")
    void should_reject_invalid_knowledge_base_weights_in_dto() {
        var knowledgeBaseId = UUID.randomUUID();
        var nullKey = new HashMap<UUID, Double>();
        nullKey.put(null, 1.0);
        var nullValue = new HashMap<UUID, Double>();
        nullValue.put(knowledgeBaseId, null);

        assertThatThrownBy(() -> request(nullKey)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request(nullValue)).isInstanceOf(IllegalArgumentException.class);
        for (var invalid :
                List.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.1)) {
            assertThatThrownBy(() -> request(Map.of(knowledgeBaseId, invalid)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("Given 零和有限正权重 When 构造搜索 DTO Then 保留权重")
    void should_accept_finite_non_negative_knowledge_base_weights_in_dto() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var request = request(Map.of(first, 0.0, second, 1.5));

        assertThat(request.effectiveKnowledgeBaseWeights())
                .containsEntry(first, 0.0)
                .containsEntry(second, 1.5);
    }

    private KnowledgeSearchDTO request(Map<UUID, Double> weights) {
        return new KnowledgeSearchDTO("query", Set.of(), true, weights, Map.of(), 5, 0.7, "hybrid");
    }
}
