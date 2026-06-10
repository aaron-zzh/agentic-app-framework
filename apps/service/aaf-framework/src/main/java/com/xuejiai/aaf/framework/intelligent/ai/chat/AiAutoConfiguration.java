package com.xuejiai.aaf.framework.intelligent.ai.chat;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelSelector;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.core.model.DefaultCapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelPreferenceRepository;

/** AI 模块自动配置。 */
@Configuration
@ConditionalOnClass(ChatModel.class)
@EnableConfigurationProperties(AiProperties.class)
public class AiAutoConfiguration {

    @Bean
    ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    CapabilityRouter capabilityRouter(
            AiProperties properties,
            ModelPreferenceRepository preferenceRepository,
            AiModelSelector aiModelSelector,
            AiModelRepository modelRepository) {
        // 代码内置兜底（最低优先级），可被 yaml aaf.ai.default-models 覆盖
        var builtIn =
                new java.util.HashMap<>(
                        Map.of(
                                CapabilityRoutingContext.CAP_CHAT,
                                        properties.getDefaultModel() != null
                                                ? properties.getDefaultModel()
                                                : "deepseek-v4-pro",
                                CapabilityRoutingContext.CAP_IMAGE_GEN, "qwen-image-plus",
                                CapabilityRoutingContext.CAP_VIDEO_GEN, "wan2.6-i2v-flash",
                                CapabilityRoutingContext.CAP_SPEECH_ASR, "qwen3-asr-flash",
                                CapabilityRoutingContext.CAP_SPEECH_TTS, "cosyvoice-v3-flash",
                                CapabilityRoutingContext.CAP_MUSIC_GEN, "fun-music-v1",
                                CapabilityRoutingContext.CAP_OMNI_REALTIME,
                                        "qwen3-omni-flash-realtime",
                                CapabilityRoutingContext.CAP_RERANK, "gte-rerank-v2",
                                CapabilityRoutingContext.CAP_EMBEDDING, "text-embedding-v3"));
        // yaml 配置覆盖内置默认值
        if (properties.getDefaultModels() != null) {
            builtIn.putAll(properties.getDefaultModels());
        }
        return new DefaultCapabilityRouter(
                preferenceRepository, aiModelSelector, modelRepository, builtIn);
    }
}
