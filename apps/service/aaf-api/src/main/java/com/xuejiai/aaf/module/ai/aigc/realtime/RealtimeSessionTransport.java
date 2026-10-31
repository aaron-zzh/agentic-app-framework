package com.xuejiai.aaf.module.ai.aigc.realtime;

/** 实时会话对传输层的最小输出端口。 */
public interface RealtimeSessionTransport {

    boolean isOpen();

    void sendEvent(RealtimeServerEvent event);

    void sendAudio(byte[] pcm24Le);

    void close(RealtimeCloseReason reason);
}
