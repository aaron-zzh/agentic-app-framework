package com.xuejiai.aaf.framework.intelligent.ai.speech;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;

import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.AsrListener;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.AsrTask;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.DashScopeAsrClient;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.DashScopeTtsClient;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.RealtimeSpeechGateway;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.SpeechPoolProperties;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.TtsListener;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.TtsTask;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * 基于阿里云 DashScope 官方 SDK 的语音服务实现。
 *
 * <p>ASR：Recognition SDK（fun-asr-realtime），WebSocket 流式
 *
 * <p>TTS：SpeechSynthesizer SDK（cosyvoice-v3-flash），WebSocket 流式
 *
 * <p>启用条件：配置 {@code spring.ai.dashscope.api-key}
 *
 * <p>推荐音色（cosyvoice-v3-flash）：
 *
 * <pre>
 * 场景          voice 参数              特质
 * ─────────────────────────────────────────────────────
 * 语音助手      longxiaochun_v3        知性积极女，25~30岁，支持SSML/时间戳
 * 社交陪伴(男)  longanyang             阳光大男孩，20~30岁，支持Instruct情感控制
 *               Instruct示例: "你说话的情感是happy。"
 *               Instruct示例: "你正在进行闲聊互动，你说话的情感是neutral。"
 * 社交陪伴(女)  longanhuan             欢脱元气女，20~30岁，支持Instruct情感控制
 * 客服          longyingling_v3        温和共情女，25~30岁，支持SSML/时间戳
 * 新闻播报      longshuo_v3            博才干练男，25~30岁，支持SSML/时间戳
 * ─────────────────────────────────────────────────────
 * 动态配置：voice 参数直接传入即可覆盖默认值；
 * 系统默认音色通过 aaf.speech.tts.voice 配置，或 ai_model_preference 表（capability=SPEECH_TTS）管理。
 * </pre>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
@EnableConfigurationProperties(SpeechPoolProperties.class)
public class DashScopeSpeechService implements SpeechService {

    private static final String DEFAULT_ASR_MODEL = "fun-asr-realtime";
    private static final String DEFAULT_TTS_MODEL = "cosyvoice-v3-flash";

    /** 默认音色：语音助手场景，知性积极女 */
    private static final String DEFAULT_VOICE = "longxiaochun_v3";

    private final String apiKey;

    /** 实时会话用的上游对象池（ASR/TTS 连接按任务复用）。 */
    private final RealtimeSpeechGateway realtime;

    @Value("${aaf.speech.tts.voice:" + DEFAULT_VOICE + "}")
    private String defaultVoice;

    @Value("${aaf.speech.asr.model:" + DEFAULT_ASR_MODEL + "}")
    private String asrModel;

    public DashScopeSpeechService(
            @Value("${spring.ai.dashscope.api-key:}") String apiKey,
            SpeechPoolProperties poolProperties) {
        this.apiKey = apiKey;
        poolProperties.validate(SpeechPoolProperties.connectionPoolSizeFromEnv());
        this.realtime =
                new RealtimeSpeechGateway(
                        poolProperties,
                        () -> new DashScopeAsrClient(language -> buildAsrParam(language, "pcm")),
                        () -> new DashScopeTtsClient(this::buildPcmTtsParam));
    }

    @PreDestroy
    void shutdownRealtimePools() {
        realtime.close();
    }

    // ========== 实时会话（对象池） ==========

    @Override
    public AsrTask openAsrTask(String language, AsrListener listener) {
        return realtime.openAsr(language, listener);
    }

    @Override
    public TtsTask startTtsTask(String text, String voice, TtsListener listener) {
        return realtime.startTts(cleanText(text), voice, listener);
    }

    // ========== ASR ==========

    /** ASR 非流式：将音频字节写入临时文件，调用 Recognition.call(param, File) 阻塞识别。 适合短音频（≤5min）。 */
    @Override
    public String transcribe(byte[] audioBytes, String language) {
        File tmpFile = null;
        Recognition recognizer = new Recognition();
        try {
            tmpFile = File.createTempFile("aaf-asr-", ".wav");
            try (var fos = new FileOutputStream(tmpFile)) {
                fos.write(audioBytes);
            }
            var param = buildAsrParam(language, "wav");
            String result = recognizer.call(param, tmpFile);
            log.debug("ASR 识别完成: length={}", result != null ? result.length() : 0);
            return result != null ? result : "";
        } catch (Exception e) {
            log.error("ASR 识别失败", e);
            throw new RuntimeException("语音识别失败: " + e.getMessage(), e);
        } finally {
            recognizer.getDuplexApi().close(1000, "done");
            if (tmpFile != null) tmpFile.delete();
        }
    }

    /**
     * ASR 流式：音频字节流 → 实时识别帧流，识别连接取自对象池，任务结束后归还复用。
     *
     * <p>对外行为与改造前一致：只推送定稿句子（{@link AsrResult#ofText}）和计费用量帧（{@link AsrResult#ofUsage}，毫秒）。
     *
     * <ul>
     *   <li>sentenceEnd 之前到达的 usage 随句子立即外推，使 handler 在 WebSocket 关闭前就拿到最新真实 duration
     *   <li>音频流结束后发 finish-task 等 task-finished，再补推一次最终累计值后 complete
     * </ul>
     *
     * <p>上层 handler 对 usage 帧采用"取 max + 关闭时一次结算"，多次推送不会重复扣费。DashScope 的 duration 单位是秒，这里乘 1000 转毫秒。
     *
     * @throws com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.SpeechPoolExhaustedException
     *     对象池耗尽时经 Flux 的 error 信号抛出
     */
    @Override
    public Flux<AsrResult> transcribeStream(Flux<byte[]> audioStream, String language) {
        return Flux.create(
                sink -> {
                    var latestSecs = new AtomicInteger();
                    var task =
                            openAsrTask(
                                    language,
                                    new AsrListener() {
                                        @Override
                                        public void onPartial(String text) {
                                            // 对外只推定稿句子，与改造前一致
                                        }

                                        @Override
                                        public void onSentenceEnd(String text) {
                                            if (text != null && !text.isBlank()) {
                                                sink.next(AsrResult.ofText(text));
                                            }
                                            emitUsageIfAny(sink, latestSecs.get());
                                        }

                                        @Override
                                        public void onUsage(int durationSecs) {
                                            latestSecs.accumulateAndGet(durationSecs, Math::max);
                                        }

                                        @Override
                                        public void onError(Throwable error) {
                                            log.error("ASR 流式识别失败", error);
                                            emitUsageIfAny(sink, latestSecs.get());
                                            sink.error(error);
                                        }
                                    });
                    var subscription =
                            audioStream.subscribe(
                                    task::sendAudio,
                                    error -> {
                                        Thread.startVirtualThread(task::abort);
                                        sink.error(error);
                                    },
                                    () ->
                                            Thread.startVirtualThread(
                                                    () -> {
                                                        var seconds = task.finish();
                                                        emitUsageIfAny(
                                                                sink,
                                                                Math.max(
                                                                        seconds, latestSecs.get()));
                                                        sink.complete();
                                                    }));
                    // 下游取消或终止时：停止读音频，并放弃仍在进行的任务（已结束则为空操作）
                    sink.onDispose(
                            () -> {
                                subscription.dispose();
                                Thread.startVirtualThread(task::abort);
                            });
                },
                FluxSink.OverflowStrategy.BUFFER);
    }

    /** 累计 duration > 0 时向 sink 推送一帧计费用量。 */
    private static void emitUsageIfAny(FluxSink<AsrResult> sink, int durationSecs) {
        if (durationSecs > 0) {
            sink.next(AsrResult.ofUsage(durationSecs * 1000));
        }
    }

    // ========== TTS ==========

    /** TTS 非流式：阻塞等待完整音频返回，携带字符数用量。 */
    @Override
    public SynthesisResult synthesize(AiModel model, String text, String voice) {
        text = cleanText(text);
        int charCount = text.length();
        String modelName =
                (model != null && StringUtils.hasText(model.getModelName()))
                        ? model.getModelName()
                        : DEFAULT_TTS_MODEL;
        var param = buildTtsParam(modelName, voice);
        var synthesizer = new SpeechSynthesizer(param, null);
        try {
            var buf = synthesizer.call(text);
            byte[] audio = buf != null ? buf.array() : new byte[0];
            return new SynthesisResult(audio, charCount);
        } catch (Exception e) {
            log.error("TTS 合成失败: model={}, voice={}", modelName, voice, e);
            throw new RuntimeException("语音合成失败: " + e.getMessage(), e);
        } finally {
            synthesizer.getDuplexApi().close(1000, "done");
        }
    }

    /** TTS 单向流式：callAsFlowable，文本一次性传入，音频分帧推送。 */
    @Override
    public Flux<byte[]> synthesizeStream(String text, String voice) {
        final String cleanedText = cleanText(text);
        return Flux.create(
                sink -> {
                    var synthesizer = new SpeechSynthesizer(buildTtsParam(null, voice), null);
                    try {
                        synthesizer
                                .callAsFlowable(cleanedText)
                                .blockingForEach(
                                        result -> {
                                            var frame = result.getAudioFrame();
                                            if (frame != null) sink.next(frame.array());
                                        });
                        sink.complete();
                    } catch (Exception e) {
                        log.error("TTS 流式合成失败: voice={}", voice, e);
                        sink.error(e);
                    } finally {
                        synthesizer.getDuplexApi().close(1000, "done");
                    }
                },
                FluxSink.OverflowStrategy.BUFFER);
    }

    /** TTS 双向流式：streamingCallAsFlowable，文本来自上游 Flux（如 LLM 输出）。 */
    @Override
    public Flux<byte[]> synthesizeStream(Flux<String> textStream, String voice) {
        return Flux.create(
                sink -> {
                    var synthesizer = new SpeechSynthesizer(buildTtsParam(null, voice), null);
                    try {
                        io.reactivex.Flowable<String> textFlowable =
                                io.reactivex.Flowable.create(
                                        (io.reactivex.FlowableEmitter<String> emitter) ->
                                                textStream.subscribe(
                                                        emitter::onNext,
                                                        emitter::onError,
                                                        emitter::onComplete),
                                        io.reactivex.BackpressureStrategy.BUFFER);

                        synthesizer
                                .streamingCallAsFlowable(textFlowable)
                                .blockingForEach(
                                        result -> {
                                            var frame = result.getAudioFrame();
                                            if (frame != null) sink.next(frame.array());
                                        });
                        sink.complete();
                    } catch (Exception e) {
                        log.error("TTS 双向流式合成失败: voice={}", voice, e);
                        sink.error(e);
                    } finally {
                        synthesizer.getDuplexApi().close(1000, "done");
                    }
                },
                FluxSink.OverflowStrategy.BUFFER);
    }

    // ========== 内部方法 ==========

    private RecognitionParam buildAsrParam(String language, String format) {
        var builder =
                RecognitionParam.builder()
                        .apiKey(apiKey)
                        .model(asrModel)
                        .format(format)
                        .sampleRate(16000);
        // language_hints: fun-asr-realtime 仅支持 zh/en/ja，需将 zh-CN/en-US 等区域码归一化为主语言码
        String hint = normalizeLanguageHint(language);
        if (hint != null) {
            builder.parameter("language_hints", new String[] {hint});
        }
        return builder.build();
    }

    /** 将 BCP-47 区域码（如 zh-CN、en-US）归一化为 fun-asr-realtime 支持的主语言码（zh/en/ja）。 */
    private String normalizeLanguageHint(String language) {
        if (!StringUtils.hasText(language)) {
            return null;
        }
        String primary = language.trim().toLowerCase().split("[-_]")[0];
        return switch (primary) {
            case "zh", "en", "ja" -> primary;
            default -> null; // 不支持的语种交给模型自动识别
        };
    }

    private SpeechSynthesisParam buildTtsParam(String modelName, String voice) {
        String v = StringUtils.hasText(voice) ? voice : defaultVoice;
        String m = StringUtils.hasText(modelName) ? modelName : DEFAULT_TTS_MODEL;
        return SpeechSynthesisParam.builder().apiKey(apiKey).model(m).voice(v).build();
    }

    /** 实时会话用的合成参数：固定输出 PCM16 16kHz 单声道，设备不再解码 MP3。 */
    private SpeechSynthesisParam buildPcmTtsParam(String voice) {
        String v = StringUtils.hasText(voice) ? voice : defaultVoice;
        return SpeechSynthesisParam.builder()
                .apiKey(apiKey)
                .model(DEFAULT_TTS_MODEL)
                .voice(v)
                .format(SpeechSynthesisAudioFormat.PCM_16000HZ_MONO_16BIT)
                .build();
    }

    /** 清理 TTS 输入文本：去除不可打印控制字符、合并多余空白，保留换行作为句间停顿 */
    private static String cleanText(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "") // 去除控制字符，保留 \t\n\r
                .replaceAll("[ \\t]+", " ") // 多个空格/tab 合并
                .replaceAll("(\\r?\\n){3,}", "\n\n") // 连续空行合并
                .strip();
    }
}
