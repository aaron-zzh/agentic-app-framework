package com.xuejiai.aaf.framework.intelligent.ai.speech.realtime;

/**
 * 池化的上游识别连接抽象，把 DashScope SDK 隔离在一个很薄的适配层之后，便于对池与任务生命周期做单测。
 *
 * <p>一个实例对应一个可复用的 {@code Recognition}；同一实例在任务间复用，任务内不并发。
 */
public interface AsrClient {

    /** 发送 run-task 开启新任务（新 task_id）。可能阻塞到任务 started。 */
    void start(String language, Callback callback);

    /** 发送一段音频。 */
    void send(byte[] pcm);

    /** 发送 finish-task 并阻塞到 task-finished。 */
    void stop();

    /** 强制关闭底层连接，该实例不再复用。 */
    void close();

    /** 上游事件回调，运行在 SDK 线程。 */
    interface Callback {

        void onPartial(String text);

        void onSentenceEnd(String text);

        /** 上游累计识别时长（秒），随事件到达，取最大值即总用量。 */
        void onUsage(int durationSecs);

        void onComplete();

        void onError(Exception error);
    }
}
