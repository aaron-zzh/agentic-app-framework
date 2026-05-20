---
level: Practice
layer: Product
purpose: AAF 前端 Web 技术选型
status: published
version: 2.0.0
date: 2026-05-10
author: AaronZZH
gains:
  - 了解前端各技术选型的决策依据
  - 新成员能快速理解技术栈选择原因
---

# 前端技术选型（webui）

> 满足对话式交互设计的技术需要：流式渲染、语义组件动态组装、多层协作可视化、实时协同、多端适配。

## 一、Next.js 16 核心特性

| 特性 | 状态 | 价值 |
|------|------|------|
| Cache Components + PPR | 稳定 | 显式缓存（`"use cache"` 指令），页面/组件/函数三级粒度，静态壳即时加载 + 动态内容流式注入 |
| App Router + RSC | 稳定 | 服务端组件零客户端 JS、直接访问后端、自动代码分割、数据就近获取 |
| Streaming SSR + Suspense | 稳定 | 渐进式流式渲染，不阻塞首屏，配合 Suspense 边界精细控制加载状态 |
| 路由增强（Layout 去重 + 增量预取） | 稳定 | 共享 Layout 只下载一次（数据传输减少 60-80%），视口外链接取消预取 |
| 缓存 API（revalidateTag/updateTag/refresh） | 稳定 | SWR 语义、Server Actions 即时更新、动态数据刷新三种场景分离 |
| Devtools MCP | 稳定 | AI 辅助调试，Agent 可感知路由/缓存/日志/错误上下文 |

## 二、React 19 核心特性

### 2.1 服务端能力

| 特性 | 说明 |
|------|------|
| Server Components | 组件在服务端执行，零客户端 bundle 影响，直接访问数据库/文件系统/密钥 |
| Server Actions (`"use server"`) | 服务端函数直接从客户端调用，自动序列化/反序列化，表单渐进增强 |
| `use()` Hook | 在渲染期间读取 Promise/Context，替代 useEffect 数据获取反模式 |
| Streaming + Suspense | 服务端流式输出 HTML，客户端渐进水合 |

### 2.2 客户端能力（React 19.2）

| 特性 | 说明 |
|------|------|
| View Transitions | 原生动画过渡，导航/状态切换时元素平滑动画（`viewTransitionName`） |
| `<Activity>` 组件 | 后台活动管理，`display: none` 隐藏但保持状态，Tab 切换即时恢复 |
| `useEffectEvent()` | 从 Effect 中提取非响应式逻辑，避免不必要的重新订阅 |
| `useOptimistic()` | 乐观更新 UI，Server Action 完成前即时反馈 |
| `useFormStatus()` | 表单提交状态感知，无需手动管理 loading 状态 |
| `useActionState()` | Server Action 返回值 + 错误处理统一管理 |

## 三、核心依赖清单

### 3.1 框架核心

| 用途 | 依赖 | 版本   | 备注 |
|------|------|------|------|
| 框架 | next | ~16  | App Router + Turbopack |
| UI 库 | react  | ^19.0.0 | Server Components + 并发特性 |
| 语言 | typescript | ~6   | 严格模式，禁 `any` |

### 3.2 样式与 UI 组件

| 用途 | 依赖 | 备注                                           |
|------|------|----------------------------------------------|
| 原子化 CSS | tailwindcss v4 | CSS-first 配置（无 JS 配置文件），Rust 引擎，增量构建 5ms     |
| 组件库 | shadcn/ui | Base UI 原语 + Tailwind 样式，源码复制到项目，零依赖锁定       |
| 无障碍原语 | @base-ui-components/react | shadcn/ui 底层（Base UI），WAI-ARIA 合规，MUI 团队维护 |
| 类名合并 | tailwind-merge | 智能合并 Tailwind 类名，解决冲突覆盖，`cn()` 工具函数底层        |
| 条件类名 | clsx | 轻量条件类名拼接，配合 tailwind-merge 组成 `cn()`         |
| 组件变体 | class-variance-authority | 组件 size/variant/color 变体管理，shadcn/ui 核心依赖    |
| 动画工具类 | tw-animate-css | Tailwind v4 动画工具类，CSS-first 兼容               |
| 主题切换 | next-themes | 暗色/亮色/系统主题切换，SSR 无闪烁，与 CSS 变量配合              |
| 图标 | lucide-react | Tree-shakeable SVG 图标                        |
| 颜色系统 | OKLCH | 感知均匀色彩空间，P3 广色域，主题一致性                        |
| 动画 | framer-motion | View Transitions 补充，复杂交互动画                   |
| tailwindcss插件| tw-shimmer/tw-glass | 骨架屏/加载 shimmer 效果插件，AI 等待状态、毛玻璃折射效果插件，UI 层次感 |

### 3.3 状态管理

| 用途 | 依赖 | 备注 |
|------|------|------|
| 服务端状态 | @tanstack/react-query | 服务端数据缓存/同步/乐观更新，与 RSC 配合 SSR 预取 |
| 规范化数据层 | @tanstack/db | 客户端嵌入式数据库，规范化集合 + live queries + 乐观更新（v1.0 stable 后引入） |
| 客户端 UI 状态 | zustand | 轻量 hook-based，仅管理纯客户端 UI 状态（侧边栏/主题/临时交互） |
| URL 状态 | nuqs | 类型安全 URL 搜索参数，服务端/客户端双向同步，替代手动 searchParams |
| 表单状态 | react-hook-form + zod | 非受控表单 + schema 校验，与 Server Actions 配合 |

**硬约束**：TanStack Query 管服务端缓存；TanStack DB 扩展 Query 提供规范化存储 + 响应式 live queries；Zustand 仅管客户端 UI；禁止把服务端数据复制到 Zustand。

### 3.4 数据获取与通信

| 用途           | 依赖                                              | 备注                                                          |
|--------------|-------------------------------------------------|-------------------------------------------------------------|
| GraphQL 请求层  | graphql-request                                 | 轻量 GraphQL 客户端，配合 TanStack Query 统一缓存管理                     |
| GraphQL 代码生成 | @graphql-codegen/cli                            | Schema → TypeScript 类型 + 操作文档自动生成                           |
| REST 请求      | 原生 fetch                                        | Next.js 扩展 fetch（缓存/重验证）                                    |
| AI 对话（主方案）   | @assistant-ui/react + @assistant-ui/react-ag-ui | AG-UI 协议适配 + runtime + composable primitives，对接后端           |
| AI 对话 UI     | assistant-ui CLI 注入组件                           | shadcn 模式源码（Thread/Composer/ToolFallback/Reasoning），可自由改造   |
| AI 对话编辑器     | @assistant-ui/react-lexical                     | Lexical 作为 Composer 输入框集成                                   |
| AI 流式 Markdown | streamdown + @assistant-ui/react-streamdown     | 增量解析流式 md，无闪烁/无重排，AI 输出渲染，@streamdown/math、@streamdown/code |
| AI 对话表单| @assistant-ui/react-hook-form                   | assistant-ui 与 react-hook-form 集成                           |
| AI 辅助（轻量场景） | ai（Vercel AI SDK）                               | 仅用于不经过 AgentScope 的轻量直连 LLM 场景（如编辑器内补全），非主通信层               |
| SSE 流式接收 | 原生 EventSource                                  | Agent 状态推送、非对话场景的服务端事件                                      |
| WebSocket | 原生 WebSocket                                    | 仅多人协作实时同步场景                                                 |
| Schema 校验 | zod                                             | 运行时类型校验，API 响应/表单/环境变量统一 schema                             |
| HTML 渲染前消毒 | rehype-sanitize + dompurify                     | DOM 级 XSS 防护，AI 输出 HTML 渲染前消毒                               |

> 核心决策：assistant-ui（AG-UI 协议）为主，AI SDK 为辅。不使用 CopilotKit。

**REST vs GraphQL 分工策略**：

| 交互模式 | 数据获取方式 | 理由 |
|---------|------------|------|
| 结构化视图（v0.1–v0.2） | REST（fetch + TanStack Query） | 视图固定、端点明确、实现简单 |
| 生成式/对话模式（v0.3+） | GraphQL（graphql-request + codegen） | AI 动态组装查询、按需获取、schema 自描述便于 AI 理解 |

结构化视图的 EntityDef 数据需求可预测，REST 一个端点返回一个视图所需数据即可。生成式模式中 AI Agent 动态生成界面，数据需求不可预测，GraphQL 的声明式查询让 AI 精确获取所需字段，避免 over-fetch 或多次请求。两者长期共存。

### 3.5 富文本与编辑器

| 用途 | 依赖 | 备注 |
|------|------|------|
| 富文本编辑器 | lexical + @lexical/react | Meta 出品，插件架构，AI Tool 可直接操作文档节点 |
| 实时协同 | yjs + @lexical/yjs | CRDT 多人协作冲突合并（v2.0+ 启用） |
| Markdown 渲染 | react-markdown + remark-gfm | AI 输出 Markdown 渲染 |
| 代码高亮 | shiki | 编译时高亮，零运行时 JS，支持 RSC |

### 3.6 交互增强

| 用途 | 依赖 | 备注                                     |
|------|------|----------------------------------------|
| 命令面板 | cmdk | 对话式交互的命令区（Command Bar），⌘K 快捷键触发，斜杠命令入口 |
| 虚拟列表 | @tanstack/react-virtual | 对话历史/文档列表长列表性能优化，仅渲染可见区域 |
| 数据表格 | @tanstack/react-table | Headless 表格（排序/筛选/分页/虚拟化），配合 shadcn/ui DataTable |
| 拖拽 | @dnd-kit/core | 列表排序/文档树拖拽/看板卡片，无障碍友好，轻量 |
| Toast 通知 | sonner | 轻量 Toast，AI 异步通知/操作反馈/异常提示 |
| 可调整面板 | react-resizable-panels | IDE 风格可拖拽调整面板布局，shadcn/ui 官方集成 |
| 抽屉 | vaul | 移动端友好 Drawer 组件，shadcn/ui 官方集成 |
| 轮播 | embla-carousel-react | 轻量 Carousel，知识卡片/图片轮播，shadcn/ui 官方集成 |
| 文本自适应 | react-textarea-autosize | 自动调整高度 textarea，AI 对话输入框标配 |
| 快捷键 | react-hotkeys-hook | 声明式快捷键绑定（⌘K/Esc/快捷操作） |
| 文件上传 | react-dropzone | 拖拽文件上传区域，知识库文档上传 |
| 剪贴板复制 | copy-to-clipboard | 跨浏览器剪贴板复制，代码块复制按钮 |
| 节点流程图 | @xyflow/react (React Flow) | 工作流可视化编辑、RAG 管道、Agent 编排拓扑图、知识图谱关系展示 |
| 图表 | echarts | Auto Dev 监控面板、协作控制台仪表板、Token 用量分析 |
| PDF 预览 | @react-pdf/renderer 或 react-pdf | 文档导出预览、报表展示 |

### 3.7 搜索与动画

| 用途 | 依赖 | 备注 |
|------|------|------|
| 客户端模糊搜索 | fuse.js | 命令面板/文档树/知识库的前端即时搜索，无需后端请求 |
| 微动画图标 | lottie-react | Agent 状态指示、加载动画、空状态插画（JSON 动画，轻量） |
| 自动布局 | @dagrejs/dagre | 工作流图自动布局（配合 @xyflow/react） |
| 深度可视化 | d3-force + d3-hierarchy + d3-scale + d3-shape | 按需引入子模块（不引入完整 d3），力导向知识图谱/自定义布局/比例尺 |

### 3.8 工具函数库

| 用途 | 依赖 | 备注 |
|------|------|------|
| 通用工具函数 | es-toolkit | lodash 的现代替代，Tree-shakeable、TypeScript 原生、ESM、体积减少 90%+ |
| 日期处理 | date-fns | 模块化日期库，Tree-shakeable，时间戳格式化/相对时间/日期计算 |

### 3.9 工程化与质量

| 用途 | 依赖 | 备注                                       |
|------|------|------------------------------------------|
| 格式化 + Lint | biome | Rust 引擎，统一替代 ESLint + Prettier，10-35x 更快 |
| 单元测试 | vitest + @testing-library/react | Vite 原生，与 Nx 集成                          |
| 覆盖率 | @vitest/coverage-v8 | V8 原生覆盖率                                 |
| E2E 测试 | playwright | 跨浏览器，与 Nx 集成（AAF-023 #6 引入）              |
| 类型检查 | tsc --noEmit | CI 强制，零 `any` 容忍                         |
| Git Hooks | husky + lint-staged | 提交前格式化 + lint                            |
| 提交规范 | commitlint | 强制 Conventional Commits                  |
| 构建编排 | @nx/next | Nx monorepo 集成，增量构建/测试/缓存                |
| 类型检查加速 | @typescript/native-preview (tsgo) | 替代 tsc 类型检查加速                            |

### 3.10 可观测性与调试

| 用途 | 依赖 | 备注 |
|------|------|------|
| 错误监控 | @sentry/nextjs | 生产异常捕获 + 性能追踪 + 用户会话回放 |
| 分析 | @vercel/analytics（或自建） | Core Web Vitals + 用户行为 |
| 开发调试 | React DevTools + TanStack Query Devtools | 开发环境组件/查询状态可视化 |
| AI 调试 | Next.js Devtools MCP | AI Agent 感知应用上下文辅助调试 |

## 四、关键架构决策

| 决策 | 选择                      | 放弃                              | 理由                                                        |
|------|-------------------------|---------------------------------|-----------------------------------------------------------|
| 渲染策略 | RSC + PPR（静态壳 + 动态流式）   | 纯 CSR / 纯 SSR                   | 首屏即时 + 个性化内容流式注入，TTFB 降低 60-80%                           |
| 路由 | App Router（文件系统）        | Pages Router                    | RSC 原生支持、Layout 嵌套、并行路由、拦截路由                              |
| 构建器 | Turbopack（默认） | Webpack | 5-10x 更快，Rust 引擎，文件系统缓存                                   |
| 样式方案 | Tailwind CSS v4 + OKLCH | CSS Modules / styled-components | 零运行时、CSS-first 配置、Rust 引擎极速构建、与 shadcn/ui 生态一致            |
| 组件库 | shadcn/ui（源码复制）         | Ant Design / MUI / Chakra       | 零依赖锁定、完全可控、BaseUI 无障碍原语、Tailwind 原生、RSC 友好                     |
| 服务端状态 | TanStack Query + TanStack DB | SWR / Apollo Client         | Query 管数据获取缓存；DB 提供规范化集合 + live queries + 乐观更新            |
| 客户端状态 | Zustand                 | Redux         | 极简 API、零样板、与 RSC 边界清晰；TanStack DB 覆盖跨组件响应式场景              |
| GraphQL 客户端 | graphql-request + TanStack Query | urql / Apollo Client / Relay | 统一缓存管理（一套 TanStack 生态）、轻量（3KB）、无独立缓存层冲突                   |
| 富文本编辑器 | Lexical                 | Slate / TipTap / ProseMirror    | Meta 出品、插件架构、AI Tool 可操作节点、React 19 兼容、协同支持               |
| Lint + Format | Biome                   | ESLint + Prettier               | 单工具替代两个、Rust 引擎 10-35x 更快、零配置冲突、Next.js 16 移除 `next lint` |
| URL 状态 | nuqs                    | 手动 searchParams                 | 类型安全、服务端/客户端双向、shallow 更新不触发服务端重渲染                        |
| 流式通信 | SSE（对话） + WebSocket（协作） | 全 WebSocket                     | SSE 语义匹配单向推送、浏览器原生、WebSocket 仅双向协作场景                      |
| AI 对话集成 | assistant-ui（AG-UI 协议） | CopilotKit / Vercel AI SDK 独立使用 | AG-UI 事件模型支持多 Agent/状态同步/人工审批；composable primitives       |
| 命令面板 | cmdk                    | kbar / 自研                       | 轻量（2KB）、无样式锁定、shadcn/ui 官方集成、WAI-ARIA 合规                  |
| 画板引擎 | tldraw（v2.0+）           | Excalidraw          | SDK 设计可嵌入、React 原生、协同内置、形状/连线/文本一体化                       |
| 流程图 | @xyflow/react           | 自研 / mermaid                    | 节点+边声明式 API、拖拽/缩放/布局内置、React 19 兼容、社区活跃                   |
| 图表 | ECharts                 | D3 / Chart.js / Recharts        | 声明式配置适合 React、SSR 支持、图表类型丰富；D3 仅按需引入子模块做力导向等自定义可视化        |
| 测试框架 | Vitest + Playwright     | Jest + Cypress                  | Vite 原生速度、ESM 原生支持、Playwright 跨浏览器更稳定                     |

**无代码能力**：前端通过 EntityDef 驱动视图渲染，EntityDef 支持后端动态存储。用户/AI 通过对话或配置界面创建实体 → 后端自动建表 + 注册 API → 前端获取 EntityDef 自动渲染完整 CRUD 界面。全程无需编写前端代码、无需部署。详见 [结构化视图 · AI 生成实体](./interaction-mode-structured-view.md) | [用户自定义字段](../../framework/core/custom-fields.md)。

## 五、前端架构分层

```text
┌─────────────────────────────────────────────────────────┐
│  app/                    路由层（页面 + 布局 + 加载状态）    │
│  ├── (auth)/             认证相关路由组                     │
│  ├── (dashboard)/        仪表板路由组                       │
│  ├── chat/               聊天协作                          │
│  └── api/                Route Handlers（BFF 薄层）        │
├─────────────────────────────────────────────────────────┤
│  components/             UI 组件层                          │
│  ├── ui/                 shadcn/ui 基础组件（源码复制）      │
│  └── {feature}/          业务组件（按功能域组织）            │
├─────────────────────────────────────────────────────────┤
│  lib/                    逻辑层                             │
│  ├── api/                API 客户端（GraphQL + REST）       │
│  ├── store/              Zustand stores（纯客户端 UI 状态） │
│  ├── hooks/              自定义 Hooks                       │
│  ├── utils/              工具函数                           │
│  └── schemas/            Zod schema 定义                    │
├─────────────────────────────────────────────────────────┤
│  packages/               共享库（v0.2+ 落地）               │
│  ├── ui/                 跨端共享 UI 组件                   │
│  └── core/               跨端共享业务逻辑                   │
└─────────────────────────────────────────────────────────┘
```

### 5.1 数据流模式

```text
Server Component（数据获取）
  │
  ├── 直接 fetch / GraphQL 查询（服务端，无客户端 JS）
  │     └── 传递 props 给子组件
  │
  └── TanStack Query 预取（SSR dehydrate → 客户端 hydrate）
        └── Client Component 消费缓存数据

Client Component（交互）
  │
  ├── TanStack Query（服务端数据读取/变更）
  ├── Zustand（纯 UI 状态：侧边栏/主题/临时状态）
  ├── nuqs（URL 搜索参数同步）
  └── Server Actions（表单提交/数据变更）
```

### 5.2 缓存策略

| 层级 | 机制 | 适用场景 |
|------|------|---------|
| 页面级 | `"use cache"` + PPR | 静态内容（产品页、文档页） |
| 组件级 | `"use cache"` | 独立缓存的组件（用户头像、侧边栏导航） |
| 函数级 | `"use cache"` | 昂贵计算/API 调用结果 |
| 客户端 | TanStack Query | 服务端数据客户端缓存 + 后台重验证 |
| 路由级 | Next.js 增量预取 | 导航预加载，Layout 去重 |

## 六、框架内置能力（开箱即用）

### 6.1 请求与响应基础

| 能力 | 封装价值 |
|------|---------|
| API 客户端封装 | 统一 fetch wrapper，自动携带 JWT、错误码映射、类型安全响应 |
| GraphQL 客户端配置 | urql 预配置（认证 exchange、错误处理 exchange、SSR exchange） |
| 统一错误处理 | 全局 Error Boundary + Toast 通知，按后端错误码国际化展示 |
| 类型安全 API | GraphQL Codegen 自动生成类型 + Zod schema 运行时校验双保险 |

### 6.2 认证与权限

| 能力 | 封装价值 |
|------|---------|
| JWT 管理 | Token 存储（httpOnly cookie）、自动刷新、过期重定向 |
| 路由守卫 | proxy.ts 统一拦截未认证请求，重定向登录页 |
| 权限组件 | `<Authorized permission="xxx">` 声明式权限控制 |
| RBAC 菜单 | 动态路由 + 菜单根据用户角色过滤 |

### 6.3 AI 交互能力

| 能力 | 封装价值 |
|------|---------|
| **可组合原语** | assistant-ui primitives（ThreadPrimitive/MessagePrimitive/ComposerPrimitive/ActionBarPrimitive），非整体 widget，自由组合 |
| 流式对话渲染 | AG-UI SSE 事件 → assistant-ui runtime → 逐字渲染 + 自动滚动 + Markdown 实时解析 + 代码高亮 |
| Tool Call 渲染 | `makeAssistantToolUI` 将 Agent 工具调用映射为 React 组件（天气卡片/文档预览/图表等） |
| 人工审批（HITL） | AG-UI `INTERRUPT` 事件 + assistant-ui 内联审批 UI，Agent 执行中暂停等待人类确认 |
| Generative UI | Agent 运行时输出结构化数据（DSL）→ 前端语义组件动态组装渲染 |
| 共享状态 | `makeAssistantVisible` 暴露前端状态给 Agent + AG-UI `STATE_DELTA` 推送 Agent 状态到前端，双向同步 |
| CoAgent 协作 | 多 Agent 并行执行，各自独立 runtime，状态互相感知 |
| 对话记忆可视化 | 对话历史列表 + 上下文窗口展示 + Thread 持久化/恢复 |
| Agent 状态面板 | 多 Agent 执行状态实时展示（AG-UI RUN/STEP 事件） |
| AI 文档操作 | Lexical 插件，Agent Tool Call 返回的文档变更实时应用到编辑器 |
| 附件上传/展示 | assistant-ui Attachment 组件（图片/文件/拖拽上传） |
| 语音输入 | Web Speech API / Whisper 集成（待评估） |
| 推理过程展示 | assistant-ui Reasoning 组件（折叠/展开思维链） |
| 建议操作 | Agent 推荐下一步操作，用户一键执行 |

### 6.4 主题与国际化

| 能力 | 封装价值 |
|------|---------|
| 暗色/亮色主题 | OKLCH 色彩系统 + CSS 变量 + `prefers-color-scheme` 自动适配 |
| 国际化 | next-intl（服务端 + 客户端统一），按路由分割翻译文件 |
| 响应式布局 | Tailwind 断点 + Container Queries，移动端优先 |

### 6.5 性能优化

| 能力 | 封装价值 |
|------|---------|
| 图片优化 | `next/image` 自动 WebP/AVIF + 响应式 srcset + 懒加载 |
| 字体优化 | `next/font` 零布局偏移 + 子集化 |
| 代码分割 | RSC 自动分割 + `dynamic()` 按需加载重组件（编辑器/图表） |
| 预取策略 | Link 组件视口内自动预取 + hover 优先级提升 |

## 八、颜色系统决策：OKLCH

| 维度 | OKLCH | HSL |
|------|-------|-----|
| 感知均匀性 | ✅ 亮度调整不失真 | ❌ 同 L 值不同色相亮度差异大 |
| 色域支持 | P3 广色域原生 | 仅 sRGB |
| 主题生成 | 调整 L/C 即可生成一致色阶 | 需手动微调每个色值 |
| 浏览器支持 | 现代浏览器全支持（Chrome 111+, Safari 16.4+） | 全支持 |
| Tailwind v4 | 原生支持 OKLCH | 需手动转换 |

详见 [为什么选 OKLCH](../../../explanation/why-oklch.md)。

## 九、其他内置能力

| 能力          | 场景                      | 候选方案 | 计划版本 |
|-------------|-------------------------|---------|---------|
| 支付集成        | 积分购买、Token 充值、订阅        | 微信/支付宝扫码支付（国内）、Stripe（国际） | v1.0+ |
| 语音输入/输出     | 对话语音交互                  | Web Speech API / Whisper / ElevenLabs | v1.0+ |
| 本地优先 + 云同步  | 离线编辑、弱网体验               | IndexedDB/OPFS + CRDT 同步（Yjs） | v2.0+ |
| 插件系统        | 前端功能扩展                  | 动态 import + 插件注册表 + 沙箱隔离 | v2.0+ |
| 模板市场      | 工作流/工具/技能/Agent/知识库模板分享 | 模板 Registry + 版本管理 + 预览 | v1.0+ |
| 文档+画布+表格三合一 | Block 编辑器               | Lexical Block + tldraw 嵌入 + 表格插件 | v2.0+ |
| 嵌套文档 + 拖拽排序 | 文档树管理                   | dnd-kit + 递归树组件 | v0.2+ |
| 文档版本历史      | 版本对比/回滚                 | CodeMirror Merge / diff 算法 + 时间线 UI | v1.0+ |
| 全文搜索        | 文档/知识库搜索                | 后端 Elasticsearch/PgVector + 前端 fuse.js（本地） | v1.0+ |
| RAG 管道可视化   | 知识检索流程展示                | @xyflow/react 子图 + 步骤高亮 | v1.0+ |
| 多模型切换 UI    | 模型选择/对比                 | 模型选择器组件 + 配额展示 | v0.2+ |
| 积分/Token 计费 | 用量统计/规则配置               | 仪表板组件 + 规则编辑表单 | v1.0+ |
| 虚拟工作室       | 3D 空间协作                 | react-three-fiber + tunnel-rat | v3.0+ |
| 文档演示模式     | 智能文档一键转幻灯片             | reveal.js 嵌入 | v2.0+ |
| 地理地图可视化   | 用户分布/地理数据展示            | Leaflet + react-leaflet | 按需 |
| 画板图像编辑     | 画板内图片裁剪/滤镜/标注          | fabric.js | v2.0+ |
| 力导向知识图谱   | 知识库实体关系物理模拟可视化         | D3 force + React 封装 | v1.0+ |

---

> 详细 UI 设计规范见 [UI 设计](../../ui/)
> [对话式交互设计](tmp/conversational-interaction.md)
