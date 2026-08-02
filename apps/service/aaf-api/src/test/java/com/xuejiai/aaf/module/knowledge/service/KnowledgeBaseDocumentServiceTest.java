package com.xuejiai.aaf.module.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import com.xuejiai.aaf.common.enums.knowledge.DocumentStatusEnum;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.crud.enforcement.CrudEnforcementDecision;
import com.xuejiai.aaf.framework.crud.enforcement.CrudEnforcementService;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceCatalogEntry;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceRegistry;
import com.xuejiai.aaf.framework.engine.knowledge.graph.GraphService;
import com.xuejiai.aaf.framework.engine.knowledge.pipeline.KnowledgePipelineService;
import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeGraphProjectionService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeBase;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeDocument;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeBaseRepository;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeDocumentRepository;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeDocumentVO;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import jakarta.persistence.EntityManager;

/** 知识文档删除与重试的一致性不变量测试。 */
class KnowledgeBaseDocumentServiceTest extends BaseMockitoUnitTest {

    private static final long KNOWLEDGE_BASE_ID = 3L;
    private static final long DOCUMENT_ID = 11L;

    @Mock private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock private KnowledgeDocumentRepository knowledgeDocumentRepository;
    @Mock private KnowledgeDocumentUploadService uploadService;
    @Mock private KnowledgeDocumentQueueService queueService;
    @Mock private KnowledgeDocumentExecutionLeaseService executionLeaseService;
    @Mock private HybridSearchService hybridSearchService;
    @Mock private GraphService graphService;
    @Mock private KnowledgeGraphProjectionService graphProjectionService;
    @Mock private TrustedKnowledgeStore truthStore;
    @Mock private KnowledgePipelineService pipelineService;
    @Mock private KnowledgeDocumentCleanupQueueService cleanupQueueService;
    @Mock private EntityManager entityManager;
    @Mock private OperatorContext operatorContext;
    @Mock private CrudResourceRegistry crudResourceRegistry;
    @Mock private CrudEnforcementService crudEnforcementService;
    @Mock private CrudResourceCatalogEntry resourceEntry;
    @Mock private CrudEnforcementDecision<KnowledgeBase> enforcementDecision;

    @InjectMocks private KnowledgeBaseService service;

    @BeforeEach
    void setUp() {
        // BaseCrudService 的继承字段不是 KnowledgeBaseService 构造器参数。
        ReflectionTestUtils.setField(service, "crudResourceRegistry", crudResourceRegistry);
        ReflectionTestUtils.setField(service, "crudEnforcementService", crudEnforcementService);

        var knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(KNOWLEDGE_BASE_ID);
        when(crudResourceRegistry.requireByEntityType(KnowledgeBase.class))
                .thenReturn(resourceEntry);
        when(crudEnforcementService.<KnowledgeBase>enforceObjectPreflight(
                        resourceEntry, CrudOperation.UPDATE, AccessMode.DEFAULT))
                .thenReturn(enforcementDecision);
        // requireEntity 会把范围条件与 ID 条件组合，测试夹具必须提供非空 Specification。
        when(enforcementDecision.scopeSpecification())
                .thenReturn((root, query, builder) -> builder.conjunction());
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
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Given 已发布文档 When 删除 Then 撤销真理数据并仅在提交后清理外部文件")
    void should_revoke_truth_and_enqueue_cleanup_after_commit_when_document_deletes() {
        var document = document(DocumentStatusEnum.COMPLETED.getCode());
        when(knowledgeDocumentRepository.findByIdAndKnowledgeBaseId(DOCUMENT_ID, KNOWLEDGE_BASE_ID))
                .thenReturn(Optional.of(document));
        doAnswer(
                        invocation -> {
                            KnowledgeDocumentExecutionLeaseService.GuardedAction action =
                                    invocation.getArgument(1);
                            action.run(() -> {});
                            return null;
                        })
                .when(executionLeaseService)
                .execute(
                        eq(DOCUMENT_ID),
                        any(KnowledgeDocumentExecutionLeaseService.GuardedAction.class));
        TransactionSynchronizationManager.initSynchronization();

        service.deleteDocument(KNOWLEDGE_BASE_ID, DOCUMENT_ID);

        verify(pipelineService).revokeDocument(DOCUMENT_ID);
        verify(knowledgeDocumentRepository).delete(document);
        verify(cleanupQueueService, never()).enqueue(any());

        TransactionSynchronizationUtils.triggerAfterCommit();
        verify(cleanupQueueService).enqueue(document);
        TransactionSynchronizationUtils.triggerAfterCompletion(
                TransactionSynchronization.STATUS_COMMITTED);
    }

    @Test
    @DisplayName("Given 失败文档 When 重试 Then 保留旧代际并仅在提交后重新入队")
    void should_reset_and_enqueue_after_commit_without_revoking_current_generation() {
        var document = document(DocumentStatusEnum.FAILED.getCode());
        document.setErrorMessage("projection unavailable");
        document.setChunkCount(4);
        when(knowledgeDocumentRepository.findByIdAndKnowledgeBaseId(DOCUMENT_ID, KNOWLEDGE_BASE_ID))
                .thenReturn(Optional.of(document));
        when(knowledgeDocumentRepository.save(document)).thenReturn(document);
        doAnswer(
                        invocation -> {
                            KnowledgeDocumentExecutionLeaseService.GuardedSupplier<?> action =
                                    invocation.getArgument(1);
                            return action.run(() -> {});
                        })
                .when(executionLeaseService)
                .executeResult(
                        eq(DOCUMENT_ID),
                        ArgumentMatchers
                                .<KnowledgeDocumentExecutionLeaseService.GuardedSupplier<
                                                KnowledgeDocumentVO>>
                                        any());
        TransactionSynchronizationManager.initSynchronization();

        var response = service.retryDocument(KNOWLEDGE_BASE_ID, DOCUMENT_ID);

        assertThat(response.status()).isEqualTo(DocumentStatusEnum.PENDING.getCode());
        assertThat(document.getErrorMessage()).isNull();
        assertThat(document.getChunkCount()).isZero();
        verifyNoInteractions(pipelineService);
        verify(queueService, never()).enqueue(any());

        TransactionSynchronizationUtils.triggerAfterCommit();
        verify(queueService).enqueue(document);
        TransactionSynchronizationUtils.triggerAfterCompletion(
                TransactionSynchronization.STATUS_COMMITTED);
    }

    private KnowledgeDocument document(Integer status) {
        var document = new KnowledgeDocument();
        document.setId(DOCUMENT_ID);
        document.setKnowledgeBaseId(KNOWLEDGE_BASE_ID);
        document.setTitle("guide.md");
        document.setFileType("md");
        document.setFileSize(128L);
        document.setStatus(status);
        return document;
    }
}
