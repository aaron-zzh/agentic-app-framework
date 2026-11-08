---
level: Practice
layer: Principle
purpose: 确定 Web 与设备端实时语音对话统一走 Omni Realtime v1，并通过服务端委托工具调用助理完成需要工具的请求
status: proposed
version: 1.0.0
date: 2026-11-08
author: AaronZZH & Kiro
changelog:
  - 2026-11-08 | 1.0.0 | 初稿：方向由人类确认，委托工具契约待 architect 设计
---

# ADR-010: 实时语音统一走 Omni Realtime，需要工具时委托助理执行

```yaml
status: proposed
date: 2026-11-08
deciders: [协调者]
consulted: []
informed: []
related-tasks: [AAF-120 / #12028–#12033]
```

## Context and Problem Statement

对话框工具栏的电话图标已经走 Omni 链路，设备端同时存在两条语音链路。需要确定后续的统一方向，避免两条链路各自演进。核对代码得到的现状：

- Web：电话图标 → `OmniVoiceAdapter` → `/ws/realtime-conversation`（`omni-realtime.v1`）→ DashScope Omni Realtime（`qwen3.5-omni-plus-realtime`）。
- 设备：`/api/device/realtime-conversation`（Omni v1）与 `/api/device/voice/session`（`cascade v3`，ASR → 助理 → TTS）并存。网页端的 cascade 会话（`/ws/voice/session`）未实现。
- Omni 会话只向模型注册了 `knowledge_search` 一个工具；助理在文字对话里的工具集、技能路由、委派与任务规划不参与。
- Omni v1 设计明确不做会话持久化，通话内容不进入对话历史。
- Omni v1 禁止客户端声明工具、助理、模型与知识范围，这些由服务端解析。

纯 Omni 的能力上限太低，ASR + LLM + TTS 在网页端又缺用户身份下的大脑适配，需要现在划定方向。

## Decision Drivers

- 语音交互对首包延迟敏感，闲聊不应为工具能力付出额外延迟
- 工具、技能、记忆与权限只维护一份，不为语音另建并行抽象
- 工具范围与调用身份必须由服务端决定，延续 Omni v1 的边界
- AAF 未发布，不保留兼容层与双路径
- 语音场景不适合确认类操作与长耗时任务

## Considered Options

- 纯 Omni（现状）
- ASR + LLM + TTS 级联
- Omni + 助理工具委托

## Decision Outcome

**Chosen option**: "Omni + 助理工具委托"，理由：闲聊保持 Omni 的低延迟与可打断，需要工具的请求交给助理，用助理自己的模型与完整工具集执行，工具与权限只在助理侧维护一份。

Web 与设备端的实时对话后续都调整为走这条链路。

方向性约定（具体契约由 architect 设计）：

- 服务端向 Omni 会话注册少量委托工具，而不是把助理的工具逐个映射给 Omni。委托工具的定义、调用身份、可用范围均由服务端解析，客户端不能声明。
- 委托执行以调用主体的身份进行：Web 为登录用户，设备为设备所有者，积分计入同一主体。
- 先开放只读能力。涉及写入的操作不靠语音确认：Web 用界面确认卡片，设备端暂不开放。
- 耗时长的请求立即返回"已开始处理"，结果走任务通道（见 [ADR-008](ADR-008-task-execution-shape-and-streaming.md)），不在通话中阻塞等待。
- 委托结果需要适合口述；打断（`response.cancel`）必须同时取消进行中的委托执行。

### Positive Consequences

- 闲聊延迟与纯 Omni 相同，不受工具链路影响
- 助理的工具、技能与权限不为语音重复实现
- Web 与设备共用同一会话核心，行为一致

### Negative Consequences

- 被委托的轮次同时用到两个模型，延迟、成本与口径一致性都比单模型差；Omni 复述助理结果时可能改写
- 委托期间需要先说一句过渡语，否则会有静默
- 积分要同时结算 Omni 音频用量与助理模型用量
- 打断需要贯通到助理执行，实现面比 `knowledge_search` 的取消大

### Reversal Triggers（反向选择触发条件）

仅当出现以下之一时考虑回切：

1. Omni 模型的工具调用稳定性不足，委托工具经常漏调或误调
2. 委托轮次的延迟使体验明显劣于级联方案
3. 出现必须指定主模型、自选音色或转写需完整进入对话线程的场景

## Pros and Cons of the Options

### 纯 Omni（现状）

- Good: 已实现，延迟最低，可打断
- Bad: 只有 `knowledge_search`，无法使用助理的工具与技能；不落库；推理用 Omni 自身模型而非助理主模型

### ASR + LLM + TTS 级联

- Good: 完整助理能力，模型与音色可选，可拿到字级时间戳，容易走现有对话线程
- Bad: 首包延迟更高；网页端的用户身份大脑适配未实现；需要自行处理句切分与打断

### Omni + 助理工具委托

- Good: 见 Positive Consequences
- Bad: 见 Negative Consequences

## More Information

- 现有协议与会话核心：`docs/task/v0.13/AAF-120/design.md` 的 Omni Realtime v1 章节
- 待核实：
  - `AssistantExecutionService` 能否被 Omni 会话进程内调用（AAF-120 dev-log 记过它缺少截断助手历史的接口）
  - `qwen3.5-omni-plus-realtime` 的工具调用稳定性与能否并行调用
  - 语音轮次是否写入对话线程（目前设计为不持久化）
- 未决事项：设备端 `cascade v3` 是保留还是下线。本 ADR 不决定，需另行确认；`cascade v3` 在确认前保持原样。
- 后续动作：
  - 立用户故事，由 architect 设计委托工具契约、身份与范围、持久化方式、长任务处理；属 🔴 高风险（权限范围与架构），需人类审核后再开发
  - 与 AAF-123 无关：对话消息的文字转语音仍走 `POST /api/voice/tts/stream`
