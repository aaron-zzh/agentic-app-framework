package com.xuejiai.aaf.framework.engine.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

/** 时间衰减策略单元测试。 */
class TimeDecayStrategyTest {

    private final TimeDecayStrategy strategy = new TimeDecayStrategy();

    @Test
    void decay_当天事件衰减为1() {
        var now = Instant.now();
        assertThat(strategy.decay(now, now)).isEqualTo(1.0);
    }

    @Test
    void decay_7天前事件应有明显衰减() {
        var now = Instant.now();
        var sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
        var result = strategy.decay(sevenDaysAgo, now);
        // 0.95^7 ≈ 0.698
        assertThat(result).isBetween(0.69, 0.71);
    }

    @Test
    void decay_30天前事件应大幅衰减() {
        var now = Instant.now();
        var thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);
        var result = strategy.decay(thirtyDaysAgo, now);
        // 0.95^30 ≈ 0.214
        assertThat(result).isBetween(0.21, 0.22);
    }

    @Test
    void timeBonus_精确匹配应加分() {
        var eventTime = Instant.now();
        var queryTime = eventTime.plus(12, ChronoUnit.HOURS);
        assertThat(strategy.timeBonus(eventTime, queryTime)).isEqualTo(1.5);
    }

    @Test
    void timeBonus_超过24小时不加分() {
        var eventTime = Instant.now();
        var queryTime = eventTime.plus(25, ChronoUnit.HOURS);
        assertThat(strategy.timeBonus(eventTime, queryTime)).isEqualTo(1.0);
    }

    @Test
    void timeBonus_queryTime为null不加分() {
        assertThat(strategy.timeBonus(Instant.now(), null)).isEqualTo(1.0);
    }

    @Test
    void score_综合分数应为衰减乘以加分() {
        var now = Instant.now();
        var eventTime = now.minus(7, ChronoUnit.DAYS);
        var result = strategy.score(eventTime, now, null);
        // 无时间匹配加分，纯衰减
        assertThat(result).isEqualTo(strategy.decay(eventTime, now));
    }
}
