package com.xuejiai.aaf.module.knowledge.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.knowledge.DocumentStatusEnum;
import com.xuejiai.aaf.framework.engine.lease.DistributedLeasePort;
import com.xuejiai.aaf.framework.engine.lease.DistributedLeasePort.Lease;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskExecutionInProgressException;
import com.xuejiai.aaf.framework.org.OrgIgnore;
import com.xuejiai.aaf.framework.task.TaskProperties;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeDocumentRepository;

import lombok.extern.slf4j.Slf4j;

/** 恢复数据库已提交但尚未成功派发的知识文档任务。 */
@Slf4j
@Component
public class KnowledgeDocumentDispatchRecovery {

    private static final String RECOVERY_LEASE_KEY = "knowledge-document:dispatch-recovery";
    private static final String RECOVERY_CURSOR_KEY = "knowledge-document:dispatch-recovery:cursor";
    private static final String RECOVERY_CYCLE_MAX_KEY =
            "knowledge-document:dispatch-recovery:cycle-max";

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeDocumentQueueService queueService;
    private final DistributedLeasePort distributedLeases;
    private final TaskProperties taskProperties;
    private final StringRedisTemplate redisTemplate;
    private final TaskScheduler taskScheduler;
    private final Duration staleAge;
    private final Duration leaseTtl;
    private final Duration renewalInterval;
    private final int dispatchBatchSize;
    private final int scanWindowSize;
    private final String instanceId = UUID.randomUUID().toString();

    public KnowledgeDocumentDispatchRecovery(
            KnowledgeDocumentRepository documentRepository,
            KnowledgeDocumentQueueService queueService,
            DistributedLeasePort distributedLeases,
            TaskProperties taskProperties,
            StringRedisTemplate redisTemplate,
            TaskScheduler taskScheduler,
            @Value("${aaf.knowledge.dispatch-recovery-stale-seconds:300}") long staleSeconds,
            @Value("${aaf.knowledge.dispatch-recovery-lease-seconds:120}") long leaseSeconds,
            @Value("${aaf.knowledge.dispatch-recovery-batch-size:100}") int dispatchBatchSize,
            @Value("${aaf.knowledge.dispatch-recovery-scan-window-size:5000}") int scanWindowSize) {
        if (staleSeconds < 1
                || leaseSeconds < 3
                || dispatchBatchSize < 1
                || scanWindowSize < dispatchBatchSize) {
            throw new IllegalArgumentException("知识文档恢复派发配置无效");
        }
        this.documentRepository = documentRepository;
        this.queueService = queueService;
        this.distributedLeases = distributedLeases;
        this.taskProperties = taskProperties;
        this.redisTemplate = redisTemplate;
        this.taskScheduler = taskScheduler;
        this.staleAge = Duration.ofSeconds(staleSeconds);
        this.leaseTtl = Duration.ofSeconds(leaseSeconds);
        this.renewalInterval = Duration.ofSeconds(Math.max(1, leaseSeconds / 3));
        this.dispatchBatchSize = dispatchBatchSize;
        this.scanWindowSize = scanWindowSize;
    }

    @Scheduled(
            fixedDelayString = "${aaf.knowledge.dispatch-recovery-interval-ms:60000}",
            initialDelayString = "${aaf.knowledge.dispatch-recovery-initial-delay-ms:60000}")
    @OrgIgnore
    public void recoverPendingDocuments() {
        if (!taskProperties.getQueue().isEnabled()) {
            return;
        }
        var ownerId = instanceId + ':' + UUID.randomUUID();
        var lease = distributedLeases.acquire(RECOVERY_LEASE_KEY, ownerId, leaseTtl);
        if (lease.isEmpty()) {
            return;
        }
        var currentLease = new AtomicReference<>(lease.orElseThrow());
        var lost = new AtomicBoolean(false);
        ScheduledFuture<?> renewal = null;
        try {
            renewal =
                    taskScheduler.scheduleAtFixedRate(
                            () -> renew(currentLease, lost), renewalInterval);
            if (renewal == null) {
                throw new IllegalStateException("知识文档恢复租约续期任务未创建");
            }
            var guard = (Runnable) () -> requireCurrent(currentLease, lost);
            guard.run();
            recoverBatch(guard);
        } catch (TaskExecutionInProgressException inProgress) {
            log.warn("知识文档恢复租约已失效，本轮停止扫描");
        } finally {
            if (renewal != null) {
                renewal.cancel(false);
            }
            release(currentLease.get());
        }
    }

    private void recoverBatch(Runnable guard) {
        var cutoff = LocalDateTime.now().minus(staleAge);
        var cursor = readLong(RECOVERY_CURSOR_KEY);
        var cycleMaxId = readLong(RECOVERY_CYCLE_MAX_KEY);
        if (cycleMaxId < 1 || cursor >= cycleMaxId) {
            cursor = 0L;
            cycleMaxId = documentRepository.findRecoveryCycleMaxId();
            guard.run();
            writeCycle(0L, cycleMaxId);
        }
        if (cycleMaxId < 1) {
            return;
        }

        var documents =
                documentRepository.findRecoveryScanWindow(
                        cursor, cycleMaxId, PageRequest.of(0, scanWindowSize));
        var scanned = 0;
        var dispatched = 0;
        var nextCursor = cursor;
        for (var document : documents) {
            guard.run();
            scanned++;
            nextCursor = document.getId();
            if (!isDispatchCandidate(document.getStatus(), document.getUpdateTime(), cutoff)) {
                continue;
            }
            try {
                queueService.enqueue(document);
                documentRepository.touchDispatchTimeIfPending(
                        document.getId(),
                        DocumentStatusEnum.PENDING.getCode(),
                        document.getUpdateTime());
                dispatched++;
                if (dispatched >= dispatchBatchSize) {
                    break;
                }
            } catch (RuntimeException failure) {
                log.error("恢复派发知识文档失败，documentId={}", document.getId(), failure);
            }
        }
        var reachedHighWater =
                nextCursor >= cycleMaxId
                        || (scanned == documents.size() && documents.size() < scanWindowSize);
        guard.run();
        if (reachedHighWater) {
            writeCycle(0L, 0L);
        } else {
            writeLong(RECOVERY_CURSOR_KEY, nextCursor);
        }
    }

    private void renew(AtomicReference<Lease> currentLease, AtomicBoolean lost) {
        if (lost.get()) {
            return;
        }
        var current = currentLease.get();
        try {
            var renewed = distributedLeases.renew(current, leaseTtl);
            if (renewed.isEmpty()) {
                lost.set(true);
                return;
            }
            currentLease.compareAndSet(current, renewed.orElseThrow());
        } catch (RuntimeException failure) {
            lost.set(true);
            log.error("知识文档恢复租约续期异常", failure);
        }
    }

    private void requireCurrent(AtomicReference<Lease> currentLease, AtomicBoolean lost) {
        if (lost.get()) {
            throw new TaskExecutionInProgressException("知识文档恢复租约已失效");
        }
        try {
            distributedLeases.requireCurrent(currentLease.get());
        } catch (RuntimeException failure) {
            lost.set(true);
            throw new TaskExecutionInProgressException("知识文档恢复租约已失效", failure);
        }
    }

    private void release(Lease lease) {
        try {
            distributedLeases.release(lease);
        } catch (RuntimeException failure) {
            log.warn("释放知识文档恢复租约失败", failure);
        }
    }

    private boolean isDispatchCandidate(
            Integer status, LocalDateTime updateTime, LocalDateTime cutoff) {
        return DocumentStatusEnum.PENDING.getCode().equals(status)
                && updateTime != null
                && updateTime.isBefore(cutoff);
    }

    private long readLong(String key) {
        var value = redisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException failure) {
            log.warn("知识文档恢复游标损坏，将重置: key={}, value={}", key, value);
            return 0L;
        }
    }

    private void writeCycle(long cursor, long cycleMaxId) {
        writeLong(RECOVERY_CURSOR_KEY, cursor);
        writeLong(RECOVERY_CYCLE_MAX_KEY, cycleMaxId);
    }

    private void writeLong(String key, long value) {
        redisTemplate.opsForValue().set(key, Long.toString(value));
    }
}
