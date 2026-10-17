package com.xuejiai.aaf.framework.intelligent.ai.speech.realtime;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.common.ResultCallback;

/** 基于 DashScope {@link Recognition} 的 {@link AsrClient}，一个实例对应一个可复用的 SDK 对象。 */
public final class DashScopeAsrClient implements AsrClient {

    private final Recognition recognition = new Recognition();
    private final Function<String, RecognitionParam> paramFactory;

    /**
     * @param paramFactory 语言 → 识别参数（PCM 16kHz），由服务统一构建以保持与既有 ASR 参数一致
     */
    public DashScopeAsrClient(Function<String, RecognitionParam> paramFactory) {
        this.paramFactory = paramFactory;
    }

    @Override
    public void start(String language, Callback callback) {
        recognition.call(
                paramFactory.apply(language),
                new ResultCallback<RecognitionResult>() {
                    @Override
                    public void onEvent(RecognitionResult result) {
                        var usage = result.getUsage();
                        if (usage != null && usage.getDuration() != null) {
                            callback.onUsage(usage.getDuration());
                        }
                        var sentence = result.getSentence();
                        if (sentence == null || sentence.getText() == null) {
                            return;
                        }
                        if (result.isSentenceEnd()) {
                            callback.onSentenceEnd(sentence.getText());
                        } else {
                            callback.onPartial(sentence.getText());
                        }
                    }

                    @Override
                    public void onComplete() {
                        callback.onComplete();
                    }

                    @Override
                    public void onError(Exception error) {
                        callback.onError(error);
                    }
                });
    }

    @Override
    public void send(byte[] pcm) {
        recognition.sendAudioFrame(ByteBuffer.wrap(pcm));
    }

    @Override
    public void stop() {
        recognition.stop();
    }

    @Override
    public void close() {
        recognition.getDuplexApi().close(1000, "bye");
    }
}
