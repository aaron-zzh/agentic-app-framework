---
level: Practice
layer: Model
purpose: 规范 WebUI 工作区路由、页面 View 与应用壳层的职责边界
status: published
version: 1.3.0
date: 2026-10-10
author: AaronZZH
changelog:
  - 2026-10-10 | 新增 EntityDef 声明式自定义视图 customViews，特定界面挂在实体下切换，收窄手写页例外
  - 2026-10-10 | 新增「管理后台页面」：admin 页面统一由 sys_entity_def 驱动，动作在 actions 声明
  - 2026-08-13 | 统一页面 View 到 sections，并明确 layouts 应用壳层
---

# 工作区页面结构规范

> 本文档是 webui `(workspace)` 路由组下所有页面的编写规范，供后续新建页面和存量页面重构参考。

## 核心原则

- `app/` 下的 `page.tsx` 只做路由接线，不写业务逻辑
- 页面业务组装和 UI 放在 `sections/{domain}/view/` 下的 View 组件
- 可复用领域能力放在 `features/{feature}/`，由 sections 消费
- 跨页面应用壳放在 `layouts/{shell}/`，由 `app/**/layout.tsx` 接线
- 需要 Chatter 嵌入的页面，在 View 组件里声明；无声明则默认浮动

## 标准结构

### 目录层次

```text
app/(workspace)/{路由}/
  page.tsx          ← 路由入口（Server Component，极简）
  layout.tsx        ← 仅在该路由需要私有路由边界时创建
  loading.tsx       ← 仅在需要独立骨架屏时创建

layouts/{shell}/
  {Shell}Layout.tsx ← 跨页面应用壳；侧栏、顶栏、导航和全局入口

sections/{domain}/
  view/
    index.ts
    {domain}-view.tsx ← 页面主视图（"use client"，组合 features）
  components/         ← 该业务域的页面级组件（可选）

features/{feature}/
  components/       ← 可复用能力组件
  hooks/            ← 能力内部 hooks
  lib/              ← 注册表、算法和转换逻辑
  types.ts          ← 对外类型
  index.ts          ← 公开 API
```

### page.tsx 模板

```tsx
/**
 * {功能名}页面
 * @author AaronZZH & Kiro
 */

import { {Domain}View } from "@/sections/{domain}/view"

export default function {Domain}Page() {
  return <{Domain}View />
}
```

`page.tsx` 规则：
- 不加 `"use client"`（保持 Server Component）
- 不写任何 hooks、状态、业务逻辑
- 只做一件事：渲染对应的 View 组件
- 如果需要传路由参数（`params`、`searchParams`），透传给 View

`app/**/layout.tsx` 同样保持薄层：只处理路由边界、Server Component 接线和 metadata，并渲染 `layouts/{shell}/` 导出的壳组件。仅单一路由使用的局部排版不进入 `layouts/`，放在该路由的 `_components/`。

### View 组件模板

```tsx
/**
 * {功能名}视图
 * @author AaronZZH & Kiro
 */

"use client"

// 可选：需要嵌入式 Chatter 时声明
// import { useChatterLayoutPreference } from "@/features/chatter"

export function {Domain}View() {
  // 需要嵌入对话面板时，在这里声明
  // useChatterLayoutPreference("panel")

  return (
    <div className="flex h-full flex-col">
      {/* 页面内容 */}
    </div>
  )
}
```

## Chatter 布局声明

工作区 Chatter 有两种模式，**默认浮动**，无需任何配置：

| 模式 | 触发方式 | 适用场景 |
|------|---------|---------|
| `dialog`（浮动） | 默认，无需声明 | 普通信息页、表单、列表页 |
| `panel`（嵌入） | View 里调用 `useChatterLayoutPreference("panel")` | 需要边操作边对话的工具类页面 |

```tsx
// 需要嵌入时，在 View 组件顶层调用
import { useChatterLayoutPreference } from "@/features/chatter"

export function AigcView() {
  useChatterLayoutPreference("panel")  // 进入时嵌入，离开时自动恢复浮动
  // ...
}
```

当前声明嵌入模式的页面：

- `aigc`（图像生成）— 需要边生成边对话

## 常见错误模式

**不要在 page.tsx 里写业务逻辑：**

```tsx
// ❌ 错误：page.tsx 里写 hooks 和状态
"use client"
export default function Page() {
  const [data, setData] = useState([])
  useEffect(() => { fetch(...) }, [])
  return <div>...</div>
}

// ✅ 正确：page.tsx 只做路由入口
export default function Page() {
  return <DomainView />
}
```

**不要把 Chatter 嵌入声明放在 page.tsx：**

```tsx
// ❌ 错误：page.tsx 是 Server Component，不能调 hooks
export default function Page() {
  useChatterLayoutPreference("panel")  // 报错
  return <DomainView />
}

// ✅ 正确：在 View 组件（"use client"）里声明
export function DomainView() {
  useChatterLayoutPreference("panel")
  return ...
}
```

**不要在多个地方管同一份布局状态：**

```tsx
// ❌ 错误：页面自己管 open 状态，和 chatter-store 双真理源
const [chatOpen, setChatOpen] = useState(false)
<Chatter open={chatOpen} onOpenChange={setChatOpen} />

// ✅ 正确：只声明偏好，open 状态由 chatter-store 统一管理
useChatterLayoutPreference("panel")
```

## 管理后台页面

`(workspace)/admin/**` 下的数据管理与运维页面，统一由 `sys_entity_def`（EntityDef）声明驱动，不再手写页面。

### 统一范围

以下内容一律在 EntityDef 里声明，由 `features/entity-engine` 通用渲染：

- 列表：列、默认排序、搜索与筛选字段、批量动作
- 表单与详情：字段、只读、条件可见性
- 动作：运维类和业务类操作，例如重建、重试、校验、设为主配置

### 动作声明

动作写在 EntityDef 的 `actions` 数组，字段含义以 `lib/types/entity/entity.ts` 的 `EntityAction` 为准：

| 字段 | 说明 |
|------|------|
| `key` / `label` | 动作标识与按钮文案 |
| `type` | `entity` / `single` / `batch` |
| `execution` | `sync` 同步；`async` 返回可查询的任务 ID |
| `endpoint` | POST 端点，行级动作用 `{id}` 占位 |
| `confirmMessage` | 执行前确认提示；有费用、不可逆或耗时的动作必须填写 |
| `visibleWhen` | 基于 `$record` 的条件可见性 |
| `position` | `formHeader` / `listToolbar` / `rowAction` / `contextMenu` |

规则：

- 动作的权限与数据范围以后端为准（`@PreAuthorize`、CRUD catalog、`ADMIN_MAINTENANCE` 访问模式）；EntityDef 只是 UI 元数据，前端隐藏按钮不构成授权
- 不得在页面里为单个动作手写按钮加专用 hook；需要新动作时先加后端端点，再补 EntityDef 的 `actions`
- 内置 EntityDef 种子放在 `apps/service/aaf-api/src/main/resources/db/seed/view/`，命名 `v{序号}__{领域}_entity_def.sql`
- 接入方法见 [如何扩展类型化 CRUD 资源并接入 EntityDef](../../../../guide/development/entity-extension.md)

### 自定义视图

有特定界面的实体（运维看板、图谱、向导等），不再单独做并行的手写路由页，而是在同一个 EntityDef 里声明 `customViews`，通用管理界面保持不变：

| 字段 | 说明 |
|------|------|
| `key` | URL `view` 参数值；不得占用内置视图 key（`list`、`form`、`kanban`、`pivot`、`graph`、`chart`、`calendar`、`canvas`），冲突的声明会被忽略 |
| `label` | 切换按钮文案 |
| `icon` | 可选，lucide 图标名 |
| `component` | 前端注册表里的组件名，不是路径或脚本 |
| `scope` | `list`：列表工具栏的视图切换处出现按钮；`record`：记录详情页头部出现「详情 / 自定义视图」切换 |

规则：

- 组件通过 `registerCustomView(name, Component)` 注册，props 为 `CustomViewProps`（`entity`、`recordId`）；注册放在 `features/{domain}/register-custom-views.ts`，并在 `sections/entity/view/entity-module-route.tsx` 以副作用方式引入，保证注册先于渲染。示例：知识库运维视图 `knowledge-ops`
- `component` 未注册时，ViewEngine 显示「自定义视图组件未注册」占位，不会动态加载任意模块
- 自定义视图只负责展示与交互，写操作仍在 `actions` 声明、权限仍以后端为准
- 数据读取走既有的 admin 或 CRUD API，不为自定义视图新增旁路接口
- 自定义视图是通用管理界面的补充，不替代列表、表单和动作；能用通用界面表达的内容不得放进自定义视图

### 允许的例外

自定义视图不适用的页面（不围绕单个实体的统计看板、跨实体向导），允许在 `sections/{domain}/view/` 自定义 View。例外必须满足：

- 在 View 文件头部注释写明不能挂在某个 EntityDef 下的原因
- 仍遵守本文档的 page.tsx 与 View 分层规则

### 存量页面

已有的手写管理页不强制一次性改完，按「现有页面重构指引」逐步迁移，并登记到 [改进意见](../../../../prd/improvements.md)。新增或大改的管理页不得再手写。

## 现有页面重构指引

存量页面按以下优先级逐步重构，不强制一次性完成：

- **P0**：page.tsx 里有大量 hooks 和业务逻辑的 → 抽 View 组件
- **P1**：page.tsx 标了 `"use client"` 的 → 去掉标记，逻辑移到 View
- **P2**：已有 View 但和 page.tsx 职责混乱的 → 整理分层

重构时遵守 AGENTS.md 批量修改规则：单次 PR 不超过 5 个文件。
