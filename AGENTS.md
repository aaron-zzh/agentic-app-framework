# AGENTS.md — AAF 指针文档

> **本文件是 AI 智能体的入口索引，不是权威规范文档。**
> 所有详细规范、设计、流程的**唯一真理来源**都在 `docs/` 下。本文件仅提供一眼看清项目的快速参考，不再内联重复内容。
> 发现本文件与 docs/ 冲突时，以 docs/ 为准并在 [改进意见](docs/prd/improvements.md) 中记录。

## 项目一句话

AAF（Agentic App Framework）是生产级 AI 原生多智能体应用开发框架。AI 是架构的一等公民，不是附加物。核心能力：多智能体协作 · 工作流引擎 · 知识库管理 · 规范驱动开发 · AI 自动开发 · 无代码开发 · 外部生态整合。

## 核心概念（消歧义）

> AAF 中有几个词在不同上下文含义不同，AI 编码时必须区分。

| 概念 | 含义 | 代码位置 | 不是什么 |
|------|------|---------|---------|
| **工作流** | AI 编排流水线——LLM 节点、知识库节点、条件分支等组成的 AI 应用流程，对标 Dify 工作流。底层同样基于 Flowable 执行，节点类型为 `LlmNode`/`AgentNode` 等 AI 节点 | `apps/webui/src/features/flow-editor/`、`engine/workflow/node/LlmNode` 等 | 不是审批流（虽然底层引擎相同） |
| **审批流** | Flowable BPMN 驱动的业务流程——请假、报销、采购等有人工节点的企业流程。节点类型为 `UserTask`（人工审批）、`ServiceTask` 等 | `apps/service/aaf-api/src/main/java/.../module/system/workflow/`、`engine/workflow/FlowableWorkflowEngine` | 不是 AI 编排（虽然底层引擎相同） |
| **技能（Skill）** | Assistant 层的意图路由规则——匹配用户意图后路由到对应 Agent | `engine/skill/SkillDefinition` | 不是 AgentScope 的 Skill |
| **工具（Tool）** | Agent 可调用的原子能力——AgentScope `@Tool` 注解方法，LLM 通过 Function Calling 调用 | `engine/workflow/WorkflowTool`、AgentScope `Toolkit` | 不是 Spring AI `ToolCallback`（那是直连链路用的） |

> **注意**：工作流和审批流**底层都是 Flowable**，区别在于流程节点类型。代码包名 `module.system.workflow` 和 `engine.workflow` 当前指的是**审批流**（Flowable），不是 AI 工作流。命名不一致是历史遗留，v0.2.0 重构时统一。

## 技术栈（速览）

- **后端**：Java 25, Spring Boot 4, Spring AI, WebFlux, GraphQL, MCP
- **数据**：PostgreSQL/PgVector, Neo4j, Redis, Flyway
- **工作流**：Flowable
- **前端**：Next.js 16, React 19, TypeScript
- **跨端**：UniApp
- **工程化**：Nx monorepo, pnpm, Maven

## 目录结构

```text
apps/service/  → Spring Boot 后端（Maven 多模块）
apps/webui/    → Next.js 前端（App Router）
apps/uniapp/   → UniApp 小程序/APP（待开发）
packages/      → 共享库（待建设）
docs/          → 项目文档（Diátaxis 四象限 + Team/Dev 规范）
.kiro/         → 智能体配置（steering / agents / prompts / hooks / skills）
tmp/           → 参考项目与素材（不参与构建，仅供 AI 查阅学习）
```

### tmp/ 参考目录索引

| 目录 | 用途 | 关键项目 |
|------|------|---------|
| `tmp/mem/` | 记忆与知识库架构参考 | graphiti（Neo4j 时序图谱）、m_flow（图路由记忆）、ReMe（AgentScope 程序化记忆）、mem0（多级记忆架构） |
| `tmp/agent/` | 智能体框架参考 | agentscope（含 agentscope-java/studio/runtime/samples）、camel、CowAgent |
| `tmp/nextjs/` | Next.js 应用参考 | next-ts|
| `tmp/java/` | Java 后端参考 | ruoyi-vue-pro、JeecgBoot |

- 后端根包：`com.xuejiai.aaf`，业务模块 `com.xuejiai.aaf.module.{模块名}`
- 后端 Maven 命令通过 `project.json` 桥接为 Nx target，统一 `pnpm nx <target> service`

## 一键命令

| 命令 | 负责人 | 作用 |
|------|--------|------|
| `pnpm check` | developer | 全部项目自验证（lint + 单测 + typecheck + build） |
| `pnpm check:affected` | developer | 只验证 affected 项目（完工汇报前必跑） |
| `pnpm acceptance` | tester | 全部项目验收/集成测试 |
| `pnpm acceptance:affected` | tester | 只跑 affected 项目的验收测试 |
| `pnpm format` / `pnpm format:check` | — | Nx 格式化 / 格式检查 |

developer 完工前必跑 `pnpm check:affected`，失败循环修复到全绿。

## 开发流程（摘要）

> 详见 [迭代过程规范](docs/reference/team/process-standard.md)。

### 迭代全景

```text
准备 → 启动 → 执行 → 发布 → 总结
                ↑        |
                └── 回退 ←┘（质量门控不通过）
```

周期 2-4 周。默认**交互模式**（每阶段完成后等待人类指令）；人类明确说"自动执行"时切换批量模式。

### 核心原则

- **一句话开发**：人类给出目标 → AI 全流程执行 → 系统自动化保障 → 人类异步审核
- **规范驱动**：先写规范再写代码，规范是人类和 AI 的共同真理来源（Spec-Anchored 模式）
- **质量内建**：验证与确认分离，系统自动化门控，问题按类型回退修复

### 阶段概览

| 阶段 | 目标 | 入口条件 | 关键步骤 |
|------|------|---------|---------|
| **1. 准备** | 明确迭代范围 | 人类提出版本目标 | 版本规划 → 需求调研 → 用户故事拆分 → 技术预研 → 选取迭代范围 |
| **2. 执行** | 按任务流水线交付 | Epic 已审核并录入 backlog | 需求细化 → 技术设计 → UI 设计 → 编码 → 审查 → 验收 → 审计 → 质量门控 |
| **3. 发布** | 部署验证 | 质量门控通过 + 🔴 人类确认 | 测试环境部署 → DB 迁移 → 用户验收 → 发布说明 → 正式发布 |
| **4. 总结** | 回顾改进 | 发布完成 | 迭代回顾 → 度量分析 → 过程改进 → 知识沉淀 → backlog 归档 |

### 执行阶段：任务流水线

```text
product(Epic→Story 拆分 + Spec 细化)
  + architect(技术设计 + 任务拆分)
    → designer(UI/交互设计)           ← 涉及前端时
      → developer(编码 + 单元测试)    ← 验证（check）
        → architect(代码审查)
          → tester(验收测试)          ← 确认（acceptance）
            → qa(过程审计)
              → 质量门控（blocker=0 且 major≤2）
```

### 各步骤关键要求

| 步骤 | 负责人 | 关键产出 | 适用规范 |
|------|--------|---------|---------|
| 需求细化 | product | Spec（数据模型、业务规则、Gherkin AC） | [需求管理规范](docs/reference/dev/requirement-standard.md) |
| 技术设计 | architect | 接口签名 + 类结构 + ADR + 技术任务 `#N` | [架构约束](docs/reference/dev/architecture-constraints.md) |
| UI 设计 | designer | 页面结构 + 交互流程 + 组件规范 | [UI 设计规范](docs/design/ui/Readme.md) |
| 编码实现 | developer | 源码 + `dev-log.md` + `check:affected` 全绿 | [编码风格](docs/reference/dev/apps/service/coding-style-standard.md) |
| 代码审查 | architect | `review.md`（blocker/major/minor 计数） | [代码审查规范](docs/reference/dev/code-review-standard.md) |
| 验收测试 | tester | `test-report.md`（AC 覆盖矩阵） | [验收测试规范](docs/reference/dev/test/acceptance-test-standard.md) |
| 过程审计 | qa | 审计记录（流程合规 + 文档完整性） | [过程审计规范](docs/reference/team/process-audit-standard.md) |

### 规范驱动流转

```text
需求规格(product) → 设计规格(architect) → 代码(developer) → 测试(tester) → 审计(qa)
     ↑                    ↑                    ↑                ↑              ↑
     └────────────────────┴────────────────────┴────────────────┴──────────────┘
                          发现偏离 → 向上游反馈修正
```

下游发现与上游规范矛盾时，必须向上游反馈修正，不得自行偏离。

## AI 行为硬规则（编码时遵守）

以下规则参考 multica CLAUDE.md 高密度风格，部分条目已登记 [改进意见 A-F](docs/prd/improvements.md) 待评估采纳，当前 agent 应主动遵循（作为软约束，未来转硬约束）：

- **禁兼容层**：AAF 未 v1.0 发布，不允许加 fallback / dual-write / legacy adapter / temporary shim。替换旧 API 直接替换，不保留双路径
- **不做 broad refactors**：只改任务要求的代码范围。借机重构相邻模块即 blocker（与"≥5 文件需协调者评估"配套）
- **优先已有模式**：同一问题已有实现 → 复用或改造；禁止并行抽象（两套做同一件事）
- **代码注释语言**：Java / TS 代码内注释保持一致（建议中文，与 `docs/` 真理源一致），禁中英混用
- **文档禁编号**：编写文档时章节标题不加数字编号（如"一、""1."），用 Markdown 标题层级表达结构。已有编号的历史文档不主动修改，新建和重写时遵守
- **TypeScript 严格模式**：类型必须显式，禁 `any` / 禁 `@ts-ignore`（特殊情况加注释解释）
- **前端服务端状态边界**：TanStack Query 管服务端缓存；Zustand 仅管客户端 UI；禁止把服务端数据复制到 Zustand
- **测试放置**：共享逻辑 → `packages/*.test.ts`；平台接线 → `apps/*.test.tsx`；测试需 mock `next/*` 来测共享组件即 blocker（位置错误）
- **编码任务开始前必须加载对应 agent 上下文**：前端任务开始前加载 `.kiro/agents/developer-webui.json` 中 `resources` 列出的所有文档；后端任务开始前加载 `.kiro/agents/developer-service.json` 中 `resources` 列出的所有文档；前后端同时涉及时两者都加载。不得凭记忆跳过加载直接编码
- **完工前必跑 `pnpm check:affected`**：失败即未完工，不得提交或汇报
- **上下文使用率 ≤ 50%**：超过必须分析原因 + 记录 + 优化（详见 [上下文管理规范](docs/reference/team/context-management-standard.md)）
- **strReplace 前必须读取文件最新内容**：禁止凭记忆直接写 `old_str`，必须先用文件读取工具确认当前内容再做替换，否则大概率 `old_str not found`
- **Windows 环境禁用 Linux shell 参数**：shell 命令运行在 Windows PowerShell，禁用 `-tail` / `-head`（Linux 专属），读取文件末尾用 `Select-Object -Last N`，读取开头用 `Select-Object -First N`；优先用文件读取工具代替 shell 命令
- **base-ui Trigger 组件禁止套 `<Button>`**：`DropdownMenuTrigger`、`SelectTrigger` 等 base-ui primitive trigger 本身渲染为 `<button>`，不能再用 `asChild` 套 `<Button>`（会产生 button 嵌套 button 导致 hydration error）。需要自定义样式时直接给 trigger 加 `className`（可用 `buttonVariants()` 生成），需要自定义组件时用 `render` prop：`<Menu.Trigger render={<MyButton />}>`
- **base-ui 组合优先用 `render` prop，禁用 `asChild`**：base-ui 不支持 `asChild`（Radix 模式），组合自定义组件统一用 `render` prop。两种用法：`render={<Button variant="ghost" />}`（用封装的 Button 组件）或 `render={<button type="button" />}`（原生 button 自加 className）
- **`Button` 渲染为非 button 元素时必须加 `nativeButton={false}`**：`Button` 配合 `render={<Link href="..." />}` 渲染为链接时，必须加 `nativeButton={false}`，否则 base-ui 会同时渲染 `<button>` 导致嵌套错误。示例：`<Button nativeButton={false} render={<Link href="/path" />}>文字</Button>`

## AI 验证循环（写代码时的内循环）

参考 multica "AI Agent Verification Loop"——按场景分层决定验证力度：

| 场景 | 验证要求 | 说明 |
|------|----------|------|
| 🟢 对话中小改动（探索/试错/文档） | 可跳过 | 自行判断，不阻塞对话节奏 |
| 🟡 单文件/单模块改动 | 跑对应项目 test | `pnpm nx test service` 或 `pnpm nx test webui` |
| 🔴 任务完工汇报前 / PR 前 | `pnpm check:affected` 全绿 | 失败即未完工，不得汇报 |
| 🔴 迭代交付前 | `pnpm check` + `pnpm acceptance` 全绿 | 见交付清单 |

```
写代码（满足需求）
    ↓
pnpm check:affected
    ↓
全绿？
  └─ 是 → 任务完成（可汇报）
  └─ 否 → 读错误输出 → 修代码 → 重跑
```

**快速迭代**：只改 Java 时先 `pnpm nx test service`，只改 TS 时先 `pnpm nx test webui`，完工前再跑全量 `check:affected` 作为门禁。

## 测试放置与命名规则

| 层 | developer 单测 | tester 验收/集成 |
|----|---------------|------------------|
| Java | `XxxTest.java` → Surefire | `XxxIT.java` / `XxxAcceptanceTest.java` → Failsafe |
| TS | `xxx.test.ts(x)` / `xxx.spec.ts(x)` → Vitest | `xxx.accept.test.ts(x)` → Vitest（独立 config） |

**Tests follow the code, not the app**（借鉴 multica 硬规则，已登记 [改进意见 E](docs/prd/improvements.md)）：

- 共享业务逻辑（pure logic，无 DOM）→ `packages/*/src/**/*.test.ts`
- 共享 UI 组件（jsdom，不 mock 框架）→ `packages/views/**/*.test.tsx`（v0.2 `packages/` 落地后）
- 平台接线（需 mock `next/*` / `react-router`）→ `apps/webui/**/*.test.tsx`
- E2E 用户流程 → `e2e/**/*.spec.ts`（AAF-023 #6 Playwright 引入后）
- Java 单元 → Surefire；Java 集成/验收 → Failsafe

如测试需 mock `next/navigation` 来测共享组件，**测试放错位置**——移到 `packages/` 并 mock `@aaf/core`。

## 包边界硬规则（速览）

AAF 后端 Maven 模块依赖方向（详见 [architecture-constraints.md](docs/reference/dev/architecture-constraints.md)）：

- `aaf-dependencies` ← 无依赖（纯 BOM，禁止任何 Java 代码）
- `aaf-common` ← 仅第三方工具库，零业务依赖，禁止 Spring Bean / 数据库访问
- `aaf-framework` ← 依赖 common，禁止依赖业务模块 / aaf-api / aaf-auto-dev
- `aaf-auto-dev` ← 依赖 framework + common，禁止依赖 aaf-api
- `aaf-api` ← 依赖所有上面，是启动入口；跨业务包禁止直接访问 entity/repository

违反方向即 ArchUnit blocker（待 AAF-023 Maven 拆分完成 + P2.3 激活 `LayeringTest.java` 的 5 条真实规则）。前端 `packages/` 边界规则在 v0.2 + P2.3 首个共享包落地时同步定义。

## 文档导航（唯一真理源）

> 详细规范在 `docs/` 下，本文件与 `.kiro/steering/collaboration.md` 是**摘要 + 入口**，不是权威源。
> 冲突时以 `docs/reference/team/Readme.md` 为准。

### 团队与流程（从这里开始）

| 场景 | 文档 |
|------|------|
| 团队架构与角色总览 | [docs/reference/team/Readme.md](docs/reference/team/Readme.md) |
| 协作硬约束红线 | [.kiro/steering/collaboration.md](.kiro/steering/collaboration.md) |
| 协作详细规范（人 / AI / 系统三方） | [docs/reference/team/collaboration-standard.md](docs/reference/team/collaboration-standard.md) |
| 迭代过程详细规范 | [docs/reference/team/process-standard.md](docs/reference/team/process-standard.md) |
| 过程审计 | [docs/reference/team/process-audit-standard.md](docs/reference/team/process-audit-standard.md) |

### 编码与测试

| 类别 | 文档 |
|------|------|
| 编码行为硬约束 1-9 | [.kiro/skills/coding-standards/SKILL.md](.kiro/skills/coding-standards/SKILL.md) |
| 编码风格（Java） | [docs/reference/dev/apps/service/coding-style-standard.md](docs/reference/dev/apps/service/coding-style-standard.md) |
| 架构约束 | [docs/reference/dev/architecture-constraints.md](docs/reference/dev/architecture-constraints.md) |
| 代码审查（含对称性检查 12 项） | [docs/reference/dev/code-review-standard.md](docs/reference/dev/code-review-standard.md) |
| 提交规范 | [docs/reference/dev/git/commit-standard.md](docs/reference/dev/git/commit-standard.md) |
| 单元测试 | [docs/reference/dev/test/unit-test-standard.md](docs/reference/dev/test/unit-test-standard.md) |
| 验收测试 | [docs/reference/dev/test/acceptance-test-standard.md](docs/reference/dev/test/acceptance-test-standard.md) |
| 集成测试 | [docs/reference/dev/test/integration-test-standard.md](docs/reference/dev/test/integration-test-standard.md) |

### 需求管理

| 类别 | 文档 |
|------|------|
| 需求管理规范 | [docs/reference/dev/requirement-standard.md](docs/reference/dev/requirement-standard.md) |
| 路线图 | [docs/prd/roadmap.md](docs/prd/roadmap.md) |

### 设计与思想

| 文档 | 内容 |
|------|------|
| [docs/design/architecture.md](docs/design/architecture.md) | 整体架构（五层 + 五层智能） |
| [docs/design/framework/engine/meta/meta-engine.md](docs/design/framework/engine/meta/meta-engine.md) | 元引擎核心设计 |
| [docs/explanation/architecture-thought.md](docs/explanation/architecture-thought.md) | 架构决策背后的 Why |
| [docs/explanation/design-principles.md](docs/explanation/design-principles.md) | 化繁为简、DRY、AI 友好等 |
| [docs/design/framework/component-overview.md](docs/design/framework/component-overview.md) | 架构级核心组件总览 |

### 任务与需求

| 文档 | 内容 |
|------|------|
| [docs/task/backlog.md](docs/task/backlog.md) | 所有用户故事的唯一来源 |
| [docs/task/aaf-v0.1.0.md](docs/task/aaf-v0.1.0.md) | 当前迭代任务计划 |
| [docs/prd/roadmap.md](docs/prd/roadmap.md) | 版本里程碑路线图 |
| [docs/prd/improvements.md](docs/prd/improvements.md) | 改进意见池 |

## 硬约束（摘要，详见 steering 与编码规范）

- 任务编号：用户故事 `AAF-{三位}`、技术任务 `#XXXNN`（XXX=故事编号，NN=序号，无前缀零）；提交脚注 `Task: #XXXNN`
- 完工前必跑 `pnpm check:affected`，失败汇报视为未完工
- 批量修改文件（≥5 个）或改接口签名 → 协调者评估
- 🔴 高风险设计必须人类审核后再开发
- 上下文使用率不得超过 50%
- 规范文档（`docs/reference/`）只能由协调者修改
- 一个知识点一份文档；发现重复记录到 [改进意见](docs/prd/improvements.md)
- 开发记录：v0.1.0 采用轻量模式——每完成一个任务在 `dev-log.md` 中记录一行：`- ✅ #N 标题 — 一句话核心点（日期）`。从 v0.2.0 开始使用完整格式

<!-- nx configuration start-->
<!-- Leave the start & end comments to automatically receive updates. -->

# General Guidelines for working with Nx

- For navigating/exploring the workspace, invoke the `nx-workspace` skill first - it has patterns for querying projects, targets, and dependencies
- When running tasks (for example build, lint, test, e2e, etc.), always prefer running the task through `nx` (i.e. `nx run`, `nx run-many`, `nx affected`) instead of using the underlying tooling directly
- Prefix nx commands with the workspace's package manager (e.g., `pnpm nx build`, `npm exec nx test`) - avoids using globally installed CLI
- You have access to the Nx MCP server and its tools, use them to help the user
- For Nx plugin best practices, check `node_modules/@nx/<plugin>/PLUGIN.md`. Not all plugins have this file - proceed without it if unavailable.
- NEVER guess CLI flags - always check nx_docs or `--help` first when unsure

## Scaffolding & Generators

- For scaffolding tasks (creating apps, libs, project structure, setup), ALWAYS invoke the `nx-generate` skill FIRST before exploring or calling MCP tools

## When to use nx_docs

- USE for: advanced config options, unfamiliar flags, migration guides, plugin configuration, edge cases
- DON'T USE for: basic generator syntax (`nx g @nx/react:app`), standard commands, things you already know
- The `nx-generate` skill handles generator discovery internally - don't call nx_docs just to look up generator syntax

<!-- nx configuration end-->
