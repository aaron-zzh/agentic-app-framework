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

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：AAF-105 #10502（共用 `GenerateOptions` 映射）
- **范围**：
  - 流式消费侧区分推理通道与正文通道，正文不因开启思考而断流。
  - `AiModel.enableThinking` 由"全局默认 false"改为按模型能力决定；路由侧沿用既有 `FEATURE_REASONING_REQUIRED` 与 `ModelSelectionRequirement.reasoning()`，不新增判定逻辑。
  - 非思考型模型不得被强制开启。
- **完成标准**：思考型模型开启思考后正文仍正常流式；非思考型行为不变；`compile` 通过。

### #10602 思考参数纳入配置并透传

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10601
- **范围**：
  - `thinkingBudget` / `reasoningEffort` 纳入 `AiModel` 配置，透传到 `GenerateOptions`；未配置时不下发该字段。
  - 审计链复用既有 `PromptEnvelopeCaptureMiddleware`（已记录两个参数），不新建审计路径。
- **完成标准**：参数进入 PromptEnvelope；未配置时请求体无对应字段；`compile` 通过。

### #10603 思考内容不外发

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10601
- **范围**：
  - `THINKING_BLOCK_*` 在 `AgentScopeEventMapper` 层拦截并注明原因（结构性保证，不在 AG-UI 投影层过滤）。
  - 可公开 reasoning 摘要预留租户级开关，默认关闭；阶段感知由 `STEP_*` / Activity 表达。
  - 与 AAF-104 #10401 是同一约束，两者只实现一次，先落地者为准。
- **完成标准**：公共事件流不含思考内容，新增 converter 也无法绕过；`compile` 通过。

### #10604 推理块回放接入

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10601
- **范围**：
  - 替换 `AgentScopeMessageMapper` 中 `AgentMessage.Role.REASONING` 直接抛异常的分支，映射为 AgentScope `ThinkingBlock`。
  - 保持既有不变量：只能由适配器从上一轮模型输出回放，禁止调用方构造，禁止被上下文压缩器裁剪或改写。
  - 回放内容不进入外发路径（与 #10603 一致）。
- **完成标准**：o1/DeepSeek-R1 类模型多轮不丢推理块；压缩器与外发路径均不触碰回放内容；`compile` 通过。

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
