/**
 * 学习反馈服务——执行效果评估与经验沉淀。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.agent.trace.ExecutionCompletedEvent;
import com.xuejiai.aaf.framework.intelligent.agent.trace.ExecutionStatus;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** Learning 横切通道的入口：异步监听执行完成事件，评估效果，触发记忆/知识更新。 */
@Slf4j
@Service
public class LearningFeedbackService {

    /** 技能执行统计（agentId → 成功/失败计数） */
    private final ConcurrentHashMap<String, SkillStats> statsMap = new ConcurrentHashMap<>();

    /** 异步监听 Agent 执行完成事件，更新统计并触发经验沉淀。 */
    @Async
    @EventListener
    public void onExecutionCompleted(ExecutionCompletedEvent event) {
        var stats = statsMap.computeIfAbsent(event.agentId(), k -> new SkillStats());
        boolean success = event.status() == ExecutionStatus.SUCCESS;
        if (success) {
            stats.successCount.incrementAndGet();
        } else {
            stats.failureCount.incrementAndGet();
        }
        stats.lastExecutedAt = Instant.now();

        log.debug(
                "学习反馈: agentId={}, success={}, 累计成功率={}%",
                event.agentId(), success, stats.getSuccessRate());

        // TODO: 接入程序化记忆蒸馏——正反馈强化 Skill，负反馈触发优化
    }

    /** 记录用户反馈（点赞/点踩）。 */
    public void recordUserFeedback(String sessionId, Long userId, boolean positive) {
        // TODO: 接入程序化记忆蒸馏
        log.debug("用户反馈: session={}, positive={}", sessionId, positive);
    }

    /** 获取技能执行统计。 */
    public Map<String, SkillStats> getStats() {
        return Map.copyOf(statsMap);
    }

    /** 技能执行统计 */
    @Getter
    public static class SkillStats {
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private volatile Instant lastExecutedAt;

        public int getSuccessRate() {
            int total = successCount.get() + failureCount.get();
            return total == 0 ? 0 : (successCount.get() * 100 / total);
        }
    }
}
