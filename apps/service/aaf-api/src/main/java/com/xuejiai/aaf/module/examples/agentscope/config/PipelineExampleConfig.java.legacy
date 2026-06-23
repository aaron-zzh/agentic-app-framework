package com.xuejiai.aaf.module.examples.agentscope.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.Model;

/**
 * Pipeline 顺序管道示例配置。
 *
 * <p>业务场景：自然语言 → SQL 生成 → SQL 质量评分，演示多 Agent 串联调用。 使用纯 AgentScope Java ReActAgent，不依赖 Spring AI
 * Alibaba SequentialAgent。 仅在 aaf.examples.agentscope.enabled=true 时激活。
 */
@Configuration
@ConditionalOnProperty(
        name = "aaf.examples.agentscope.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class PipelineExampleConfig {

    private static final String SQL_GENERATOR_PROMPT =
            """
            你是 MySQL 数据库专家。根据用户的自然语言描述，输出对应的 SQL 语句。
            只输出合法的 MySQL SQL，不要包含解释。
            """;

    private static final String SQL_RATER_PROMPT =
            """
            你是 SQL 质量审查员。给定用户的自然语言请求和生成的 SQL，
            输出一个 0 到 1 之间的浮点数评分，表示 SQL 与用户意图的匹配程度。
            只输出数字，不要其他文字。示例：0.85
            """;

    /** SQL 生成 Agent（Pipeline 第一步） */
    @Bean("sqlGeneratorAgent")
    public ReActAgent sqlGeneratorAgent(Model exampleDashScopeModel) {
        return ReActAgent.builder()
                .name("sql_generator")
                .description("将自然语言转换为 MySQL SQL")
                .sysPrompt(SQL_GENERATOR_PROMPT)
                .model(exampleDashScopeModel)
                .memory(new InMemoryMemory())
                .build();
    }

    /** SQL 评分 Agent（Pipeline 第二步） */
    @Bean("sqlRaterAgent")
    public ReActAgent sqlRaterAgent(Model exampleDashScopeModel) {
        return ReActAgent.builder()
                .name("sql_rater")
                .description("对生成的 SQL 进行质量评分")
                .sysPrompt(SQL_RATER_PROMPT)
                .model(exampleDashScopeModel)
                .memory(new InMemoryMemory())
                .build();
    }
}
