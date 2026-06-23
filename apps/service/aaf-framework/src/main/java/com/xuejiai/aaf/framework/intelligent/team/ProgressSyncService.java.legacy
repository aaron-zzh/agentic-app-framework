/**
 * 进度同步服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.team;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.agent.runtime.AgentEventBus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/** 执行状态广播、进度汇报、超时检测。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressSyncService {

    private final AgentEventBus eventBus;

    /** 任务进度追踪 */
    private final Map<String, TaskProgress> progressMap = new ConcurrentHashMap<>();

    /** 报告进度 */
    public void reportProgress(String taskId, String agentId, int percentage, String message) {
        var progress = progressMap.computeIfAbsent(taskId, k -> new TaskProgress());
        progress.setTaskId(taskId);
        progress.setAgentId(agentId);
        progress.setPercentage(percentage);
        progress.setMessage(message);
        progress.setLastUpdatedAt(Instant.now());

        // 广播进度事件
        eventBus.publish(
                "progress:" + taskId,
                AgentEventBus.AgentMessage.of(
                        agentId, "team", "进度: " + percentage + "% - " + message));
    }

    /** 获取任务进度 */
    public TaskProgress getProgress(String taskId) {
        return progressMap.get(taskId);
    }

    /** 检测超时任务 */
    public Map<String, TaskProgress> detectTimeouts(Duration threshold) {
        var now = Instant.now();
        var timeouts = new ConcurrentHashMap<String, TaskProgress>();
        progressMap.forEach(
                (taskId, progress) -> {
                    if (progress.getLastUpdatedAt() != null
                            && Duration.between(progress.getLastUpdatedAt(), now)
                                            .compareTo(threshold)
                                    > 0
                            && progress.getPercentage() < 100) {
                        timeouts.put(taskId, progress);
                        log.warn("任务 [{}] 超时，最后更新: {}", taskId, progress.getLastUpdatedAt());
                    }
                });
        return timeouts;
    }

    /** 任务进度 */
    @Getter
    @Setter
    public static class TaskProgress {
        private String taskId;
        private String agentId;
        private int percentage;
        private String message;
        private Instant lastUpdatedAt;
    }
}
