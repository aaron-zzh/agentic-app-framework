---
level: Practice
layer: Model
purpose: 定义 Prompt 引擎对提示词资产、不可变版本、发布、缓存和评估的管理边界
status: draft
version: 1.0.0
date: 2026-08-22
author: AaronZZH
related:
  - ../../intelligent/core/prompt.md
---

# Prompt 引擎

> Prompt 引擎管理可发布的 Prompt 资产；Core 的 `PromptEnvelope` 装配模型调用。两者不能各自维护一套运行时拼接规则。

## 定位

```text
服务层 Prompt 管理能力
  → 草稿、审核、发布、灰度、回滚、评估和版本对比
              ↓
引擎层 Prompt 资产能力
  → 根对象、不可变版本、精确版本查询、hash 校验和缓存失效
              ↓
智能层 PromptInvocationGateway
  → 按调用画像选择已发布版本并生成 PromptEnvelope
              ↓
L0 Core 模型适配器
```

Prompt 引擎不理解 Assistant、Role、Skill、AgentKind、TaskBoard 或 Harness Loop，也不决定 P0–P8 顺序。运行时片段白名单、优先级、信任边界、消息装配、工具定义和最终 canonical hash 统一由 [PromptEnvelope 与模型调用装配设计](../../intelligent/bak/core/prompt.md) 定义。

## 核心能力

| 能力 | 责任 |
|---|---|
| Prompt 根对象 | 保存稳定 `code`、名称、类型、可见性和分类，不保存可原地覆盖的发布正文 |
| 不可变版本 | 保存 version、content、变量声明、content hash、状态和变更摘要 |
| 生命周期 | 支持 `draft → review → canary → active → retired`，已发布正文禁止原地修改 |
| 精确解析 | 按 `promptKey + version` 返回经过 hash 校验的版本；缺失时 fail-closed |
| 激活指针 | 系统参数只选择已发布版本、灰度和回滚指针，不保存任意完整 Prompt |
| 缓存与失效 | 数据库为唯一真理，提供精确版本和当前激活版本缓存，发布后广播失效 |
| 评估 | 管理用例、指标、A/B 或 canary 结果；评估不会自动修改生产版本 |
| 审计 | 记录发布、激活、灰度和回滚；运行时 Envelope 只引用资产 key/version/hash |

## 资产类型

| 类型 | 示例 | 运行时用途 |
|---|---|---|
| Harness Constitution | `aaf.harness.constitution` | 只供 `AUTONOMOUS_HARNESS` P1 使用 |
| Identity / TaskLoop Contract | PRIMARY、COORDINATOR、EXECUTOR、AGGREGATOR 合同 | 自主调用 P2，定义身份职责和任务循环 |
| L0 Function Contract | Role/Skill 选择、摘要、抽取、Judge | 只供 `NON_AUTONOMOUS_L0` 使用 |
| Input Wrapper | 不可信知识区块、候选摘要、历史消息包装 | 把数据与指令分离，不提高数据的信任等级 |
| 普通生成模板 | 文案、图像、视频模板 | 业务模板资产，不自动获得 Harness 治理优先级 |

Assistant Delivery、Role、Frozen Execution Contract、ActivatedSkill 和 Persona Profile 可以引用各自领域的不可变版本，但其领域真理源不迁入 Prompt 引擎。Prompt 引擎负责解析资产，不拥有这些领域定义。

## 版本选择与失败策略

```text
运行画像声明 promptKey + exactVersion
→ PromptVersionCache.requireVersion
→ 校验发布状态和 content hash
→ 返回不可变资产快照
→ PromptInvocationGateway 按调用画像装配
```

新执行在冻结后不得因“当前激活版本”变化而漂移；恢复和重试继续使用已冻结的精确版本。版本不存在、hash 不匹配或生命周期不可用时必须失败，不增加代码正文 fallback、旧版本兼容层或 last-known-good 隐式降级。

## 与现有代码的关系

- `PromptVersionCache` 继续承担数据库 Prompt 版本的两级缓存和 hash 校验。
- `PromptTemplateService` 是智能层解析已发布资产的门面，不承担最终消息装配。
- `PromptAssembler` 和目标 `PromptInvocationGateway` 消费精确资产版本并生成 `CompiledSystemPrompt` / `PromptEnvelope`。
- `aaf.harness.constitution` 与 `aaf.context.summary` 已是版本化 ENGINE Prompt；后续 Identity/TaskLoop 和其他 L0 Function Contract 应采用相同治理方式。
- 业务代码不得绕过 Envelope 直接把 Prompt 引擎正文与用户输入拼成裸 `LlmClient` 消息。

## 相关文档

- [PromptEnvelope 与模型调用装配设计](../../intelligent/bak/core/prompt.md) — 运行时分层、信任、任务循环和最终调用快照
- [五层智能架构](../../intelligent/architecture.md) — L0–L4 状态责任和模型调用位置
