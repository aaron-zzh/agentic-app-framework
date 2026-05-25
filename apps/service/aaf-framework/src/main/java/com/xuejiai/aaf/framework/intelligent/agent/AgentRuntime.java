package com.xuejiai.aaf.framework.intelligent.agent;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.core.agent.AgentExecutor;

/**
 * Agent 运行时接口——屏蔽底层 Agent 框架实现细节。
 *
 * <p>AgentFactory 只依赖此接口，不直接依赖 AgentScope/LangChain4j 等具体框架。
 * 切换底层框架只需替换实现类。
 *
 * <p>实现：
 * <ul>
 *   <li>{@code AgentScopeRuntime} — 基于 AgentScope ReActAgent（当前默认）</li>
 *   <li>未来可扩展：LangChain4j / 自研 ReAct 引擎</li>
 * </ul>
 */
public interface AgentRuntime {

    /**
     * 根据 Agent 定义创建可执行实例。
     *
     * @param definition Agent 元数据（模型/提示词/工具/超时）
     * @param tools 已包装的工具列表（含权限拦截）
     * @return AgentExecutor 实例
     */
    AgentExecutor create(AgentDefinition definition, List<String> tools);
}
