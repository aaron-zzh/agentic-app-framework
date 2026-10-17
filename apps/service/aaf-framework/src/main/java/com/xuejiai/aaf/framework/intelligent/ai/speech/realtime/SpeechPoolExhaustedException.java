package com.xuejiai.aaf.framework.intelligent.ai.speech.realtime;

/** 上游语音对象池在借出超时内没有空闲对象。调用方应快速失败并让客户端回退，而不是阻塞会话线程。 */
public class SpeechPoolExhaustedException extends RuntimeException {

    public SpeechPoolExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
