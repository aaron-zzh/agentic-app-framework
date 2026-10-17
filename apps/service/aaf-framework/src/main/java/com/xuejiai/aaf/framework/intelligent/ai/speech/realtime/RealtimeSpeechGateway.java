package com.xuejiai.aaf.framework.intelligent.ai.speech.realtime;

import java.util.function.Supplier;

/**
 * 实时语音上游入口：持有识别与合成两个对象池，按任务借还。
 *
 * <p>池只在任务进行中占用，不随客户端会话常驻，因此池大小取决于同时在说话/在合成的数量，而不是在线连接数。
 */
public final class RealtimeSpeechGateway implements AutoCloseable {

    private final SpeechPoolProperties properties;
    private final ClientPool<AsrClient> asrPool;
    private final ClientPool<TtsClient> ttsPool;

    public RealtimeSpeechGateway(
            SpeechPoolProperties properties,
            Supplier<AsrClient> asrFactory,
            Supplier<TtsClient> ttsFactory) {
        this.properties = properties;
        this.asrPool =
                new ClientPool<>(
                        "asr",
                        properties.asrSize(),
                        properties.borrowTimeoutMs(),
                        properties.idleEvictSeconds(),
                        asrFactory,
                        AsrClient::close);
        this.ttsPool =
                new ClientPool<>(
                        "tts",
                        properties.ttsSize(),
                        properties.borrowTimeoutMs(),
                        properties.idleEvictSeconds(),
                        ttsFactory,
                        TtsClient::close);
    }

    /** 借出识别连接并开始一个识别任务。池耗尽抛 {@link SpeechPoolExhaustedException}。 */
    public AsrTask openAsr(String language, AsrListener listener) {
        return PooledAsrTask.open(
                asrPool,
                language,
                listener,
                properties.finishTimeoutMs(),
                properties.cancelTimeoutMs());
    }

    /** 借出合成连接并开始合成一段文本。池耗尽抛 {@link SpeechPoolExhaustedException}。 */
    public TtsTask startTts(String text, String voice, TtsListener listener) {
        return PooledTtsTask.start(ttsPool, text, voice, listener, properties.cancelTimeoutMs());
    }

    public ClientPool.Snapshot asrSnapshot() {
        return asrPool.snapshot();
    }

    public ClientPool.Snapshot ttsSnapshot() {
        return ttsPool.snapshot();
    }

    @Override
    public void close() {
        asrPool.close();
        ttsPool.close();
    }
}
