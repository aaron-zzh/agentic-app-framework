package com.xuejiai.aaf.framework.intelligent.ai.speech.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClientPoolTest {

    private final AtomicInteger created = new AtomicInteger();
    private final AtomicInteger closed = new AtomicInteger();

    private ClientPool<Object> newPool(int size, long borrowTimeoutMs) {
        return new ClientPool<>(
                "test",
                size,
                borrowTimeoutMs,
                50,
                () -> {
                    created.incrementAndGet();
                    return new Object();
                },
                client -> closed.incrementAndGet());
    }

    @Test
    @DisplayName("Given 对象已归还 When 再次借出 Then 复用同一个对象且不新建")
    void should_reuse_object_when_released() {
        // 准备参数
        try (var pool = newPool(2, 100)) {
            // 调用
            var first = pool.borrow();
            pool.release(first);
            var second = pool.borrow();

            // 断言
            assertThat(second).isSameAs(first);
            assertThat(created.get()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Given 对象被作废 When 再次借出 Then 旧对象已关闭并新建对象")
    void should_close_and_recreate_when_discarded() {
        try (var pool = newPool(2, 100)) {
            // 调用
            var first = pool.borrow();
            pool.discard(first);
            var second = pool.borrow();

            // 断言
            assertThat(second).isNotSameAs(first);
            assertThat(closed.get()).isEqualTo(1);
            assertThat(created.get()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("Given 池已满且未归还 When 借出超时 Then 抛出池耗尽异常而不是额外创建对象")
    void should_throw_exhausted_when_pool_full() {
        try (var pool = newPool(1, 50)) {
            // 准备参数
            pool.borrow();

            // 调用 + 断言
            assertThatThrownBy(pool::borrow).isInstanceOf(SpeechPoolExhaustedException.class);
            assertThat(created.get()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Given 对象池大小超过 SDK 连接池 When 校验 Then 启动失败并给出修正指引")
    void should_reject_when_pool_larger_than_connection_pool() {
        // 准备参数
        var properties = new SpeechPoolProperties(20, 20, 300, 3000, 2000, 50);

        // 调用 + 断言
        assertThatThrownBy(() -> properties.validate(32))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DASHSCOPE_CONNECTION_POOL_SIZE");
        properties.validate(40);
    }
}
