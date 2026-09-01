---
level: Practice
layer: Product
purpose: 拆分 AAF-106 思考模式三层边界与推理块回放的通道分离、参数化、披露拦截任务
status: draft
version: 1.0.0
date: 2026-09-01
author: AaronZZH & Kiro
tags:
  - AAF-106
  - 思考模式
  - 推理块
  - 技术任务
related:
  - ../../../design/audit/2026-09-01-harness-landing-plan.md
gains:
  - 能在开启思考模式的同时保证正文正常流式
  - 能让多轮推理模型不丢推理块，且思考内容不外发
---

# AAF-106 思考模式与推理块回放任务

## 任务约束

- 技术真理源：[Harness 落地计划 · 思考模式的三层边界](../../../design/audit/2026-09-01-harness-landing-plan.md)。
- 三层互不代替：**模型侧思考开关**（质量）、**思考内容外发**（安全）、**推理块回放**（多轮连续性）。
- 风险等级：🔴 高（披露边界 + 模型行为变更）。
- 模型侧当前默认关闭的理由是技术性的（思考型模型流式时推理进 `reasoning_content`、`content` 为空），不是安全性的——必须先解决通道问题再开启。
- **阶段约束**：断言补进既有测试，不新增测试文件；不执行 `check` / `acceptance`；准出最低要求 `pnpm nx compile service` 通过。

## 技术任务

### #10601 分离 reasoning_content 与 content 双通道

- **状态**：✅ 已完成（2026-09-02，范围因 AAF-105 前提变化重新定义）— developer-service
- **负责人**：developer-service
- **依赖**：AAF-105 #10502（共用 `GenerateOptions` 映射）
- **⚠️ 范围推翻说明**：原任务描述的"思考型模型流式时 `reasoning_content`/`content` 通道冲突导致正文断流"问题，核实确认是 **Spring AI 直连链路**（`DynamicChatClientFactory`/`ResilientChatService`）特有的技术限制——`AiModel.enableThinking` 目前唯一消费方是 `DynamicChatClientFactory`，服务对象是已被 AAF-105 排除在 L0 重构范围外的 `ResilientChatService` 四个非 L0 用户。AAF-105 已把 L0（及所有自主 execution）完全迁移到 AgentScope core，核实 `ReasoningContext`（`agentscope-core/.../accumulator/ReasoningContext.java`）用独立累加器（`TextAccumulator`/`ThinkingAccumulator`）并行处理正文与思考内容，`TextBlock`/`ThinkingBlock` 是两个独立的事件流内容块类型——**core 路径从设计上不存在"正文断流"问题**，这是 provider 层（`DashScopeChatModel` 等官方实现）的责任，不需要 AAF 写代码解决。
- **范围**（重新定义）：
  - ✅ 核实 core 路径无需分离通道（已有独立累加器保证）。
  - ✅ `AiModel` 新增 `thinkingBudget`/`reasoningEffort` 两个持久化字段，供 #10602 使用。
  - ✅ 非思考型模型不被强制开启：`L0ReActAgentFactory.generateOptions(AiModel)` 仅在 `enableThinking=true` 时才构造并下发 `GenerateOptions`。
- **完成标准**（重新定义）：思考型模型开启思考后正文仍正常流式（core 路径天然保证，非 AAF 代码职责）；非思考型行为不变（`generateOptions` 返回 `null` 时 `ReActAgent` 使用 core 默认值）；`compile` 通过。
- **实际结果**：`pnpm nx compile service` BUILD SUCCESS。原范围（"分离双通道"）技术前提不成立，不做该项工作。

### #10602 思考参数纳入配置并透传

- **状态**：✅ 已完成（2026-09-02）— developer-service
- **负责人**：developer-service
- **依赖**：#10601
- **范围**：
  - ✅ `AiModel` 新增 `thinkingBudget`（Integer）/`reasoningEffort`（String）两个字段及对应数据库列（`ai_model.thinking_budget`/`ai_model.reasoning_effort`）。
  - ✅ **偏离阶段约束（人类已授权）**：新增列直接改 `v2__ai_schema.sql`（`ai_model` 表原始定义处），而非按此前约定追加进 v16——开发阶段允许直接改原 SQL 文件，人类已明确授权，记录为本次偏离。
  - ✅ `L0ReActAgentFactory.build(AiModel)` 透传两个参数到 `GenerateOptions`：仅思考型模型（`enableThinking=true`）下发；未配置字段（`null`）时不下发对应 `GenerateOptions` 字段；两者均未配置时整体不设置 `generateOptions`（沿用 core 默认值）。
  - ✅ 审计链复用既有 `PromptEnvelopeCaptureMiddleware`：核实其已记录 `thinkingBudget`/`reasoningEffort`（`values.put("thinkingBudget", options.getThinkingBudget())` 等），未新建审计路径。
- **完成标准**：参数进入 PromptEnvelope；未配置时请求体无对应字段；`compile` 通过。
- **实际结果**：`pnpm nx compile service` BUILD SUCCESS。

### #10603 思考内容不外发

- **状态**：✅ 已完成（先前由 AAF-104 #10401 落地）— 2026-09-02 核实确认
- **负责人**：developer-service
- **依赖**：#10601
- **范围**：
  - ✅ `THINKING_BLOCK_START/DELTA/END` 已在 `AgentScopeEventMapper` 层拦截（`case THINKING_BLOCK_START, THINKING_BLOCK_DELTA, THINKING_BLOCK_END -> Optional.empty();`），归类"安全忽略：思考内容不外发"。
  - 可公开 reasoning 摘要预留租户级开关，默认关闭——**未实现**，当前直接全量拦截，无摘要能力（超出"两者只实现一次"的最小范围，暂不新增）。
  - 与 AAF-104 #10401 是同一约束，本任务确认先落地者（AAF-104）已满足完成标准，不重复实现。
- **完成标准**：公共事件流不含思考内容，新增 converter 也无法绕过；`compile` 通过。
- **实际结果**：核实确认既有实现已满足，无新增代码。

### #10604 推理块回放接入

- **状态**：✅ 已完成（2026-09-02）— developer-service
- **负责人**：developer-service
- **依赖**：#10601
- **范围**：
  - ✅ 替换 `AgentScopeMessageMapper` 中 `AgentMessage.Role.REASONING` 直接抛异常的分支，映射为携带 `ThinkingBlock` 的 `ASSISTANT` 消息（`MsgRole` 无对应 `REASONING` 枚举值，推理块本就是模型上一轮输出内容的一部分）。
  - ✅ 核实既有不变量已天然满足：`DefaultHybridContextCompressor.isProtected(...)` 已将 `Role.REASONING` 列为受保护角色（不裁剪不去重），此前因映射层直接抛异常从未被真正触发；`AgentScopeEventMapper` 的 `THINKING_BLOCK_*` 拦截（#10603，AAF-104 #10401 已落地）保证外发路径不受影响。
  - ✅ **附带完成**：`AgentScopeMessageMapper.attachmentBlock`（原 `imageBlock`）从官方 `ImageBlock`（向后兼容保留的旧类型）迁移到统一 `DataBlock`（官方文档标注新代码应优先使用）。核实 `AgentMessage.AttachmentType` 当前仅有 `IMAGE` 一个值，非能力缺口修复，是风格优化；全仓核实 `ImageBlock`/`AudioBlock`/`VideoBlock` 无其它代码引用（仅一处注释提及）。
- **完成标准**：o1/DeepSeek-R1 类模型多轮不丢推理块；压缩器与外发路径均不触碰回放内容；`compile` 通过。
- **实际结果**：`pnpm nx compile service` BUILD SUCCESS。

## 新增任务

> 开发过程中发现需要新增的任务，由开发者提出，协调者评估后写入

## 评审状态（🔴 高风险适用）

| 阶段 | 执行次数 | 最后执行 | 状态 | 必须 |
|------|---------|---------|------|------|
| product（需求细化） | 0 | — | ⏳ PENDING | 🔴 是 |
| architect（技术设计） | 0 | — | ⏳ PENDING | 🔴 是 |
| designer（UI 审查） | — | — | — | 不涉及前端 |
| developer（编码） | 0 | — | ⏳ PENDING | 🔴 是 |
| architect（代码审查） | 0 | — | ⏳ PENDING | 🔴 是 |
| tester（验收测试） | 0 | — | ⏳ PENDING | 🔴 是 |
| qa（过程审计） | 0 | — | ⏳ PENDING | 🔴 是 |
