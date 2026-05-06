---
level: Practice
layer: Model
purpose: AAF 前端 Web 技术选型与决策记录
status: published
version: 1.0.0
date: 2026-05-05
author: AaronZZH
gains:
  - 了解前端各技术选型的决策依据
  - 新成员能快速理解技术栈选择原因
---

# 前端技术选型（webui）

## 技术栈总览

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 框架 | Next.js | 16 | App Router、RSC、SSR/SSG |
| UI 库 | React | 19 | 并发特性、Server Components |
| 语言 | TypeScript | 5.x | 全量类型安全 |
| 编辑器 | Lexical | - | Meta 开源富文本编辑器，可扩展 |
| 实时通信 | SSE | - | 流式对话接收 |
| 实时协作 | Yjs (CRDT) | - | 多人协同编辑 |
| 样式 | Tailwind CSS | - | 原子化 CSS |
| 颜色系统 | OKLCH | - | 感知均匀色彩空间，主题一致性 |
| 包管理 | pnpm | - | Monorepo workspace |
| 构建 | Nx | - | 单代码库任务编排 |

## 关键决策记录

### 为什么选 Next.js App Router

- RSC（React Server Components）减少客户端 JS 体积
- App Router 原生支持流式渲染，与 SSE 流式对话契合
- 文件系统路由简化页面组织

### 为什么选 Lexical 而不是 Slate/TipTap

- Meta 出品，活跃维护，性能优于 Slate
- 插件架构灵活，支持 AI Tool 直接操作文档节点
- 与 React 19 并发特性兼容性好

### 为什么用 SSE 而不是 WebSocket 接收流

- 流式对话是单向推送，SSE 语义更准确
- 浏览器原生支持，无需额外库
- WebSocket 保留用于多人协作实时同步

### 为什么选 OKLCH 颜色系统

- 感知均匀，亮度调整不失真
- 原生支持 P3 广色域
- 详见 [为什么选 OKLCH](../../../explanation/why-oklch.md)

## 目录结构

```text
apps/webui/
  app/          Next.js App Router 页面
  components/   共享 UI 组件
  lib/          工具函数、API 客户端
  hooks/        自定义 React Hooks
```

> 详细 UI 设计规范见 [UI 设计](../../ui/)
> 语义组件与 DSL 渲染见 [对话式交互设计](../../framework/engine/conversational-interaction.md)
