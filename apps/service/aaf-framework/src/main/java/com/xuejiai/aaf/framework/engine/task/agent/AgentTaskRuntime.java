package com.xuejiai.aaf.framework.engine.task.agent;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.lease.DistributedLeasePort;

import lombok.extern.slf4j.Slf4j;

/** 智能体任务注册与单次派发运行时。 */
@Slf4j
@Component
public class AgentTaskRuntime {

    private static final String LEASE_KEY_PREFIX = "agent-task:";
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(30);

    private final DistributedLeasePort distributedLeases;
    private final RetryScheduler retryScheduler;
    private final Duration leaseTtl;
    private final String ownerId;
    private final ConcurrentMap<String, AgentTask> tasks = new ConcurrentHashMap<>();

    public AgentTaskRuntime(
            DistributedLeasePort distributedLeases,
            RetryScheduler retryScheduler,
            List<AgentTask> tasks,
            @Value("${aaf.task.agent.lease-ttl-seconds:300}") long leaseTtlSeconds) {
        this.distributedLeases = distributedLeases;
        this.retryScheduler = retryScheduler;
        this.leaseTtl = Duration.ofSeconds(leaseTtlSeconds);
        this.ownerId = createOwnerId();
        tasks.forEach(this::register);
    }

    public void register(AgentTask task) {
        Objects.requireNonNull(task, "task 不能为空");
        var taskType = task.taskType();
        if (taskType == null || taskType.isBlank()) {
            throw new IllegalArgumentException("taskType 不能为空白");
        }
        var existing = tasks.putIfAbsent(taskType, task);
        if (existing != null && existing != task) {
            throw new IllegalStateException("智能体任务类型重复注册: " + taskType);
        }
    }

    public Optional<AgentTask> find(String taskType) {
        if (taskType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tasks.get(taskType));
    }

    public void dispatch(String taskType, String taskId, String tenantId, String triggerType) {
        var task = find(taskType);
        if (task.isEmpty()) {
            log.warn("智能体任务类型未注册，跳过派发: taskType={}", taskType);
            return;
        }

        var leaseKey = LEASE_KEY_PREFIX + tenantId + ":" + taskId;
        var lease = distributedLeases.acquire(leaseKey, ownerId, leaseTtl);
        if (lease.isEmpty()) {
            return;
        }

        var acquiredLease = lease.orElseThrow();
        try {
            var context = new AgentTaskContext(taskId, tenantId, triggerType, acquiredLease);
            var outcome = Objects.requireNonNull(task.orElseThrow().execute(context), "任务结果不能为空");
            if (outcome.outcome() == AgentTaskOutcome.Outcome.FAILED_RETRYABLE) {
                retryScheduler.scheduleRetry(taskType, taskId, tenantId, RETRY_BACKOFF);
            }
        } catch (Exception e) {
            log.error("智能体任务执行异常: taskType={}, taskId={}", taskType, taskId, e);
        } finally {
            distributedLeases.release(acquiredLease);
        }
    }

    private static String createOwnerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + UUID.randomUUID();
        } catch (UnknownHostException e) {
            return "unknown-host:" + UUID.randomUUID();
        }
    }
}
