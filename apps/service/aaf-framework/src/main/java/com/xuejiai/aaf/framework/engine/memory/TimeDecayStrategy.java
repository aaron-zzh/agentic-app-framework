/**
 * 时间衰减策略。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.engine.memory;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

/** 基于时间的记忆权重衰减计算。 */
@Component
public class TimeDecayStrategy {

    /** 每日衰减因子（每天衰减 5%） */
    private static final double DAILY_DECAY = 0.95;

    /** 精确时间匹配加分（24 小时内匹配加 50%） */
    private static final double EXACT_MATCH_BONUS = 1.5;

    /** 计算时间衰减系数 */
    public double decay(Instant eventTime, Instant now) {
        long days = Duration.between(eventTime, now).toDays();
        return Math.pow(DAILY_DECAY, Math.max(0, days));
    }

    /** 计算时间匹配加分 */
    public double timeBonus(Instant eventTime, Instant queryTime) {
        if (queryTime == null) return 1.0;
        long hours = Math.abs(Duration.between(eventTime, queryTime).toHours());
        return hours < 24 ? EXACT_MATCH_BONUS : 1.0;
    }

    /** 综合时间分数 = 衰减 × 匹配加分 */
    public double score(Instant eventTime, Instant now, Instant queryTime) {
        return decay(eventTime, now) * timeBonus(eventTime, queryTime);
    }
}
