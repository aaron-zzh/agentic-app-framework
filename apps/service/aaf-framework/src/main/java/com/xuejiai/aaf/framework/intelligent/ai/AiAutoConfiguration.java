/**
 * AI 自动配置。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** AI 模块自动配置，注册 ChatClient 和模型路由。 */
@Configuration
@ConditionalOnClass(ChatModel.class)
@EnableConfigurationProperties(AiProperties.class)
public class AiAutoConfiguration {

    @Bean
    ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    ModelRouter modelRouter(AiProperties properties) {
        return new DefaultModelRouter(properties);
    }
}
