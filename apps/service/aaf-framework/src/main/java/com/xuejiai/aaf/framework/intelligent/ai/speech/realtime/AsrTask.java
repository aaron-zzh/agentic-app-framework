package com.xuejiai.aaf.framework.intelligent.ai.speech.realtime;

/**
 * 一个上游实时识别任务，对应一次借出的池化连接。
 *
 * <p>生命周期：{@code SpeechService.openAsrTask} 借出并发送 run-task → 多次 {@link #sendAudio} → {@link
 * #finish} 或 {@link #abort} 结束并归还/作废连接。{@link #finish} 与 {@link #abort} 互斥且幂等，只有第一次生效。
 */
public interface AsrTask {

    /** 发送一段 PCM16 16kHz 单声道音频。任务已结束或已失败时静默丢弃。 */
    void sendAudio(byte[] pcm);

    /**
     * 结束音频输入（finish-task）并等待上游 task-finished；超时或失败则关闭连接不再复用。阻塞调用，应在虚拟线程上调用。
     *
     * @return 上游累计识别时长（秒）；未取得则为 0
     */
    int finish();

    /** 放弃任务：与 {@link #finish} 相同地等待结束事件，但使用更短的超时，且不回调监听器。阻塞调用。 */
    void abort();
}
