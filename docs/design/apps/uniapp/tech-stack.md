---
level: Practice
layer: Model
purpose: AAF 小程序/APP 技术选型与决策记录
status: draft
version: 0.1.0
date: 2026-05-05
author: AaronZZH
gains:
  - 了解小程序/APP 端技术选型方向
  - 新成员能快速理解多端适配策略
---

# 小程序/APP 技术选型（uniapp）

> 当前状态：待开发。本文档记录技术选型方向，待开发启动时细化。

## 技术栈总览

| 类别 | 技术 | 说明 |
|------|------|------|
| 框架 | UniApp | 一套代码，编译到微信小程序 / H5 / iOS / Android |
| 语言 | Vue 3 + TypeScript | Composition API |
| UI 组件 | uni-ui | 官方组件库，多端适配 |
| 状态管理 | Pinia | Vue 3 官方推荐 |
| 实时通信 | SSE / WebSocket | 流式对话，按平台能力选择 |

## 关键决策记录

### 为什么选 UniApp 而不是 React Native / Flutter

- 微信小程序是核心目标平台，UniApp 对微信生态支持最成熟
- Vue 3 语法与 webui 的 React 差异可接受，团队学习成本低
- 一套代码覆盖小程序 + H5 + APP，减少维护成本

### 多端统一策略

- 语义组件和 DSL 与 webui 共用同一套后端定义
- 适配层只处理渲染差异，业务逻辑不重复
- 详见 [对话式交互设计 — 多端统一](../webui/tmp/conversational-interaction.md)

## 与 webui 的关系

| 维度 | webui | uniapp |
|------|-------|--------|
| 目标平台 | 桌面 Web（主入口） | 微信小程序 / APP |
| 交互模式 | 对话 + 工作区双栏 | 对话优先，轻量卡片 |
| 组件来源 | 同一套语义组件 DSL | 同一套语义组件 DSL |
| 渲染引擎 | Next.js / React | UniApp / Vue 3 |
