---
level: Practice
layer: Product
purpose: AAF 前端目录结构设计（apps/webui + packages/）
status: draft
version: 2.1.0
date: 2026-05-14
author: AaronZZH
changelog:
  - 2026-05-14 | v2.1 借鉴 next-ts：表单体系（Form/Field/schemaUtils）、sections/view/ 子目录、路由常量集中、CSS 变量布局、导航配置分离
  - 2026-05-13 | v2.0 重写：增加 features/ 层、依赖方向规则、插件门控、Nx 边界约束
  - 2026-05-10 | v1.0 初版
---

# AAF 前端目录结构设计

> 技术选型依据见 [tech-stack.md](./tech-stack.md) | 插件商业化见 [plugin-commercialization.md](./plugin-commercialization.md)

## 一、设计原则

### 1.1 两种交互模式并存

| 模式 | 类比 | 特征 | 对应目录 |
|------|------|------|---------|
| **结构化视图模式** | Action/View | 数据驱动的列表/表单/看板视图，菜单导航，CRUD | `app/(workspace)/` |
| **生成式交互模式** | AAF 一切皆文档/画板/对话 | 对话驱动 UI 生成，语义组件动态组装，画板自由布局 | `app/(canvas)/` |

两种模式共享底层（components/ui/ + lib/ + features/），交互范式不同，不强行统一。

### 1.2 分层与依赖方向

```text
packages/  ←  features/  ←  sections/  ←  app/
（共享层）    （功能层）     （区块层）     （路由层）
                 ↑
            components/  ←  sections/  ←  app/
            （组件层）
                 ↑
              lib/       ←  所有层均可依赖
            （逻辑层）
```

**依赖方向规则**（单向，禁止反向引用）：
- `app/` → 可引用 sections/ features/ components/ lib/
- `sections/` → 可引用 features/ components/ lib/，禁止引用 app/
- `features/` → 可引用 components/ lib/ packages/，禁止引用 sections/ app/
- `components/` → 可引用 lib/ packages/，禁止引用 features/ sections/ app/
- `lib/` → 可引用 packages/，禁止引用 UI 层
- `packages/` → 零内部依赖（仅依赖外部 npm 包）

### 1.3 各层职责

| 层 | 职责 | 内部结构 | 示例 |
|----|------|---------|------|
| `app/` | 路由组织 + 页面组合，不含业务逻辑 | Next.js 约定文件 | page.tsx, layout.tsx |
| `features/` | 自成体系的复合功能模块，有内部插件/注册表 | components/ hooks/ lib/ types.ts | FlowEditor, RichTextEditor, Copilot |
| `sections/` | 按业务域组织的复合组件，消费 features | 扁平组件文件 | ChatPage, DocumentEditor, FlowList |
| `components/` | 无业务语义的纯 UI 组件 | 按形态分组 | ui/, common/, form/, assistant-ui/ |
| `lib/` | 数据获取、状态、工具函数、门控 | 按职能分组 | api/, queries/, store/, modules/ |
| `packages/` | 跨 app 共享（webui + uniapp） | 独立 Nx 项目 | core/, ui/, editor/ |

### 1.4 文件放置决策树

```text
这段代码应该放哪里？
│
├── 跨 webui 和 uniapp 都需要？ → packages/
│
├── 有内部插件/注册表体系，被多个业务域当"引擎"用？ → features/
│
├── 纯 UI，无业务语义？ → components/
│
├── 绑定具体业务域，组合 features + components？ → sections/
│
├── 数据获取 / 状态 / 工具函数？ → lib/
│
└── 页面路由 / 布局？ → app/
```

### 1.5 features/ 内部统一结构约定

每个 feature 遵循统一的内部组织（借鉴 cal.diy 垂直切片）：

```text
features/{feature-name}/
├── components/       → 该 feature 的 UI 组件
├── hooks/            → 该 feature 的 React hooks
├── lib/              → 核心逻辑（注册表/工厂/算法，纯 TS）
├── types.ts          → 类型定义（对外导出）
└── index.ts          → 公开 API（barrel export）
```

可选子目录（按需）：
- `nodes/` / `features/` / `presets/` — 插件/节点/预设注册
- `converters/` — 格式转换
- `tools/` — Tool Call UI 注册

### 1.6 路由私有组件约定

仅该页面使用的组件放 `_components/`（`_` 前缀 = Next.js 不当路由）：

```text
app/(workspace)/workflow/[flowId]/
├── page.tsx
└── _components/
    └── FlowDebugger.tsx
```

### 1.7 features/ vs sections/ vs components/ 对比

| 维度 | component | feature | section |
|------|-----------|---------|---------|
| 业务语义 | 无 | 领域通用 | 业务绑定 |
| 内部架构 | 无 | 有（插件/注册表/preset） | 组合式 |
| 复用范围 | 全局 | 跨业务域 | 单一业务域 |
| 状态管理 | props only | 内部 store + 对外 API | 依赖 lib/ |
| 商业化控制 | 不控制 | 部分能力按插件门控 | 整体按插件门控 |
| 例子 | Button1, Dialog | FlowEditor, RichTextEditor | ChatPage, WorkflowPage |


---

## 二、apps/webui 目录结构

```text
apps/webui/
├── src/
│   ├── app/                          → Next.js App Router 路由层
│   │   ├── layout.tsx                → 根布局（字体/主题/Provider 注入）
│   │   ├── global.css                → 全局样式（Tailwind v4 + OKLCH 变量）
│   │   ├── not-found.tsx             → 404 页面
│   │   │
│   │   ├── (auth)/                   → 路由组：认证流程（无主导航）
│   │   │   ├── login/page.tsx
│   │   │   ├── register/page.tsx
│   │   │   └── layout.tsx            → 认证布局（居中卡片）
│   │   │
│   │   ├── (workspace)/              → 路由组：结构化视图模式
│   │   │   ├── layout.tsx            → 工作区布局（侧边栏 + 顶栏 + 主内容区）
│   │   │   ├── dashboard/page.tsx
│   │   │   ├── chat/                 → AI 对话
│   │   │   │   ├── page.tsx
│   │   │   │   └── [threadId]/page.tsx
│   │   │   ├── document/             → 文档管理
│   │   │   │   ├── page.tsx
│   │   │   │   └── [docId]/page.tsx
│   │   │   ├── knowledge/            → 知识库
│   │   │   │   ├── page.tsx
│   │   │   │   └── [kbId]/page.tsx
│   │   │   ├── workflow/             → 工作流
│   │   │   │   ├── page.tsx
│   │   │   │   └── [flowId]/page.tsx
│   │   │   ├── agent/               → Agent 管理
│   │   │   │   ├── page.tsx
│   │   │   │   └── [agentId]/page.tsx
│   │   │   ├── autodev/page.tsx      → Auto Dev 监控
│   │   │   └── settings/            → 系统设置
│   │   │       ├── page.tsx
│   │   │       ├── profile/page.tsx
│   │   │       ├── team/page.tsx
│   │   │       ├── model/page.tsx
│   │   │       └── plugins/page.tsx  → 插件管理
│   │   │
│   │   ├── (canvas)/                 → 路由组：生成式交互模式
│   │   │   ├── layout.tsx            → 画板布局（全屏，浮动工具栏）
│   │   │   ├── space/[spaceId]/page.tsx → 虚拟工作空间
│   │   │   └── compose/[pageId]/page.tsx → 生成式页面（DSL 驱动）
│   │   │
│   │   └── api/                      → Route Handlers（BFF 薄层）
│   │       ├── auth/[...nextauth]/route.ts
│   │       ├── chat/route.ts         → AI 对话流式代理（SSE）
│   │       ├── upload/route.ts
│   │       └── proxy/route.ts        → 后端 API 代理（开发环境）
│   │
│   ├── features/                     → 复合功能模块层
│   │   ├── flow-editor/             → 统一流程图编辑器
│   │   │   ├── components/           → 画布/节点面板/自定义边/变量选择器
│   │   │   ├── nodes/               → 节点实现
│   │   │   │   ├── _base/           → 节点基础组件（端口/错误/重试）
│   │   │   │   ├── approval/        → 审批节点集
│   │   │   │   ├── workflow/        → AI 工作流节点集
│   │   │   │   └── chatbot/         → 聊天机器人节点集
│   │   │   ├── hooks/               → use-flow-state / use-auto-layout / use-validation
│   │   │   ├── lib/                 → registry.ts / variables.ts / elk-layout.ts
│   │   │   ├── types.ts
│   │   │   └── index.ts
│   │   │
│   │   ├── rich-text-editor/         → 统一富文本编辑器
│   │   │   ├── components/           → 主组件/只读渲染/Chatter 输入/textarea 升级
│   │   │   ├── features/            → Lexical Feature 插件
│   │   │   │   ├── heading/ format/ list/ link/ blockquote/
│   │   │   │   ├── code-block/ table/ upload/ mention/ emoji/
│   │   │   │   ├── blocks/ slash-menu/ markdown-shortcut/
│   │   │   │   ├── toolbar/ draggable-block/ collab/ history/
│   │   │   │   └── horizontal-rule/
│   │   │   ├── presets/             → document / richField / chatter / minimal
│   │   │   ├── converters/          → html.ts / markdown.ts / plaintext.ts
│   │   │   ├── lib/                 → create-editor.ts / nodes.ts / theme.ts
│   │   │   ├── types.ts
│   │   │   └── index.ts
│   │   │
│   │   ├── copilot/                  → Copilot 智能助理（横切编排层）
│   │   │   ├── components/           → 对话面板/内联建议/升级提示
│   │   │   ├── tools/               → Tool Call UI 注册
│   │   │   ├── hooks/               → 上下文感知/建议/模板
│   │   │   ├── lib/                 → CopilotService / ToolRegistry / PromptTemplates
│   │   │   ├── types.ts
│   │   │   └── index.ts
│   │   │
│   │   └── data-table/              → 数据表格引擎
│   │       ├── components/           → DataTable / FilterBuilder / ColumnConfig
│   │       ├── hooks/               → 排序/筛选/分页/选择/内联编辑
│   │       ├── lib/                 → 列注册表 / 操作符映射 / 导出
│   │       ├── types.ts
│   │       └── index.ts
│   │

│   ├── sections/                     → 业务区块层（按领域组织）
│   │   ├── chat/                     → 对话协作（基于 assistant-ui）
│   │   │   ├── view/                 → 页面入口（barrel export）
│   │   │   │   ├── index.ts
│   │   │   │   └── chat-view.tsx     → 对话页面组装
│   │   │   ├── AgentToolUIs.tsx      → makeAssistantToolUI 注册
│   │   │   └── AgentContext.tsx      → makeAssistantVisible 注册
│   │   ├── canvas/                   → 画板/生成式交互
│   │   │   ├── CanvasBoard.tsx       → 画板主容器（tldraw，v2.0+）
│   │   │   ├── SemanticCard.tsx      → 语义组件卡片
│   │   │   └── CardRenderer.tsx      → 卡片类型分发渲染器
│   │   ├── document/                 → 文档编辑（消费 features/rich-text-editor）
│   │   │   ├── view/                 → 页面入口
│   │   │   │   ├── index.ts
│   │   │   │   ├── document-list-view.tsx
│   │   │   │   └── document-edit-view.tsx
│   │   │   ├── document-form.tsx     → 表单组件（创建/编辑复用）
│   │   │   └── document-outline.tsx
│   │   ├── workflow/                 → 工作流（消费 features/flow-editor）
│   │   │   ├── view/
│   │   │   │   ├── index.ts
│   │   │   │   ├── flow-list-view.tsx
│   │   │   │   └── flow-editor-view.tsx
│   │   │   └── flow-toolbar.tsx
│   │   ├── knowledge/               → 知识库
│   │   │   ├── KnowledgeList.tsx
│   │   │   ├── DocumentUpload.tsx
│   │   │   └── SearchResult.tsx
│   │   ├── agent/                    → Agent 管理
│   │   │   ├── AgentCard.tsx
│   │   │   ├── AgentStatus.tsx
│   │   │   └── AgentList.tsx
│   │   ├── layout/                   → 布局区块
│   │   │   ├── AppSidebar.tsx
│   │   │   ├── AppHeader.tsx
│   │   │   ├── CommandBar.tsx        → ⌘K 命令面板
│   │   │   ├── nav-config.ts         → 导航配置数据（纯数据，与组件分离）
│   │   │   └── components/           → AccountMenu / SearchBar / NotificationBell
│   │   └── settings/                 → 设置区块
│   │       ├── ProfileForm.tsx
│   │       ├── ModelConfig.tsx
│   │       └── PluginMarket.tsx      → 插件市场页面
│   │
│   ├── components/                   → 纯 UI 组件层（无业务语义）
│   │   ├── assistant-ui/             → assistant-ui 注入的对话组件（源码可改造）
│   │   │   ├── thread.tsx
│   │   │   ├── thread-list.tsx
│   │   │   ├── markdown-text.tsx
│   │   │   └── ...
│   │   ├── ui/                       → shadcn/ui 组件（源码复制，按需添加）
│   │   │   └── button / input / dialog / command / sonner / ...
│   │   ├── common/                   → 通用组件
│   │   │   ├── LoadingSpinner.tsx
│   │   │   ├── EmptyState.tsx
│   │   │   ├── ErrorBoundary.tsx
│   │   │   ├── VirtualList.tsx
│   │   │   └── MarkdownRenderer.tsx
│   │   ├── form/                     → 表单组件（react-hook-form + zod）
│   │   │   ├── form.tsx              → Form 包装器（FormProvider + <form> 标签）
│   │   │   ├── fields.ts            → Field 命名空间对象（Field.Text / Field.Select / ...）
│   │   │   ├── schema-utils.ts      → Zod schema 工厂函数（email/phone/file/date）
│   │   │   ├── field-text.tsx       → 文本输入（Controller + shadcn Input）
│   │   │   ├── field-textarea.tsx   → 多行文本
│   │   │   ├── field-number.tsx     → 数字输入
│   │   │   ├── field-select.tsx     → 下拉选择
│   │   │   ├── field-checkbox.tsx   → 复选框
│   │   │   ├── field-date.tsx       → 日期选择
│   │   │   ├── field-upload.tsx     → 文件上传
│   │   │   └── field-editor.tsx     → 富文本（消费 features/rich-text-editor）
│   │   └── icons/                    → 图标（lucide-react 扩展）
│   │
│   ├── lib/                          → 逻辑层
│   │   ├── api/                      → API 客户端（原始 fetch，无 React 依赖）
│   │   │   ├── client.ts             → 基础封装（JWT/错误映射/重试）
│   │   │   ├── graphql.ts            → urql 客户端
│   │   │   └── chat / document / knowledge / workflow / agent / user / model .ts
│   │   │
│   │   ├── queries/                  → TanStack Query hooks（服务端数据）
│   │   │   ├── use-chat.ts
│   │   │   ├── use-document.ts
│   │   │   ├── use-knowledge.ts
│   │   │   ├── use-workflow.ts
│   │   │   ├── use-agent.ts
│   │   │   ├── use-user.ts
│   │   │   ├── use-model.ts
│   │   │   └── query-client.ts       → QueryClient 配置
│   │   │
│   │   ├── store/                    → Zustand stores（纯客户端 UI 状态）
│   │   │   ├── ui-store.ts           → 侧边栏/主题/命令面板
│   │   │   ├── canvas-store.ts       → 画板选中/缩放/视口
│   │   │   └── editor-store.ts       → 编辑器焦点/工具栏
│   │   │
│   │   ├── modules/                  → 插件门控（商业化控制）
│   │   │   ├── plugin-registry.ts    → PluginDef[] 定义
│   │   │   ├── plugin-state.ts       → 运行时状态（从后端拉取）
│   │   │   ├── use-plugin.ts         → usePluginEnabled() hook
│   │   │   └── plugin-gate.tsx       → <PluginGate> + <UpgradeHint>
│   │   │
│   │   ├── auth/                     → 认证（hooks/guards/utils）
│   │   ├── theme/                    → 主题（tokens/css-vars/config）
│   │   ├── hooks/                    → 通用 hooks（use-stream/use-debounce/use-keyboard）
│   │   ├── schemas/                  → Zod schema（运行时校验 + 类型推导）
│   │   │   ├── utils.ts              → schema 工厂函数（email/phone/file/date/nullableInput）
│   │   │   └── {domain}.ts           → 按领域定义 schema（user/document/...）
│   │   ├── utils/                    → 纯函数工具（format/cn/url/tree/event-bus）
│   │   ├── constants/                → 应用配置
│   │   │   ├── paths.ts              → 路由常量集中定义（含动态路由函数）
│   │   │   └── config.ts             → 应用级配置常量
│   │   └── _mock/                    → 开发阶段 Mock 数据（_ 前缀 = 内部）
│   │
│   ├── providers/                    → React Context Providers
│   │   ├── QueryProvider.tsx
│   │   ├── ThemeProvider.tsx
│   │   ├── AuthProvider.tsx
│   │   └── ToastProvider.tsx
│   │
│   ├── i18n/                         → 国际化（next-intl）
│   │   ├── config.ts
│   │   └── locales/ zh/ en/          → 按语言 × 命名空间
│   │
│   └── types/                        → TypeScript 类型定义
│       └── api / chat / document / workflow / agent / user / common .ts
│
├── public/                           → 静态资源
├── package.json
├── next.config.js
├── tsconfig.json
├── vitest.config.ts
└── project.json                      → Nx 项目配置
```


---

## 三、packages/ 共享包设计

> v0.1.0 阶段 packages/ 为空。提取判断标准：**webui 和 uniapp 都需要，且无框架依赖**。

```text
packages/
├── core/                    → 共享业务逻辑（纯 TypeScript，无 DOM/React）
│   ├── src/
│   │   ├── types/           → 跨端共享类型（Message/Document/Agent/Workflow）
│   │   ├── schemas/         → Zod schema（API 响应校验）
│   │   ├── utils/           → 纯函数工具（format/parse/validate）
│   │   └── constants/       → 跨端常量（API 路径/错误码/枚举）
│   └── package.json
│
├── ui/                      → 共享 UI 组件（React，跨 webui/uniapp-web）
│   ├── src/components/      → MarkdownRenderer / CodeBlock / Avatar / LoadingSpinner
│   ├── .storybook/          → Storybook（组件文档化 + 独立开发）
│   └── package.json
│
├── editor/                  → Lexical 编辑器核心逻辑（跨端共享，无 React UI）
│   ├── src/
│   │   ├── nodes/           → 自定义节点类型定义
│   │   ├── converters/      → 格式转换器（HTML/Markdown/plaintext）
│   │   └── plugins/         → 纯逻辑 Lexical 插件
│   └── package.json
│   # features/rich-text-editor/ 是 webui 端的完整 UI 实现，消费此包
│
├── _config/                 → 共享配置（_ 前缀 = 内部包，不发布）
│   ├── tsconfig/            → 共享 TypeScript 配置
│   ├── eslint/              → 共享 ESLint 配置
│   └── tailwind/            → 共享 Tailwind 预设
│
└── _test-utils/             → 内部测试工具
    ├── src/
    │   ├── render.tsx        → 带 Provider 的测试渲染
    │   ├── mocks/            → 通用 Mock（API/Router/Auth）
    │   └── fixtures/         → 测试数据
    └── package.json
```

### 提取时机

| 触发条件 | 动作 |
|---------|------|
| uniapp 需要同一段类型/工具函数 | 提取到 `packages/core/` |
| uniapp 需要同一个 UI 组件 | 提取到 `packages/ui/` |
| Lexical 编辑器需要跨端复用 | 提取到 `packages/editor/` |
| 测试工具在多个 app 中重复 | 提取到 `packages/_test-utils/` |

---

## 四、路由设计

### 4.1 结构化视图模式

```text
(workspace)/
├── layout.tsx              → 固定布局：侧边栏 + 顶栏 + 内容区
├── [module]/page.tsx       → 默认视图（列表）
├── [module]/[id]/page.tsx  → 详情/编辑视图（表单）
└── [module]/@modal/[id]/page.tsx → 弹窗视图（拦截路由）
```

视图类型通过 URL 参数区分：`/document?view=list|kanban|calendar`

### 4.2 生成式交互模式

```text
(canvas)/
├── layout.tsx              → 全屏布局，浮动工具栏
├── space/[spaceId]/page.tsx → 虚拟工作空间（自由画板）
└── compose/[pageId]/page.tsx → 生成式页面（DSL 驱动）
```

### 4.3 路由组语义

| 路由组 | 布局特征 |
|--------|---------|
| `(auth)` | 居中卡片，无导航 |
| `(workspace)` | 侧边栏 + 顶栏 + 内容区 |
| `(canvas)` | 全屏，浮动工具栏 |

---

## 五、关键设计决策

### 5.1 lib/api/ vs lib/queries/ 分工

```text
lib/api/chat.ts          → 原始 fetch 函数（可在 RSC 中调用）
lib/queries/use-chat.ts  → TanStack Query hooks（仅 Client Component）
```

### 5.2 store/ 严格边界

Zustand 只存纯客户端 UI 状态，禁止存服务端数据（那是 TanStack Query 的职责）。

### 5.3 AI 对话层

直接消费 `@assistant-ui/react` + `@assistant-ui/react-ag-ui`（npm 包），不自研。对话 UI 通过 CLI 注入到 `components/assistant-ui/`（源码可改造）。

### 5.4 表单组件体系（components/form/）

借鉴 next-ts 的 hook-form 组件设计，提供两层表单能力：

**通用层**（`components/form/`）— 手写表单页面使用：

```tsx
// Form 包装器：封装 FormProvider + <form> 标签
<Form methods={methods} onSubmit={onSubmit}>
  <Field.Text name="email" label="邮箱" />
  <Field.Select name="role" label="角色" options={roleOptions} />
  <Field.Upload name="avatar" label="头像" />
</Form>
```

- `form.tsx`：Form 包装器，封装 `FormProvider` + `<form>` + 可选 `onSubmit`
- `fields.ts`：Field 命名空间对象，统一导出所有表单控件（`Field.Text` / `Field.Select` / ...）
- `schema-utils.ts`：Zod schema 工厂函数，统一校验规则（`schemaUtils.email()` / `schemaUtils.phone()` / `schemaUtils.file()`）
- `field-*.tsx`：每个控件基于 shadcn/ui 原语 + `Controller` 封装，外部只需传 `name` + `label`

**引擎层**（`features/entity-engine/`）— EntityDef 配置驱动：

- FormView 从 component-registry 动态获取字段组件
- `buildZodSchema(fields)` 根据 FieldDef[] 自动生成 Zod schema
- 两层共享底层 shadcn/ui 原语，但入口不同（静态 vs 动态）

### 5.5 路由常量集中管理

借鉴 next-ts 的 `routes/paths.ts`，在 `lib/constants/paths.ts` 集中定义所有路由路径：

```ts
const ROOTS = { WORKSPACE: '/workspace', AUTH: '/auth' }

export const paths = {
  auth: {
    login: `${ROOTS.AUTH}/login`,
    register: `${ROOTS.AUTH}/register`,
  },
  workspace: {
    root: ROOTS.WORKSPACE,
    module: (slug: string) => `${ROOTS.WORKSPACE}/${slug}`,
    record: (slug: string, id: string) => `${ROOTS.WORKSPACE}/${slug}/${id}`,
    settings: `${ROOTS.WORKSPACE}/settings`,
  },
}
```

路径变更只改一处，动态路由参数类型安全，IDE 自动补全友好。

### 5.6 CSS 变量驱动布局尺寸

借鉴 next-ts 的布局 CSS 变量模式，在 `global.css` 中定义布局尺寸变量：

```css
:root {
  --layout-sidebar-width: 256px;
  --layout-sidebar-collapsed-width: 64px;
  --layout-header-height: 56px;
  --layout-content-padding: 24px;
}
```

布局组件通过 CSS 变量控制尺寸，主题切换/响应式调整只需修改变量值，无需改组件代码。

### 5.7 导航配置数据分离

借鉴 next-ts 的 `nav-config-dashboard.tsx`，将导航菜单数据从组件中分离：

```ts
// sections/layout/nav-config.ts — 纯数据，无 React 依赖
export const navConfig = [
  { group: 'content', label: '内容管理', items: [
    { title: '文档', path: paths.workspace.module('document'), icon: 'file-text' },
    { title: '知识库', path: paths.workspace.module('knowledge'), icon: 'book' },
  ]},
  // ...
]
```

EntityDef 注册表可自动生成导航配置（`entityRegistry.getByGroup()` → navConfig），手动配置作为覆盖/补充。

### 5.8 sections/view/ 子目录模式

当同一业务域有多个页面视图时（列表/详情/创建/编辑），用 `view/` 子目录统一管理页面入口：

```text
sections/document/
├── view/                       → 页面入口（barrel export）
│   ├── index.ts
│   ├── document-list-view.tsx  → 列表页组装
│   └── document-edit-view.tsx  → 编辑页组装
├── document-form.tsx           → 表单组件（创建/编辑复用，通过 props 区分）
└── document-outline.tsx        → 大纲组件
```

- `view/` 与 `app/` 路由一一对应，app/ 的 page.tsx 只引用 view
- 业务组件扁平放置在 section 根目录
- 创建/编辑共用一个 form 组件（`*-form.tsx`），通过 `isEdit` prop 区分

---

## 六、文件命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| React 组件 | PascalCase | `ChatThread.tsx` |
| Hook | camelCase，`use-` 前缀 | `use-chat.ts` |
| 工具/类型/常量 | camelCase | `format.ts` |
| Next.js 约定文件 | 小写 | `page.tsx`, `layout.tsx` |
| shadcn/ui 组件 | kebab-case | `dropdown-menu.tsx` |
| 内部包/目录 | `_` 前缀 | `_mock/`, `_test-utils/`, `_config/` |

---

## 七、Nx 项目边界约束

通过 Nx `@nx/enforce-module-boundaries` 规则强制依赖方向：

```jsonc
// nx.json 或 .eslintrc（v0.2+ 激活）
{
  "depConstraints": [
    { "sourceTag": "scope:app", "onlyDependOnLibsWithTags": ["scope:feature", "scope:section", "scope:component", "scope:lib", "scope:package"] },
    { "sourceTag": "scope:section", "onlyDependOnLibsWithTags": ["scope:feature", "scope:component", "scope:lib", "scope:package"] },
    { "sourceTag": "scope:feature", "onlyDependOnLibsWithTags": ["scope:component", "scope:lib", "scope:package"] },
    { "sourceTag": "scope:component", "onlyDependOnLibsWithTags": ["scope:lib", "scope:package"] },
    { "sourceTag": "scope:lib", "onlyDependOnLibsWithTags": ["scope:package"] },
    { "sourceTag": "scope:package", "onlyDependOnLibsWithTags": [] }
  ]
}
```

> v0.1.0 阶段 webui 是单一 Nx 项目，边界靠约定。v0.2+ 首个 packages/ 落地时激活 lint 规则。

---

## 八、与参考项目的对应关系

| AAF 目录/模式 | 参考来源 | 借鉴内容 |
|--------------|---------|---------|
| `features/` 垂直切片 | cal.diy `packages/features/` | 每个功能自包含（components/hooks/lib） |
| `features/` 内部统一结构 | cal.diy 每个 feature 含 repositories/services/di | 统一约定，降低认知成本 |
| `lib/modules/` 插件门控 | outline `plugins/` + plugin.json | 元数据声明 + client/server 分离 |
| `packages/_config/` | mastra `_config/` | 共享配置用 `_` 前缀标识内部包 |
| `packages/_test-utils/` | mastra `_test-utils/` | 内部测试工具 |
| `lib/queries/use-*.ts` | dify `service/use-*.ts` | TanStack Query hooks 按领域一文件 |
| `lib/api/` | dify `service/base.ts` | 基础 fetch 封装，与 Query hooks 分离 |
| `packages/editor/` + `features/rich-text-editor/` | AFFiNE `blocksuite/` + outline `shared/editor/` | 核心逻辑共享包 + 平台 UI 实现分离 |
| `providers/` | refine Provider 抽象 + assistant-ui RuntimeProvider | 统一 Provider 注入 |
| `(workspace)/` | Action/View | 结构化视图，数据驱动多视图切换 |
| `(canvas)/` | AG-UI Canvas 模式 | Agent 驱动 UI 状态 |
| `components/assistant-ui/` | assistant-ui CLI 注入 | 对话 UI 源码可改造 |
| 依赖方向规则 | Nx enforce-module-boundaries | 单向依赖，lint 强制 |
| `components/form/` Field 命名空间 | next-ts `components/hook-form/fields.tsx` | `Field.Text` 统一入口，语义清晰 |
| `components/form/` Form 包装器 | next-ts `form-provider.tsx` | 封装 FormProvider + form 标签 |
| `lib/schemas/utils.ts` schema 工厂 | next-ts `schema-utils.ts` | `schemaUtils.email()` 统一校验规则 |
| `lib/constants/paths.ts` 路由常量 | next-ts `routes/paths.ts` | 集中定义 + 动态路由函数化 |
| CSS 变量驱动布局尺寸 | next-ts `layouts/` CSS variables | `--layout-sidebar-width` 等变量控制 |
| `sections/layout/nav-config.ts` | next-ts `nav-config-dashboard.tsx` | 导航配置数据与组件分离 |
| `sections/*/view/` 子目录 | next-ts `sections/*/view/` | 多页面域统一入口 + barrel export |
| 创建/编辑表单复用 | next-ts `*-create-edit-form.tsx` | 同一组件通过 props 区分模式 |
