# AAF-037 前端差距分析报告

> 分析日期：2026-05-19
> 对比范围：v0.1.0 前端实现 vs 设计文档（interaction-mode-structured-view.md / tech-stack.md / directory-structure.md / P1 功能文档）

## 一、核心架构差距（#3701）

### 1.1 FieldDef 联合类型缺失（major）

| 设计文档定义 | types/field.ts 状态 | components/form/ 组件 |
|-------------|--------------------|--------------------|
| subtable | ❌ 未定义 | ✅ subtable.tsx 已实现 |
| formula | ❌ 未定义 | — （FormulaEngine 在 lib/ 已实现） |
| money | ❌ 未定义 | ✅ field-money.tsx 已实现 |
| quantity | ❌ 未定义 | ✅ field-money.tsx 含 FieldQuantity |
| signature | ❌ 未定义 | ✅ field-signature.tsx 已实现 |
| cascader | ❌ 未定义 | ✅ field-cascader.tsx 已实现 |
| switch | ❌ 未定义 | ✅ field-switch.tsx 已实现 |

**现状**：types/field.ts 定义了 15 种类型（text/textarea/number/email/date/checkbox/select/relationship/richText/json/code/upload + 布局 group/tabs/row），缺失 7 种高级类型。组件已实现但类型系统未覆盖，导致 EntityDef 配置中无法声明这些字段。

### 1.2 组件注册表映射不完整（major）

**register.ts 当前注册**：

| 类别 | 已注册 | 缺失 |
|------|--------|------|
| Field | text/email/textarea/number/checkbox/select/date（7 种） | relationship/richText/upload/switch/money/signature/cascader/subtable（8 种） |
| Cell | text/email/textarea/number/date/select/checkbox/relationship（8 种） | richText/upload/json/code/switch/money/formula（7 种） |

**影响**：ViewEngine 渲染时，未注册的字段类型会 fallback 到空白，无法正常展示。

### 1.3 ViewType 声明与实现不一致（minor）

```typescript
// 当前声明
export type ViewType = "list" | "form" | "kanban" | "graph" | "chart" | "calendar"

// 实际实现
switch: list ✅ | form ✅ | kanban ✅ | pivot ✅（未在 ViewType 中）| graph ❌ | chart ❌ | calendar ❌
```

**修正**：ViewType 应加入 `pivot`，graph/chart/calendar 保留占位。

### 1.4 架构三层模型一致性 ✅

| 模块 | 状态 | 说明 |
|------|------|------|
| EntityRegistry | ✅ | register/get/getAll/getByGroup/registerMixin |
| ComponentRegistry | ✅ | registerFieldType/registerCellType/registerViewType/registerBatchAction |
| ViewEngine | ✅ | 路由 + overrides + 注册表查找 + 内置视图 |
| Mixin/Extends | ✅ | resolveMixins + resolveExtends |
| FieldContext | ✅ | $record/$user/$parent/$params/$env |
| FormulaEngine | ✅ | 表达式求值 |
| ValidationRules | ✅ | cross_field/unique/custom |

### 1.5 数据层 hooks 命名偏离（minor）

| 设计文档 | 实际实现 | 偏离程度 |
|---------|---------|---------|
| useEntityRecord | useEntityDetail | 命名不同，接口一致 |
| useEntityList | useEntityList | ✅ |
| useEntityMutation | useEntityMutations | ✅（复数形式，含 create/update/delete） |

---

## 二、技术选型与目录结构合规（#3702）

### 2.1 依赖安装状态

**核心依赖（全部已安装）**：TanStack Query / Zustand / nuqs / shadcn(base-ui) / @dnd-kit / react-hook-form / lexical / yjs / sonner / cmdk / framer-motion / next-intl / react-grid-layout / @tanstack/react-virtual / @tanstack/react-table / react-resizable-panels / vaul / @monaco-editor/react

**按需引入（v0.1 不构成 blocker）**：

| 依赖 | 设计文档用途 | 当前替代方案 |
|------|------------|------------|
| zod | Schema 校验 | 根 package.json 有，workspace 继承 |
| react-hotkeys-hook | 快捷键 | cmdk 内置 |
| fuse.js | 模糊搜索 | 简单 filter |
| es-toolkit | 工具函数 | 无需求 |
| date-fns | 日期处理 | 原生 Date |
| graphql-request | GraphQL | v0.1 只用 REST |
| shiki | 代码高亮 | 后续按需 |
| @sentry/nextjs | 错误监控 | 后续引入 |

### 2.2 依赖方向违反（blocker）

**规则**：`lib/` → 禁止引用 `features/` 和 UI 层；`components/` → 禁止引用 `features/`

**违反 1：lib/ → features/（13 处）**

| 文件 | 引用 |
|------|------|
| lib/queries/use-entity-list.ts | @/features/entity-engine/types |
| lib/queries/use-entity-detail.ts | @/features/entity-engine/types |
| lib/queries/use-entity-mutations.ts | @/features/entity-engine/types |
| lib/queries/use-filter-params.ts | @/features/entity-engine/components/list |
| lib/hooks/use-ai-awareness.ts | @/features/entity-engine/types/field |
| lib/hooks/use-batch-operation.ts | @/features/entity-engine/types |
| lib/hooks/use-column-preferences.ts | @/features/entity-engine/types |
| lib/hooks/use-export-progress.ts | @/features/entity-engine/types |
| lib/hooks/use-optimistic-lock.ts | @/features/entity-engine/types |
| lib/hooks/use-subtable.ts | @/features/entity-engine/types |
| lib/api/page-def.ts | @/features/page-engine/types |
| lib/queries/use-entity-list.test.ts | @/features/entity-engine/types |
| lib/queries/use-entity-mutations.test.ts | @/features/entity-engine/types |

**根因**：EntityDef/FieldDef 等核心类型定义在 features/ 层，但 lib/ 的 hooks 需要这些类型。

**修复方案**：将 `features/entity-engine/types/` 移动到 `lib/types/entity/`（或提取到 `@aaf/core`）。features/entity-engine 改为从 lib/types 重导出。

**违反 2：components/ → features/（6 处）**

| 文件 | 引用 |
|------|------|
| components/common/CommandPalette.tsx | @/features/entity-engine（entityRegistry） |
| components/common/SubscribeButton.tsx | @/features/entity-engine/types |
| components/form/field-textarea.tsx | @/features/rich-text-editor |
| components/form/relationship-picker.tsx | @/features/entity-engine/types |
| components/form/rich-text-editor.tsx | @/features/rich-text-editor |
| components/form/subtable.tsx | @/features/entity-engine/types |

**修复方案**：
- 类型引用：类型移到 lib/types 后自然解决
- CommandPalette 引用 entityRegistry：改为通过 props 注入实体列表
- form 组件引用 rich-text-editor：改为 re-export 或 lazy import

### 2.3 目录结构偏离（可接受）

| 设计文档 | 实际 | 评估 |
|---------|------|------|
| `(canvas)/` 路由组 | 未创建 | v0.1 不涉及画板，可接受 |
| sections/ 下 chat/canvas/document/workflow/knowledge/agent | 只有 layout/ 和 entity/ | v0.1 用 ViewEngine 统一渲染，合理 |
| features/ 有 flow-editor/copilot/data-table | 有 entity-engine/rich-text-editor/page-engine/page-editor/entity-editor/livechat/ai-assist/dashboard | 合理演进 |
| packages/ui + packages/editor + packages/_test-utils | 未创建 | v0.2+ 提取 |

### 2.4 workspace packages 合规

| 设计文档 | 实际 | 状态 |
|---------|------|------|
| packages/core | @aaf/core ✅ | 存在 |
| packages/_config/tailwind | @aaf/tailwind-config ✅ | 存在 |
| packages/_config/tsconfig | packages/tsconfig ✅ | 存在 |
| — | @aaf/hooks | 设计文档未列但合理 |
| packages/ui | — | v0.2+ |
| packages/editor | — | v0.2+ |
| packages/_test-utils | — | 后续按需 |

---

## 三、已实现功能文档对比（#3703）

### 3.1 P1 文档对比

| 文档 | 结论 | 偏离 |
|------|------|------|
| rich-text-editor.md | ✅ 符合 | minor：缺 markdown.ts/plaintext.ts converter |
| page-engine.md | ✅ 符合 | 无 |
| command-palette.md | ✅ 骨架符合 | minor：无 fuse.js；无 "/" DSL 指令和 "@" 上下文引用（v0.3+） |
| structured-view-supplements.md | 见 #3701 | — |
| change-history-design.md | ✅ 符合 | 无 |
| data-dictionary-design.md | ✅ 符合 | 无 |

### 3.2 P2 UI 规范对比

| 文档 | 结论 |
|------|------|
| design-system.md | ✅ OKLCH 色彩系统（global.css 130+ 变量） |
| ui-experience.md | ✅ loading.tsx / Skeleton / error.tsx / ErrorBoundary / 响应式断点 |
| sense-ui.md | ✅ framer-motion 动效（components/animate/） |

---

## 四、重构优先级

| 优先级 | 问题 | 任务 | 影响面 |
|--------|------|------|--------|
| 🔴 blocker | 依赖方向违反（19 处） | #3705 | 架构合规 |
| 🟡 major | FieldDef 类型缺失 7 种 | #3704 | 类型安全 |
| 🟡 major | 组件注册表不完整（15 种缺失） | #3706 | 运行时渲染 |
| 🟢 minor | useEntityDetail 命名偏离 | 随 #3705 一并修正 | 一致性 |
| 🟢 minor | ViewType 未含 pivot | 随 #3706 一并修正 | 类型完整 |
| 🟢 minor | converter 不全 | 后续版本 | 非核心路径 |

### 执行顺序

```text
#3704（类型补全）→ #3706（注册表补全）→ #3705（依赖方向修复）
```

先做 #3704/#3706（纯增量，风险低），最后做 #3705（涉及文件移动和 import 路径变更，影响面广）。
