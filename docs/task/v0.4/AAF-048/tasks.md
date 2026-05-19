---
level: Practice
layer: Product
status: in-progress
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# Core 内核层（AAF-048）

## 技术任务

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| #4801 | LLM 抽象层 | Spring AI ChatModel 统一接口、多 Provider 适配器 | ✅ 已完成 |
| #4802 | Prompt 模板引擎 | 模板 CRUD、变量注入、版本管理、A/B 测试 | 🟡 部分完成 |
| #4803 | Function Calling 接口契约 | Core 层定义 `FunctionDefinition` + `ToolProvider` 接口，实现由 Agent 层（AAF-050 #5002）负责 | ✅ 已完成 |
| #4804 | Token 计量系统 | 按用户/对话/模型统计、配额管理、用量告警 | 🟡 部分完成 |
| #4805 | 模型管理 | 模型注册/启用/禁用、参数配置、性能基准测试 | 🟡 部分完成 |

## 已完成

- `ResilientChatService`：LLM 统一调用 + 主模型降级 + 流式支持
- `ChatContextBuilder`：滑动窗口上下文管理 + Token 估算
- `ModelRouter` / `DefaultModelRouter`：场景路由模型
- `AiProperties`：多模型配置
- `PromptTemplateService`：模板 CRUD、变量注入（`${var}`）、版本管理
- `PromptTemplate` 实体 + Repository
- `TokenMeteringService`：用量记录（事件监听）、配额检查、月度统计
- `TokenMeteringHook`：AgentScope Hook 桥接
- `TokenUsageRecord` 实体 + Repository
- `TokenQuotaService`：配额查询
- `ModelManagementService`：模型注册/启用/禁用/降级
- `AiModel` 实体 + Repository
- `FunctionDefinition` Record + `ToolProvider` 接口（Core 层契约）
- **今日新增**：`core/agent/AgentExecutor`、`core/assistant/AssistantExecutor`、`core/skill/SkillProvider`+`SkillDef`、`core/llm/LlmClient`、`core/memory/MemoryPipeline`+`MemoryStrategy`+`MemoryContext`+`PipelineInput`

## 待实现

### #4801 LLM 抽象层（补充）

- [ ] `LlmClient` 接口实现：`SpringAiLlmClient`（重构 `ResilientChatService`）+ `AgentScopeLlmClient`（包装 AgentScope Model）

### #4802 Prompt 模板引擎（补充）

- [ ] A/B 测试：同一模板多版本并行，按流量比例分配
- [ ] 模板分类管理 API（按 category 查询）

### #4803 Function Calling 接口契约（已完成）

- [x] `FunctionDefinition`：工具定义 Schema
- [x] `ToolProvider` 接口：Agent 层实现
- [x] `AgentExecutor` / `AssistantExecutor` / `SkillProvider` / `LlmClient` / `MemoryPipeline` 接口
- 工具注册表、调用分发等实现职责在 **AAF-050 #5002**（engine/tool/）

### #4804 Token 计量系统（补充）

- [ ] 用量告警：配额达到 80%/100% 时通知
- [ ] 按对话维度统计（当前只有用户维度）
- [ ] `TokenMeteringHook` 中 userId 从 SecurityContext 获取（当前硬编码 0L）

### #4805 模型管理（补充）

- [ ] 性能基准测试：延迟/吞吐量/成功率指标采集
- [ ] API Key 加密存储（当前 AiModel 无 apiKey 字段）
- [ ] 多模态能力标记（vision/audio/embedding）

### 数据库迁移脚本（阻塞项）

- [ ] `ai_model` 表迁移脚本
- [ ] `prompt_template` 表迁移脚本
- [ ] `token_usage_record` 表迁移脚本
