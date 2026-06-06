---
name: AAF Design System
version: alpha
description: AI 原生应用开发框架设计系统 — 界面是对话的投影，语义组件动态组装

colors:
  # 主色 — 智能蓝，传递专业与可信
  primary: "oklch(0.55 0.18 250)"
  on-primary: "oklch(1 0 0)"
  primary-container: "oklch(0.92 0.06 250)"
  on-primary-container: "oklch(0.25 0.12 250)"

  # 辅助色 — 知识紫，关联知识库与记忆系统
  secondary: "oklch(0.50 0.15 290)"
  on-secondary: "oklch(1 0 0)"
  secondary-container: "oklch(0.92 0.05 290)"
  on-secondary-container: "oklch(0.25 0.10 290)"

  # 强调色 — 行动橙，驱动用户操作
  tertiary: "oklch(0.62 0.18 55)"
  on-tertiary: "oklch(1 0 0)"
  tertiary-container: "oklch(0.93 0.06 55)"
  on-tertiary-container: "oklch(0.28 0.12 55)"

  # 置信度门控三态（AAF 核心语义色）
  confidence-high: "oklch(0.55 0.16 145)"
  confidence-mid: "oklch(0.72 0.18 80)"
  confidence-low: "oklch(0.58 0.22 25)"

  # 状态色
  success: "oklch(0.55 0.16 145)"
  warning: "oklch(0.72 0.18 80)"
  danger: "oklch(0.58 0.22 25)"
  info: "oklch(0.60 0.14 230)"

  # 中性色 — 界面骨架
  surface: "oklch(0.98 0.004 250)"
  surface-dim: "oklch(0.92 0.006 250)"
  surface-container-low: "oklch(0.96 0.005 250)"
  surface-container: "oklch(0.94 0.006 250)"
  surface-container-high: "oklch(0.90 0.008 250)"
  on-surface: "oklch(0.15 0.01 250)"
  on-surface-variant: "oklch(0.45 0.02 250)"
  outline: "oklch(0.70 0.01 250)"
  outline-variant: "oklch(0.85 0.006 250)"

  # 深色 — 对话区代码块、DSL 编辑器
  surface-dark: "oklch(0.14 0.015 250)"
  surface-dark-container: "oklch(0.20 0.015 250)"
  on-surface-dark: "oklch(0.92 0.008 250)"

typography:
  h1:
    fontFamily: "PingFang SC, Microsoft YaHei, Inter, sans-serif"
    fontSize: 2rem
    fontWeight: "700"
    lineHeight: 2.5rem
    letterSpacing: -0.02em
  h2:
    fontFamily: "PingFang SC, Microsoft YaHei, Inter, sans-serif"
    fontSize: 1.5rem
    fontWeight: "600"
    lineHeight: 2rem
    letterSpacing: -0.01em
  h3:
    fontFamily: "PingFang SC, Microsoft YaHei, Inter, sans-serif"
    fontSize: 1.25rem
    fontWeight: "600"
    lineHeight: 1.75rem
  body-lg:
    fontFamily: "PingFang SC, Microsoft YaHei, Inter, sans-serif"
    fontSize: 1rem
    fontWeight: "400"
    lineHeight: 1.75rem
  body-md:
    fontFamily: "PingFang SC, Microsoft YaHei, Inter, sans-serif"
    fontSize: 0.875rem
    fontWeight: "400"
    lineHeight: 1.5rem
  code:
    fontFamily: "JetBrains Mono, Fira Code, Consolas, monospace"
    fontSize: 0.875rem
    fontWeight: "400"
    lineHeight: 1.6rem
  label-md:
    fontFamily: "PingFang SC, Microsoft YaHei, Inter, sans-serif"
    fontSize: 0.875rem
    fontWeight: "500"
    lineHeight: 1.25rem
  label-sm:
    fontFamily: "PingFang SC, Microsoft YaHei, Inter, sans-serif"
    fontSize: 0.75rem
    fontWeight: "500"
    lineHeight: 1rem
    letterSpacing: 0.02em

rounded:
  sm: 4px
  md: 8px
  lg: 12px
  xl: 16px
  full: 9999px

spacing:
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 40px
  xxl: 64px

components:
  # ── 基础操作 ──────────────────────────────────────────
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label-md}"
    rounded: "{rounded.md}"
    padding: "8px 16px"
  button-primary-hover:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"

  button-secondary:
    backgroundColor: "{colors.surface-container}"
    textColor: "{colors.on-surface}"
    typography: "{typography.label-md}"
    rounded: "{rounded.md}"
    padding: "8px 16px"

  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.primary}"
    typography: "{typography.label-md}"
    rounded: "{rounded.md}"
    padding: "8px 16px"

  # ── 对话区 ────────────────────────────────────────────
  chat-input:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-md}"
    rounded: "{rounded.lg}"
    padding: "12px 16px"

  message-user:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    typography: "{typography.body-md}"
    rounded: "{rounded.lg}"
    padding: "{spacing.md}"

  message-assistant:
    backgroundColor: "{colors.surface-container}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-md}"
    rounded: "{rounded.lg}"
    padding: "{spacing.md}"

  # ── 置信度门控（AAF 核心 UI 元素）────────────────────
  confidence-indicator-high:
    backgroundColor: "{colors.confidence-high}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label-sm}"
    rounded: "{rounded.full}"
    padding: "2px 8px"

  confidence-indicator-mid:
    backgroundColor: "{colors.confidence-mid}"
    textColor: "{colors.on-surface}"
    typography: "{typography.label-sm}"
    rounded: "{rounded.full}"
    padding: "2px 8px"

  confidence-indicator-low:
    backgroundColor: "{colors.confidence-low}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label-sm}"
    rounded: "{rounded.full}"
    padding: "2px 8px"

  confirm-card:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.lg}"
    padding: "{spacing.lg}"

  exception-card:
    backgroundColor: "{colors.tertiary-container}"
    textColor: "{colors.on-tertiary-container}"
    rounded: "{rounded.lg}"
    padding: "{spacing.lg}"

  # ── 工作区 ────────────────────────────────────────────
  semantic-card:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.lg}"
    padding: "{spacing.lg}"
  semantic-card-hover:
    backgroundColor: "{colors.surface-container}"

  code-block:
    backgroundColor: "{colors.surface-dark-container}"
    textColor: "{colors.on-surface-dark}"
    typography: "{typography.code}"
    rounded: "{rounded.md}"
    padding: "{spacing.md}"

  # ── 多层协作可视化（Team / Assistant / Agent）─────────
  layer-team:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    rounded: "{rounded.md}"
    padding: "{spacing.sm}"

  layer-assistant:
    backgroundColor: "{colors.secondary-container}"
    textColor: "{colors.on-secondary-container}"
    rounded: "{rounded.md}"
    padding: "{spacing.sm}"

  layer-agent:
    backgroundColor: "{colors.surface-container-high}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.sm}"
    padding: "{spacing.sm}"

  # ── 状态徽章 ──────────────────────────────────────────
  badge-success:
    backgroundColor: "{colors.success}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label-sm}"
    rounded: "{rounded.full}"
    padding: "2px 8px"

  badge-warning:
    backgroundColor: "{colors.warning}"
    textColor: "{colors.on-surface}"
    typography: "{typography.label-sm}"
    rounded: "{rounded.full}"
    padding: "2px 8px"

  badge-danger:
    backgroundColor: "{colors.danger}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label-sm}"
    rounded: "{rounded.full}"
    padding: "2px 8px"
---

## Overview

**界面是对话的投影**，不是预先设计的固定结构。AAF 的 UI 设计哲学来自对话式交互设计：用户意图通过 DSL 驱动语义组件动态组装，界面随上下文实时生成。

设计风格：**专业克制的 AI 原生工具**。参照 Linear、Vercel Dashboard 的简洁密度感，结合中文界面可读性需求。不追求视觉华丽，追求信息密度与操作效率的平衡。

核心 UI 隐喻：**对话区是意图通道，工作区是结果投影**，两者共享同一套语义组件和 DSL，通过置信度门控动态切换人机主导权。

本文件对应[元引擎设计](../framework/engine/meta/meta-engine.md) `doc 域` 中的 `style` 子域，是 v3 阶段 `doc/layout` 和 `doc/behavior` 规范的前置基础。

## Colors

颜色体系基于 **OKLCH 颜色空间**（详见 [why-oklch.md](../../explanation/why-oklch.md)），感知均匀，支持广色域，深浅主题切换只修改 CSS 变量。

**主色调语义**：

- `primary`（智能蓝 250°）：主操作、链接、激活态，是 AI 能力的视觉代言
- `secondary`（知识紫 290°）：知识库、记忆系统相关 UI，区分推理能力与知识存储
- `tertiary`（行动橙 55°）：确认操作、异常提示、CTA，驱动用户决策，不滥用

**置信度三态色**是 AAF 独有的语义色，贯穿所有 AI 执行路径：

| 颜色 | 置信度 | 行为 | UI 表现 |
|------|--------|------|---------|
| 绿 `confidence-high` | > 0.9 | 自动执行 | 进度条 + 异步通知 |
| 黄 `confidence-mid` | 0.7–0.9 | 等待确认 | 确认卡片 + 可编辑参数 |
| 红 `confidence-low` | < 0.7 | 转人工 | 暂停说明 + 建议选项 |

**深浅分区**：工作区用浅色 `surface` 系列，对话区代码块和 DSL 编辑器用深色 `surface-dark` 系列，形成视觉分区而非整页深色模式。

## Typography

中英文混排优先系统字体栈（PingFang SC / Microsoft YaHei），回退 Inter，保证跨平台一致性。代码和 DSL 内容必须使用等宽字体（JetBrains Mono / Fira Code），与普通文本形成明确区分。

- `h1/h2/h3`：页面标题、模块标题、卡片标题
- `body-lg`：对话消息正文，行高 1.75rem 保证中文可读性
- `body-md`：表单、列表、说明文字
- `code`：DSL 片段、代码生成结果、命令行输入，必须等宽
- `label-md/sm`：按钮文字、徽章、状态标签

## Layout

桌面端主布局为**双栏**：左侧工作区（约 60%）+ 右侧助理区（约 40%）。

```
┌──────────────────────────┬─────────────────┐
│  工作区 Workspace         │  助理区 Assistant│
│  画板 / 文档 / 代码        │  对话流          │
│  语义组件动态组装           │  多层协作可视化   │
├──────────────────────────┴─────────────────┤
│  命令区 Command Bar（/ @ # [[ 斜杠命令）      │
└─────────────────────────────────────────────┘
```

响应式规则：

- 桌面（≥ 1280px）：双栏并列，命令区底部固定
- 平板（768–1279px）：工作区全宽，助理区抽屉展开
- 移动（< 768px）：助理区全屏优先，工作区折叠

间距基准：8px 网格，组件内 padding 用 `spacing.sm/md/lg`，区块间距用 `spacing.lg/xl`。

## Elevation & Depth

层级通过背景色深浅区分，不依赖阴影堆叠：

- Level 0：页面背景 `surface`
- Level 1：卡片、面板 `surface-container-low`
- Level 2：悬浮卡片、确认卡片 `surface-container`
- Level 3：命令面板、模态框 `surface-container-high`

阴影仅用于浮层：`box-shadow: 0 4px 16px oklch(0 0 0 / 8%)`

## Shapes

- `rounded.sm`（4px）：徽章、状态标签
- `rounded.md`（8px）：标准按钮、输入框、小卡片
- `rounded.lg`（12px）：对话消息气泡、语义卡片、确认卡片
- `rounded.xl`（16px）：模态框、大型面板
- `rounded.full`：置信度指示器、状态徽章、头像

## Components

### 置信度门控组件

置信度指示器是 AAF 最核心的 UI 元素，出现在所有 AI 执行路径中。三种状态对应三种组件：

- `confidence-indicator-high`：绿色徽章，显示"自动执行中"+ 进度条
- `confidence-indicator-mid`：黄色，触发 `confirm-card`，展示执行计划 + 确认/修改/取消
- `confidence-indicator-low`：红色，触发 `exception-card`，说明暂停原因 + 建议选项

`confirm-card` 和 `exception-card` 是对话消息的一部分，**不是弹窗**，用户可直接在对话中回复"确认"或"回滚"。

### 多层协作可视化

对应五层智能架构，默认只展示 Team 层，用户点击展开 Assistant / Agent / 工具调用细节：

- `layer-team`（蓝色）：项目级，Assistant 列表 + 整体进度
- `layer-assistant`（紫色）：会话级，当前 Agent 调度状态
- `layer-agent`（灰色）：任务级，感知→规划→执行→评估进度

移动端只展示 Team 层摘要，不展示完整细节。

### 语义卡片

`semantic-card` 是工作区基本单元，承载动态生成的内容。同一功能对不同角色呈现不同密度：普通用户看简化摘要，开发者看完整配置面板（展开态）。

### 代码块与 DSL 编辑器

`code-block` 使用深色背景，与浅色工作区形成对比，明确区分"内容"与"程序"。DSL 编辑器复用同一视觉规范。

## Do's and Don'ts

**✅ Do**

- 置信度指示器必须出现在所有 AI 执行路径的 UI 中
- 确认卡和异常卡作为对话消息内联展示，不使用独立弹窗
- 代码和 DSL 内容必须使用 `code` 字体 + `code-block` 样式
- 颜色值统一使用 OKLCH，不混用 HEX / HSL
- 深浅主题切换只修改 CSS 变量，不修改组件结构
- 多层协作可视化默认折叠，用户主动展开

**❌ Don't**

- 不在同一页面同时使用 primary / secondary / tertiary 三种强调色
- 不用弹窗打断对话流
- 不在置信度低时静默执行并隐藏状态
- 不为不同端重复定义组件样式，适配层只处理渲染差异
- 不用纯装饰性动画，动效只服务于状态变化感知（流式渲染、进度更新）
- 不在移动端展示多层协作可视化的完整细节
