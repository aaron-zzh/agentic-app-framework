package com.xuejiai.aaf.framework.intelligent.ai.speech;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;

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
public class DashScopeSpeechService implements SpeechService {

    private static final String DEFAULT_ASR_MODEL = "fun-asr-realtime";
    private static final String DEFAULT_TTS_MODEL = "cosyvoice-v3-flash";

    /** 默认音色：语音助手场景，知性积极女 */
    private static final String DEFAULT_VOICE = "longxiaochun_v3";

    private final String apiKey;

    @Value("${aaf.speech.tts.voice:" + DEFAULT_VOICE + "}")
    private String defaultVoice;

    @Value("${aaf.speech.asr.model:" + DEFAULT_ASR_MODEL + "}")
    private String asrModel;

    public DashScopeSpeechService(@Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.apiKey = apiKey;
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

    /** ASR 流式：音频字节流 → 实时句子流。 使用 Recognition.streamCall(param, Flowable<ByteBuffer>)，每个完整句子推送一次。 */
    @Override
    public Flux<String> transcribeStream(Flux<byte[]> audioStream, String language) {
        return Flux.create(
                sink -> {
                    Recognition recognizer = new Recognition();
                    try {
                        var param = buildAsrParam(language, "pcm");
                        // 将 Reactor Flux<byte[]> 转为 RxJava Flowable<ByteBuffer>
                        io.reactivex.Flowable<ByteBuffer> audioFlowable =
                                io.reactivex.Flowable.create(
                                        (io.reactivex.FlowableEmitter<ByteBuffer> emitter) ->
                                                audioStream.subscribe(
                                                        chunk ->
                                                                emitter.onNext(
                                                                        ByteBuffer.wrap(chunk)),
                                                        emitter::onError,
                                                        emitter::onComplete),
                                        io.reactivex.BackpressureStrategy.BUFFER);

                        recognizer
                                .streamCall(param, audioFlowable)
                                .blockingForEach(
                                        result -> {
                                            if (result.isSentenceEnd()) {
                                                String text = result.getSentence().getText();
                                                if (text != null && !text.isBlank()) {
                                                    sink.next(text);
                                                }
                                            }
                                        });
                        sink.complete();
                    } catch (Exception e) {
                        log.error("ASR 流式识别失败", e);
                        sink.error(e);
                    } finally {
                        recognizer.getDuplexApi().close(1000, "done");
                    }
                },
                FluxSink.OverflowStrategy.BUFFER);
    }

    // ========== TTS ==========

    /** TTS 非流式：阻塞等待完整音频返回。 */
    @Override
    public byte[] synthesize(String text, String voice) {
        text = cleanText(text);
        var param = buildTtsParam(voice);
        var synthesizer = new SpeechSynthesizer(param, null);
        try {
            var buf = synthesizer.call(text);
            return buf != null ? buf.array() : new byte[0];
        } catch (Exception e) {
            log.error("TTS 合成失败: voice={}", voice, e);
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
                    var synthesizer = new SpeechSynthesizer(buildTtsParam(voice), null);
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
                    var synthesizer = new SpeechSynthesizer(buildTtsParam(voice), null);
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

    private SpeechSynthesisParam buildTtsParam(String voice) {
        String v = StringUtils.hasText(voice) ? voice : defaultVoice;
        return SpeechSynthesisParam.builder()
                .apiKey(apiKey)
                .model(DEFAULT_TTS_MODEL)
                .voice(v)
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
