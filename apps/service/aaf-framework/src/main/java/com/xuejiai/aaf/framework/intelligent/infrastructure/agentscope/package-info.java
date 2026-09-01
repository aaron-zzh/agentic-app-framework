/**
 * AgentScope 2.0 基础设施适配层：AAF 智能层端口的唯一 AgentScope 实现。
 *
 * <p>执行内核是 <b>core {@code ReActAgent}</b>，不是官方 {@code HarnessAgent}（ADR-005 议题三）：AAF 只借用 ReAct
 * 推理循环与工具调用能力，工作区、记忆、技能、子智能体、文件与 Shell 都不在 core 执行面内，这些职责由 AAF 自身承担， 避免出现第二套真理源。Agent
 * 外层的规格冻结、生命周期、缓存、中断、事件与治理由本包承担，即 AAF Harness 层。
 *
 * <p>一次执行的链路：
 *
 * <pre>
 * AgentExecutionCommand
 *   → definition（取定义）→ compiler（编译 ReActAgent）→ mapping（消息/上下文入向）
 *   → ReActAgent.streamEvents → mapping（事件出向）+ middleware（计量）→ ExecutionEventStore
 * </pre>
 *
 * <p>各子包与类的职责：
 *
 * <ul>
 *   <li><b>definition</b>
 *       <ul>
 *         <li>{@code JpaAgentDefinitionAdapter} — 读 agent_definition 表，把 active 版本翻译成 AgentSpec
 *       </ul>
 *   <li><b>compiler</b>
 *       <ul>
 *         <li>{@code AgentScopeSpecCompiler} — 把 AgentSpec / 动态子智能体规格编译为无状态 ReActAgent，按执行画像（版本 +
 *             生效工具 + 系统提示词）缓存，并在构建后断言最终工具面等于 AAF 冻结白名单
 *       </ul>
 *   <li><b>execution</b>
 *       <ul>
 *         <li>{@code HarnessAgentExecutionAdapter} — AgentExecutionPort 实现，编排事件流、超时、
 *             取消与中断，并保证事件串行入库
 *       </ul>
 *   <li><b>mapping</b>
 *       <ul>
 *         <li>{@code AgentScopeMessageMapper} — AAF 消息 → AgentScope Msg（入向）
 *         <li>{@code AgentScopeRuntimeContextMapper} — 调用上下文 → per-call RuntimeContext，并生成
 *             租户/用户/任务隔离的状态键（委托态叠加 fencing token）
 *         <li>{@code AgentScopeEventMapper} — AgentScope 运行事件 → 稳定脱敏的 AAF ExecutionEvent（出向）
 *       </ul>
 *   <li><b>middleware</b>
 *       <ul>
 *         <li>{@code AgentScopeTokenMeteringObserver} — 调用前预扣委托预算，调用后按真实 usage 记账
 *       </ul>
 *   <li><b>model</b>
 *       <ul>
 *         <li>{@code AgentScopeModelResolver} — 以 ai_model 表为真理源构建 AgentScope Model
 *       </ul>
 *   <li><b>tool</b>
 *       <ul>
 *         <li>{@code RegistryToolPortAdapter} — 工具目录与注册中心的端口实现，含启用状态、幂等、 Connector 受信参数等校验
 *         <li>{@code AgentScopeToolkitFactory} — 按精确版本把工具定义编译成 AgentScope Toolkit
 *         <li>{@code PortBackedAgentTool} — 工具执行边界，所有真实调用必须经 ToolGateway， 待授权时挂起 Agent
 *         <li>{@code ToolResultEvidenceStore} — 在工具返回与工具结果事件之间一次性传递脱敏业务证据
 *       </ul>
 *   <li><b>state</b>
 *       <ul>
 *         <li>{@code SpringRedisClientAdapter} — 让 RedisAgentStateStore 复用 Spring 管理的 Redis 连接
 *       </ul>
 *   <li><b>spring</b>
 *       <ul>
 *         <li>{@code AgentRuntimePortAutoConfiguration} — 把生产仓储与工具注册中心接入 P2 稳定端口
 *         <li>{@code AgentScopeInfrastructureAutoConfiguration} — 上述组件的唯一装配入口， 端口齐备时才生效
 *       </ul>
 * </ul>
 */
package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope;
