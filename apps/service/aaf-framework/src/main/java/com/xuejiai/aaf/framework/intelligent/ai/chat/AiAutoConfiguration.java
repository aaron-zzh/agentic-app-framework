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
                                                : "deepseek:chat",
                                CapabilityRoutingContext.CAP_IMAGE_GEN, "qwen:wan2.7-image",
                                CapabilityRoutingContext.CAP_VIDEO_GEN, "qwen:happyhorse-1.0-i2v",
                                CapabilityRoutingContext.CAP_SPEECH_ASR, "qwen:fun-asr-realtime",
                                CapabilityRoutingContext.CAP_SPEECH_TTS, "qwen:cosyvoice-v3-flash",
                                CapabilityRoutingContext.CAP_MUSIC_GEN, "qwen:fun-music-v1",
                                CapabilityRoutingContext.CAP_OMNI_REALTIME,
                                        "qwen:qwen3-omni-flash-realtime",
                                CapabilityRoutingContext.CAP_RERANK, "qwen:qwen3-rerank",
                                CapabilityRoutingContext.CAP_EMBEDDING, "qwen:text-embedding-v4",
                                CapabilityRoutingContext.CAP_OCR, "qwen:qwen3.5-ocr"));
        // yaml 配置覆盖内置默认值
        if (properties.getDefaultModels() != null) {
            builtIn.putAll(properties.getDefaultModels());
        }
        return new DefaultCapabilityRouter(
                preferenceRepository, aiModelSelector, modelRepository, builtIn);
    }
}
