package com.xuejiai.aaf.module.knowledge.service;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.xuejiai.aaf.framework.engine.lease.DistributedLeasePort;
import com.xuejiai.aaf.framework.engine.lease.DistributedLeasePort.Lease;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskExecutionInProgressException;

import lombok.extern.slf4j.Slf4j;

/** 知识文档跨实例执行租约；租约存活期间同一文档只允许一个处理执行。 */
@Slf4j
@Service
public class KnowledgeDocumentExecutionLeaseService {

    private static final String LEASE_KEY_PREFIX = "knowledge-document:processing:";

    private final DistributedLeasePort distributedLeases;
    private final TaskScheduler taskScheduler;
    private final Duration leaseTtl;
    private final Duration renewalInterval;
    private final String instanceId = UUID.randomUUID().toString();

    public KnowledgeDocumentExecutionLeaseService(
            DistributedLeasePort distributedLeases,
            TaskScheduler taskScheduler,
            @Value("${aaf.knowledge.document-processing-lease-seconds:120}") long leaseSeconds) {
        if (leaseSeconds < 3) {
            throw new IllegalArgumentException("知识文档执行租约至少为 3 秒");
        }
        this.distributedLeases = distributedLeases;
        this.taskScheduler = taskScheduler;
        this.leaseTtl = Duration.ofSeconds(leaseSeconds);
        this.renewalInterval = Duration.ofSeconds(Math.max(1, leaseSeconds / 3));
    }

    public void execute(Long documentId, GuardedAction action) {
        Objects.requireNonNull(action, "action 不能为空");
        executeResult(
                documentId,
                guard -> {
                    action.run(guard);
                    return null;
                });
    }

    public <T> T executeResult(Long documentId, GuardedSupplier<T> action) {
        Objects.requireNonNull(documentId, "documentId 不能为空");
        Objects.requireNonNull(action, "action 不能为空");
        var ownerId = instanceId + ':' + UUID.randomUUID();
        var lease =
                distributedLeases
                        .acquire(LEASE_KEY_PREFIX + documentId, ownerId, leaseTtl)
                        .orElseThrow(
                                () ->
                                        new TaskExecutionInProgressException(
                                                "知识文档正在由其他任务处理: " + documentId));
        var currentLease = new AtomicReference<>(lease);
        var lost = new AtomicBoolean(false);
        ScheduledFuture<?> renewal = null;
        var transactionManaged = false;
        try {
            renewal = scheduleRenewal(documentId, currentLease, lost);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                deferReleaseUntilTransactionCompletion(documentId, currentLease, renewal);
                transactionManaged = true;
            }
            var guard = (Runnable) () -> requireCurrent(documentId, currentLease, lost);
            guard.run();
            return action.run(guard);
        } finally {
            if (!transactionManaged) {
                cancelAndRelease(documentId, currentLease, renewal);
            }
        }
    }

    private void deferReleaseUntilTransactionCompletion(
            Long documentId, AtomicReference<Lease> currentLease, ScheduledFuture<?> renewal) {
        var cleaned = new AtomicBoolean(false);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cleanupOnce();
                    }

                    @Override
                    public void afterCompletion(int status) {
                        cleanupOnce();
                    }

                    private void cleanupOnce() {
                        if (cleaned.compareAndSet(false, true)) {
                            cancelAndRelease(documentId, currentLease, renewal);
                        }
                    }
                });
    }

    private void cancelAndRelease(
            Long documentId, AtomicReference<Lease> currentLease, ScheduledFuture<?> renewal) {
        if (renewal != null) {
            renewal.cancel(false);
        }
        release(documentId, currentLease.get());
    }

    private ScheduledFuture<?> scheduleRenewal(
            Long documentId, AtomicReference<Lease> currentLease, AtomicBoolean lost) {
        return Objects.requireNonNull(
                taskScheduler.scheduleAtFixedRate(
                        () -> renew(documentId, currentLease, lost), renewalInterval),
                "知识文档租约续期任务未创建");
    }

    private void renew(Long documentId, AtomicReference<Lease> currentLease, AtomicBoolean lost) {
        if (lost.get()) {
            return;
        }
        var current = currentLease.get();
        try {
            var renewed = distributedLeases.renew(current, leaseTtl);
            if (renewed.isEmpty()) {
                lost.set(true);
                log.warn("知识文档执行租约续期失败，documentId={}", documentId);
                return;
            }
            currentLease.compareAndSet(current, renewed.orElseThrow());
        } catch (RuntimeException failure) {
            lost.set(true);
            log.error("知识文档执行租约续期异常，documentId={}", documentId, failure);
        }
    }

    private void requireCurrent(
            Long documentId, AtomicReference<Lease> currentLease, AtomicBoolean lost) {
        if (lost.get()) {
            throw new TaskExecutionInProgressException("知识文档执行租约已失效: " + documentId);
        }
        try {
            distributedLeases.requireCurrent(currentLease.get());
        } catch (RuntimeException failure) {
            lost.set(true);
            throw new TaskExecutionInProgressException("知识文档执行租约已失效: " + documentId, failure);
        }
    }

    private void release(Long documentId, Lease lease) {
        try {
            distributedLeases.release(lease);
        } catch (RuntimeException failure) {
            log.warn("释放知识文档执行租约失败，documentId={}", documentId, failure);
        }
    }

    @FunctionalInterface
    public interface GuardedAction {
        void run(Runnable guard);
    }

    @FunctionalInterface
    public interface GuardedSupplier<T> {
        T run(Runnable guard);
    }
}
