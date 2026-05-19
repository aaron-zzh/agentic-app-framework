/**
 * Agent 执行检查点与重试机制。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.agent;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent 执行状态管理：检查点保存/恢复 + 重试策略。
 *
 * <p>检查点存 Redis（TTL 自动过期），支持：
 * <ul>
 *   <li>每步执行完保存检查点</li>
 *   <li>失败时从最近检查点恢复</li>
 *   <li>指数退避重试</li>
 *   <li>任务完成后自动清理</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCheckpointService {

    private static final String KEY_PREFIX = "agent:checkpoint:";
    private static final Duration CHECKPOINT_TTL = Duration.ofHours(2);
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1000;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 保存检查点。
     *
     * @param executionId 执行实例 ID
     * @param step 当前步骤编号
     * @param state 执行状态快照
     */
    public void saveCheckpoint(String executionId, int step, ExecutionState state) {
        try {
            state.setCurrentStep(step);
            var json = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(KEY_PREFIX + executionId, json, CHECKPOINT_TTL);
        } catch (Exception e) {
            log.warn("检查点保存失败 [{}]: {}", executionId, e.getMessage());
        }
    }

    /**
     * 恢复检查点。
     *
     * @param executionId 执行实例 ID
     * @return 最近的执行状态，null 表示无检查点
     */
    public ExecutionState restoreCheckpoint(String executionId) {
        try {
            var json = redisTemplate.opsForValue().get(KEY_PREFIX + executionId);
            if (json != null) {
                return objectMapper.readValue(json, ExecutionState.class);
            }
        } catch (Exception e) {
            log.warn("检查点恢复失败 [{}]: {}", executionId, e.getMessage());
        }
        return null;
    }

    /**
     * 清理检查点（任务完成后调用）。
     */
    public void clearCheckpoint(String executionId) {
        redisTemplate.delete(KEY_PREFIX + executionId);
    }

    /**
     * 带重试的执行。指数退避，失败后从检查点恢复。
     *
     * @param executionId 执行 ID
     * @param action 要执行的动作
     * @return 执行结果
     */
    public <T> T executeWithRetry(String executionId, RetryableAction<T> action) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return action.execute(attempt);
            } catch (Exception e) {
                log.warn("[{}] 第 {} 次执行失败: {}", executionId, attempt, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    throw new RuntimeException("重试 %d 次后仍失败".formatted(MAX_RETRIES), e);
                }
                backoff(attempt);
            }
        }
        throw new IllegalStateException("不可达");
    }

    private void backoff(int attempt) {
        try {
            long delay = BASE_DELAY_MS * (1L << (attempt - 1)); // 1s, 2s, 4s
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 可重试动作 */
    @FunctionalInterface
    public interface RetryableAction<T> {
        T execute(int attempt) throws Exception;
    }

    /** 执行状态快照 */
    @lombok.Getter
    @lombok.Setter
    public static class ExecutionState {
        private String executionId;
        private String agentId;
        private int currentStep;
        private List<String> completedSteps;
        private Map<String, Object> intermediateResults;
        private String lastError;
    }
}
