package com.xuejiai.aaf.framework.agentscope.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** AafContextHolder 单元测试。 */
class AafContextHolderTest {

    @AfterEach
    void cleanup() {
        AafContextHolder.clear();
        AafContextHolder.setDevModeFallback(null);
    }

    @Test
    void get_returnsNull_whenNotSet() {
        assertThat(AafContextHolder.get()).isNull();
        assertThat(AafContextHolder.userId()).isNull();
        assertThat(AafContextHolder.conversationId()).isNull();
        assertThat(AafContextHolder.threadId()).isNull();
    }

    @Test
    void set_and_get_returnsContext() {
        var ctx = new AafContextHolder.AafContext(1L, 2L, 3L, 4L, "t1");
        AafContextHolder.set(ctx);

        assertThat(AafContextHolder.userId()).isEqualTo(1L);
        assertThat(AafContextHolder.assistantId()).isEqualTo(2L);
        assertThat(AafContextHolder.conversationId()).isEqualTo(3L);
        assertThat(AafContextHolder.knowledgeBaseId()).isEqualTo(4L);
        assertThat(AafContextHolder.threadId()).isEqualTo("t1");
    }

    @Test
    void clear_removesContext() {
        AafContextHolder.set(new AafContextHolder.AafContext(1L, null, null, null, "t1"));
        AafContextHolder.clear();
        assertThat(AafContextHolder.get()).isNull();
    }

    @Test
    void devModeFallback_usedWhenThreadLocalEmpty() {
        var fallback = new AafContextHolder.AafContext(99L, null, null, null, "fallback");
        AafContextHolder.setDevModeFallback(fallback);

        assertThat(AafContextHolder.userId()).isEqualTo(99L);
    }

    @Test
    void threadLocal_takesPriorityOverFallback() {
        AafContextHolder.setDevModeFallback(
                new AafContextHolder.AafContext(99L, null, null, null, "fallback"));
        AafContextHolder.set(new AafContextHolder.AafContext(1L, null, null, null, "real"));

        assertThat(AafContextHolder.userId()).isEqualTo(1L);
        assertThat(AafContextHolder.threadId()).isEqualTo("real");
    }

    @Test
    void enableThinking_defaultsFalse() {
        AafContextHolder.set(new AafContextHolder.AafContext(1L, null, null, null, "t1"));
        assertThat(AafContextHolder.enableThinking()).isFalse();
    }

    @Test
    void enableThinking_trueWhenSet() {
        var ctx = new AafContextHolder.AafContext(1L, null, null, null, "t1", true, 8000, null);
        AafContextHolder.set(ctx);
        assertThat(AafContextHolder.enableThinking()).isTrue();
        assertThat(AafContextHolder.thinkingBudget()).isEqualTo(8000);
    }

    @Test
    void thinkingBudget_defaultsTo8000WhenNotSet() {
        AafContextHolder.set(
                new AafContextHolder.AafContext(1L, null, null, null, "t1", true, null, null));
        assertThat(AafContextHolder.thinkingBudget()).isEqualTo(8000);
    }

    @Test
    void threadIsolation() throws InterruptedException {
        AafContextHolder.set(new AafContextHolder.AafContext(1L, null, null, null, "main"));

        var otherUserId = new Long[1];
        var t = new Thread(() -> otherUserId[0] = AafContextHolder.userId());
        t.start();
        t.join();

        // 其他线程的 ThreadLocal 独立，未设置应为 null
        assertThat(otherUserId[0]).isNull();
        // 主线程不受影响
        assertThat(AafContextHolder.userId()).isEqualTo(1L);
    }
}
