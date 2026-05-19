---
level: Practice
layer: Product
status: in-progress
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# Agent 智能体层（AAF-050）

## 技术任务

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| #5001 | Agent 定义与注册 | Agent 元数据、能力声明、生命周期管理、AgentExecutor 接口、AgentPool 池化 | 🟡 部分完成 |
| #5002 | 工具系统完整实现 | engine/tool/：ToolRegistry + ToolCallDispatcher + McpToolService + ScriptSandbox | 🟡 部分完成 |
| #5003 | 认知循环实现 | 感知→规划→执行→评估→学习 循环 | ✅ 已完成 |
| #5004 | Agent 沙箱 | 虚拟线程隔离 + 超时控制 + 检查点（含 WorkingMemory 快照） | 🟡 部分完成 |
| #5005 | Agent 通信 | Agent 间消息传递、事件总线、协作协议 | ✅ 已完成 |
| #5006 | 智能体可视化配置 | Agent/Skill/记忆/工具的 CRUD 管理界面（developer-webui） | ❌ 未实现 |
| #5007 | Core 层统一模型管理 | LlmClient 接口 + SpringAiLlmClient + AgentScopeLlmClient | 🟡 部分完成 |

## 已完成

- `AgentDefinition`：JPA 实体（agentId/name/systemPrompt/modelId/capabilities/tools/mcpServers）
- `AgentDefinitionRepository`：按 agentId/status/capabilities 查询
- `AgentRegistryService`：注册/查找/按能力查找/停用/归档
- `AgentScopeExecutor`：实现 `AgentExecutor` 接口，包装 AgentScope `ReActAgent`（**今日新增**）
- `AgentFactory`：重构为返回 `AgentExecutor`，不暴露 `ReActAgent`（**今日重构**）
- `CognitiveCycleExecutor`：完整认知循环（感知→规划→执行→评估→学习）+ 检查点 + 重试（**今日重构**，使用 AgentPool+AgentSandbox）
- `runtime/AgentPool`：池化复用，借出/归还时重置框架内部状态（**今日新增**）
- `runtime/AgentSandbox`：虚拟线程隔离 + 超时，依赖 `AgentExecutor` 接口（**今日重构**）
- `runtime/AgentEventBus`：发布/订阅 + 点对点消息（**今日迁移到 runtime/**）
- `runtime/AgentCheckpointService`：检查点保存/恢复/重试，补充 `workingMemorySnapshot` 字段（**今日迁移**）
- `WorkingMemory` / `WorkingMemoryImpl`：Agent 工作记忆（焦点管理）
- `AttentionBudget` / `AttentionBudgetImpl`：注意力预算分配
- `engine/tool/ToolRegistry`：工具注册表，Spring Bean 自动发现，白名单改为 assistantId 维度（**今日新增**）
- `engine/tool/ToolCallDispatcher`：调用分发（**今日新增**）
- `engine/tool/McpToolService`：MCP 工具服务，迁移到 engine/tool/（**今日迁移重构**）
- `engine/tool/ScriptSandbox`：脚本安全执行，扩展 AgentScope，加资源限制（**今日新增**）

## 待实现

### #5001 Agent 定义与注册（补充）

- [ ] `AgentPool` 中 `reset()` 实现：AgentScope `ReActAgent` 暂无公开 `clearHistory`，当前 reset 为空操作，需确认 AgentScope API 或改为重建实例
- [ ] `AgentDefinition` 加 `poolSize` 字段（池大小可配置，默认 1）

### #5002 工具系统完整实现（核心补充）

> Core 层（AAF-048 #4803）已定义 `FunctionDefinition` + `ToolProvider` 接口契约，engine/tool/ 已实现基础框架。

- [ ] MCP 服务器实际连接实现（当前 `buildToolkit` 中 MCP 连接是注释占位）
- [ ] Spring AI `ToolCallback` 适配器：将 Spring AI 工具桥接到 AgentScope `Toolkit`
- [ ] 工具调用结果回传给 Agent 的完整链路
- [ ] 工具调用审计日志
- [ ] **[架构修正]** 旧的 `intelligent/agent/McpToolService` 已被 `engine/tool/McpToolService` 替代，待删除旧文件

### #5004 Agent 沙箱（补充）

- [ ] 检查点 `ExecutionState` 补充 `WorkingMemory` 快照字段——当前恢复后工作记忆为空（字段已加，写入逻辑待实现）
- [ ] 长任务检查点持久化到 Cognition Agent 工作区（当前只有 Redis TTL 2h）
- [ ] 检查点 TTL 可配置

### #5006 智能体可视化配置（核心缺失）

- [ ] 前端 Agent 管理页面：列表/创建/编辑/删除
- [ ] 工具管理界面：MCP 服务器配置/工具白名单
- [ ] Agent 运行监控仪表盘：执行次数/成功率/延迟

### #5007 Core 层统一模型管理（补充）

- [ ] `SpringAiLlmClient`：实现 `LlmClient`，重构 `ResilientChatService`
- [ ] `AgentScopeLlmClient`：实现 `LlmClient`，包装 AgentScope Model
- [ ] AiModel 表 API Key 加密存储字段
- [ ] 多模态能力标记（vision/audio/embedding/function_calling）

### 数据库迁移脚本（阻塞项）

- [ ] `ai_agent_definition` 表迁移脚本
