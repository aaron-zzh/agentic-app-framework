---
level: Practice
layer: Product
purpose: AAF 命令面板设计——全局统一入口，融合搜索、指令、自然语言
status: draft
version: 1.0.0
date: 2026-05-13
author: AaronZZH
---

# 命令面板（Command Palette）

> `⌘K` 唤起的全局统一入口，融合搜索、DSL 指令、自然语言意图三种输入模式。
> 技术选型：[cmdk](https://cmdk.paco.me/)（2KB，shadcn/ui 官方集成）+ [fuse.js](https://www.fusejs.io/)（客户端模糊搜索）
> 所属体系：[DSL 运行时](../../framework/dsl/dsl-runtime.md) | [Copilot 插件](./copilot-plugin.md) | [结构化视图模式](./interaction-mode-structured-view.md)

## 一、定位

命令面板是用户与系统交互的**统一快捷入口**——一个输入框覆盖搜索、导航、操作、AI 对话四种场景。

```text
⌘K 打开
  ├── 直接输入关键词 → 模糊搜索（实体/记录/命令/页面）
  ├── 输入 "/" → DSL 指令模式（结构化命令）
  ├── 输入 "@" → 上下文引用（文档/知识库/Agent/用户）
  └── 输入自然语言 → 转发 Copilot Agent → 返回 DSL 或直接执行
```

**核心价值**：所有操作都可通过一个入口触达，降低记忆负担（VS Code / Linear / Notion 验证过的模式）。

## 二、搜索范围

| 类别 | 搜索内容 | 数据来源 |
|------|---------|---------|
| 实体模块 | 所有注册实体（文档管理、用户、工作流…） | EntityRegistry |
| 记录 | 实体内记录（全局搜索） | 后端 API + fuse.js 缓存 |
| 命令 | 所有注册的 `/` 指令 | CommandRegistry |
| 页面导航 | 路由页面、视图切换 | 路由表 |
| 新建操作 | 新建记录快捷入口 | EntityRegistry.actions |
| Agent | 可调用的 AI Agent | Agent 注册表 |

## 三、输入模式

### 3.1 模糊搜索（默认）

直接输入关键词，fuse.js 在客户端即时匹配：

```text
⌘K → "用户"
  → 📋 用户管理（模块）
  → 📄 用户 Aaron（记录）
  → ⚡ 新建用户（操作）
  → 🔧 /create entity User（命令）
```

### 3.2 斜杠命令（`/` 前缀）

进入 DSL 指令模式，自动补全可用命令：

```text
⌘K → "/create"
  → /create entity    创建实体
  → /create workflow  创建工作流
  → /create document  创建文档
```

完整命令列表见 [DSL 运行时 §3.1](../../framework/dsl/dsl-runtime.md#31-命令解析器command-parser)。

### 3.3 @引用（`@` 前缀）

注入上下文到后续操作或对话：

```text
⌘K → "@"
  → @设计文档       引用文档内容
  → @产品知识库     从知识库检索
  → @Copilot       指定 Agent
  → @Aaron         提及用户
```

### 3.4 自然语言

非 `/` 非 `@` 开头且非短关键词时，转发 Copilot Agent 处理：

```text
⌘K → "创建一个客户记录"     → 打开客户表单 + 预填
⌘K → "本周我的待办"         → 展示待办列表
⌘K → "分析上季度退货原因"   → 触发 BI Agent
```

Copilot 返回 DSL → 前端执行或渲染结果。这是"自然语言→DSL"能力的主要用户入口。

## 四、交互流程

```text
⌘K 唤起浮层（居中弹出，遮罩背景）
  │
  ├── 空状态：展示最近操作 + 常用命令
  │
  ├── 输入中：
  │   ├── 实时模糊匹配（fuse.js，<50ms）
  │   ├── 结果分组展示（模块/记录/命令/页面）
  │   └── 键盘导航（↑↓ 选择，Enter 执行，Esc 关闭）
  │
  ├── 选中执行：
  │   ├── 导航类 → 路由跳转，面板关闭
  │   ├── 操作类 → 执行命令，Toast 反馈
  │   ├── 后端类 → POST /api/dsl/execute，等待结果
  │   └── AI 类 → 转发 Copilot，流式展示结果
  │
  └── Esc / 点击遮罩 → 关闭
```

## 五、命令注册机制

所有模块通过 CommandRegistry 注册命令，命令面板自动发现：

```typescript
// 命令注册
registerCommand({
  id: 'create-entity',
  prefix: '/create',
  label: '创建',
  description: '创建实体/工作流/文档',
  keywords: ['新建', 'create', 'new'],
  args: [
    { name: 'type', options: ['entity', 'workflow', 'document'] },
    { name: 'name', placeholder: '名称' },
  ],
  execute: async (args) => {
    const result = await api.dsl.execute(`/create ${args.type} ${args.name}`)
    toast.success(`已创建 ${args.type}: ${args.name}`)
  },
})
```

实体模块自动注册：
- 每个 EntityDef 自动生成"新建 X"、"搜索 X"命令
- 无需手动注册，EntityRegistry 变更时自动更新

## 六、技术实现

### 技术选型

| 库 | 用途 | 选型理由 |
|----|------|---------|
| cmdk | 命令面板 UI | 2KB、无样式锁定、shadcn/ui 官方集成、WAI-ARIA 合规 |
| fuse.js | 客户端模糊搜索 | 轻量、支持加权评分、无需后端请求 |

### 目录结构

```text
features/command-palette/
├── components/
│   ├── command-palette.tsx       → 主面板组件（cmdk 封装）
│   ├── command-group.tsx         → 分组渲染（模块/记录/命令）
│   ├── command-item.tsx          → 单条结果项
│   └── command-input.tsx         → 输入框（模式识别 + 补全）
├── hooks/
│   ├── use-command-palette.ts    → 开关状态 + 快捷键绑定
│   ├── use-command-search.ts     → fuse.js 搜索逻辑
│   └── use-command-execute.ts    → 命令执行 + 路由
├── lib/
│   ├── command-registry.ts       → 命令注册表（单例）
│   ├── search-index.ts           → fuse.js 索引构建
│   └── mode-detector.ts          → 输入模式识别（/、@、自然语言）
├── types.ts
└── index.ts
```

### 无障碍

- WAI-ARIA combobox 模式（cmdk 内置）
- 键盘完全可操作（Tab/↑↓/Enter/Esc）
- 屏幕阅读器友好（role、aria-label、live region）

## 七、与 Copilot 的协作

命令面板是 Copilot 的轻量入口（完整对话在侧边栏 Thread）：

```text
命令面板（⌘K）          Copilot 侧边栏
├── 单次意图执行         ├── 多轮对话
├── 快进快出             ├── 持续交互
├── 结果即操作           ├── 结果可追问
└── 自然语言 → DSL       └── 自然语言 → DSL + 解释
```

Copilot 扩展命令面板能力：除了导航/搜索，还支持自然语言意图执行（创建记录、查询数据、触发工作流）。

## 八、快捷键

| 快捷键 | 操作 |
|--------|------|
| ⌘K | 打开/关闭命令面板 |
| ⌘N | 新建文档（等价于 `/create document`） |
| ⌘/ | 斜杠命令（聚焦输入框并预填 `/`） |
| Esc | 关闭面板 |
| ↑↓ | 导航结果列表 |
| Enter | 执行选中项 |

## 九、实现路径

| 阶段 | 能力 |
|------|------|
| v0.1 | cmdk 基础面板 + 模糊搜索（实体/页面/命令）+ 键盘导航 |
| v0.1 | `/` 指令解析 + 基础命令（goto/search/help） |
| v0.2 | 自然语言转发 Copilot + @引用 + 上下文快捷键 |
| v0.3 | 记录级全局搜索（后端 API）+ 搜索历史 + 常用命令 |

## 十、关联设计

| 文档 | 关系 |
|------|------|
| [DSL 运行时](../../framework/dsl/dsl-runtime.md) | 命令面板是 DSL 命令式指令的主要用户入口 |
| [Copilot 插件](./copilot-plugin.md) | 自然语言意图通过命令面板转发 Copilot |
| [结构化视图模式](./interaction-mode-structured-view.md) | 命令面板在 AppHeader 中，覆盖全局 |
| 生成式交互模式（待建） | 斜杠命令 + @引用的完整定义 |
| [tech-stack.md](./tech-stack.md) | cmdk + fuse.js 选型依据 |
