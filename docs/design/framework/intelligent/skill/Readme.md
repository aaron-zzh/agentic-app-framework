---
level: Practice
layer: Model
purpose: 索引跨层能力单元 Skill 的版本模型、渐进加载与工具解析设计
status: draft
version: 1.1.0
date: 2026-08-25
author: Kiro
tags:
  - Skill
  - 目录索引
dependencies:
  - ../architecture.md
  - ../action-governance.md
scope:
  includes:
    - Skill 的在线版本模型、渐进加载与发布治理索引
    - Skill 分层激活、工具声明与动作治理引用索引
  excludes:
    - 工具调用时的动作授权与门禁（属顶层 action-governance）
    - Skill 管理界面的增删改查设计
gains:
  - 能定位 Skill 版本、加载与工具解析的设计文档
  - 能判断某项能力约束属于 Skill 声明还是运行时授权
---

# Skill 跨层能力单元设计

> Skill 是意图路由使用的版本化能力单元：由 L3 的 Role 组织，被 L2 的执行画像消费，拥有独立版本与发布模型，因此不归属单一层级。

两条不可混淆的边界：**能力声明不等于动作获权**——Skill 只声明工具候选，最终可见性与每次调用由动作治理决定。**Skill 只固定能力方向**，不预判任务复杂度，也不能扩大 Role、助理或用户的权限。

## 文档列表

| 文档 | 内容 | 状态 |
|---|---|---|
| [skill.md](skill.md) | Skill 在线版本模型、渐进加载、references、知识绑定与发布治理 | 草案 · 契约已细化，含实现偏离 |
| [skill-tool-resolution.md](skill-tool-resolution.md) | 分层激活、角色与内置两层合并、Skill 侧工具声明；授权交集链接顶层契约 | 草案 · 契约已细化，含目标态 |

## 与其他目录的关系

| 目录 | 关系 |
|---|---|
| [../action-governance.md](../action-governance.md) | 有效工具的完整交集与调用门禁在该文档定义，本目录只定义 Skill 侧声明 |
| [../assistant/](../assistant/Readme.md) | Role 组织 Skill，路由决定本次激活结果 |
| [../agent/](../agent/Readme.md) | 激活结果决定 L2 的可见工具集 |
| [../cognition/](../cognition/Readme.md) | Skill 的知识绑定限定可检索范围 |
