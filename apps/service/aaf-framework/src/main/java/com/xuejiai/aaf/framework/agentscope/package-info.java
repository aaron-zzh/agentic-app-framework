/**
 * AAF 内容创作 Agent（基于 agentscope 2.0 HarnessAgent）。
 *
 * <p>本包是 AAF 与 agentscope 2.0+ 的集成入口，整体围绕 {@code HarnessAgent} 一个核心类展开：
 *
 * <ul>
 *   <li>{@code agent/} —— 主 Agent（内容创作）和子 Agent（编辑/校对）的工厂
 *   <li>{@code prompt/} —— 内容创作系统提示词
 *   <li>{@code tool/} —— 知识库检索 / 记忆读写 / HITL 审批等 AAF 原生工具
 *   <li>{@code middleware/} —— 复用 codingagent 示例的 budget/queue 中间件 + AAF 持久化中间件
 *   <li>{@code config/} —— Spring Boot 自动配置，把 HarnessAgent 暴露成 Bean 供 AG-UI starter 自动注册
 * </ul>
 *
 * <p>对外协议：完全复用 {@code agentscope-agui-spring-boot-starter} 的默认实现，端点 {@code POST
 * /agui/run/{agentId}} （agentId 与 Bean 名一一对应，例如 {@code content-creation} / {@code editor}）。
 *
 * <p>不在本包：旧 1.x 集成代码已重命名为 {@code .java.legacy}，保留源码不删除。详见 {@code
 * apps/service/aaf-framework/.../intelligent/agentscope/} 与 {@code
 * apps/service/aaf-api/.../module/ai/agui/}。
 */
package com.xuejiai.aaf.framework.agentscope;
