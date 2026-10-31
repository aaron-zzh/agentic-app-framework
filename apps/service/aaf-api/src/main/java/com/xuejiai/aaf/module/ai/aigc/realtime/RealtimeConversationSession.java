package com.xuejiai.aaf.module.ai.aigc.realtime;

/** Omni Realtime v1 的单连接会话核心。 */
public interface RealtimeConversationSession extends AutoCloseable {

    void receiveControl(RealtimeClientControl control);

    void receiveAudio(byte[] pcm16Le);

    void cancelResponse();

    @Override
    void close();
}
