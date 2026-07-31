package com.xuejiai.aaf.module.knowledge.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.TaskScheduler;

import com.xuejiai.aaf.common.enums.knowledge.DocumentStatusEnum;
import com.xuejiai.aaf.framework.engine.lease.DistributedLeasePort;
import com.xuejiai.aaf.framework.engine.lease.DistributedLeasePort.Lease;
import com.xuejiai.aaf.framework.task.TaskProperties;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeDocument;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeDocumentRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class KnowledgeDocumentDispatchRecoveryTest extends BaseMockitoUnitTest {

    @Mock private KnowledgeDocumentRepository documentRepository;
    @Mock private KnowledgeDocumentQueueService queueService;
    @Mock private DistributedLeasePort distributedLeases;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private TaskScheduler taskScheduler;
    @Mock private ScheduledFuture<?> renewalFuture;

    private KnowledgeDocumentDispatchRecovery recovery;
    private Lease lease;

    @BeforeEach
    void setUp() {
        recovery =
                new KnowledgeDocumentDispatchRecovery(
                        documentRepository,
                        queueService,
                        distributedLeases,
                        new TaskProperties(),
                        redisTemplate,
                        taskScheduler,
                        300,
                        120,
                        100,
                        5000);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doReturn(renewalFuture)
                .when(taskScheduler)
                .scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofSeconds(40)));
        when(documentRepository.findRecoveryCycleMaxId()).thenReturn(11L);
        lease =
                new Lease(
                        "knowledge-document:dispatch-recovery",
                        "instance:invocation",
                        1,
                        Instant.now().plusSeconds(120));
        when(distributedLeases.acquire(
                        eq("knowledge-document:dispatch-recovery"),
                        anyString(),
                        eq(Duration.ofSeconds(120))))
                .thenReturn(Optional.of(lease));
    }

    @Test
    @DisplayName("Given 存在陈旧待处理文档 When 恢复扫描 Then 重派并条件更新派发时间")
    void should_redispatch_stale_pending_document_and_touch_after_enqueue() {
        // 准备参数
        var document = document();
        when(documentRepository.findRecoveryScanWindow(eq(0L), eq(11L), any(Pageable.class)))
                .thenReturn(List.of(document));
        when(queueService.enqueue(document)).thenReturn("knowledge-document:11");
        when(documentRepository.touchDispatchTimeIfPending(
                        eq(11L),
                        eq(DocumentStatusEnum.PENDING.getCode()),
                        any(LocalDateTime.class)))
                .thenReturn(1);

        // 调用
        recovery.recoverPendingDocuments();

        // 断言
        var ordered = inOrder(queueService, documentRepository);
        ordered.verify(queueService).enqueue(document);
        ordered.verify(documentRepository)
                .touchDispatchTimeIfPending(
                        11L, DocumentStatusEnum.PENDING.getCode(), document.getUpdateTime());
        verify(valueOperations, atLeastOnce())
                .set("knowledge-document:dispatch-recovery:cursor", "0");
        verify(distributedLeases, atLeastOnce()).requireCurrent(lease);
        verify(distributedLeases).release(lease);
    }

    @Test
    @DisplayName("Given 固定周期高水位和满窗口 When 达到派发上限 Then 游标停在最后扫描文档")
    void should_advance_cursor_without_skipping_unscanned_documents() {
        // 准备参数
        var limitedRecovery =
                new KnowledgeDocumentDispatchRecovery(
                        documentRepository,
                        queueService,
                        distributedLeases,
                        new TaskProperties(),
                        redisTemplate,
                        taskScheduler,
                        300,
                        120,
                        1,
                        2);
        when(valueOperations.get("knowledge-document:dispatch-recovery:cursor")).thenReturn("5");
        when(valueOperations.get("knowledge-document:dispatch-recovery:cycle-max"))
                .thenReturn("20");
        var first = document();
        first.setId(6L);
        var second = document();
        second.setId(7L);
        when(documentRepository.findRecoveryScanWindow(eq(5L), eq(20L), any(Pageable.class)))
                .thenReturn(List.of(first, second));
        when(queueService.enqueue(first)).thenReturn("knowledge-document:6");

        // 调用
        limitedRecovery.recoverPendingDocuments();

        // 断言
        verify(queueService).enqueue(first);
        verify(queueService, never()).enqueue(second);
        verify(valueOperations).set("knowledge-document:dispatch-recovery:cursor", "6");
    }

    @Test
    @DisplayName("Given Redis 入队仍失败 When 恢复扫描 Then 不更新派发时间以便下轮重试")
    void should_not_touch_dispatch_time_when_enqueue_fails() {
        // 准备参数
        var document = document();
        when(documentRepository.findRecoveryScanWindow(eq(0L), eq(11L), any(Pageable.class)))
                .thenReturn(List.of(document));
        when(queueService.enqueue(document))
                .thenThrow(new IllegalStateException("redis unavailable"));

        // 调用
        recovery.recoverPendingDocuments();

        // 断言
        verify(documentRepository, never()).touchDispatchTimeIfPending(any(), any(), any());
        verify(distributedLeases).release(lease);
    }

    private KnowledgeDocument document() {
        var document = new KnowledgeDocument();
        document.setId(11L);
        document.setKnowledgeBaseId(3L);
        document.setOwnerId(7L);
        document.setOrgId(5L);
        document.setWorkspaceId(6L);
        document.setStatus(DocumentStatusEnum.PENDING.getCode());
        document.setUpdateTime(LocalDateTime.of(2026, 7, 31, 8, 0));
        return document;
    }
}
