---
level: Practice
layer: Model
purpose: AAF-113 后续对话式项目创作技术任务
status: draft
version: 1.0.0
date: 2026-09-05
author: AaronZZH & Kiro
---

# AAF-113 对话式项目创作任务

> 依赖 AAF-112、AAF-115 及 v0.13 项目 UI 三个阶段门全部验收通过；本轮不实现。

- [ ] **#11301** 实现现有助理的只读项目上下文与线程隔离
- [ ] **#11302** 优化 `system.role.content-creator` 与项目协作 Skill
- [ ] **#11303** 分波次开放复用 AigcProjectApi/AigcExecutionApi 的受控写 Tool
- [ ] **#11304** 实现富媒体 ToolUI、项目素材选择与结构化消息恢复
- [ ] **#11305** 完成权限、幂等、确认门、断线恢复与端到端验收

## 硬门

- 不新增项目专属 Assistant、Agent、Team 或对话端点。
- 不建立对话专属生成链；UI 与 Tool 必须产生同一种 ExecutionRun 和候选。
- 只读项目对话通过 tenant/workspace/user/project 隔离验收前，不开放写 Tool。
- 删除聊天记录不得删除项目事实。
