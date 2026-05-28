package com.xuejiai.aaf.module.examples.agentscope.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import io.agentscope.core.model.tts.DashScopeRealtimeTTSModel;
import io.agentscope.core.model.tts.RealtimeTTSModel;
import lombok.extern.slf4j.Slf4j;

/**
 * Realtime TTS 示例配置。
 *
 * <p>演示 AgentScope {@link RealtimeTTSModel} 能力：流式文本转语音（TTS）， 支持增量推送文本，WebSocket
 * 实时返回音频流，适合"边生成边播放"场景。
 *
 * <p>与普通 TTS 的区别：
 *
 * <ul>
 *   <li>普通 TTS（TTSModel）：一次性输入完整文本，HTTP + SSE 返回音频
 *   <li>Realtime TTS（RealtimeTTSModel）：流式输入 + WebSocket 流式输出， 可在 LLM 生成文本的同时推送给 TTS，实现更低延迟的语音交互
 * </ul>
 *
 * <p>需要外部服务：DashScope API（阿里云通义） 模型：qwen3-tts-flash-realtime 或 cosyvoice-v2
 *
 * <p>仅在 aaf.examples.agentscope.enabled=true 时激活。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
        name = "aaf.examples.agentscope.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class RealtimeExampleConfig {

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashScopeApiKey;

    /**
     * Realtime TTS 模型 Bean。
     *
     * <p>[Realtime能力点] DashScopeRealtimeTTSModel 基于 WebSocket 传输， 支持 push(text) 增量推送文本，finish()
     * 获取剩余音频， synthesizeStream(text) 一次性合成并流式返回。
     *
     * <p>音色选项（voice）：Cherry、Ethan、Serena 等（参考 DashScope 文档）
     */
    @Bean("exampleRealtimeTts")
    public RealtimeTTSModel exampleRealtimeTts() {
        String key =
                StringUtils.hasText(dashScopeApiKey)
                        ? dashScopeApiKey
                        : System.getenv("AI_DASHSCOPE_API_KEY");
        if (!StringUtils.hasText(key)) {
            log.warn("[Realtime TTS] DASHSCOPE_API_KEY 未配置，TTS 功能不可用");
        }
        // [Realtime能力点] WebSocket 连接，流式输入输出
        return DashScopeRealtimeTTSModel.builder()
                .apiKey(key)
                .modelName("qwen3-tts-flash-realtime")
                .voice("Cherry")
                .build();
    }
}
