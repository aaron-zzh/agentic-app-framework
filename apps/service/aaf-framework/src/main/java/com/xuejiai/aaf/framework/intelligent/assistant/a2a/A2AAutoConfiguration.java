package com.xuejiai.aaf.framework.intelligent.assistant.a2a;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.intelligent.assistant.AssistantService;

/**
 * A2A 引擎自动配置——按 {@code aaf.a2a.engine} 属性切换实现。
 *
 * <ul>
 *   <li>{@code aaf.a2a.engine=local}（默认）→ LocalA2AEngine
 *   <li>{@code aaf.a2a.engine=agentscope} → AgentScopeA2AEngine
 * </ul>
 */
@Configuration
public class A2AAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "aaf.a2a.engine", havingValue = "local", matchIfMissing = true)
    public A2AEngine localA2AEngine(AssistantService assistantService) {
        return new LocalA2AEngine(assistantService);
    }

    @Bean
    @ConditionalOnProperty(name = "aaf.a2a.engine", havingValue = "agentscope")
    public A2AEngine agentScopeA2AEngine(AssistantService assistantService) {
        return new AgentScopeA2AEngine(assistantService);
    }
}
