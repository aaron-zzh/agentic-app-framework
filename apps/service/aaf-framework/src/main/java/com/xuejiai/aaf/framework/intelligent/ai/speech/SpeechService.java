package com.xuejiai.aaf.framework.intelligent.ai.speech;

import reactor.core.publisher.Flux;

/** 语音服务接口（ASR + TTS），多厂商策略模式。 */
public interface SpeechService {

    /**
     * ASR 非流式：音频字节 → 完整文本（阻塞）。
     * 适合短音频（≤5min）一次性识别。
     */
    String transcribe(byte[] audioBytes, String language);

    /**
     * ASR 流式：音频字节流 → 实时识别文本流。
     * 每个元素为一个完整句子（isSentenceEnd=true 时输出）。
     * 适合长音频或实时麦克风输入场景。
     * 实现类必须覆盖此方法以提供真正的流式能力，否则抛出异常。
     */
    default Flux<String> transcribeStream(Flux<byte[]> audioStream, String language) {
        return Flux.error(new UnsupportedOperationException(
                "该实现不支持流式 ASR，请使用 transcribe() 或换用支持流式的实现"));
    }

    /** TTS 非流式：返回完整音频字节（短文本用） */
    byte[] synthesize(String text, String voice);

    /**
     * TTS 单向流式：文本一次性传入，音频分帧返回。
     * 适合短文本实时播放场景。
     */
    Flux<byte[]> synthesizeStream(String text, String voice);

    /**
     * TTS 双向流式：文本来自上游流（如 LLM 输出），音频分帧返回。
     * 适合 LLM 边生成边合成的实时场景。
     * 默认实现：将文本流合并后走单向流式。
     */
    default Flux<byte[]> synthesizeStream(Flux<String> textStream, String voice) {
        return textStream
                .collect(StringBuilder::new, StringBuilder::append)
                .flatMapMany(sb -> synthesizeStream(sb.toString(), voice));
    }
}
