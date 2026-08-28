---
level: Practice
layer: Model
purpose: 索引 L0 内核层的推理能力、模型路由与 Prompt 装配设计
status: draft
version: 1.2.0
date: 2026-08-25
author: Kiro
tags:
  - L0 Core
  - 目录索引
dependencies:
  - ../architecture.md
scope:
  includes:
    - L0 内核层的职责边界与接口契约索引
  excludes:
    - 任务状态与业务副作用相关设计
gains:
  - 能定位模型调用、路由与 Prompt 装配的设计文档
---

# L0 内核层设计

> L0 提供通用思考与生成能力：模型选择、理解、推理、生成，并控制上下文与资源消耗。
> **设计边界**：请求级，无人格、无记忆，不感知具体用户与业务身份，不持有任务状态，不产生业务副作用；当前 `LlmClient` 的 `userId` 计量参数偏离见 [core.md](core.md#实现态)。

## 文档列表

| 文档 | 内容 | 状态 |
|---|---|---|
| [core.md](core.md) | L0 职责边界、对上层的接口契约、LLM 基础能力与资源计量 | 草案 · 契约已细化，部分落地 |
| [model-router.md](model-router.md) | 模型统一管理、路由决策链、动态客户端与模型偏好 | 草案 · 路由已落地，能力画像含目标态 |
| [prompt.md](prompt.md) | `PromptEnvelope` 装配、分层信任边界、任务循环与非自主调用画像 | 草案 · 契约已细化，Envelope 为目标态 |

## 与其他目录的关系

| 目录 | 关系 |
|---|---|
| [../](../Readme.md) | 主流程与调用形态由 `architecture.md` 定义，本目录只定义 L0 内部机制 |
| [../cognition/](../cognition/Readme.md) | L1 负责记忆与知识，L0 不持有任何持久认知 |
| [../agent/](../agent/Readme.md) · [../assistant/](../assistant/Readme.md) | L2/L3 是 L0 的调用方，可多次发起自主与非自主模型调用 |
