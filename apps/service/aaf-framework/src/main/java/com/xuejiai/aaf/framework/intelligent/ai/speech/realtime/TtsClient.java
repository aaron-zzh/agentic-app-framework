package com.xuejiai.aaf.framework.intelligent.ai.speech.realtime;

/** 池化的上游合成连接抽象，与 {@link AsrClient} 对称。一个实例对应一个可复用的 {@code SpeechSynthesizer}，输出 PCM16 16kHz 单声道。 */
public interface TtsClient {

    /** 发送 run-task 合成一段文本（新 task_id），音频与结束事件经回调到达。可能阻塞到任务 started。 */
    void start(String text, String voice, Callback callback);

    /** 向上游发送取消指令，随后上游应返回 task-finished。 */
    void cancel();

    /** 强制关闭底层连接，该实例不再复用。 */
    void close();

    /** 上游事件回调，运行在 SDK 线程。 */
    interface Callback {

        void onAudio(byte[] pcm);

        void onComplete();

        void onError(Exception error);
    }
}
