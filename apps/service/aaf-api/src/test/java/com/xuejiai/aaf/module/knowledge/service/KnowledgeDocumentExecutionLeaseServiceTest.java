package com.xuejiai.aaf.module.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import com.xuejiai.aaf.framework.engine.lease.DistributedLeasePort;
import com.xuejiai.aaf.framework.engine.lease.DistributedLeasePort.Lease;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskExecutionInProgressException;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class KnowledgeDocumentExecutionLeaseServiceTest extends BaseMockitoUnitTest {

    @Mock private DistributedLeasePort distributedLeases;
    @Mock private TaskScheduler taskScheduler;
    @Mock private ScheduledFuture<?> renewalFuture;

    private KnowledgeDocumentExecutionLeaseService leaseService;

    @BeforeEach
    void setUp() {
        leaseService =
                new KnowledgeDocumentExecutionLeaseService(distributedLeases, taskScheduler, 9);
        doReturn(renewalFuture)
                .when(taskScheduler)
                .scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofSeconds(3)));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Given 同一实例两次执行 When 获取文档租约 Then 每次使用不同 owner")
    void should_use_unique_owner_for_each_invocation() {
        // 准备参数
        when(distributedLeases.acquire(
                        eq("knowledge-document:processing:11"),
                        anyString(),
                        eq(Duration.ofSeconds(9))))
                .thenAnswer(
                        invocation -> {
                            String owner = invocation.getArgument(1);
                            return Optional.of(
                                    new Lease(
                                            "knowledge-document:processing:11",
                                            owner,
                                            1,
                                            Instant.now().plusSeconds(9)));
                        });
        var ownerCaptor = ArgumentCaptor.forClass(String.class);

        // 调用
        leaseService.execute(11L, Runnable::run);
        leaseService.execute(11L, Runnable::run);

        // 断言
        verify(distributedLeases, times(2))
                .acquire(
                        eq("knowledge-document:processing:11"),
                        ownerCaptor.capture(),
                        eq(Duration.ofSeconds(9)));
        assertThat(ownerCaptor.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Given 文档租约已被占用 When 执行 Then 抛出执行中异常且不启动续租")
    void should_defer_when_document_lease_is_busy() {
        // 准备参数
        when(distributedLeases.acquire(
                        eq("knowledge-document:processing:11"),
                        anyString(),
                        eq(Duration.ofSeconds(9))))
                .thenReturn(Optional.empty());

        // 调用 + 断言
        assertThatThrownBy(() -> leaseService.execute(11L, Runnable::run))
                .isInstanceOf(TaskExecutionInProgressException.class);
        verify(taskScheduler, never())
                .scheduleAtFixedRate(any(Runnable.class), any(Duration.class));
    }

    @Test
    @DisplayName("Given 文档租约续期成功 When 执行完成 Then 校验并释放最新租约")
    void should_validate_and_release_the_latest_renewed_lease() {
        // 准备参数
        var original =
                new Lease(
                        "knowledge-document:processing:11",
                        "instance:invocation",
                        1,
                        Instant.now().plusSeconds(9));
        var renewed =
                new Lease(
                        "knowledge-document:processing:11",
                        "instance:invocation",
                        1,
                        Instant.now().plusSeconds(18));
        when(distributedLeases.acquire(
                        eq("knowledge-document:processing:11"),
                        anyString(),
                        eq(Duration.ofSeconds(9))))
                .thenReturn(Optional.of(original));
        when(distributedLeases.renew(original, Duration.ofSeconds(9)))
                .thenReturn(Optional.of(renewed));
        var renewalCaptor = ArgumentCaptor.forClass(Runnable.class);
        doReturn(renewalFuture)
                .when(taskScheduler)
                .scheduleAtFixedRate(renewalCaptor.capture(), eq(Duration.ofSeconds(3)));

        // 调用
        leaseService.execute(
                11L,
                guard -> {
                    renewalCaptor.getValue().run();
                    guard.run();
                });

        // 断言
        verify(distributedLeases).requireCurrent(renewed);
        verify(distributedLeases).release(renewed);
        verify(distributedLeases, never()).release(original);
    }

    @Test
    @DisplayName("Given 续租失败 When 再次校验租约 Then 粘性标记失租并阻止继续执行")
    void should_block_further_work_after_renewal_is_lost() {
        // 准备参数
        var lease =
                new Lease(
                        "knowledge-document:processing:11",
                        "instance:invocation",
                        1,
                        Instant.now().plusSeconds(9));
        when(distributedLeases.acquire(
                        eq("knowledge-document:processing:11"),
                        anyString(),
                        eq(Duration.ofSeconds(9))))
                .thenReturn(Optional.of(lease));
        when(distributedLeases.renew(lease, Duration.ofSeconds(9))).thenReturn(Optional.empty());
        var renewalCaptor = ArgumentCaptor.forClass(Runnable.class);
        doReturn(renewalFuture)
                .when(taskScheduler)
                .scheduleAtFixedRate(renewalCaptor.capture(), eq(Duration.ofSeconds(3)));

        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                leaseService.execute(
                                        11L,
                                        guard -> {
                                            renewalCaptor.getValue().run();
                                            guard.run();
                                        }))
                .isInstanceOf(TaskExecutionInProgressException.class);
        verify(renewalFuture).cancel(false);
        verify(distributedLeases).release(lease);
    }

    @Test
    @DisplayName("Given 文档操作处于事务中 When 操作返回 Then 提交后才释放租约")
    void should_hold_lease_until_transaction_commits() {
        // 准备参数
        var lease =
                new Lease(
                        "knowledge-document:processing:11",
                        "instance:invocation",
                        1,
                        Instant.now().plusSeconds(9));
        when(distributedLeases.acquire(
                        eq("knowledge-document:processing:11"),
                        anyString(),
                        eq(Duration.ofSeconds(9))))
                .thenReturn(Optional.of(lease));
        TransactionSynchronizationManager.initSynchronization();

        // 调用
        var result = leaseService.executeResult(11L, guard -> "done");

        // 断言
        assertThat(result).isEqualTo("done");
        verify(distributedLeases, never()).release(lease);
        verify(renewalFuture, never()).cancel(false);

        TransactionSynchronizationUtils.triggerAfterCommit();
        verify(distributedLeases).release(lease);
        verify(renewalFuture).cancel(false);
        TransactionSynchronizationUtils.triggerAfterCompletion(
                TransactionSynchronization.STATUS_COMMITTED);
        verify(distributedLeases).release(lease);
    }
}
