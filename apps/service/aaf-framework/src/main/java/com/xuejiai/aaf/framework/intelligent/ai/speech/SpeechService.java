package com.xuejiai.aaf.framework.intelligent.ai.speech;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.AsrListener;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.AsrTask;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.TtsListener;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.TtsTask;
import com.xuejiai.aaf.framework.intelligent.core.AiCapability;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;

import reactor.core.publisher.Flux;

/** 语音服务接口（ASR + TTS），多厂商策略模式。 */
public interface SpeechService extends AiCapability {

    @Override
    default String capability() {
        return CapabilityRoutingContext.CAP_SPEECH_TTS;
    }

    @Override
    default String bizName() {
        return "语音合成";
    }

    /** 按字符数精确预估积分（TOKEN 计费，inputPricePerK × charCount / 1000）。 */
    @Override
    default long estimateCost(AiModel model, Object req, int markupRate) {
        if (model == null || model.getInputPricePerK() == null) return 0;
        int charCount = req instanceof String s ? s.length() : 0;
        return Math.max(
                1,
                Math.round(
                        model.getInputPricePerK().doubleValue()
                                * charCount
                                / 1000.0
                                * AiCreditGuard.YUAN_TO_CREDIT
                                * markupRate));
    }

    /** ASR 非流式：音频字节 → 完整文本（阻塞）。 适合短音频（≤5min）一次性识别。 */
    String transcribe(byte[] audioBytes, String language);

    /**
     * ASR 流式：音频字节流 → 实时识别帧流（{@link AsrResult}）。
     *
     * <p>普通帧携带识别文本；最后一帧（isCompleteResult）{@code usage} 非 null，携带本次识别总时长（毫秒），供调用方结算积分。
     */
    default Flux<AsrResult> transcribeStream(Flux<byte[]> audioStream, String language) {
        return Flux.error(
                new UnsupportedOperationException("该实现不支持流式 ASR，请使用 transcribe() 或换用支持流式的实现"));
    }

    /**
     * 实时 ASR 任务：借出池化连接并开始一个识别任务（PCM16 16kHz 单声道输入）。
     *
     * <p>与 {@link #transcribeStream} 的区别：任务由调用方按话轮显式开始/结束，连接在任务间复用，适合常驻会话。
     *
     * @throws com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.SpeechPoolExhaustedException
     *     对象池在借出超时内没有空闲对象
     */
    default AsrTask openAsrTask(String language, AsrListener listener) {
        throw new UnsupportedOperationException("该实现不支持实时 ASR 任务");
    }

    /**
     * 实时 TTS 任务：借出池化连接合成一段文本，输出 PCM16 {@value #REALTIME_TTS_SAMPLE_RATE}Hz 单声道。
     *
     * @throws com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.SpeechPoolExhaustedException
     *     对象池在借出超时内没有空闲对象
     */
    default TtsTask startTtsTask(String text, String voice, TtsListener listener) {
        throw new UnsupportedOperationException("该实现不支持实时 TTS 任务");
    }

    /** 实时 TTS 输出采样率（Hz）。 */
    int REALTIME_TTS_SAMPLE_RATE = 16_000;

    /** TTS 非流式：返回合成结果（含音频字节和计费用量） */
    SynthesisResult synthesize(AiModel model, String text, String voice);

    /** TTS 单向流式：文本一次性传入，音频分帧返回。 适合短文本实时播放场景。 */
    Flux<byte[]> synthesizeStream(String text, String voice);

    /** TTS 双向流式：文本来自上游流（如 LLM 输出），音频分帧返回。 适合 LLM 边生成边合成的实时场景。 默认实现：将文本流合并后走单向流式。 */
    default Flux<byte[]> synthesizeStream(Flux<String> textStream, String voice) {
        return textStream
                .collect(StringBuilder::new, StringBuilder::append)
                .flatMapMany(sb -> synthesizeStream(sb.toString(), voice));
    }
}
