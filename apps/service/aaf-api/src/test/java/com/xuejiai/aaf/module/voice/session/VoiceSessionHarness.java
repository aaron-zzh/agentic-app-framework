package com.xuejiai.aaf.module.voice.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SynthesisResult;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.AsrListener;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.AsrTask;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.TtsListener;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.TtsTask;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.module.voice.api.VoiceClient;
import com.xuejiai.aaf.module.voice.config.VoiceSessionProperties;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/** 语音会话测试夹具：全部执行器在调用线程上同步运行（但保持串行语义），上游语音服务、客户端端口、通道与定时器都是可编排的假实现，让整条话轮流水可以确定性地驱动。 */
final class VoiceSessionHarness {

    final RecordingChannel channel = new RecordingChannel();
    final FakeSpeech speech = new FakeSpeech();
    final FakeClient client = new FakeClient();
    final ManualTimer timer = new ManualTimer();
    final AtomicLong clock = new AtomicLong(1_000);
    final VoiceSession session;

    VoiceSessionHarness() {
        this(VoiceSessionProperties.defaults());
    }

    VoiceSessionHarness(VoiceSessionProperties props) {
        var deps = new VoiceSession.Deps(speech, props, Runnable::run, timer, clock::get);
        session =
                new VoiceSession("ws-1", client, channel, new SerialExecutor(Runnable::run), deps);
        session.onOpen();
    }

    // ---- 驱动 ----

    void hello(String extra) {
        send(
                """
                {"type":"hello","proto":3,"audioIn":{"codec":"pcm_s16le","rate":16000,"channels":1},\
                "audioOut":{"codecs":["pcm_s16le"],"rate":16000},"lang":"zh-CN"%s}"""
                        .formatted(extra));
    }

    void send(String json) {
        session.onText(json);
    }

    void mic(int turn, int bytes) {
        var payload = new byte[bytes];
        var buffer = ByteBuffer.allocate(5 + bytes);
        buffer.put((byte) 0x01).putInt(turn).put(payload).flip();
        session.onBinary(buffer);
    }

    void turnStart(int turn, String reply) {
        send(
                """
                {"type":"turn_start","turn":%d,"lang":"zh-CN","reply":"%s","tts":true,\
                "followUp":false,"aec":true,"preRollMs":600}"""
                        .formatted(turn, reply));
    }

    // ---- 观察 ----

    List<Map<String, Object>> messages(String type) {
        return channel.texts.stream().filter(m -> type.equals(m.get("type"))).toList();
    }

    Map<String, Object> only(String type) {
        var found = messages(type);
        assertThat(found).as("消息 %s", type).hasSize(1);
        return found.get(0);
    }

    List<String> types() {
        return channel.texts.stream().map(m -> (String) m.get("type")).toList();
    }

    // ---- 假实现 ----

    static final class RecordingChannel implements VoiceSession.Channel {
        final List<Map<String, Object>> texts = new ArrayList<>();
        final List<byte[]> binaries = new ArrayList<>();
        int closeCode = -1;

        @Override
        @SuppressWarnings("unchecked")
        public void sendText(String json) {
            texts.add(JsonUtils.parseObject(json, Map.class));
        }

        @Override
        public void sendBinary(byte[] frame) {
            binaries.add(frame);
        }

        @Override
        public void close(int code, String reason) {
            closeCode = code;
        }

        /** 二进制帧的 sid（头 5 字节后的大端 u32）。 */
        static int sidOf(byte[] frame) {
            return ByteBuffer.wrap(frame, 1, 4).getInt();
        }
    }

    static final class ManualTimer implements VoiceSession.Timer {
        record Scheduled(long delayMs, Runnable task, AtomicBoolean cancelled) {}

        final List<Scheduled> scheduled = new ArrayList<>();

        @Override
        public Runnable schedule(long delayMs, Runnable task) {
            var entry = new Scheduled(delayMs, task, new AtomicBoolean());
            scheduled.add(entry);
            return () -> entry.cancelled().set(true);
        }

        /** 触发指定延迟的全部未取消任务。 */
        void fire(long delayMs) {
            for (var entry : List.copyOf(scheduled)) {
                if (entry.delayMs() == delayMs && !entry.cancelled().getAndSet(true)) {
                    entry.task().run();
                }
            }
        }
    }

    static final class FakeAsr implements AsrTask {
        final AsrListener listener;
        final List<byte[]> sent = new ArrayList<>();

        /** finish() 时上游补发的最后一句。 */
        String tailSentence;

        int seconds = 2;
        boolean finished;
        boolean aborted;

        FakeAsr(AsrListener listener) {
            this.listener = listener;
        }

        @Override
        public void sendAudio(byte[] pcm) {
            sent.add(pcm);
        }

        @Override
        public int finish() {
            finished = true;
            if (tailSentence != null) {
                listener.onSentenceEnd(tailSentence);
            }
            return seconds;
        }

        @Override
        public void abort() {
            aborted = true;
        }
    }

    static final class FakeTts implements TtsTask {
        final String text;
        final TtsListener listener;
        boolean cancelled;

        FakeTts(String text, TtsListener listener) {
            this.text = text;
            this.listener = listener;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        void audio(int bytes) {
            listener.onAudio(new byte[bytes]);
        }

        void complete() {
            listener.onComplete();
        }
    }

    static final class FakeSpeech implements SpeechService {
        final List<FakeAsr> asrTasks = new ArrayList<>();
        final List<FakeTts> ttsTasks = new ArrayList<>();
        RuntimeException asrOpenFailure;

        @Override
        public AsrTask openAsrTask(String language, AsrListener listener) {
            if (asrOpenFailure != null) {
                throw asrOpenFailure;
            }
            var task = new FakeAsr(listener);
            asrTasks.add(task);
            return task;
        }

        @Override
        public TtsTask startTtsTask(String text, String voice, TtsListener listener) {
            var task = new FakeTts(text, listener);
            ttsTasks.add(task);
            return task;
        }

        FakeAsr lastAsr() {
            return asrTasks.get(asrTasks.size() - 1);
        }

        @Override
        public String transcribe(byte[] audioBytes, String language) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SynthesisResult synthesize(AiModel model, String text, String voice) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flux<byte[]> synthesizeStream(String text, String voice) {
            throw new UnsupportedOperationException();
        }
    }

    static final class FakeClient implements VoiceClient {
        RuntimeException precheckFailure;
        final List<String> replyRequests = new ArrayList<>();
        Sinks.Many<String> replySink;
        boolean replyCancelledByDownstream;
        final List<Integer> asrSettlements = new ArrayList<>();
        final List<String> ttsSettlements = new ArrayList<>();
        final List<String> cancellations = new ArrayList<>();
        boolean closed;

        @Override
        public String clientKey() {
            return "device:1";
        }

        @Override
        public void precheck() {
            if (precheckFailure != null) {
                throw precheckFailure;
            }
        }

        @Override
        public Flux<String> reply(String text) {
            replyRequests.add(text);
            replySink = Sinks.many().unicast().onBackpressureBuffer();
            return replySink.asFlux().doOnCancel(() -> replyCancelledByDownstream = true);
        }

        @Override
        public void settleAsr(int durationMs) {
            asrSettlements.add(durationMs);
        }

        @Override
        public void settleTts(String spokenText) {
            ttsSettlements.add(spokenText);
        }

        @Override
        public void onReplyCancelled(String reason, String replyText, int heardSentences) {
            cancellations.add(reason + ":" + heardSentences + ":" + replyText);
        }

        @Override
        public void onClose() {
            closed = true;
        }
    }
}
