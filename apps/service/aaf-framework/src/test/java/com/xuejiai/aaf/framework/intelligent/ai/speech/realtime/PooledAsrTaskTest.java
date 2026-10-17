package com.xuejiai.aaf.framework.intelligent.ai.speech.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PooledAsrTaskTest {

    /** 可编排行为的假识别连接。 */
    static final class FakeAsrClient implements AsrClient {
        final List<byte[]> sent = new CopyOnWriteArrayList<>();
        final AtomicBoolean closed = new AtomicBoolean();
        volatile Callback callback;
        volatile boolean stopBlocks;
        volatile RuntimeException startFailure;
        volatile int usageOnStop;
        final CountDownLatch neverReleased = new CountDownLatch(1);

        @Override
        public void start(String language, Callback callback) {
            if (startFailure != null) {
                throw startFailure;
            }
            this.callback = callback;
        }

        @Override
        public void send(byte[] pcm) {
            sent.add(pcm);
        }

        @Override
        public void stop() {
            if (usageOnStop > 0) {
                callback.onUsage(usageOnStop);
            }
            if (stopBlocks) {
                try {
                    neverReleased.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public void close() {
            closed.set(true);
            neverReleased.countDown();
        }
    }

    static final class RecordingListener implements AsrListener {
        final List<String> partials = new CopyOnWriteArrayList<>();
        final List<String> sentences = new CopyOnWriteArrayList<>();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();

        @Override
        public void onPartial(String text) {
            partials.add(text);
        }

        @Override
        public void onSentenceEnd(String text) {
            sentences.add(text);
        }

        @Override
        public void onError(Throwable error) {
            errors.add(error);
        }
    }

    private final List<FakeAsrClient> clients = new ArrayList<>();

    private ClientPool<AsrClient> newPool(int size) {
        return new ClientPool<>(
                "asr-test",
                size,
                100,
                50,
                () -> {
                    var client = new FakeAsrClient();
                    clients.add(client);
                    return client;
                },
                AsrClient::close);
    }

    @Test
    @DisplayName("Given 任务正常结束 When finish Then 返回上游时长并归还连接供下一任务复用")
    void should_release_and_reuse_connection_when_finished_cleanly() {
        try (var pool = newPool(2)) {
            // 准备参数
            var listener = new RecordingListener();
            var task = PooledAsrTask.open(pool, "zh-CN", listener, 500, 200);
            clients.get(0).usageOnStop = 3;
            task.sendAudio(new byte[] {1, 2});
            clients.get(0).callback.onPartial("你");
            clients.get(0).callback.onSentenceEnd("你好");

            // 调用
            var seconds = task.finish();
            var next = PooledAsrTask.open(pool, "zh-CN", new RecordingListener(), 500, 200);

            // 断言
            assertThat(seconds).isEqualTo(3);
            assertThat(listener.partials).containsExactly("你");
            assertThat(listener.sentences).containsExactly("你好");
            assertThat(clients).hasSize(1);
            assertThat(clients.get(0).closed).isFalse();
            assertThat(next).isNotNull();
        }
    }

    @Test
    @DisplayName("Given 上游报错 When finish Then 通知监听器并作废连接不复用")
    void should_discard_connection_when_upstream_failed() {
        try (var pool = newPool(2)) {
            // 准备参数
            var listener = new RecordingListener();
            var task = PooledAsrTask.open(pool, "zh-CN", listener, 500, 200);
            clients.get(0).callback.onError(new IllegalStateException("boom"));

            // 调用
            task.finish();
            PooledAsrTask.open(pool, "zh-CN", new RecordingListener(), 500, 200);

            // 断言
            assertThat(listener.errors).hasSize(1);
            assertThat(clients.get(0).closed).isTrue();
            assertThat(clients).hasSize(2);
        }
    }

    @Test
    @DisplayName("Given 上游迟迟不返回 task-finished When finish 超时 Then 关闭连接不归还")
    void should_discard_connection_when_stop_times_out() {
        try (var pool = newPool(2)) {
            // 准备参数
            var task = PooledAsrTask.open(pool, "zh-CN", new RecordingListener(), 100, 100);
            clients.get(0).stopBlocks = true;

            // 调用
            task.finish();

            // 断言
            assertThat(clients.get(0).closed).isTrue();
        }
    }

    @Test
    @DisplayName("Given 任务已 abort When 上游继续回调 Then 监听器不再收到事件")
    void should_stay_silent_when_aborted() {
        try (var pool = newPool(2)) {
            // 准备参数
            var listener = new RecordingListener();
            var task = PooledAsrTask.open(pool, "zh-CN", listener, 500, 200);

            // 调用
            task.abort();
            clients.get(0).callback.onSentenceEnd("迟到的句子");
            clients.get(0).callback.onError(new IllegalStateException("late"));

            // 断言
            assertThat(listener.sentences).isEmpty();
            assertThat(listener.errors).isEmpty();
        }
    }

    @Test
    @DisplayName("Given 任务已结束 When 再次 finish 或发送音频 Then 幂等且不再写入上游")
    void should_be_idempotent_after_end() {
        try (var pool = newPool(2)) {
            // 准备参数
            var task = PooledAsrTask.open(pool, "zh-CN", new RecordingListener(), 500, 200);
            task.finish();

            // 调用
            task.finish();
            task.sendAudio(new byte[] {9});

            // 断言
            assertThat(clients.get(0).sent).isEmpty();
        }
    }

    @Test
    @DisplayName("Given run-task 启动失败 When open Then 连接被作废并向上抛出")
    void should_discard_and_throw_when_start_fails() {
        try (var pool =
                new ClientPool<AsrClient>(
                        "asr-test",
                        1,
                        100,
                        50,
                        () -> {
                            var client = new FakeAsrClient();
                            client.startFailure = new IllegalStateException("handshake");
                            clients.add(client);
                            return client;
                        },
                        AsrClient::close)) {
            // 调用 + 断言
            assertThatThrownBy(
                            () ->
                                    PooledAsrTask.open(
                                            pool, "zh-CN", new RecordingListener(), 500, 200))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(clients.get(0).closed).isTrue();
            assertThat(pool.snapshot().active()).isZero();
        }
    }
}
