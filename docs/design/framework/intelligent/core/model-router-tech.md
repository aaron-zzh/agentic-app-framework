---
level: Practice
layer: Model
purpose: 模型管理与路由技术方案——ai_model 表 + ModelRouter 六层决策链 + DynamicChatClientFactory + ModelPreference
status: draft
version: 0.1.0
date: 2026-05-28
author: AaronZZH
---

# 模型管理与路由技术方案

> ai_model 表 + ModelRouter 六层决策链 + DynamicChatClientFactory + ModelPreference。

## 两条 LLM 调用路径

AAF 存在两条并行的 LLM 调用路径，分别服务不同场景：

### 路径 A：Spring AI 路径（对话/RAG/记忆提取等非 Agent 场景）

```text
ResilientChatService
  → ModelRouter（六层决策链）
  → DynamicChatClientFactory.get(modelId)
  → Spring AI ChatClient → 各厂商 API
```

适用场景：简单对话、RAG 检索增强生成、记忆提取、知识库问答等不需要 Agent 自主规划的场景。

### 路径 B：AgentScope 路径（Agent 执行场景）

```text
AgentFactory.create(definition)
  → AgentScopeExecutor → ReActAgent
  → OpenAIChatModel.builder()
      .apiKey(ai_model.apiKey)
      .baseUrl(ai_model.baseUrl)
      .modelName(ai_model.modelName)
  → 各厂商 API（OpenAI 兼容 / Anthropic / DashScope / Gemini / Ollama）
```

适用场景：需要规划+工具调用+迭代的复杂任务，由 AgentScope ReActAgent 驱动 ReAct 循环。

### 两条路径的共享点

| 共享资源 | 说明 |
|---------|------|
| `ai_model` 表 | apiKey / baseUrl / modelName 的唯一来源，两条路径都从此表读取模型配置 |
| `TokenUsageEvent` | 统一计量事件，两条路径执行后都发布此事件，由 TokenMeteringService 统一处理 |
| `ModelPreference` 表 | 用户/系统级模型偏好。路径 A 通过 ModelRouter 使用；路径 B 待接入 |

### 路径选择决策

```text
请求到达
  ├─ 固定流程（审批/发布/CI）→ 工作流引擎（Flowable），节点可嵌入 Agent 任务
  ├─ 简单对话/RAG/知识检索 → 路径 A（ResilientChatService 直接调用）
  ├─ 复杂任务（需要规划+工具调用+迭代）→ 路径 B（AgentScope ReActAgent）
  └─ 多 Assistant 协作（项目级目标）→ Team 层编排（内部走路径 B）
```

判断标准：
- 任务复杂度高（多步骤、需要工具）→ Agent（路径 B）
- 任务价值高（值得 LLM 多轮推理）→ Agent（路径 B）
- 步骤不确定（需要动态规划）→ Agent（路径 B）
- 简单问答/检索 → 直接 API（路径 A）

## ModelRouter 六层决策链

ModelRouter 按优先级从高到低依次尝试，命中即返回 modelId：

| 优先级 | 决策层 | 说明 |
|--------|--------|------|
| 1 | 显式指定 `explicitModelId` | 调用方直接指定，最高优先级 |
| 2 | 编排引擎配置 `orchestrationModelId` | 工作流节点 / AgentDefinition 中配置 |
| 3 | AI 辅助决策 `AiModelSelector` | 根据任务特征（图片/推理/长文本/成本）自动选择 |
| 4 | 用户偏好 `ModelPreference`（USER scope） | 用户个人设置的偏好模型 |
| 5 | 系统默认 `ModelPreference`（SYSTEM scope） | 管理员配置的系统级默认 |
| 6 | yaml 兜底 `AiProperties.defaultModel` | 配置文件中的最终兜底 |

## DynamicChatClientFactory

根据 modelId 从 `ai_model` 表读取配置，动态构建对应的 ChatClient：

| providerType | 构建方式 | 说明 |
|---|---|---|
| `OPENAI_COMPAT` | `OpenAiChatModel`（Spring AI M6，OpenAiSetup） | 兼容 OpenAI 协议的所有厂商 |
| `ANTHROPIC` | `AnthropicChatModel`（Spring AI，从容器取 Bean） | Anthropic Claude 系列 |
| `OLLAMA` | `OllamaChatModel`（Spring AI，从容器取 Bean） | 本地 Ollama 部署 |

## ResilientChatService

路径 A 的核心服务，提供降级和计量能力：

- **主模型降级**：主模型调用失败 → 自动切换到 `ai_model` 表中配置的 `fallback_model_id`
- **Token 计量**：每次调用后发布 `TokenUsageEvent` → `TokenMeteringService` 记录用量

## 相关文档

- [执行流程全景](../../execution-flow.md)
- [Core 层设计](../architecture.md)
- [预算控制引擎](../../engine/governance/budget-control.md)
- [积分与结算引擎](../../engine/governance/credit-settlement.md)
