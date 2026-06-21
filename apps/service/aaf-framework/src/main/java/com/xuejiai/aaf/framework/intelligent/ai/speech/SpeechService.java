package com.xuejiai.aaf.framework.intelligent.ai.speech;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
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
