package com.xuejiai.aaf.framework.intelligent.agent.runtime;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent 执行检查点：保存/恢复/重试。
 * （迁移自 intelligent/agent/AgentCheckpointService，补充 workingMemorySnapshot 字段）
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

    public void saveCheckpoint(String executionId, int step, ExecutionState state) {
        try {
            state.setCurrentStep(step);
            var json = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(KEY_PREFIX + executionId, json, CHECKPOINT_TTL);
        } catch (Exception e) {
            log.warn("检查点保存失败 [{}]: {}", executionId, e.getMessage());
        }
    }

    public ExecutionState restoreCheckpoint(String executionId) {
        try {
            var json = redisTemplate.opsForValue().get(KEY_PREFIX + executionId);
            if (json != null) return objectMapper.readValue(json, ExecutionState.class);
        } catch (Exception e) {
            log.warn("检查点恢复失败 [{}]: {}", executionId, e.getMessage());
        }
        return null;
    }

    public void clearCheckpoint(String executionId) {
        redisTemplate.delete(KEY_PREFIX + executionId);
    }

    public <T> T executeWithRetry(String executionId, RetryableAction<T> action) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return action.execute(attempt);
            } catch (Exception e) {
                log.warn("[{}] 第 {} 次执行失败: {}", executionId, attempt, e.getMessage());
                if (attempt == MAX_RETRIES) throw new RuntimeException("重试 %d 次后仍失败".formatted(MAX_RETRIES), e);
                backoff(attempt);
            }
        }
        throw new IllegalStateException("不可达");
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(BASE_DELAY_MS * (1L << (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    public interface RetryableAction<T> {
        T execute(int attempt) throws Exception;
    }

    @lombok.Getter
    @lombok.Setter
    public static class ExecutionState {
        private String executionId;
        private String agentId;
        private int currentStep;
        private List<String> completedSteps;
        private Map<String, Object> intermediateResults;
        /** 工作记忆快照（恢复时重新注入，防止状态丢失） */
        private Map<String, Object> workingMemorySnapshot;
        private String lastError;
    }
}
