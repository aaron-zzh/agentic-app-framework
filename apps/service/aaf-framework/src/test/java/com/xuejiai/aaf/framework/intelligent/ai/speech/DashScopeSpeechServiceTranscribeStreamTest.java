package com.xuejiai.aaf.framework.intelligent.ai.speech;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.AsrListener;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.AsrTask;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.SpeechPoolExhaustedException;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.SpeechPoolProperties;

import reactor.core.publisher.Sinks;

/** 验证 {@code transcribeStream} 在对象池之上保持原有的对外行为：只推定稿句子与计费用量帧。 */
class DashScopeSpeechServiceTranscribeStreamTest {

    /** 不连接上游：只覆写任务入口的 DashScope 服务。 */
    static final class TestableService extends DashScopeSpeechService {
        AsrTask next;
        RuntimeException failure;
        AsrListener listener;

        TestableService() {
            super("test-key", new SpeechPoolProperties(4, 4, 100, 1000, 500, 50));
        }

        @Override
        public AsrTask openAsrTask(String language, AsrListener listener) {
            if (failure != null) {
                throw failure;
            }
            this.listener = listener;
            return next;
        }
    }

    static final class FakeTask implements AsrTask {
        final List<byte[]> audio = new CopyOnWriteArrayList<>();
        final AtomicBoolean finished = new AtomicBoolean();
        final CountDownLatch aborted = new CountDownLatch(1);
        volatile Runnable onFinish = () -> {};
        volatile int seconds = 3;

        @Override
        public void sendAudio(byte[] pcm) {
            audio.add(pcm);
        }

        @Override
        public int finish() {
            finished.set(true);
            onFinish.run();
            return seconds;
        }

        @Override
        public void abort() {
            aborted.countDown();
        }
    }

    private final TestableService service = new TestableService();

    @AfterEach
    void tearDown() {
        service.shutdownRealtimePools();
    }

    @Test
    @DisplayName("Given 音频流 When 识别出两句 Then 只推定稿句子与用量帧，音频结束后 finish 并补推最终用量")
    void should_emit_only_sentences_and_usage_when_streaming() {
        // 准备参数
        var task = new FakeTask();
        service.next = task;
        var audio = Sinks.many().unicast().<byte[]>onBackpressureBuffer();
        var results = new ArrayList<AsrResult>();
        var done = new CountDownLatch(1);

        // 调用
        service.transcribeStream(audio.asFlux(), "zh-CN")
                .subscribe(results::add, error -> done.countDown(), done::countDown);
        audio.tryEmitNext(new byte[] {1, 2, 3});
        service.listener.onPartial("你");
        service.listener.onUsage(1);
        service.listener.onSentenceEnd("你好。");
        task.onFinish =
                () -> {
                    service.listener.onUsage(3);
                    service.listener.onSentenceEnd("再见。");
                };
        audio.tryEmitComplete();

        // 断言
        awaitDone(done);
        assertThat(task.audio).hasSize(1);
        assertThat(task.finished).isTrue();
        assertThat(results.stream().filter(r -> !r.hasUsage()).map(AsrResult::text).toList())
                .containsExactly("你好。", "再见。");
        var usageSeconds =
                results.stream()
                        .filter(AsrResult::hasUsage)
                        .map(r -> r.usage().standardUsage().get("duration"))
                        .toList();
        assertThat(usageSeconds).isNotEmpty().last().isEqualTo(3);
    }

    @Test
    @DisplayName("Given 对象池耗尽 When 订阅 Then Flux 以池耗尽异常结束")
    void should_error_when_pool_exhausted() {
        // 准备参数
        service.failure = new SpeechPoolExhaustedException("full", null);

        // 调用 + 断言
        var error =
                service.transcribeStream(
                                Sinks.many().unicast().<byte[]>onBackpressureBuffer().asFlux(),
                                "zh-CN")
                        .map(AsrResult::text)
                        .materialize()
                        .blockLast(Duration.ofSeconds(2));
        assertThat(error).isNotNull();
        assertThat(error.getThrowable()).isInstanceOf(SpeechPoolExhaustedException.class);
    }

    @Test
    @DisplayName("Given 下游取消订阅 When 识别进行中 Then 放弃上游任务并归还/作废连接")
    void should_abort_task_when_downstream_cancels() throws Exception {
        // 准备参数
        var task = new FakeTask();
        service.next = task;
        var audio = Sinks.many().unicast().<byte[]>onBackpressureBuffer();
        var subscription = service.transcribeStream(audio.asFlux(), "zh-CN").subscribe();

        // 调用
        subscription.dispose();

        // 断言
        assertThat(task.aborted.await(2, TimeUnit.SECONDS)).isTrue();
    }

    private static void awaitDone(CountDownLatch done) {
        try {
            assertThat(done.await(3, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
