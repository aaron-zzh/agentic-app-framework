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

/**
 * {@link KnowledgeBaseService} 单元测试。
 *
 * <p>{@code KnowledgeBaseService} 继承 {@link com.xuejiai.aaf.framework.crud.BaseCrudService}，
 * 所有单对象读取入口（{@code search}/{@code getDocument}/{@code getGraph}/{@code getStats}/ {@code
 * deleteDocument}/{@code retryDocument}）内部都会先调用 {@code requireEntity(id)} 走一遍统一安全管线（L1 资源解析 → L3
 * 范围过滤 → L4 对象级鉴权），再执行业务逻辑。
 *
 * <p><b>为什么要 mock {@code crudResourceRegistry}/{@code crudEnforcementService}：</b> 这两个字段由 {@code
 * BaseCrudService} 基类通过 {@code @Autowired} 注入，构造器里拿不到， 只能用 {@link ReflectionTestUtils#setField} 在
 * {@code @BeforeEach} 里塞进去， 否则安全管线会因为字段为 null 直接抛 {@code NullPointerException}。
 *
 * <p><b>为什么 {@code scopeSpecification()} 不能 mock 成 {@code null}：</b> 框架内部用 {@code
 * Specification.allOf(idSpec(id), decision.scopeSpecification())} 组合查询条件，Spring Data JPA 4.x 的
 * {@code allOf}/{@code where} 都会对参数做非空校验， 传 {@code null} 会直接抛 {@code
 * IllegalArgumentException}。这里改用一个永真的 {@code (root, query, cb) -> cb.conjunction()}
 * 表示"不额外限制范围"，语义上等价于 生产环境租户/组织范围校验通过。
 *
 * <p><b>租约相关 stub 为什么分散到单个测试而不是共享 {@code @BeforeEach}：</b> {@code
 * executionLeaseService.execute}/{@code executeResult} 只在 {@code deleteDocument}/{@code
 * retryDocument} 两个入口才会用到，其余四个查询类测试根本不会 调用它们。Mockito 严格 stub 模式下，未被消费的 {@code @BeforeEach} stub
 * 会被判定为 {@code UnnecessaryStubbingException} 导致测试失败，所以只在真正需要的测试方法内单独打桩。
 *
 * @author AaronZZH & Kiro
 */
class KnowledgeBaseServiceTest extends BaseMockitoUnitTest {

    // ========== KnowledgeBaseService 构造器依赖 ==========
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

    // ========== BaseCrudService 安全管线依赖（@Autowired 字段，需反射注入） ==========
    @Mock private CrudResourceRegistry crudResourceRegistry;
    @Mock private CrudEnforcementService crudEnforcementService;
    @Mock private CrudResourceCatalogEntry resourceEntry;
    @Mock private CrudEnforcementDecision<KnowledgeBase> enforcementDecision;

    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 每个测试前重建 Service 实例，并铺好安全管线放行路径，让业务断言不必关心鉴权细节。
     *
     * <p>放行链路：{@code requireByEntityType} 解析资源定义 → {@code enforceObjectPreflight} 返回鉴权决策 → {@code
     * scopeSpecification} 提供永真条件 → {@code findOne} 返回预置的 知识库实体（id=3，ownerId=7）→ {@code
     * allowsCurrentTarget} 放行。任意一环缺失都会导致 {@code requireEntity(id)} 抛出 {@code
     * CRUD_RESOURCE_NOT_FOUND}，而不是走到业务断言。
     */
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
        // BaseCrudService 的这两个字段是 @Autowired，构造器拿不到，测试环境下手动反射注入
        ReflectionTestUtils.setField(
                knowledgeBaseService, "crudResourceRegistry", crudResourceRegistry);
        ReflectionTestUtils.setField(
                knowledgeBaseService, "crudEnforcementService", crudEnforcementService);

        // 统一放行用的知识库实体：id=3 与各测试用例的 knowledgeBaseId 保持一致
        var knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(3L);
        knowledgeBase.setOwnerId(7L);
        // 第一步：按实体类型解析出资源定义（BaseCrudService#resourceEntry 内部调用）
        when(crudResourceRegistry.requireByEntityType(KnowledgeBase.class))
                .thenReturn(resourceEntry);
        // 第二步：对象读取前置鉴权（L1/L3），返回携带范围条件的鉴权决策
        doReturn(enforcementDecision)
                .when(crudEnforcementService)
                .enforceObjectPreflight(
                        eq(resourceEntry), any(CrudOperation.class), any(AccessMode.class));
        // 第三步：范围条件用永真谓词代替 null——Specification.allOf/where 对 null 参数会抛异常
        when(enforcementDecision.scopeSpecification())
                .thenReturn((root, query, cb) -> cb.conjunction());
        // 第四步：按 id + 范围条件查出实体（框架内部 findOne(Specification.allOf(...))）
        when(knowledgeBaseRepository.findOne(any(Specification.class)))
                .thenReturn(Optional.of(knowledgeBase));
        // 第五步：对象级 L4 鉴权放行，业务代码才能继续往下执行
        when(crudEnforcementService.allowsCurrentTarget(
                        eq(resourceEntry),
                        eq(enforcementDecision),
                        eq(knowledgeBase),
                        any(Map.class),
                        isNull()))
                .thenReturn(true);
    }

    /**
     * 删除/重试测试会手动 {@code initSynchronization()} 模拟事务回调，测试结束后必须清理， 否则残留的同步状态会污染后续测试（JUnit 同一线程复用）。
     */
    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * 验证 search 按 mode=vector 路由到 {@code hybridSearchService.vectorSearch}，且原始结果的 metadata 原样透传到响应。
     */
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

    /** 验证 getDocument 用 (documentId, knowledgeBaseId) 联合查询，避免跨知识库越权读取他人文档。 */
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

    /** 验证 getGraph 把 {@code GraphService} 内部节点/边模型正确映射为前端契约字段（label/source/target）。 */
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

    /**
     * 验证 getStats 汇总三类数据来源：文档表统计（count/sumChunk/sumFileSize）+ 向量表原生 SQL 查询（embeddingCount，PgVector
     * 元数据里的 knowledge_base_id 过滤）。
     */
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

    /**
     * 验证 deleteDocument 的两段式提交语义：事务内先软删除并清理关联数据， 只有等事务真正提交（{@code
     * triggerAfterCommit}）后才对外派发清理任务，避免提交失败时产生孤儿清理请求。
     *
     * <p>{@code executionLeaseService.execute} 本身只是"拿到租约后执行 action"的包装， 这里用 {@code doAnswer}
     * 让它直接同步跑传入的 action，模拟租约拿锁成功的路径。
     */
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

    /**
     * 验证 retryDocument 对失败文档的重置语义：清空错误信息和分块计数、状态改回 PENDING， 且同样遵循"事务提交后才入队"的两段式提交（避免提交失败却已经入队重复处理）。
     *
     * <p>{@code executionLeaseService.executeResult} 与 {@code execute} 类似， 但用于有返回值的场景，这里同步执行 action
     * 模拟拿锁成功。
     */
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

    /** 验证同一文档正在被 worker 处理（分布式租约被占用）时，删除请求不能"抄近路"清数据， 必须原样转换成 409 业务异常并且不触碰任何数据，避免与后台任务产生数据竞争。 */
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

    /** 同上，但针对 retryDocument：租约被占用时不清空错误信息、不改状态、不重新入队，保持文档原状。 */
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

    /** 构造测试用文档实体，固定 id=11、knowledgeBaseId=3，与 setUpService 放行的知识库 id 对齐。 */
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
