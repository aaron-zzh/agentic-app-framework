package com.xuejiai.aaf.framework.intelligent.ai.speech.realtime;

/** 实时 TTS 任务回调。回调线程是上游 SDK 的线程，实现方需自行切回自己的串行上下文。 */
public interface TtsListener {

    /** 一段 PCM16 16kHz 单声道音频。 */
    void onAudio(byte[] pcm);

    /** 合成完成（上游 task-finished）。取消后不再回调。 */
    void onComplete();

    /** 合成失败。取消后不再回调。 */
    void onError(Throwable error);
}
