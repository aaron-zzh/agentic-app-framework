---
level: Overview
layer: Model
purpose: 定义 Assistant 专题设计的边界，并索引 Assistant、Skill 与执行编排设计
status: draft
version: 1.0.0
date: 2026-08-16
author: Kiro
scope:
  includes:
    - Assistant 的会话级职责与运行时设计
    - Assistant 路由、Skill 装配与上下文边界设计
gains:
  - 能找到 Assistant 领域的专项设计
  - 能区分 Assistant 路由与 Skill 执行知识的职责
---

# Assistant 专题设计

> 本目录描述五层智能架构中 Assistant 的会话级职责、路由与执行编排边界；目标领域模型以 [五层智能架构](../architecture.md) 为准。

## 文档列表

1. [Assistant 功能设计](assistant-design.md) — Assistant 的用户入口、认知循环和功能边界。
2. [Assistant 技术方案](assistant-tech.md) — Assistant 会话、并行实例和输入缓冲的运行时设计。
3. [Skill 渐进加载与在线知识边界设计](skill-progressive-loading-design.md) — Skill 正文、references、知识库、授权与版本模型设计。
