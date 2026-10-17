package com.xuejiai.aaf.framework.intelligent.ai.speech.realtime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 上游语音对象池配置。
 *
 * <p>约束：{@code asrSize + ttsSize} 不得超过 SDK 连接池大小（环境变量 {@code DASHSCOPE_CONNECTION_POOL_SIZE}，默认
 * 32），否则池里借对象而连接池已满会阻塞调用线程；同时不应超过账号 QPS 限制。
 *
 * @param asrSize 识别对象池大小（同时在说话的话轮数 × 1.5~2）
 * @param ttsSize 合成对象池大小（同时在合成的句子数 × 1.5~2）
 * @param borrowTimeoutMs 借出等待上限，超时即抛 {@link SpeechPoolExhaustedException}
 * @param finishTimeoutMs 正常结束任务时等待 task-finished 的上限
 * @param cancelTimeoutMs 取消任务时等待结束事件的上限，超时则关闭连接
 * @param idleEvictSeconds 池内空闲对象存活上限，应小于上游 60 秒空闲断连
 */
@ConfigurationProperties(prefix = "aaf.speech.pool")
public record SpeechPoolProperties(
        @DefaultValue("16") int asrSize,
        @DefaultValue("16") int ttsSize,
        @DefaultValue("300") long borrowTimeoutMs,
        @DefaultValue("3000") long finishTimeoutMs,
        @DefaultValue("2000") long cancelTimeoutMs,
        @DefaultValue("50") long idleEvictSeconds) {

    /** SDK 连接池大小的环境变量名，与 SDK 保持一致。 */
    public static final String CONNECTION_POOL_SIZE_ENV = "DASHSCOPE_CONNECTION_POOL_SIZE";

    /** SDK 连接池默认大小。 */
    public static final int DEFAULT_CONNECTION_POOL_SIZE = 32;

    /** 校验对象池大小与 SDK 连接池的关系，违反时启动失败并给出修正指引。 */
    public void validate(int connectionPoolSize) {
        if (asrSize <= 0 || ttsSize <= 0) {
            throw new IllegalStateException("aaf.speech.pool.asr-size / tts-size 必须大于 0");
        }
        if (asrSize + ttsSize > connectionPoolSize) {
            throw new IllegalStateException(
                    ("aaf.speech.pool 的 asr-size(%d) + tts-size(%d) 超过 SDK 连接池大小 %d，"
                                    + "会导致借出对象时阻塞；请调低对象池，或调大环境变量 %s"
                                    + "（及 DASHSCOPE_MAXIMUM_ASYNC_REQUESTS / "
                                    + "DASHSCOPE_MAXIMUM_ASYNC_REQUESTS_PER_HOST）")
                            .formatted(
                                    asrSize,
                                    ttsSize,
                                    connectionPoolSize,
                                    CONNECTION_POOL_SIZE_ENV));
        }
    }

    /** 读取 SDK 连接池大小环境变量，缺失或非法时取默认值。 */
    public static int connectionPoolSizeFromEnv() {
        var raw = System.getenv(CONNECTION_POOL_SIZE_ENV);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_CONNECTION_POOL_SIZE;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            return DEFAULT_CONNECTION_POOL_SIZE;
        }
    }
}
