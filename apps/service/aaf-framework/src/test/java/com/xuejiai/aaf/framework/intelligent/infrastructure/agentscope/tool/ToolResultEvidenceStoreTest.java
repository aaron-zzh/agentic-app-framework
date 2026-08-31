package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;

/** RQ-10：工具证据在非正常终止路径下必须可清零，并有 TTL 与容量兜底。 */
class ToolResultEvidenceStoreTest {

    private static final ExecutionId FIRST = new ExecutionId("execution-1");
    private static final ExecutionId SECOND = new ExecutionId("execution-2");

    @Test
    @DisplayName("Given 工具结果未被消费 When 按执行清理 Then 只清本执行残留")
    void should_clear_only_target_execution_evidence() {
        var store = new ToolResultEvidenceStore();
        store.record(FIRST, "call-1", Map.of("artifactId", "a-1"), true, false);
        store.record(FIRST, "call-2", Map.of("artifactId", "a-2"), true, false);
        store.record(SECOND, "call-3", Map.of("artifactId", "a-3"), true, false);

        var removed = store.clear(FIRST);

        assertThat(removed).isEqualTo(2);
        assertThat(store.take(FIRST, "call-1")).isEmpty();
        assertThat(store.take(SECOND, "call-3")).isPresent();
    }

    @Test
    @DisplayName("Given 证据已超过 TTL When 写入新证据 Then 过期项被淘汰")
    void should_evict_expired_evidence_on_write() {
        var clock = new MutableClock(Instant.parse("2026-08-31T00:00:00Z"));
        var store = new ToolResultEvidenceStore(clock, Duration.ofMinutes(5), 100);
        store.record(FIRST, "call-1", Map.of(), true, false);

        clock.advance(Duration.ofMinutes(6));
        store.record(FIRST, "call-2", Map.of(), true, false);

        assertThat(store.take(FIRST, "call-1")).isEmpty();
        assertThat(store.take(FIRST, "call-2")).isPresent();
    }

    @Test
    @DisplayName("Given 条目超过容量上限 When 继续写入 Then 缓存保持有界")
    void should_stay_bounded_when_capacity_exceeded() {
        var clock = new MutableClock(Instant.parse("2026-08-31T00:00:00Z"));
        var store = new ToolResultEvidenceStore(clock, Duration.ofMinutes(30), 2);

        for (var index = 0; index < 5; index++) {
            store.record(FIRST, "call-" + index, Map.of(), true, false);
            clock.advance(Duration.ofSeconds(1));
        }

        assertThat(store.size()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Given 已记录证据 When take 两次 Then 第二次为空")
    void should_consume_evidence_once() {
        var store = new ToolResultEvidenceStore();
        store.record(FIRST, "call-1", Map.of("approvalId", "approval-1"), false, true);

        assertThat(store.take(FIRST, "call-1"))
                .hasValueSatisfying(
                        values -> {
                            assertThat(values).containsEntry("approvalId", "approval-1");
                            assertThat(values).containsEntry("authorizationRequired", true);
                        });
        assertThat(store.take(FIRST, "call-1")).isEmpty();
    }

    /** 可推进的测试时钟：TTL 行为必须由确定时间驱动，不用 sleep。 */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
