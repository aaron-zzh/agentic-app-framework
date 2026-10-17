package com.xuejiai.aaf.module.voice.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 语音会话（协议 v3）配置，设备端与网页端共用。
 *
 * @param idleTimeoutSec 无话轮、无 tts 请求超过该秒数后服务端发 session_close(idle_timeout)，下限 30
 * @param helloTimeoutMs 连接后等待 hello 的上限
 * @param maxActiveTurns 单会话同时进行的话轮数上限（进行中的回复 + 新话轮 + 余量）
 * @param maxTtsInFlight 单个回复同时进行的合成任务数（句间流水）
 * @param minSentenceChars 回复切句的最短字数（不含空白），不足则并入下一句
 * @param maxSentenceChars 无句末标点时遇到逗号类标点的强制切句长度
 * @param speculative 是否支持推测（speech_end / speech_resume）
 * @param serverEndpointing 是否允许客户端请求服务端断句（网页连续对话）
 * @param serverEndpointSilenceMs 服务端断句：一句定稿后无新文本多久判定话轮结束
 * @param heldAudioMaxBytes 软结束后暂存的续说音频上限（PCM16 16kHz，64000≈2 秒）
 * @param ttsFrameBytes 下发 TTS 二进制帧的最大负载
 * @param partialMinIntervalMs asr_partial 最小发送间隔
 * @param cancelRetentionMs 话轮结束后保留多久以接收迟到的 cancel(not_adopted)
 * @param voices 音色预设 ID → 上游音色，hello.voice 只能取这里的 key
 */
@ConfigurationProperties(prefix = "aaf.voice")
public record VoiceSessionProperties(
        @DefaultValue("600") int idleTimeoutSec,
        @DefaultValue("5000") long helloTimeoutMs,
        @DefaultValue("3") int maxActiveTurns,
        @DefaultValue("2") int maxTtsInFlight,
        @DefaultValue("4") int minSentenceChars,
        @DefaultValue("60") int maxSentenceChars,
        @DefaultValue("true") boolean speculative,
        @DefaultValue("true") boolean serverEndpointing,
        @DefaultValue("700") long serverEndpointSilenceMs,
        @DefaultValue("64000") int heldAudioMaxBytes,
        @DefaultValue("16384") int ttsFrameBytes,
        @DefaultValue("100") long partialMinIntervalMs,
        @DefaultValue("3000") long cancelRetentionMs,
        Map<String, String> voices) {

    public VoiceSessionProperties {
        idleTimeoutSec = Math.max(30, idleTimeoutSec);
        voices = voices == null ? Map.of() : Map.copyOf(voices);
    }

    /** 测试与默认场景使用的默认配置。 */
    public static VoiceSessionProperties defaults() {
        return new VoiceSessionProperties(
                600, 5000, 3, 2, 4, 60, true, true, 700, 64_000, 16_384, 100, 3000, Map.of());
    }
}
