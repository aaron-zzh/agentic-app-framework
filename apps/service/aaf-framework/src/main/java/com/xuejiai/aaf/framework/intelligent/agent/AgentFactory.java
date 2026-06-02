/**
 * Agent 工厂——创建 AgentExecutor 实例。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.agent;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.core.agent.AgentRuntime;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.agent.AgentExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent 工厂——通过 {@link AgentRuntime} 接口创建 Agent 实例。
 *
 * <p>不直接依赖任何底层 Agent 框架（AgentScope/LangChain4j）， 具体实现由 AgentRuntime 决定。切换框架只需替换 AgentRuntime Bean。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentFactory {

    private final AgentRuntime runtime;
    private final ToolPermissionGuard toolPermissionGuard;

    /**
     * 创建 AgentExecutor 实例。
     *
     * @param definition Agent 定义
     * @return 可执行的 Agent 实例
     */
    public AgentExecutor create(AgentDefinition definition) {
        var tools =
                definition.getTools() != null
                        ? List.of(definition.getTools().replaceAll("[\\[\\]\"]", "").split(","))
                        : List.<String>of();
        return runtime.create(definition, tools);
    }
}
