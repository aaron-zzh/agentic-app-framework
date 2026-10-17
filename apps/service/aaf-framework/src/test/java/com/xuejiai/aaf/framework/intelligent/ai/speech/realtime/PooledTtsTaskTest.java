package com.xuejiai.aaf.framework.intelligent.ai.speech.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PooledTtsTaskTest {

    /** 可编排行为的假合成连接：start 后由测试驱动回调。 */
    static final class FakeTtsClient implements TtsClient {
        final CountDownLatch started = new CountDownLatch(1);
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicBoolean cancelCalled = new AtomicBoolean();
        volatile Callback callback;
        volatile String text;

        @Override
        public void start(String text, String voice, Callback callback) {
            this.text = text;
            this.callback = callback;
            started.countDown();
        }

        @Override
        public void cancel() {
            cancelCalled.set(true);
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    static final class RecordingListener implements TtsListener {
        final List<byte[]> audio = new CopyOnWriteArrayList<>();
        final AtomicBoolean completed = new AtomicBoolean();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();

        @Override
        public void onAudio(byte[] pcm) {
            audio.add(pcm);
        }

        @Override
        public void onComplete() {
            completed.set(true);
        }

        @Override
        public void onError(Throwable error) {
            errors.add(error);
        }
    }

    private final List<FakeTtsClient> clients = new ArrayList<>();

    private ClientPool<TtsClient> newPool() {
        return new ClientPool<>(
                "tts-test",
                2,
                100,
                50,
                () -> {
                    var client = new FakeTtsClient();
                    clients.add(client);
                    return client;
                },
                TtsClient::close);
    }

    private static void await(FakeTtsClient client) throws InterruptedException {
        assertThat(client.started.await(2, TimeUnit.SECONDS)).isTrue();
    }

    private static void eventually(java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    @Test
    @DisplayName("Given 合成正常结束 When 上游 task-finished Then 回调完成并归还连接复用")
    void should_complete_and_release_when_finished() throws Exception {
        try (var pool = newPool()) {
            // 准备参数
            var listener = new RecordingListener();
            PooledTtsTask.start(pool, "你好", "v", listener, 200);
            await(clients.get(0));

            // 调用
            clients.get(0).callback.onAudio(new byte[] {1, 2});
            clients.get(0).callback.onComplete();
            var reused = pool.borrow();

            // 断言
            assertThat(listener.audio).hasSize(1);
            assertThat(listener.completed).isTrue();
            assertThat(reused).isSameAs(clients.get(0));
        }
    }

    @Test
    @DisplayName("Given 上游报错 When onError Then 通知监听器并作废连接")
    void should_discard_when_upstream_error() throws Exception {
        try (var pool = newPool()) {
            // 准备参数
            var listener = new RecordingListener();
            PooledTtsTask.start(pool, "你好", "v", listener, 200);
            await(clients.get(0));

            // 调用
            clients.get(0).callback.onError(new IllegalStateException("boom"));

            // 断言
            assertThat(listener.errors).hasSize(1);
            assertThat(clients.get(0).closed).isTrue();
        }
    }

    @Test
    @DisplayName("Given 取消后上游及时返回结束事件 When cancel Then 监听器静默且连接归还复用")
    void should_release_when_cancel_acknowledged() throws Exception {
        try (var pool = newPool()) {
            // 准备参数
            var listener = new RecordingListener();
            var task = PooledTtsTask.start(pool, "你好", "v", listener, 1000);
            await(clients.get(0));

            // 调用
            task.cancel();
            eventually(() -> clients.get(0).cancelCalled.get());
            clients.get(0).callback.onAudio(new byte[] {7});
            clients.get(0).callback.onComplete();
            eventually(() -> pool.snapshot().idle() == 1);

            // 断言
            assertThat(listener.audio).isEmpty();
            assertThat(listener.completed).isFalse();
            assertThat(clients.get(0).closed).isFalse();
        }
    }

    @Test
    @DisplayName("Given 取消后上游不返回结束事件 When 超时 Then 关闭连接不复用")
    void should_discard_when_cancel_times_out() throws Exception {
        try (var pool = newPool()) {
            // 准备参数
            var task = PooledTtsTask.start(pool, "你好", "v", new RecordingListener(), 100);
            await(clients.get(0));

            // 调用
            task.cancel();

            // 断言
            eventually(() -> clients.get(0).closed.get());
        }
    }
}
