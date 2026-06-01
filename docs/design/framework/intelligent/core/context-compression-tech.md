---
level: Practice
layer: Model
purpose: 输入前上下文压缩的逻辑与技术实现——预算模型、压缩策略、三级动作、配置体系与调用接线
status: draft
version: 1.0.0
date: 2026-06-01
author: AaronZZH & Kiro
---

# 输入前上下文压缩

> 运行过程的可观测事件（`CONTEXT_COMPRESSION_STARTED/COMPLETED`）通过统一事件桥推送，见 [Agent 运行状态推送](../agent/agent-run-events-tech.md)。

## 解决的问题

每次调用 LLM 前，对话历史可能超过模型上下文窗口，或虽未超窗但携带大量低价值内容导致 token 浪费与延迟上升。上下文压缩在「消息送进模型之前」按目标模型的窗口做预算核算与裁剪，保证不超窗、控成本，且行为可被系统配置动态调节。

核心定位：**压缩只发生在输入侧、调用前一刻，对调用方透明**。调用方拿到的是一份已经裁剪好、保证可继续推理的消息列表。

## 第一性原理

- **预算先行**：窗口是硬约束。先算清「这次最多能放多少输入 token」，再决定怎么裁，而不是先裁再看够不够。
- **分级递进**：能用便宜手段解决就不用贵的。规则裁剪（零成本）→ 丢弃旧历史（零成本）→ 摘要（一次额外 LLM 调用，最贵）逐级触发，前一级不够才进下一级。
- **保护近端**：最近若干轮和系统提示词承载当前任务意图，永远优先保留；被压缩的总是最早、最可被推断出来的内容。
- **降级不阻断**：压缩是为可靠性服务的，它自己不能成为新的故障点或阻塞点。任何一级失败都回退到上一级的结果继续跑。

## 整体流程

入口是 `ContextPreprocessor.prepare(ContextPreparationRequest)`，返回 `ContextPreparationResult`（裁剪后消息 + 预算 + 前后 token + 执行的动作列表）。

```text
prepare(messages, modelId, userId, policy)
  │
  ├─ settings.enabled() == false 或 messages == null → 原样返回（reason=disabled）
  │
  ├─ 计算 ContextBudget（按模型窗口 + 策略）
  ├─ tokenBefore = 估算(messages)
  ├─ messages 数 < 阈值 且 tokenBefore < 触发线 → 原样返回（reason=below-threshold）
  │
  ├─ 发 CONTEXT_COMPRESSION_STARTED 事件
  │
  ├─ ① 规则裁剪超大单条消息（RULE_TRUNCATE_LARGE_MESSAGE）
  ├─ ② 丢弃旧历史，保留 system + 最近 lastKeep 条（DROP_OLD_HISTORY）
  ├─ 若仍超触发线 且 启用摘要 →
  │     ③ 摘要早期历史（SUMMARIZE_HISTORY，带超时保护）
  │
  ├─ tokenAfter = 估算(prepared)
  └─ 记录 ContextCompressionLogEvent + 发 CONTEXT_COMPRESSION_COMPLETED 事件
```

## 预算模型

`ContextPolicyService.budget(...)` 产出 `ContextBudget`，是整个压缩的标尺：

```text
contextWindow   = 模型的 contextWindow（缺省取配置 defaultContextWindow，默认 128000）
reservedOutput  = 模型的 maxTokens（缺省取配置 reservedOutputTokens，默认 4096）
fixedPrompt     = 配置 fixedPromptBudget（系统提示词/工具定义等固定开销，默认 4000）

inputBudget     = max(1024, contextWindow − reservedOutput − fixedPrompt)
triggerTokens   = max(512, floor(inputBudget × ratio))
```

`ratio` 与 `lastKeep` 由策略决定（见下）。`inputBudget` 是「本次输入最多可用 token」，`triggerTokens` 是「达到多少就开始压」。两者分离的意义：留出压缩反应空间，不要等到逼近硬上限才动手。

token 估算由 `ContextTokenEstimator` 承担，采用 `字符数 / 2.5 + 每条消息 5 token 开销` 的快速近似。它是启发式而非精确分词，目的是低成本地驱动预算判断，不追求与计费 token 完全一致。

## 压缩策略

`ContextPolicy`（`balanced` / `aggressive` / `preserve-recent` / `full-detail`）影响两个参数：

| 策略 | 触发比例 ratio | lastKeep 保留条数 | 取向 |
|------|:-:|:-:|------|
| BALANCED | 配置值（默认 0.5） | 配置值（默认 12） | 默认均衡 |
| AGGRESSIVE | 0.4 | min(配置, 8) | 更早压、留更少，省 token |
| PRESERVE_RECENT | 0.7 | max(配置, 30) | 晚压、留更多近端 |
| FULL_DETAIL | 0.9 | max(配置, 40) | 几乎不压，保细节 |

策略来源优先级：请求显式传入 `policy` > 配置 `defaultPolicy`。`ContextPolicy.from(String)` 容错解析，未知值回落 `BALANCED`。

## 三级压缩动作

按成本从低到高依次尝试，记录在 `ContextCompressionAction` 列表中：

**① 规则裁剪超大消息（RULE_TRUNCATE_LARGE_MESSAGE）**
单条消息字符数超过 `largeInputCharThreshold`（默认 8000）时，只保留前 `rulePreviewChars`（默认 1600）字符并追加裁剪标记。针对「贴了一大段日志/数据」这类单点膨胀，零成本。

**② 丢弃旧历史（DROP_OLD_HISTORY）**
当总量仍超 `triggerTokens` 且消息数足够时，保留首条 `SystemMessage` + 最近 `lastKeep` 条，中间插入一条占位说明告知模型「早期上下文已省略」。保护系统提示词与近端轮次。

**③ 摘要早期历史（SUMMARIZE_HISTORY）**
规则裁剪后仍超线、且 `enableSummary=true` 时，把「保护区之外的早期消息」交给摘要模型压成一段摘要 `SystemMessage`，拼回保留区。摘要模型由 `summaryModelId` 指定，为空则复用本次主模型。摘要提示词（system/user 模板）可配置，user 模板支持 `${budgetTokens}` 与 `${messages}` 占位。

摘要由 `ContextSummarizer` 接口抽象，默认实现 `SpringAiContextSummarizer` 走 `DynamicChatClientFactory` 同步调用。

### 摘要的超时保护

摘要是唯一一级会真正发起外部 LLM 调用的动作，也是唯一可能长时间阻塞的环节。第一性原理要求「压缩不能成为新的阻塞点」，因此摘要调用被 `summaryTimeoutMs`（默认 8000ms）约束：

```java
var summary =
        CompletableFuture.supplyAsync(() -> contextSummarizer.summarize(request))
                .get(config.summaryTimeoutMs(), TimeUnit.MILLISECONDS);
```

超时、异常、空结果都走同一个 graceful fallback：返回规则裁剪 + 丢弃旧历史后的结果继续推理，绝不让摘要把整条 LLM 请求拖死。

## 配置体系

参数三层下沉，下层覆盖上层默认：

```text
AiProperties.ContextConfig（代码默认值 + 摘要提示词模板）
        ↓ 被覆盖
SysConfigKeys.Ai.CONTEXT_*（15 个 sys_config 键）
        ↓ 运行时读取
SystemConfigContextSettingsProvider（@Primary，读 sys_config，缺失回落 AiProperties）
        ↓ 种子
db/seed/v10__ai_context_config_seed.sql（初始库内配置）
```

`ContextSettingsProvider` 是参数读取的统一接口：

- `SystemConfigContextSettingsProvider`（aaf-api）：标注 `@Primary`，从 `sys_config` 实时读取，使配置可热调。
- `AiPropertiesContextSettingsProvider`（aaf-framework）：基于配置文件的回落实现，供无 sys_config 的场景（如框架层独立测试）使用。

> 实现说明：`@Primary` 是为解决「两个 `ContextSettingsProvider` Bean 并存」的确定性选择。早期仅靠 `@ConditionalOnMissingBean` 挂在被组件扫描的 `@Service` 上，其生效依赖扫描顺序，不可靠，可能导致两 Bean 同时注册而注入歧义。`@Primary` 让选择与扫描顺序无关。

## 调用接线

压缩有两条接入路径，对应两种 LLM 调用方式：

**Spring AI 直连链路**——`ResilientChatService` 在同步与流式调用前都先 `prepareContext(...)`：

```java
var prepared = prepareContext(messages, modelId, ownerId);
// doCall(prepared) / doStream(prepared)，主模型与降级模型共用同一份 prepared
```

**AgentScope 运行时链路**——`AgentScopeRuntime` 给 ReActAgent 挂 `AutoContextHook`，并用 `AafAutoContextMemoryAdapter` 把同一套预算映射成 AgentScope 的 `AutoContextConfig`（`ContextPolicyService.toAutoContextConfig(model, policy)`）。即 AAF 的预算口径在两条链路上保持一致，区别只是执行者：直连链路由 `ContextPreprocessor` 裁剪，AgentScope 链路由其 `AutoContextMemory` 渐进式压缩。

## 可观测

每次 `prepare` 都发布 `ContextCompressionLogEvent`（含 policy、窗口、inputBudget、tokenBefore/After、消息数前后、动作列表、摘要模型、耗时、reason），并打印一行结构化日志，便于离线分析压缩效果。当实际发生压缩（动作非 `NONE`）时，额外发 `CONTEXT_COMPRESSION_COMPLETED` 运行事件推给前端。

## 关键类清单

| 类 | 模块 | 职责 |
|------|------|------|
| `ContextPreprocessor` | aaf-framework | 压缩主流程编排 |
| `ContextPolicyService` | aaf-framework | 预算计算 + 策略参数 + AgentScope 配置映射 |
| `ContextBudget` / `ContextPolicy` | aaf-framework | 预算值对象 / 策略枚举 |
| `ContextTokenEstimator` | aaf-framework | token 快速估算 |
| `ContextSummarizer` / `SpringAiContextSummarizer` | aaf-framework | 摘要抽象 / Spring AI 实现 |
| `ContextSettings(Provider)` | aaf-framework | 运行参数读取接口 |
| `AiPropertiesContextSettingsProvider` | aaf-framework | 配置文件回落实现 |
| `SystemConfigContextSettingsProvider` | aaf-api | sys_config 实现（@Primary） |
| `AafAutoContextMemoryAdapter` | aaf-framework | AgentScope AutoContextMemory 适配 |
| `ContextCompression{Action,LogEvent}` | aaf-framework | 动作枚举 / 日志事件 |

## 已知局限与后续

- token 估算为字符近似，与真实分词存在偏差；对预算判断足够，但不宜直接当计费依据。
- 摘要超时采用 `CompletableFuture` + `get(timeout)`，超时后底层调用会在公共线程池中跑完才结束（结果被丢弃），属于有界但非即时取消，后续可换显式可取消执行器。
- 摘要质量依赖提示词模板与所选摘要模型，建议生产环境为摘要单独配置轻量模型以平衡成本与效果。
