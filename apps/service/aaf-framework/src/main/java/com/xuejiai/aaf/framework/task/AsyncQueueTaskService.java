package com.xuejiai.aaf.framework.task;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.xuejiai.aaf.framework.messaging.sse.SseSessionManager;
import com.xuejiai.aaf.framework.task.queue.AsyncTaskMessage;
import com.xuejiai.aaf.framework.task.queue.TaskQueue;

import lombok.RequiredArgsConstructor;

/** 通用异步任务服务，任务主状态持久化于 sys_async_task。 */
@Service
@RequiredArgsConstructor
public class AsyncQueueTaskService {

    public static final String STATUS_EVENT = "async-task.status";

    private static final int DEFAULT_MAX_RETRIES = 3;

    private final AsyncTaskRepository repository;
    private final TaskQueue taskQueue;
    private final SseSessionManager sseSessionManager;

    /** 创建 PENDING 主记录；Redis 投递统一由 {@link AsyncTaskDispatcher} 执行。 */
    @Transactional
    public AsyncTaskRef submit(
            String taskType,
            String payload,
            int priority,
            Long ownerId,
            Long orgId,
            Long workspaceId) {
        var now = LocalDateTime.now();
        var task = new AsyncTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskType(taskType);
        task.setStatus(AsyncTaskStatus.PENDING);
        task.setPayload(payload);
        task.setPriority((short) priority);
        task.setMaxRetries(DEFAULT_MAX_RETRIES);
        task.setAttemptCount(0);
        task.setOwnerId(ownerId);
        task.setOrgId(orgId);
        task.setWorkspaceId(workspaceId);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        task.setVersion(0);
        repository.save(task);
        publishStatusAfterCommit(task);
        return toRef(task);
    }

    /** 投递一个 PENDING 任务。Redis 成功写入后才把主状态推进到 QUEUED。 */
    @Transactional
    public boolean dispatchNextPending() {
        var tasks = repository.findByStatusForUpdate(AsyncTaskStatus.PENDING, PageRequest.of(0, 1));
        if (tasks.isEmpty()) {
            return false;
        }
        var task = tasks.getFirst();
        taskQueue.enqueue(toMessage(task));
        task.setStatus(AsyncTaskStatus.QUEUED);
        touch(task);
        publishStatusAfterCommit(task);
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<AsyncTask> find(String taskId) {
        return repository.findByTaskId(taskId);
    }

    @Transactional(readOnly = true)
    public Optional<AsyncTask> findOwned(String taskId, Long ownerId) {
        return repository.findByTaskIdAndOwnerId(taskId, ownerId);
    }

    @Transactional(readOnly = true)
    public Page<AsyncTask> findOwned(Long ownerId, AsyncTaskStatus status, Pageable pageable) {
        return status == null
                ? repository.findByOwnerId(ownerId, pageable)
                : repository.findByOwnerIdAndStatus(ownerId, status, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AsyncTask> findAll(AsyncTaskStatus status, Pageable pageable) {
        return status == null
                ? repository.findAll(pageable)
                : repository.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public boolean isManaged(String taskId) {
        return repository.existsByTaskId(taskId);
    }

    /** 管理员仅可重试 FAILED 受管任务；重新进入 PENDING 后仍由唯一投递器入队。 */
    @Transactional
    public boolean retryFailed(String taskId) {
        var task = repository.findByTaskIdForUpdate(taskId).orElse(null);
        if (task == null || task.getStatus() != AsyncTaskStatus.FAILED) {
            return false;
        }
        task.setStatus(AsyncTaskStatus.PENDING);
        task.setAttemptCount(0);
        task.setResult(null);
        task.setLastError(null);
        task.setStartedAt(null);
        task.setCompletedAt(null);
        touch(task);
        publishStatusAfterCommit(task);
        return true;
    }

    @Transactional
    public void markRunning(AsyncTaskMessage message) {
        repository
                .findByTaskId(message.id())
                .filter(task -> !isTerminal(task))
                .ifPresent(
                        task -> {
                            task.setStatus(AsyncTaskStatus.RUNNING);
                            task.setAttemptCount(message.attempt() + 1);
                            task.setLastError(null);
                            if (task.getStartedAt() == null) {
                                task.setStartedAt(LocalDateTime.now());
                            }
                            touch(task);
                            publishStatusAfterCommit(task);
                        });
    }

    @Transactional
    public void markSucceeded(String taskId, String result) {
        repository
                .findByTaskId(taskId)
                .filter(task -> !isTerminal(task))
                .ifPresent(
                        task -> {
                            task.setStatus(AsyncTaskStatus.SUCCEEDED);
                            task.setResult(result);
                            task.setLastError(null);
                            task.setCompletedAt(LocalDateTime.now());
                            touch(task);
                            publishStatusAfterCommit(task);
                        });
    }

    @Transactional
    public void markRetryWaiting(AsyncTaskMessage task) {
        repository
                .findByTaskId(task.id())
                .filter(stored -> !isTerminal(stored))
                .ifPresent(
                        stored -> {
                            stored.setStatus(AsyncTaskStatus.RETRY_WAIT);
                            stored.setAttemptCount(task.attempt());
                            stored.setLastError(task.lastError());
                            touch(stored);
                            publishStatusAfterCommit(stored);
                        });
    }

    @Transactional
    public void markFailed(AsyncTaskMessage task) {
        repository
                .findByTaskId(task.id())
                .filter(stored -> !isTerminal(stored))
                .ifPresent(
                        stored -> {
                            stored.setStatus(AsyncTaskStatus.FAILED);
                            stored.setAttemptCount(task.attempt() + 1);
                            stored.setLastError(task.lastError());
                            stored.setCompletedAt(LocalDateTime.now());
                            touch(stored);
                            publishStatusAfterCommit(stored);
                        });
    }

    private AsyncTaskMessage toMessage(AsyncTask task) {
        return new AsyncTaskMessage(
                task.getTaskId(),
                task.getTaskType(),
                task.getPayload(),
                task.getPriority(),
                task.getMaxRetries(),
                0,
                task.getCreateTime(),
                null);
    }

    private AsyncTaskRef toRef(AsyncTask task) {
        return new AsyncTaskRef(task.getTaskId(), task.getTaskType(), task.getStatus());
    }

    private boolean isTerminal(AsyncTask task) {
        return task.getStatus() == AsyncTaskStatus.SUCCEEDED
                || task.getStatus() == AsyncTaskStatus.FAILED;
    }

    private void touch(AsyncTask task) {
        task.setUpdateTime(LocalDateTime.now());
        task.setVersion(task.getVersion() + 1);
    }

    private void publishStatusAfterCommit(AsyncTask task) {
        var event = AsyncTaskStatusEvent.from(task);
        var callback =
                (Runnable) () -> sseSessionManager.push(event.ownerId(), STATUS_EVENT, event);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            callback.run();
                        }
                    });
            return;
        }
        callback.run();
    }

    public record AsyncTaskRef(String taskId, String taskType, AsyncTaskStatus status) {}

    public record AsyncTaskStatusEvent(
            String taskId,
            String taskType,
            AsyncTaskStatus status,
            Long ownerId,
            int attemptCount,
            int maxRetries,
            String result,
            String lastError,
            LocalDateTime updateTime) {
        private static AsyncTaskStatusEvent from(AsyncTask task) {
            return new AsyncTaskStatusEvent(
                    task.getTaskId(),
                    task.getTaskType(),
                    task.getStatus(),
                    task.getOwnerId(),
                    task.getAttemptCount(),
                    task.getMaxRetries(),
                    task.getResult(),
                    task.getLastError(),
                    task.getUpdateTime());
        }
    }
}
