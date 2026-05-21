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

import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** Learning 横切通道的入口：采集执行结果，评估效果，触发记忆/知识更新。 当前为轻量实现，后续可接入完整的程序化记忆蒸馏流水线。 */
@Slf4j
@Service
public class LearningFeedbackService {

    /** 技能执行统计（skillIntent → 成功/失败计数） */
    private final ConcurrentHashMap<String, SkillStats> statsMap = new ConcurrentHashMap<>();

    /** 记录执行结果。 */
    public void recordExecution(String sessionId, Long userId, String intent, boolean success) {
        var stats = statsMap.computeIfAbsent(intent, k -> new SkillStats());
        if (success) {
            stats.successCount.incrementAndGet();
        } else {
            stats.failureCount.incrementAndGet();
        }
        stats.lastExecutedAt = Instant.now();

        log.debug(
                "学习反馈: intent={}, success={}, 累计成功率={}%", intent, success, stats.getSuccessRate());
    }

    /** 记录用户反馈（点赞/点踩）。 */
    public void recordUserFeedback(String sessionId, Long userId, boolean positive) {
        // TODO: 接入程序化记忆蒸馏——正反馈强化 Skill，负反馈触发优化
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
