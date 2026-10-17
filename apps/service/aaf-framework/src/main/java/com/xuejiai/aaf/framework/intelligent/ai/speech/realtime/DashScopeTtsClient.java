package com.xuejiai.aaf.framework.intelligent.ai.speech.realtime;

import java.util.function.Function;

import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;

/**
 * 基于 DashScope {@link SpeechSynthesizer} 的 {@link TtsClient}。
 *
 * <p>复用同一个 SDK 对象：每个任务通过 {@code updateParamAndCallback} 换入新的参数与回调，再 {@code call(text)} 发送新的
 * run-task。
 */
public final class DashScopeTtsClient implements TtsClient {

    private final SpeechSynthesizer synthesizer = new SpeechSynthesizer();
    private final Function<String, SpeechSynthesisParam> paramFactory;

    /**
     * @param paramFactory 音色 → 合成参数（PCM 16kHz），由服务统一构建
     */
    public DashScopeTtsClient(Function<String, SpeechSynthesisParam> paramFactory) {
        this.paramFactory = paramFactory;
    }

    @Override
    public void start(String text, String voice, Callback callback) {
        synthesizer.updateParamAndCallback(
                paramFactory.apply(voice),
                new ResultCallback<SpeechSynthesisResult>() {
                    @Override
                    public void onEvent(SpeechSynthesisResult result) {
                        var frame = result.getAudioFrame();
                        if (frame == null || !frame.hasRemaining()) {
                            return;
                        }
                        var pcm = new byte[frame.remaining()];
                        frame.duplicate().get(pcm);
                        callback.onAudio(pcm);
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
        synthesizer.call(text);
    }

    @Override
    public void cancel() {
        synthesizer.streamingCancel();
    }

    @Override
    public void close() {
        synthesizer.getDuplexApi().close(1000, "bye");
    }
}
