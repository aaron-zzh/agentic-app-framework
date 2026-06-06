---
level: Practice
layer: Product
purpose: AAF 前端 DSL 运行时设计——DSL 解析、渲染、命令面板与指令执行
status: draft
version: 1.0.0
date: 2026-05-13
author: AaronZZH
---

# 前端 DSL 运行时（dsl-runtime）

> 前端对 Magic-DSL 的解析、渲染和交互执行能力。作为 `features/dsl-runtime/` 存在，被命令面板、AI 工作流编辑器、审批流、AI 对话等多个业务域消费。
> 语言设计：[magic-dsl.md](magic-dsl.md) | 后端引擎：[dsl-engine.md](dsl-engine.md)
> 所属体系：[结构化视图模式](../../apps/webui/interaction-mode-structured-view.md) | 生成式交互模式（待建）

## 一、定位

前端 DSL 运行时负责 **doc 域**的解析与渲染，以及**命令式指令**的前端执行/转发。它不负责 dev 域和 runtime 域的执行（那是后端引擎的职责）。

```text
后端 DSL 引擎                    前端 DSL 运行时
├── dev 域执行（建表/CRUD）       ├── doc 域渲染（布局/样式/交互）
├── runtime 域执行（工作流/Agent） ├── 命令式指令解析与转发
├── L1→L2→L3 转化                ├── DSL 编辑器（语法高亮/补全/校验）
└── 置信度门控                    └── 指令结果的 UI 呈现
```

### 核心设计能力

- **自然语言→DSL**：用户在命令面板或对话中输入自然语言，Copilot Agent 将其转化为结构化 DSL，前端渲染或转发执行
- **自然语言与 DSL 混合**：对话流中自然语言解释与 DSL 代码块交织呈现，用户可直接确认/编辑 DSL 片段后执行

## 二、应用场景

| 场景 | DSL 用途 | 域 | 关联设计 |
|------|---------|-----|---------|
| **命令面板（⌘K）** | 解析用户输入的 `/` 指令，执行或转发后端 | 命令式 | command-palette.md |
| **AI 工作流编辑器** | FlowEditor 节点配置中的 DSL 表达式（条件/变量/模板） | runtime/flow | flow-editor.md |
| **审批流** | 审批条件表达式、动态表单规则 | runtime/flow | flow-editor.md |
| **AI 对话** | Agent 返回的 DSL 片段 → 动态渲染语义组件 | doc/layout | chat-livechat-module.md |
| **Copilot** | 自然语言→DSL 生成→前端渲染/执行 | 全域 | copilot-plugin.md |
| **实体视图配置** | 列表/表单/看板的声明式配置 | doc/layout | interaction-mode-structured-view.md |
| **内联 DSL 编辑** | 高级用户直接编辑 DSL 源码 | 全域 | — |

## 三、核心能力

### 3.1 命令解析器（Command Parser）

解析 `/` 前缀的命令式指令：

```text
用户输入：/create entity Product --fields "name:String, price:Number"

解析结果：
{
  command: 'create',
  target: 'entity',
  args: { name: 'Product' },
  options: { fields: 'name:String, price:Number' }
}

执行路由：
  → 本地可执行（导航/UI 操作）→ 前端直接执行
  → 需后端执行（CRUD/部署）→ POST /api/dsl/execute
```

内置命令：

| 命令 | 作用 | 执行位置 |
|------|------|---------|
| `/goto {path}` | 页面导航 | 前端 |
| `/search {query}` | 全局搜索 | 前端 + 后端 |
| `/create {type} {name}` | 创建实体/工作流/文档 | 后端 |
| `/deploy {target}` | 部署工作流/Agent | 后端 |
| `/query {expression}` | 数据查询 | 后端 |
| `/run {workflow}` | 执行工作流 | 后端 |
| `/help {topic}` | 帮助文档 | 前端 |

### 3.2 表达式求值器（Expression Evaluator）

用于工作流条件、审批规则、动态表单中的表达式求值：

```typescript
// 条件表达式（工作流/审批流节点中使用）
evaluate("amount > 10000 && department == 'finance'", context)

// 模板表达式（变量插值）
interpolate("审批人：{{approver.name}}，金额：{{amount}}", context)

// 过滤表达式（列表筛选）
evaluate("status in ['active', 'pending'] && createdAt > '2026-01-01'", context)
```

安全约束：
- 沙箱执行，无法访问 window/document
- 表达式超时限制（100ms）
- 禁止副作用（纯函数求值）

### 3.3 布局渲染器（Layout Renderer）

将 doc/layout 域的 DSL 渲染为 React 组件树：

```typescript
// DSL 输入（来自后端 Agent 生成或用户配置）
const layoutDSL = {
  type: 'view',
  name: 'UserList',
  layout: 'table',
  columns: ['name', 'email', 'role', 'department'],
  filters: ['role', 'department'],
  actions: ['create', 'edit', 'delete'],
  sort: 'createdAt:desc'
}

// 渲染为对应的 DataTable 组件
<DSLRenderer dsl={layoutDSL} context={entityContext} />
```

渲染映射：
- `layout: 'table'` → `<DataTable />`（features/data-table）
- `layout: 'form'` → 动态表单组件
- `layout: 'kanban'` → 看板视图
- `layout: 'flow'` → `<FlowEditor />`（features/flow-editor）
- `layout: 'card'` → 语义组件卡片

### 3.4 DSL 编辑器（Code Editor）

高级用户直接编辑 DSL 源码的内嵌编辑器：

```text
┌─ DSL 编辑器 ──────────────────────────────────┐
│ entity User {                                  │
│   name: String @required                       │  ← 语法高亮
│   email: Email @unique                         │  ← 自动补全
│   role: Enum["admin", "user"]  ← 类型提示      │  ← 内联校验
│ }                                              │
├────────────────────────────────────────────────┤
│ ⚠ 1 warning: 'department' field referenced    │  ← 诊断面板
│   in workflow but not defined                   │
└────────────────────────────────────────────────┘
```

能力：
- 语法高亮（Magic-DSL 语法 + 内嵌 JSON/表达式）
- 自动补全（实体名/字段名/命令/关键字）
- 内联校验（语法错误 + 语义警告）
- 格式化
- 跳转到定义（实体/工作流引用）

基于 CodeMirror 6 实现（与 Mastra playground-ui 一致）。

## 四、场景集成索引

各场景如何使用 DSL 运行时的具体说明，见对应模块文档：

| 场景 | 使用的运行时能力 | 详见 |
|------|-----------------|------|
| 命令面板（⌘K） | 命令解析器 + 命令注册表 | command-palette.md |
| AI 工作流 / 审批流 | 表达式求值器 + DSL 编辑器 | flow-editor.md |
| AI 对话 | DSL 代码块识别 + 预览卡片 + 确认执行 | chat-livechat-module.md |
| Copilot | 自然语言→DSL 生成→前端渲染/执行 | copilot-plugin.md|
| 实体视图配置 | 布局渲染器（layout DSL → 组件树） | 结构化视图模式 |

## 五、目录结构

```text
features/dsl-runtime/
├── components/
│   ├── dsl-renderer.tsx          → DSL → React 组件树渲染器
│   ├── dsl-editor.tsx            → CodeMirror 6 DSL 编辑器
│   ├── dsl-preview-card.tsx      → 对话中的 DSL 预览卡片
│   └── command-input.tsx         → 命令面板 DSL 输入组件
├── hooks/
│   ├── use-dsl-parse.ts          → DSL 解析 hook
│   ├── use-dsl-execute.ts        → DSL 执行（本地 + 远程）
│   └── use-expression.ts         → 表达式求值 hook
├── lib/
│   ├── parser.ts                 → 命令式指令解析器
│   ├── expression-evaluator.ts   → 安全沙箱表达式求值
│   ├── layout-mapper.ts          → DSL layout → 组件映射
│   ├── command-registry.ts       → 命令注册表
│   └── syntax/                   → CodeMirror 语法定义
│       ├── magic-dsl.ts          → DSL 语法高亮规则
│       └── completions.ts        → 自动补全 provider
├── types.ts
└── index.ts
```

## 六、与现有设计的关系

| 引用方 | 使用方式 |
|--------|---------|
| 命令面板（⌘K） | `/` 指令解析 + 执行 |
| FlowEditor 节点配置 | 条件表达式求值 + DSL 编辑器 |
| 审批流条件 | 表达式求值（amount > 10000） |
| AI 对话 | DSL 代码块识别 + 预览卡片 + 确认执行 |
| 实体视图配置 | layout DSL → DataTable/Form/Kanban 渲染 |
| Copilot | 自然语言 → DSL 生成 → 前端渲染/执行 |
| 生成式交互 | Agent 输出 DSL → 语义组件动态组装 |

## 七、实现路径（v0.1.0 任务对应）

| 能力 | 对应任务 | 说明 |
|------|---------|------|
| 布局渲染器 | AAF-028 #5 ViewEngine | DSL layout → 组件树渲染（列表/表单/看板） |
| 表达式求值器 | AAF-030 #8 FieldContext | $record/$user/$parent 路径解析，安全求值 |
| 公式引擎 | AAF-030 #12 公式字段 | 算术 + IF + 聚合 + 日期/文本函数 |
| 条件引擎 | AAF-030 #9 条件可见性 | visibleWhen/readOnlyWhen/requiredWhen 实时计算 |
| 命令面板 | AAF-033 #16 ⌘K | `/` 指令解析 + 全局搜索 + 命令注册表 |
| AI 对话生成 DSL | AAF-033 #15 AI 生成 EntityDef | 自然语言 → EntityDef JSON → 预览 → 确认 |
| DSL 编辑器 | AAF-033 #9 Monaco 编辑器 | JSON Schema 校验 + 补全 + 实时预览 |
| PageDSL 解析器 | AAF-034 #2 PageEngine | PageDSL → PageDef JSON → SSG 渲染 |
