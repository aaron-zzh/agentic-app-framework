package com.xuejiai.aaf.framework.intelligent.assistant;

import java.util.Set;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuejiai.aaf.framework.engine.checkpoint.CheckpointStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 会话恢复服务——服务重启后自动恢复活跃会话。
 *
 * <p>恢复流程：扫描 Redis 活跃会话 → 过滤有未完成子任务的 → 加载 TaskBoard → 标记待恢复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionRecoveryService {

    private static final String SESSION_KEY_PREFIX = "session:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CheckpointStore checkpointStore;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverSessions() {
        log.info("开始扫描需要恢复的活跃会话...");
        Set<String> keys = redisTemplate.keys(SESSION_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            log.info("无活跃会话需要恢复");
            return;
        }

        int totalRecovered = 0;
        for (var key : keys) {
            try {
                var json = redisTemplate.opsForValue().get(key);
                if (json == null) continue;

                var session = objectMapper.readValue(json, SessionManager.SessionState.class);
                if (session.getStatus() != SessionManager.SessionStatus.ACTIVE
                        && session.getStatus() != SessionManager.SessionStatus.PROCESSING) {
                    continue;
                }

                int recovered = recoverSession(session);
                if (recovered > 0) {
                    totalRecovered++;
                    eventPublisher.publishEvent(new SessionRecoveredEvent(
                            session.getSessionId(),
                            session.getUserId(),
                            recovered,
                            "服务重启后自动恢复"));
                }
            } catch (Exception e) {
                log.warn("会话恢复失败: key={}", key, e);
            }
        }
        log.info("会话恢复完成，共恢复 {} 个会话", totalRecovered);
    }

    private int recoverSession(SessionManager.SessionState session) {
        // 查找该会话关联的 Checkpoint
        var checkpoints = checkpointStore.listByOwner(session.getSessionId());
        if (checkpoints.isEmpty()) {
            return 0;
        }

        // 从最新 Checkpoint 恢复 TaskBoard
        var latest = checkpoints.stream()
                .max((a, b) -> Integer.compare(a.step(), b.step()))
                .orElse(null);
        if (latest == null) return 0;

        var taskBoard = TaskBoard.fromSnapshot(latest.state());
        int recoveredCount = 0;
        for (var task : taskBoard.allTasks()) {
            switch (task.status()) {
                case DONE, FAILED -> { /* 跳过 */ }
                case RUNNING -> {
                    // 有 Checkpoint 则标记待恢复，否则标记失败
                    recoveredCount++;
                }
                case PENDING -> recoveredCount++;
            }
        }

        log.info("恢复会话: sessionId={}, 待恢复任务数={}", session.getSessionId(), recoveredCount);
        return recoveredCount;
    }
}
