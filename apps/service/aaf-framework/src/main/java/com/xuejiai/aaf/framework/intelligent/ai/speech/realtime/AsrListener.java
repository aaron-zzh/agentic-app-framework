package com.xuejiai.aaf.framework.intelligent.ai.speech.realtime;

/**
 * 实时 ASR 任务回调。回调线程是上游 SDK 的线程，实现方需自行切回自己的串行上下文。
 *
 * <p>同一任务内 {@link #onSentenceEnd} 与 {@link #onPartial} 按上游事件顺序到达。
 */
public interface AsrListener {

    /** 当前句识别中的完整文本（替换而非追加）。 */
    void onPartial(String text);

    /** 一句识别定稿。 */
    void onSentenceEnd(String text);

    /** 上游累计识别时长（秒，非增量），随事件到达；默认忽略。 */
    default void onUsage(int durationSecs) {}

    /** 上游识别失败。出错后该任务的连接不再复用。 */
    void onError(Throwable error);
}
