/**
 * AgentScope Java 适配层。
 *
 * <p>AAF 对 AgentScope Java SDK 的封装适配，上层只依赖 AAF 自身接口， 本包负责将调用委托给 AgentScope，屏蔽 AgentScope API 细节。
 *
 * <h2>适配模块</h2>
 *
 * <ul>
 *   <li>{@link AgentScopeAgentAdapter} — ReActAgent 执行适配
 *   <li>{@link AgentScopeSessionAdapter} — Session 管理适配（替换 AAF 自研 SessionManager）
 *   <li>{@link AgentScopeMemoryAdapter} — 工作记忆适配（替换 WorkingMemoryImpl）
 *   <li>{@link AgentScopeToolAdapter} — 工具注册/调用适配（替换 ToolRegistry/ToolCallDispatcher）
 *   <li>{@link AgentScopeAguiAdapter} — AG-UI 流式输出适配（替换 AgUiStreamHandler）
 *   <li>{@link AgentScopeA2aAdapter} — A2A 跨系统协作适配（替换 A2AProtocolService）
 * </ul>
 *
 * <h2>依赖引入计划</h2>
 *
 * <pre>
 * agentscope-spring-boot-starter          — 核心自动配置（已引入）
 * agentscope-agui-spring-boot-starter     — AG-UI 官方实现（待引入）
 * agentscope-a2a-spring-boot-starter      — A2A 官方实现（待引入）
 * agentscope-extensions-session-redis     — Redis Session（待引入）
 * agentscope-extensions-autocontext-memory — Token 预算截断（待引入）
 * agentscope-extensions-reme              — 长期记忆（参考改造）
 * </pre>
 */
package com.xuejiai.aaf.framework.intelligent.agent.agentscope;
