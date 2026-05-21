package com.xuejiai.aaf.framework.intelligent.ai.speech;

/** 语音服务接口（ASR + TTS），多厂商策略模式。 */
public interface SpeechService {

    /** ASR：音频转文字 */
    String transcribe(byte[] audioBytes, String language);

    /** TTS：文字转音频，返回 PCM/MP3 字节 */
    byte[] synthesize(String text, String voice);
}
