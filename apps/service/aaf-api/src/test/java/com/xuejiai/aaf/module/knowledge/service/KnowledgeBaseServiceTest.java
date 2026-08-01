package com.xuejiai.aaf.module.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import com.xuejiai.aaf.common.enums.knowledge.DocumentStatusEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.crud.enforcement.CrudEnforcementDecision;
import com.xuejiai.aaf.framework.crud.enforcement.CrudEnforcementService;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceCatalogEntry;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceRegistry;
import com.xuejiai.aaf.framework.engine.knowledge.graph.GraphService;
import com.xuejiai.aaf.framework.engine.knowledge.graph.GraphService.GraphEdge;
import com.xuejiai.aaf.framework.engine.knowledge.graph.GraphService.GraphNode;
import com.xuejiai.aaf.framework.engine.knowledge.graph.GraphService.GraphSnapshot;
import com.xuejiai.aaf.framework.engine.knowledge.pipeline.KnowledgePipelineService;
import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchService;
import com.xuejiai.aaf.framework.engine.knowledge.rag.RagSearchResult;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskExecutionInProgressException;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeBase;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeDocument;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeBaseRepository;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeDocumentRepository;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeDocumentVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeSearchDTO;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

class KnowledgeBaseServiceTest extends BaseMockitoUnitTest {

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
    @Mock private Query nativeQuery;
    @Mock private CrudResourceRegistry crudResourceRegistry;
    @Mock private CrudEnforcementService crudEnforcementService;
    @Mock private CrudResourceCatalogEntry resourceEntry;
    @Mock private CrudEnforcementDecision<KnowledgeBase> enforcementDecision;

    private KnowledgeBaseService knowledgeBaseService;

    @BeforeEach
    void setUpService() {
        knowledgeBaseService =
                new KnowledgeBaseService(
                        knowledgeBaseRepository,
                        knowledgeDocumentRepository,
                        uploadService,
                        queueService,
                        executionLeaseService,
                        hybridSearchService,
                        graphService,
                        pipelineService,
                        cleanupQueueService,
                        entityManager);
        ReflectionTestUtils.setField(
                knowledgeBaseService, "crudResourceRegistry", crudResourceRegistry);
        ReflectionTestUtils.setField(
                knowledgeBaseService, "crudEnforcementService", crudEnforcementService);

        var knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(3L);
        knowledgeBase.setOwnerId(7L);
        when(crudResourceRegistry.requireByEntityType(KnowledgeBase.class))
                .thenReturn(resourceEntry);
        doReturn(enforcementDecision)
                .when(crudEnforcementService)
                .enforceObjectPreflight(
                        eq(resourceEntry), any(CrudOperation.class), any(AccessMode.class));
        when(enforcementDecision.scopeSpecification())
                .thenReturn((root, query, cb) -> cb.conjunction());
        when(knowledgeBaseRepository.findOne(any(Specification.class)))
                .thenReturn(Optional.of(knowledgeBase));
        when(crudEnforcementService.allowsCurrentTarget(
                        eq(resourceEntry),
                        eq(enforcementDecision),
                        eq(knowledgeBase),
                        any(Map.class),
                        isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Given 向量搜索请求 When 检索知识库 Then 返回真实块 ID 和前端字段")
    void should_return_wire_contract_when_vector_searches() {
        // 准备参数
        when(hybridSearchService.vectorSearch("查询", 3L, 5, 0.6))
                .thenReturn(
                        List.of(
                                new RagSearchResult(
                                        "知识内容", 0.82, "vector", Map.of("chunk_id", 21L))));

        // 调用
        var response =
                knowledgeBaseService.search(3L, new KnowledgeSearchDTO("查询", 5, 0.6, "vector"));

        // 断言
        assertThat(response.results())
                .singleElement()
                .satisfies(
                        result -> {
                            assertThat(result.content()).isEqualTo("知识内容");
                            assertThat(result.source()).isEqualTo("vector");
                            assertThat(result.metadata()).containsEntry("chunk_id", 21L);
                        });
    }

    @Test
    @DisplayName("Given 文档属于知识库 When 查询详情 Then 按知识库和文档联合标识返回")
    void should_return_document_when_it_belongs_to_knowledge_base() {
        // 准备参数
        var document = document(DocumentStatusEnum.COMPLETED.getCode());
        when(knowledgeDocumentRepository.findByIdAndKnowledgeBaseId(11L, 3L))
                .thenReturn(Optional.of(document));

        // 调用
        var response = knowledgeBaseService.getDocument(3L, 11L);

        // 断言
        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.knowledgeBaseId()).isEqualTo(3L);
        verify(knowledgeDocumentRepository).findByIdAndKnowledgeBaseId(11L, 3L);
    }

    @Test
    @DisplayName("Given 知识库图谱快照 When 查询图谱 Then 映射 label source target 契约")
    void should_map_graph_snapshot_to_frontend_contract() {
        // 准备参数
        when(graphService.snapshot(3L))
                .thenReturn(
                        new GraphSnapshot(
                                List.of(new GraphNode("entity-1", "AAF", "Concept", "框架", 11L)),
                                List.of(
                                        new GraphEdge(
                                                "relation-1",
                                                "entity-1",
                                                "entity-2",
                                                "RELATES_TO",
                                                0.9,
                                                11L))));

        // 调用
        var response = knowledgeBaseService.getGraph(3L);

        // 断言
        assertThat(response.nodes().getFirst().label()).isEqualTo("AAF");
        assertThat(response.edges().getFirst().source()).isEqualTo("entity-1");
        assertThat(response.edges().getFirst().target()).isEqualTo("entity-2");
        assertThat(response.edges().getFirst().label()).isEqualTo("RELATES_TO");
    }

    @Test
    @DisplayName("Given 知识库真实统计 When 查询统计 Then 返回文档分块向量和总大小")
    void should_return_real_document_chunk_vector_and_size_stats() {
        // 准备参数
        when(knowledgeDocumentRepository.countByKnowledgeBaseId(3L)).thenReturn(2L);
        when(knowledgeDocumentRepository.sumChunkCountByKnowledgeBaseId(3L)).thenReturn(7L);
        when(knowledgeDocumentRepository.sumFileSizeByKnowledgeBaseId(3L)).thenReturn(1024L);
        when(entityManager.createNativeQuery(any(String.class))).thenReturn(nativeQuery);
        when(nativeQuery.setParameter("id", 3L)).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(7L);

        // 调用
        var response = knowledgeBaseService.getStats(3L);

        // 断言
        assertThat(response.documentCount()).isEqualTo(2L);
        assertThat(response.chunkCount()).isEqualTo(7L);
        assertThat(response.embeddingCount()).isEqualTo(7L);
        assertThat(response.totalSize()).isEqualTo(1024L);
    }

    @Test
    @DisplayName("Given 关联文档 When 删除 Then 事务内软删除并在提交后派发外部清理")
    void should_delete_and_enqueue_cleanup_after_commit() {
        // 准备参数
        doAnswer(
                        invocation -> {
                            KnowledgeDocumentExecutionLeaseService.GuardedAction action =
                                    invocation.getArgument(1);
                            action.run(() -> {});
                            return null;
                        })
                .when(executionLeaseService)
                .execute(eq(11L), any(KnowledgeDocumentExecutionLeaseService.GuardedAction.class));
        var document = document(DocumentStatusEnum.COMPLETED.getCode());
        when(knowledgeDocumentRepository.findByIdAndKnowledgeBaseId(11L, 3L))
                .thenReturn(Optional.of(document));
        TransactionSynchronizationManager.initSynchronization();

        // 调用
        knowledgeBaseService.deleteDocument(3L, 11L);

        // 断言
        verify(pipelineService).clearDocumentRelationalData(11L);
        verify(knowledgeDocumentRepository).delete(document);
        verify(cleanupQueueService, never()).enqueue(any());

        TransactionSynchronizationUtils.triggerAfterCommit();
        verify(cleanupQueueService).enqueue(document);
        TransactionSynchronizationUtils.triggerAfterCompletion(
                TransactionSynchronization.STATUS_COMMITTED);
    }

    @Test
    @DisplayName("Given 失败文档 When 重试 Then 清旧数据改为待处理并在提交后入队")
    void should_reset_and_enqueue_after_commit_when_failed_document_retries() {
        // 准备参数
        doAnswer(
                        invocation -> {
                            KnowledgeDocumentExecutionLeaseService.GuardedSupplier<?> action =
                                    invocation.getArgument(1);
                            return action.run(() -> {});
                        })
                .when(executionLeaseService)
                .executeResult(
                        eq(11L),
                        org.mockito.ArgumentMatchers
                                .<KnowledgeDocumentExecutionLeaseService.GuardedSupplier<
                                                KnowledgeDocumentVO>>
                                        any());
        var document = document(DocumentStatusEnum.FAILED.getCode());
        document.setErrorMessage("graph unavailable");
        document.setChunkCount(4);
        when(knowledgeDocumentRepository.findByIdAndKnowledgeBaseId(11L, 3L))
                .thenReturn(Optional.of(document));
        when(knowledgeDocumentRepository.save(document)).thenReturn(document);
        TransactionSynchronizationManager.initSynchronization();

        // 调用
        var response = knowledgeBaseService.retryDocument(3L, 11L);

        // 断言
        assertThat(response.status()).isEqualTo(DocumentStatusEnum.PENDING.getCode());
        assertThat(document.getStatus()).isEqualTo(DocumentStatusEnum.PENDING.getCode());
        assertThat(document.getErrorMessage()).isNull();
        assertThat(document.getChunkCount()).isZero();
        verify(pipelineService).clearDocumentData(3L, 11L);
        verify(queueService, never()).enqueue(any());

        TransactionSynchronizationUtils.triggerAfterCommit();
        verify(queueService).enqueue(document);
        TransactionSynchronizationUtils.triggerAfterCompletion(
                TransactionSynchronization.STATUS_COMMITTED);
    }

    @Test
    @DisplayName("Given worker 已持文档租约 When 删除 Then 返回执行中且不清理数据")
    void should_not_clear_or_delete_when_document_lease_is_busy() {
        // 准备参数
        doThrow(new TaskExecutionInProgressException("处理中"))
                .when(executionLeaseService)
                .execute(eq(11L), any(KnowledgeDocumentExecutionLeaseService.GuardedAction.class));

        // 调用 + 断言
        assertThatThrownBy(() -> knowledgeBaseService.deleteDocument(3L, 11L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        failure -> assertThat(failure.getCode()).isEqualTo(409));
        verify(pipelineService, never()).clearDocumentRelationalData(any());
        verify(cleanupQueueService, never()).enqueue(any());
        verify(knowledgeDocumentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Given worker 已持文档租约 When 重试 Then 返回执行中且不清理或改状态")
    void should_not_clear_or_reset_when_document_lease_is_busy() {
        // 准备参数
        doThrow(new TaskExecutionInProgressException("处理中"))
                .when(executionLeaseService)
                .executeResult(
                        eq(11L),
                        org.mockito.ArgumentMatchers
                                .<KnowledgeDocumentExecutionLeaseService.GuardedSupplier<
                                                KnowledgeDocumentVO>>
                                        any());

        // 调用 + 断言
        assertThatThrownBy(() -> knowledgeBaseService.retryDocument(3L, 11L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        failure -> assertThat(failure.getCode()).isEqualTo(409));
        verify(pipelineService, never()).clearDocumentData(any(), any());
        verify(knowledgeDocumentRepository, never()).save(any());
        verify(queueService, never()).enqueue(any());
    }

    private KnowledgeDocument document(Integer status) {
        var document = new KnowledgeDocument();
        document.setId(11L);
        document.setKnowledgeBaseId(3L);
        document.setTitle("guide.md");
        document.setFilePath("kb/guide.md");
        document.setFileType("md");
        document.setFileSize(128L);
        document.setStatus(status);
        return document;
    }
}
