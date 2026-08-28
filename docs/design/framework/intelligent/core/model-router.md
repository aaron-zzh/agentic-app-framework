---
level: Practice
layer: Model
purpose: 定义模型统一管理、路由决策链、能力标签与动态客户端构建
status: draft
version: 1.1.0
date: 2026-08-25
author: Kiro
tags:
  - 模型路由
  - 模型管理
  - 能力标签
dependencies:
  - ../architecture.md
  - ./core.md
scope:
  includes:
    - 模型统一登记与配置来源
    - 路由决策链与优先级
    - 模型能力标签与匹配规则
    - 动态客户端构建与韧性调用
  excludes:
    - 单次调用的 Prompt 装配（见 prompt.md）
    - 配额扣减与计费（见 engine/governance/）
gains:
  - 能预测一次请求最终选中哪个模型及原因
  - 能为新模型登记正确的能力标签
  - 能判断显式指定模型与自动择选的边界
---

# 模型路由

> 模型统一登记于 `ai_model`，由路由器按决策链择选；**调用方只能在已授权范围内表达偏好，不能绕过路由直连任意模型**。

## 定位与边界

模型路由把能力要求、调用约束和授权候选收敛为精确 `ModelSpec`。它不决定任务是否需要 Agent、不解释 Skill 语义、不扣减配额；任务形态见 [总体架构](../architecture.md)，计量出口见 [core.md](core.md)。

| 承担 | 不承担 |
|---|---|
| 模型登记、能力硬过滤、有序择选与选择原因 | Prompt 装配、业务授权授予、任务状态迁移 |
| 以 `ai_model` 为模型登记与选择真理源；收敛直连与 Harness 的运行配置来源 | 在 fallback 时放宽能力、权限或输出合同 |
| 节点级精确模型引用冻结 | 让子节点任意替换根请求显式模型 |

## 领域模型

| 对象 | 不变量 |
|---|---|
| `AiModel` | `modelId` 唯一；提供商、协议、端点、能力、上下文窗口、价格、启用态与 fallback 统一登记 |
| `CapabilityRoutingContext` | 携带能力、显式/编排模型、任务特征和偏好主体；不授予模型访问权 |
| `ModelCapabilityProfile` | 类型化能力集合；硬能力缺失时不得进入排序阶段 |
| `SkillModelRequirement` | required 项全部满足，optional 项只参与排序；最小上下文按 Token 比较 |
| `ModelPreference` | 仅在 `USER` / `SYSTEM` 层提供同能力的有序候选，不扩大授权集合 |
| `ModelSpec` | 节点执行前冻结为精确模型引用；子节点与重试节点各自生成画像 |

## 契约

### 六层决策链

数字越小优先级越高，命中即停止；候选在每层都必须通过“已授权、启用、硬能力满足”校验。

| 优先级 | 决策层 | 输入 | 判定与命中规则 |
|---|---|---|---|
| 一 | 显式指定 | 根请求 `explicitModelId` 或合法继承的同一模型 | 仅允许服务端授权候选；存在、启用且满足硬能力才命中，非法时拒绝，不降到低优先级掩盖错误 |
| 二 | 编排指定 | 工作流节点、已发布 Agent/Team 的 `orchestrationModelId` | 配置版本有效且模型满足硬能力才命中；不得覆盖根请求 `EXPLICIT` |
| 三 | AI/规则辅助 | 任务特征：模态、长度、推理、成本等 | 只在硬过滤后的候选内排序；选择器无结论返回空，进入下一层 |
| 四 | 用户偏好 | `USER + ownerId + capability` 的有序 `modelIds` | 取首个已授权、启用且满足硬能力的模型；偏好不是授权 |
| 五 | 系统默认 | `SYSTEM + capability` 的有序 `modelIds` | 取首个已授权、启用且满足硬能力的模型 |
| 六 | 配置兜底 | capability → fallback model | 兜底同样经过硬校验；无合法模型则失败，禁止退回任意通用模型 |

当前 `DefaultCapabilityRouter` 已按上述顺序执行六层选择（`DefaultCapabilityRouter.java:18-126`），但只校验存在、启用和字符串 capability，尚未接入授权候选与完整能力画像。因此“调用方只能在授权范围表达偏好”仍是目标门禁。

根请求为 `EXPLICIT` 时，协调者只能让执行节点继承同一模型或提议 `AUTO`；不能提议另一个显式模型。最终 `ModelSpec` 冻结到每个节点的 `ExecutionProfileSnapshot`，重试节点不复用父节点画像；画像结构见 `ExecutionProfileSnapshot.java:24-99`。

### 能力标签与匹配

能力分为硬约束和排序偏好，禁止用模型名字符串推断正式能力。

| 维度 | 规范字段 | 示例 | 匹配规则 |
|---|---|---|---|
| 任务能力 | `capabilities` | `CHAT`、`EMBEDDING`、`RERANK`、`IMAGE_GEN` | required 全包含 |
| 输入模态 | `inputModalities` | `TEXT`、`IMAGE`、`AUDIO`、`VIDEO` | 请求模态必须为模型集合子集 |
| 输出合同 | `outputCapabilities` | `STREAMING`、`TOOL_CALLING`、`JSON_SCHEMA` | 调用合同要求的能力必须全部支持 |
| 容量 | `contextWindowTokens`、`maxOutputTokens` | `128000`、`8192` | 输入预算 + 输出预留不得越界；Skill 最小窗口必须满足 |
| 推理强度 | `reasoningClass` | `STANDARD`、`ADVANCED` | required 为硬过滤，preferred 参与排序 |
| 运行约束 | 启用态、提供商、价格、延迟等级、数据区域 | 成本敏感、区域限制 | 授权/区域为硬过滤；成本与延迟用于排序 |

匹配顺序固定：

```text
授权候选交集
→ enabled + 任务能力 + 模态 + 输出合同 + 上下文容量硬过滤
→ required Skill 要求求交集
→ preferred Skill 要求、推理、成本、延迟排序
→ 六层决策链择选
→ 冻结 ModelSpec 与选择原因
```

多个 ActivatedSkill 的 required 要求取并集；冲突或无候选时 fail-closed，不静默忽略某个 Skill。optional 要求只排序，不得把不满足 required 的模型重新加入。

当前 `AiModel` 只有逗号分隔 `capabilities`、`contextWindow` 与 `enableThinking`（`AiModel.java:31-296`）；`SkillModelRequirement` 已保存 capability、required、minimumContextTokens（`SkillModelRequirement.java:13-37`），但运行时仅把包含 `reason` 的要求映射为布尔特征（`AssistantApplicationService.java:1948-1958`）。结构化输出、工具调用、模态集合和推理等级的类型化标签为目标态。

### 动态客户端与韧性

| 组件 | 契约 |
|---|---|
| `DynamicChatClientFactory` | 以 `modelId` 为缓存键，从 `ai_model` 读取启用配置；Caffeine 最多 200 项、写后 10 分钟过期，配置变更显式 `evict`；OPENAI_COMPAT 动态构建。ANTHROPIC/OLLAMA 当前取容器 Bean，模型名、端点与密钥可能来自 Spring 配置，是待收敛的第二运行配置源（`DynamicChatClientFactory.java:38-168`） |
| `AgentScopeModelResolver` | 按冻结 `ModelSpec` 查询 `ai_model` 并构建 Harness provider model；API key 仍允许 `AiProperties` 兜底（`AgentScopeModelResolver.java:20-84`、`ModelManagementService.java:80-91`） |
| `ResilientChatService` | 调用前积分预检；路由后调用主模型；仅对 429、5xx、网络/超时等可重试错误尝试模型配置的 fallback；参数、认证、内容拒绝等错误直接失败（`ResilientChatService.java:36-388`） |
| `ModelPreference` | 作用域仅 `USER` / `SYSTEM`，按 capability 保存有序候选；只影响第四、第五层及候选顺序，不影响精确调用、授权或 Skill 硬要求（`ModelPreference.java:22-52`） |

韧性边界：动态工厂对 OpenAI 兼容客户端配置 60 秒超时与底层重试；当前服务无熔断器，应用层仅做一次 fallback。`callExact` 禁止路由和 fallback。流式链路只有订阅前抛出的异常能被当前 `try/catch` 切换模型，订阅后的异步错误没有统一 fallback 合同；在补齐前不得声称流式全链路自动降级。

## 实现态

| 契约 | 实现态 |
|---|---|
| `ai_model` 是直连与 Harness 的模型登记与选择真理源 | ⚠️ 部分实现 · 路由与 Harness 均查询 `AiModel`（`DefaultCapabilityRouter.java:18-126`、`AgentScopeModelResolver.java:20-84`）；直连 `ANTHROPIC/OLLAMA` 仍取容器 Bean，Harness API key 仍可由 `AiProperties` 兜底，尚非唯一运行配置真理源 |
| 六层路由顺序 | ✅ 已实现 · `DefaultCapabilityRouter.java:18-126` |
| 显式模型只在授权范围且不被协调者替换 | ⚠️ 部分实现 · `TaskModelSelection.java:6-32` 保证 AUTO/EXPLICIT 形态；路由器尚未接入授权候选，协调计划约束缺统一门禁 |
| 最终模型引用冻结到节点画像 | ✅ 已实现 · `AssistantApplicationService.java:1389-1395` 将 `ai_model` 主键写入 `ModelSpec`，`ExecutionProfileSnapshot.java:24-99` 冻结该引用；当前未冻结模型配置版本或配置 hash |
| 类型化能力标签与 Skill 全量匹配 | ⚠️ 部分实现 · `AiModelSelector.java:21-84` 支持视觉、推理、长上下文、成本特征；视觉分支仅检查 `VISION`，推理依赖模型名且 Skill 只接入 reasoning 布尔值 |
| 动态客户端缓存与显式失效 | ✅ 已实现 · 缓存与 `evict` 见 `DynamicChatClientFactory.java:38-57`；模型更新、删除、启停和导入均调用失效（`AiModelService.java:91-140,297-313`） |
| 超时、重试、熔断、同步与流式统一降级事实 | ⚠️ 部分实现 · `ResilientChatService.java:36-388` 有超时、可重试分类和 fallback；缺熔断、统一 attempt 事实及订阅后流式降级 |
| 完整类型化能力画像与授权候选门禁 | 🎯 目标态，当前不得声称已执行 |

## 验收基线

- 任一次路由都能回溯命中的决策层、被过滤候选及最终选择原因
- required 能力先硬过滤，偏好只排序；无合法候选时明确失败
- 根请求显式模型不能被协调者替换为另一个显式模型
- 节点级模型冻结在画像中，子节点与重试节点不复用父节点画像
- 配置变更后旧客户端缓存可确定失效
- fallback 不放宽授权、Skill 能力、输出合同或任务状态边界
