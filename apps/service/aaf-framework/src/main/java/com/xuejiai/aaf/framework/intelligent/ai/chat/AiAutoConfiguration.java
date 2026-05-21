package com.xuejiai.aaf.framework.intelligent.ai.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    ModelRouter modelRouter(
            AiProperties properties,
            ModelPreferenceRepository preferenceRepository,
            AiModelSelector aiModelSelector) {
        return new DefaultModelRouter(properties, preferenceRepository, aiModelSelector);
    }
}
