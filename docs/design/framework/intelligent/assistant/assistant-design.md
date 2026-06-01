---
level: Practice
layer: Model
purpose: Layer 3 助理层 Assistant——面向人的交互入口功能设计
status: draft
version: 1.0.0
date: 2026-05-28
author: AaronZZH
---

# 助理层 Assistant 功能设计

> 会话级，面向人的交互入口，核心编排单元。

## 定位

Assistant 是用户唯一交互入口。持有人格、角色、技能、工具路由，负责意图理解后调度 Agent 执行，支持多实例 fork 并行加速。

## 核心能力

- **前注意分流**：规则+小模型快速路由（<50ms），简单请求不走 Agent
- **意图理解**：澄清意图优先于执行，通过最少问题快速收敛
- **情感感知**：识别用户情绪，动态调整回应风格和信息密度
- **技能匹配**：根据意图+关键词匹配最合适的执行路径
- **Agent 调度**：选择 Agent + 并发派发 + 结果聚合
- **多实例并行**：同 Actor + 多 Role fork 子实例，主实例协调聚合
- **输入缓冲**：执行期间接收用户追加输入（取消/修改/补充/无关）
- **记忆管道编排**：按 MemoryStrategy 决定从哪些源拉取上下文
- **置信度门控**：>0.9 自动 / 0.7-0.9 确认 / <0.7 转人工

## 组成结构

```text
Assistant = Actor + Role + MemoryStrategy

Actor（人格载体）：可复用·跨 Role
Role（能力配置）：Skill 集 + Tool 白名单，可复用·跨 Actor
MemoryStrategy：MEMORY_ONLY / KNOWLEDGE_ONLY / HYBRID / PROCEDURAL_FIRST / FULL
```

## 内置技能

| 技能 | 说明 |
|------|------|
| 自我认知 | 助手了解自己的能力边界 |
| 用户理解 | 定期分析用户行为，更新画像 |
| 自学习 | 从执行结果中提取经验 |
| 技能创建 | 高频模式自动生成新技能 |

## 认知循环

```text
情感感知 → 意图理解 → 上下文构建 → Agent 调度 → 反馈整合 → 记忆更新
```

## 多实例并行

- 主实例（协调者）长驻，绑定用户会话
- 子实例临时创建，执行完销毁，不池化
- fork 时拷贝主实例上下文只读快照
- 主实例 merge 子实例结果 + 冲突裁决

## 助理对话全流程

> 以下流程基于 `DefaultAssistantExecutor.chat()` 实现及五层智能架构设计，分场景梳理。
> 当前源码已实现步骤标注 ✅，设计中待实现标注 🔲。

### 公共前置流程（所有场景共享）

每次用户消息进入 `AssistantExecutor.chat()` 后，先走公共前置：

```text
用户消息到达
  │
  ├─ ✅ 1. 加载 Assistant 配置（AssistantDefinitionRepository）
  │       Actor（人格）+ Role（技能集 + 工具白名单）+ MemoryStrategy
  │
  ├─ ✅ 2. 会话管理（SessionManager）
  │       已有会话 → 恢复；无会话 → 创建；更新状态 PROCESSING
  │
  ├─ ✅ 3. 记录用户消息到短期记忆（ShortTermMemoryService）
  │
  ├─ 🔲 4. 前注意分流（<50ms，规则 + 小模型）
  │       简单问候 / 闲聊 → 直接回复，跳过 Agent 调度
  │       复杂任务 → 继续后续流程
  │
  ├─ 🔲 5. 情感感知（EmotionPerceptionHook）
  │       分析语气 / 操作节奏 → 推断情绪状态
  │       调整回应风格和信息密度
  │
  ├─ ✅ 6. 按 MemoryStrategy 拉取上下文（MemoryPipelineFactory）
  │       MEMORY_ONLY / KNOWLEDGE_ONLY / HYBRID / PROCEDURAL_FIRST / FULL
  │       产出 MemoryContext（P0-P5 优先级内容）
  │
  └─ ✅ 7. Skill 匹配（SkillMatchEngine）
          根据 assistantId + userMessage 匹配最合适的 Skill
          命中 → 获取绑定的 agentId + systemPrompt
          未命中 → agentId = null（走兜底逻辑）
```

---

### 场景一：简单问答 / 闲聊（无需 Agent）

适用：问候、简单事实查询、无需工具调用的对话。

```text
公共前置（步骤 1-7）
  │
  ├─ 🔲 前注意分流判断：简单请求
  │
  ├─ 🔲 直接用 Actor.systemPrompt + MemoryContext 构建提示词
  │
  ├─ 🔲 调用 Core 层 LlmClient（不走 AgentPool）
  │
  ├─ ✅ 记录响应到短期记忆
  │
  └─ ✅ 返回 AssistantResponse.success
```

**关键特征**：全程不借用 AgentPool，延迟最低（<200ms 目标）。

---

### 场景二：单技能任务（Skill 命中，单 Agent 执行）

适用：代码生成、文档撰写、数据分析等单一技能任务。

```text
公共前置（步骤 1-7）
  │
  ├─ ✅ Skill 命中 → 获取 agentId + skill.systemPrompt
  │
  ├─ ✅ 构建注入输入：
  │       preamble = skill.systemPrompt + "\n\n"
  │                + "## 上下文记忆\n" + memoryContext.toPromptSection()
  │       input = preamble + userMessage
  │
  ├─ ✅ AgentPool.borrow(definition)
  │       从池中借出 AgentScopeExecutor（内含 ReActAgent）
  │
  ├─ ✅ AgentSandbox.execute(executor, input, timeout)
  │       ReActAgent 内部认知循环（AgentScope 实现）：
  │         感知（接收 input）
  │           → 规划（LLM 推理，决定是否调用工具）
  │             → 执行（ToolCallDispatcher，受 AafToolWhitelistHook 过滤）
  │               → 循环直到无工具调用或达到步骤上限
  │       AutoContextMemory 在每轮前检测 Token 超限 → 渐进式压缩
  │       TokenMeteringHook 记录 Token 消耗
  │
  ├─ 🔲 评估（Agent 认知循环 - 待集成）：
  │       结果验证（可验证 → 自动检查；不可验证 → 标记待人工审查）
  │       置信度评估 → >0.9 自动 / 0.7-0.9 确认 / <0.7 转人工
  │
  ├─ ✅ AgentPool.release(agentId, executor)
  │
  ├─ ✅ 记录响应到短期记忆
  │
  ├─ 🔲 学习反馈（LearningFeedbackService）：
  │       执行轨迹 → TrajectoryCollector → 异步写回 Cognition
  │
  └─ ✅ 返回 AssistantResponse.success / error
```

**关键特征**：AgentScope ReActAgent 实现感知→规划→执行循环，评估和学习环节待通过 Hook 集成。

---

### 场景三：多技能并行（fork 多子实例）

适用：需要多角色协作的复杂任务（如"帮我开发用户模块"同时需要后端+前端+文档）。

```text
公共前置（步骤 1-7）
  │
  ├─ 🔲 意图理解：识别需要多 Role 并行
  │
  ├─ 🔲 主实例（协调者）fork 子实例：
  │       fork(Role-后端, contextSnapshot) → 子 Instance-1
  │       fork(Role-前端, contextSnapshot) → 子 Instance-2
  │       fork(Role-文档, contextSnapshot) → 子 Instance-3
  │       各子实例持有主实例上下文只读快照
  │
  ├─ 🔲 各子实例并行执行（走场景二流程）：
  │       子 Instance-1 → AgentPool.borrow → 执行 → release
  │       子 Instance-2 → AgentPool.borrow → 执行 → release
  │       子 Instance-3 → AgentPool.borrow → 执行 → release
  │
  ├─ 🔲 主实例 await all → ResultAggregator 聚合：
  │       冲突检测 → 裁决（主实例直接裁决）
  │       合并结果
  │
  ├─ 🔲 子实例销毁（GC 回收）
  │
  ├─ ✅ 记录聚合响应到短期记忆
  │
  └─ ✅ 返回 AssistantResponse.success
```

**关键特征**：TaskBoard 追踪子任务状态（PENDING/RUNNING/DONE/FAILED），主实例通过 Checkpoint 支持长任务恢复。

---

### 场景四：执行期用户追加输入（InputBuffer）

适用：Agent 执行耗时任务期间，用户发送新消息。

```text
Agent 执行中（场景二/三进行中）
  │
  ├─ 🔲 用户追加输入 → InputBuffer 接收并分类：
  │
  │   ┌─ 取消/中断 → 立即中断当前 Agent 执行（AgentSandbox 超时/中断）
  │   │               清理 AgentPool → 返回"已取消"
  │   │
  │   ├─ 修改指令 → 标记当前结果待废弃
  │   │             Agent 完成当前步骤后重新规划（下一个 Checkpoint 可见）
  │   │
  │   ├─ 补充信息 → 注入当前执行上下文
  │   │             Agent 通过 Checkpoint 回调检查新输入
  │   │
  │   └─ 无关/闲聊 → 排队，当前任务完成后处理
  │
  └─ 主流程继续（或重新规划）
```

**关键特征**：基于 WebSocket/SSE 双向通道，InputBuffer 是 Assistant 级别（不是 Agent 级别）。

---

### 场景五：置信度门控（低置信度转人工）

适用：Agent 执行结果置信度不足，需要人工确认。

```text
Agent 执行完成
  │
  ├─ 🔲 置信度评估（AafConfidenceHook）：
  │
  │   ┌─ > 0.9 → 自动执行，结果暂存，异步通知用户
  │   │
  │   ├─ 0.7-0.9 → 展示执行计划，等待用户确认
  │   │             HumanApprovalService 发起审批请求
  │   │             ApprovalEventStreamService 推送 SSE 事件
  │   │             用户确认 → 继续；用户拒绝 → 重新规划
  │   │
  │   └─ < 0.7 → 暂停，说明原因，转人工处理
  │               记录决策日志（决策点、选项、理由、置信度）
  │               等待人类响应（不自动超时执行）
  │
  └─ 继续后续流程（记录响应 → 返回）
```

**关键特征**：`HumanApprovalController` + `HitlApprovalGrantListener` 已有骨架，置信度评估通过 `AafConfidenceHook` 集成。

---

### 场景六：会话恢复（服务重启 / 长任务）

适用：服务重启后恢复未完成的长任务会话。

```text
服务启动
  │
  ├─ 🔲 SessionRecoveryService 扫描未完成的 AssistantCheckpoint
  │
  ├─ 🔲 恢复主实例：加载 TaskBoard + 会话上下文 + InputBuffer
  │
  ├─ 🔲 检查子任务状态：
  │       DONE → 跳过
  │       RUNNING → 查找子实例 Checkpoint → 有则恢复，无则重新 fork
  │       PENDING → 等待依赖满足后正常调度
  │
  ├─ 🔲 发布 SessionRecoveredEvent → 通知用户"之前的任务已恢复"
  │
  └─ 继续执行未完成任务
```

**关键特征**：`SessionRecoveryService` + `SessionRecoveredEvent` 已有骨架，Checkpoint 引擎提供持久化支持。

---

### 认知循环与源码映射

| 认知循环步骤 | 对应源码 / 组件 | 状态 |
|------------|---------------|------|
| 前注意分流 | 待实现（规则 + 小模型） | 🔲 |
| 情感感知 | `EmotionPerceptionHook`（设计中） | 🔲 |
| 意图理解 | `SkillMatchEngine.match()` | ✅ |
| 上下文构建 | `MemoryPipelineFactory` + `MemoryContext` | ✅ |
| Agent 调度 | `AgentPool.borrow` + `AgentSandbox.execute` | ✅ |
| 感知→规划→执行 | AgentScope `ReActAgent`（内置 ReAct 循环） | ✅ |
| 评估 | `AafConfidenceHook`（骨架存在） | 🔲 |
| 学习 | `LearningFeedbackService`（骨架存在） | 🔲 |
| 记忆更新 | `ShortTermMemoryService.append()` | ✅（短期）|
| 记忆写回 Cognition | 长期记忆写回管道 | 🔲 |

## 相关文档

- [技术方案 — Assistant](assistant-tech.md)
- [五层智能架构总览](../architecture.md)
- [Actor 模型](actor.md)
- [用户感知与个性化](../cognition/personalization.md)

## 对话场景分类

### 按对话目标

| 场景 | 描述 | 当前入口 |
|---|---|---|
| AI 助理对话 | 用户与 AI Agent 多轮对话 | `POST /agui/runs`（AgentScope AG-UI） |
| 用户间聊天 | 用户发消息给另一个用户 | `POST /api/chat/run` target=user |
| 工作流交互 | 用户触发工作流，实时查看节点执行状态 | `POST /api/workflow/run`（AG-UI SSE，独立接口） |
| Kiro 开发助手 | 用户与 Kiro CLI Agent 对话 | `POST /api/autodev/kiro/run` |

### 按 AI 对话复杂度

| 场景 | 描述 | 特征 |
|---|---|---|
| 简单问答 | 直接 LLM 回复，无工具调用 | 单轮或多轮，无 ReAct 循环 |
| 工具调用 | Agent 调用工具（搜索、计算、DB 查询等）| ReAct 循环，`AafToolWhitelistHook` 过滤 |
| 多 Agent 协作 | Supervisor 分发给子 Agent | 多跳，`AgentDispatcher` 编排 |
| 长任务 | 耗时任务，支持中断/恢复 | Checkpoint + InputBuffer |
| 人工介入（HITL） | 低置信度时暂停等待人工确认 | `AafConfidenceHook` + `HumanApprovalService` |

### 按会话形态

| 场景 | 描述 | 实现 |
|---|---|---|
| 单轮无状态 | 每次请求独立，不保留历史 | 新 threadId，Agent Memory 为空 |
| 多轮有状态 | 同一 threadId 保持上下文 | `ThreadSessionManager` 复用 Agent 实例，`AutoContextMemory` 保存历史 |
| 流式输出 | SSE 实时推送 token | AgentScope AG-UI 事件流 / `AgUiStreamHandler` |
| 同步返回 | WebSocket 或 REST 同步响应 | `ChatOrchestrationService.executeSync()` |

### 持久化链路（/agui/runs）

```text
用户发消息
  │
  ├─ PreCallEvent → AafTraceHook → UserMessageEvent → ChatPersistenceListener → 写用户消息到 DB
  │
  ├─ Agent 执行（ReAct 循环）
  │
  └─ PostCallEvent → AafTraceHook → ExecutionCompletedEvent
        ├─ ChatPersistenceListener    → 写 AI 回复到 DB（异步）
        ├─ LearningFeedbackService    → 更新执行统计（异步）
        └─ MemoryWriteBackListener    → LLM 抽取 → 写长期记忆（异步）
```
