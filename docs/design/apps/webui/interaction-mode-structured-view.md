---
level: Practice
layer: Product
purpose: AAF 前端结构化交互模式设计——配置驱动的低代码应用体系
status: draft
version: 2.2.0
date: 2026-05-13
author: AaronZZH

# 结构化交互模式设计

> **定位**：结构化视图是 AAF 的主交互面（高带宽通道），对话/生成式交互是辅助通道。AI 输出优先"焊"进结构化视图（高亮修改、内联 diff、表单预填充、看板卡片更新），用户在此做决策和审查。对话仅用于意图表达和快速指令。
>
> 设计依据：纯聊天将树状决策压缩为线性对话导致上下文腐烂；AI 使执行近乎免费后，规划与审查成为新瓶颈——结构化视图是降低审查成本的高效载体。

## 一、设计理念

### 配置驱动视图生成

- 问题：AAF 需要大量"列表 + 表单 + 看板"管理页面。传统做法每个模块手写组件，重复度高。
- 方案：**新增业务模块 = 注册实体配置 + 可选自定义覆盖**，不写页面代码。

```text
TS 配置 → 编译时类型安全 → React 渲染 → REST + TanStack Query 缓存

实体注册表（TypeScript 配置）
         ↓ 自动派生
  列表视图 / 表单视图 / 看板视图 / 筛选器 / API 调用
```

### 设计原则

- **配置优先**：80% 场景通过配置解决，20% 复杂场景通过自定义组件覆盖
- **类型安全**：配置本身是 TypeScript，享受类型推断
- **AI 友好**：结构化配置比手写 JSX 更易 AI 生成和理解

## 二、核心架构

### 三层模型

```text
┌──────────────────────────────────┐
│  实体注册表（Entity Registry）      │  ← 定义"有什么"
├──────────────────────────────────┤
│  视图引擎（View Engine）            │  ← 决定"怎么展示"
├──────────────────────────────────┤
│  组件注册表（Component Registry）   │  ← 提供"用什么渲染"
└──────────────────────────────────┘
```

### 数据流

```text
URL /workspace/document?view=list
  → 动态路由 [module]/page.tsx
  → 查找 EntityDef('document')
  → ViewEngine 根据 ?view= 选择渲染器
  → 渲染器从组件注册表获取字段组件
  → TanStack Query 根据 apiPath 获取数据
  → 渲染
```

## 三、实体注册表

### EntityDef 核心结构

```typescript
interface EntityDef {
  slug: string              // URL 路径 + 唯一标识
  label: string             // 显示名称
  apiPath: string           // 后端 API 路径
  icon?: string             // 侧边栏图标
  group?: string            // 侧边栏分组
  fields: FieldDef[]        // 字段定义
  listView: ListViewConfig  // 列表配置
  formView?: FormViewConfig // 表单配置
  kanbanView?: KanbanViewConfig
  access?: EntityAccess                   // 权限配置，详见 [前端权限配置](./permission-ui.md)
  overrides?: { listView?, formView?, kanbanView? }  // 自定义
}

// 实体级权限（后端根据当前用户计算后返回）
interface EntityAccess {
  read: boolean               // 是否可查看该实体
  create: boolean             // 是否可新建记录
  update: boolean             // 是否可编辑记录
  delete: boolean             // 是否可删除记录
  fieldAccess?: Record<string, FieldAccess>  // 字段级权限
}

interface FieldAccess {
  visible: boolean            // 是否可见（false = 不渲染）
  editable: boolean           // 是否可编辑（false = 只读展示）
}
```

视图引擎根据 `access` 自动控制 UI：无 create 权限 → 隐藏 [+ 新建]；字段 visible=false → 不渲染；editable=false → 只读。无权限的内容对用户完全不存在，而非灰色禁用。权限规则可通过 DSL 声明，详见 [前端权限配置与控制](./permission-ui.md)。
```

### 字段类型

```typescript
type FieldDef =
  | { type: 'text', name, label?, required?, maxLength?, placeholder? }
  | { type: 'textarea' | 'number' | 'email' | 'date' | 'checkbox' }
  | { type: 'select', options: {label, value, color?}[], multiple? }
  | { type: 'relationship', relationTo: string, hasMany? }
  | { type: 'richText' | 'json' | 'code' | 'upload' }
  // 布局字段
  | { type: 'group', label, fields: FieldDef[], collapsible? }
  | { type: 'tabs', tabs: {label, fields}[] }
  | { type: 'row', fields: (FieldDef & {width?})[] }
```

### 注册示例

```typescript
entityRegistry.document = {
  slug: 'document', label: '文档', apiPath: '/api/documents',
  icon: 'file-text', group: '内容管理',
  fields: [
    { name: 'title', type: 'text', required: true },
    { name: 'status', type: 'select', options: [{label:'草稿',value:'draft',color:'gray'}, ...] },
    { name: 'author', type: 'relationship', relationTo: 'user' },
    { name: 'content', type: 'richText' },
  ],
  listView: {
    columns: ['title', 'status', 'author', 'updatedAt'],
    defaultSort: 'updatedAt:desc',
    searchableFields: ['title', 'content'],
    filterableFields: ['status', 'author'],
    inlineEdit: true,  // 列表行内编辑
    batchActions: ['delete', 'archive'],
  },
  kanbanView: { statusField: 'status', cardTitle: 'title' },
  formView: {
    autosave: { enabled: true, debounceMs: 2000 },
    layout: [{ type: 'tabs', tabs: [
      { label: '基本信息', fields: ['title', 'status', 'content'] },
      { label: '关联', fields: ['author', 'tags'] },
    ]}],
  },
}
```

## 四、组件注册表

### 字段类型 → UI 组件映射

```typescript
// 表单字段组件
fieldComponents = { text: TextInput, select: SelectInput, date: DatePicker,
  relationship: RelationshipPicker, richText: RichTextEditor, ... }

// 列表单元格组件
cellComponents = { text: TextCell, date: DateCell, select: BadgeCell,
  relationship: RelationCell, checkbox: CheckCell, ... }
```

### 自定义覆盖（两种粒度）

```typescript
// 粒度 1：整个视图覆盖
overrides: { listView: WorkflowCustomList }

// 粒度 2：单个字段覆盖
{ name: 'color', type: 'text', components: { Field: ColorPicker, Cell: ColorSwatch } }
```

### 组件 Props 契约

```typescript
interface FieldProps<T> { name, value: T, onChange, error?, disabled?, field: FieldDef }
interface CellProps<T> { value: T, record: Record<string,any>, field: FieldDef }
```

## 五、视图引擎

### 路由

```text
app/(workspace)/[module]/page.tsx       → 视图引擎入口（列表/看板）
app/(workspace)/[module]/[id]/page.tsx  → 表单视图
```

### ViewEngine 伪代码

```tsx
function ViewEngine({ entity, view, params }) {
  if (entity.overrides?.[view]) return <Override />
  switch(view) {
    case 'list':   return <ListView entity={entity} params={params} />
    case 'kanban': return <KanbanView entity={entity} params={params} />
    case 'graph':  return <GraphView entity={entity} params={params} />
  }
}
```

### 列表视图伪代码

```tsx
function ListView({ entity, params }) {
  const { data, pagination } = useEntityList(entity, params)
  const columns = entity.listView.columns.map(col => ({
    field: col,
    Cell: fieldDef.components?.Cell ?? cellComponents[fieldDef.type]
  }))
  return <DataTable columns={columns} data={data} />
}
```

### 表单视图伪代码

```tsx
function FormView({ entity, id }) {
  const { data } = useEntityRecord(entity, id)
  const schema = buildZodSchema(entity.fields)  // 自动生成校验
  const form = useForm({ resolver: zodResolver(schema), defaultValues: data })
  if (entity.formView?.layout)
    return renderFormLayout(layout, fields, form)  // 按 tabs/group/row 布局
  else
    return fields.map(f => <FieldComponent field={f} />)  // 线性渲染
}
```

## 六、数据层

### 通用 Hooks

```typescript
useEntityList(entity, params)    // 列表查询，自动拼接 apiPath + 分页/排序/筛选
useEntityRecord(entity, id)      // 单条记录
useEntityMutation(entity, id?)   // 创建/更新
useEntityDelete(entity)          // 删除
```

### Zod Schema 自动生成

```typescript
buildZodSchema(fields) → z.object({
  title: z.string().required(),
  status: z.enum(['draft','published']),
  author: z.string().optional(),
  ...
})
```

### 状态边界（硬规则）

| 状态类型 | 管理方式 | 禁止 |
|---------|---------|------|
| 服务端数据 | TanStack Query | 禁复制到 Zustand |
| URL 状态 | nuqs（视图/分页/筛选/排序） | — |
| 客户端 UI | Zustand（侧边栏/弹窗/主题） | 禁存服务端数据 |

## 七、URL 状态

```text
?view=kanban          视图类型
?page=2&pageSize=20   分页
?sort=updatedAt:desc  排序
?search=设计文档       搜索
?status=active        筛选（扁平 key-value，简单场景优先）
```

所有参数通过 `nuqs` 管理，类型安全，支持 SSR。

## 八、导航与菜单

- **侧边栏**：从实体注册表自动生成（按 `group` 分组），后续接入 RBAC 动态过滤
- **命令面板**：`⌘K` 唤起，搜索实体/记录/命令
- **面包屑**：根据路由 + `entity.label` + 记录标题自动生成

## 九、视图类型

| 视图 | 用途 | 核心交互 |
|------|------|---------|
| **列表** | 表格浏览 + 批量操作 | 排序/筛选/搜索/行内编辑/批量选择 |
| **看板** | 状态流转 | 拖拽卡片切换状态（@dnd-kit） |
| **表单** | 单条编辑 | 布局渲染 + autosave + 快速翻页 |
| **图表** | 聚合统计 | ECharts 可视化 |
| **日历** | 时间维度 | 日期字段驱动 |
| **流程图** | 工作流/Agent 编排 | [统一流程图编辑器](./flow-editor.md)（@xyflow/react） |

### 表单核心设计决策：无编辑模式，即点即改

AAF 表单**没有"查看模式"和"编辑模式"的切换**：

- 打开表单即可直接修改任何可编辑字段
- 有修改时 FormHeader 自动出现 [保存] 按钮 + dirty 状态指示
- 只读字段通过 `readOnly` / `readOnlyWhen` / `access.update` 控制，非整个表单锁定

保存策略：

| 配置 | 行为 |
|------|------|
| `autosave: true` | 停止输入 2 秒自动保存，显示"已保存 ✓" |
| `autosave: false`（默认） | 用户手动点 [保存] 或 ⌘S |
| 离开时有未保存修改 | autosave 开启→自动保存后离开；关闭→弹确认对话框 |

```text
打开表单 → 字段直接可输入 → 修改 → dirty 指示 + [保存] 出现
  → autosave：2s 后自动保存
  → 手动：⌘S 或点击 [保存]
  → 离开：确认/自动保存（见第二十七章）
```

## 十、工作区布局

```text
┌─────────────────────────────────────────────┐
│ AppHeader（⌘K 命令面板 / 用户菜单）           │
├──────────┬──────────────────────────────────┤
│ Sidebar  │  ViewSwitcher + Toolbar          │
│ （菜单）  │  ────────────────────────────── │
│          │  当前视图（列表/看板/表单）        │
└──────────┴──────────────────────────────────┘
```

记录详情弹窗：Dialog + Zustand 管理（不依赖 Next.js 并行路由）。

## 十一、与生成式交互模式的共享层

| 共享层 | 结构化视图 | 生成式交互 |
|--------|-----------|-----------|
| 组件注册表 | 字段类型 → UI 组件 | DSL 节点 → 语义组件 |
| TanStack Query | 实体 CRUD | Agent 调后端 |
| Zod Schema | 表单校验 | DSL 参数校验 |
| shadcn/ui | DataTable/Form/Dialog | 消息/卡片/面板 |

**组件注册表是两种模式的统一基础设施**。

## 十二、功能设计

| 特性 | 实现 | 理由 |
|-----------|---------|------|
| 多视图切换（ActionManager） | `?view=` + ViewEngine | 无状态、可分享、SSR 友好 |
| 列表直接编辑 | `listView.inlineEdit: true` + optimistic update | 减少跳转，提升录入效率 |
| 表单自动保存 | `formView.autosave` + debounce + 状态指示器 | 现代 SaaS 标配，减少数据丢失 |
| 表单快速翻页（← 1/50 →） | FormHeader Prev/Next + prefetch | 审批/批量处理必备 |
| 快捷键 | `⌘K` 命令面板 + 上下文快捷键 | 可发现性优于 Alt+字母 |
| 自定义 Widget + Registry | 组件注册表 + overrides | 同一思想，React 组合替代继承 |
| access 控制按钮显示 | `EntityDef.access` → 自动隐藏按钮/只读字段 | 权限驱动 UI |
| Chatter 活动流 | 表单底部 ActivityLog 组件 | 借鉴概念 |

## 十三、实时协作与冲突处理

### 策略

乐观锁（version 字段）为主 → 冲突时提示用户 → 可选 CRDT 实时协作

### 机制

- 每条记录携带 `version` 字段，提交时后端校验版本号一致才写入
- 版本不一致 → 前端弹出冲突对话框，展示"我的修改 vs 服务端最新"，用户选择覆盖/合并/放弃
- autosave 场景：debounce 期间若检测到版本变化，暂停自动保存并提示

### 实时感知

```typescript
// WebSocket/SSE 推送记录变更事件
useRecordPresence(entity, id) → { editors: User[], lastVersion: number }
// 多人同时编辑同一记录时，显示头像 + "XX 正在编辑"提示
```

### 富文本协作

- richText 字段支持 Yjs CRDT 实时协同编辑
- 通过 `fieldDef.collaboration: true` 启用

## 十四、版本历史与 Diff View

### 核心能力

- 每次保存自动生成版本快照（可配置：每次/手动/定时）
- 版本时间线列表（谁在什么时间改了什么）
- 字段级 Diff 对比（新增/删除/修改高亮）
- 一键回滚到历史版本

### 配置

```typescript
interface EntityDef {
  versions?: {
    enabled: boolean
    maxPerRecord?: number       // 最多保留版本数
    drafts?: boolean            // 草稿/已发布状态分离
    compareFields?: string[]    // 参与对比的字段（默认全部）
  }
}
```

### UI 组件

```text
FormHeader → [版本历史] 按钮 → 侧边抽屉
  ├── 版本时间线（时间 + 操作人 + 摘要）
  ├── 选择两个版本 → Diff View（字段级对比）
  └── [恢复此版本] 按钮
```

### Diff 渲染

```typescript
// 按字段类型选择 diff 策略
diffRenderers = {
  text: InlineDiff,          // 文字级红绿标记
  richText: BlockDiff,       // 段落级对比
  select: ValueChangeBadge,  // "草稿 → 已发布"
  relationship: LinkDiff,    // 关联变更
}
```


## 十五、审批工作流集成

> 流程图编辑器详见 [统一流程图编辑器](./flow-editor.md)（审批节点集）。

### 设计思路

表单状态流转与 Flowable 工作流引擎联动，前端负责展示流程状态和操作按钮。

### 配置

```typescript
interface EntityDef {
  workflow?: {
    enabled: boolean
    processKey: string          // Flowable 流程定义 key
    statusField: string         // 映射到哪个字段
    actions?: WorkflowAction[]  // 前端可触发的流程操作
  }
}

interface WorkflowAction {
  key: string                   // 'approve' | 'reject' | 'submit' | 'revoke'
  label: string
  icon?: string
  confirmMessage?: string       // 操作前确认提示
  commentRequired?: boolean     // 是否必须填写意见
  visibleWhen?: string[]        // 在哪些状态下显示
}
```

### UI 交互

```text
FormHeader:
  [当前状态: 待审批]  [审批通过 ✓] [驳回 ✗] [转交...]

表单底部:
  审批时间线（提交人 → 审批人1 → 审批人2 → ...）
  每个节点：操作人 + 时间 + 意见 + 结果
```

### 数据流

```text
用户点击 [审批通过]
  → 弹出意见输入框（commentRequired 时）
  → 调用 POST /api/{entity}/workflow/complete
  → 后端推进 Flowable 流程
  → WebSocket 推送状态变更
  → 前端 invalidateQueries 刷新
```


## 十六、国际化（i18n）

### 方案

基于 `next-intl`，字段标签/选项/错误信息全部支持多语言。

### 字段标签国际化

```typescript
// 方式 1：直接写 label（单语言项目）
{ name: 'title', type: 'text', label: '标题' }

// 方式 2：用 i18n key（多语言项目）
{ name: 'title', type: 'text', labelKey: 'document.field.title' }
// 运行时：label = t(labelKey) ?? label ?? name
```

### 选项国际化

```typescript
{ type: 'select', options: [
  { value: 'draft', labelKey: 'status.draft' },  // 多语言
  { value: 'published', label: '已发布' },        // 单语言 fallback
]}
```

### 校验错误国际化

```typescript
// Zod Schema 生成时注入 i18n error map
buildZodSchema(fields, { t })  // t 为翻译函数
// → z.string().min(1, { message: t('validation.required') })
```

### 内容国际化（远期）

- 字段级多语言内容存储：`{ title_zh: '...', title_en: '...' }`
- 编辑时切换语言 Tab
- 通过 `fieldDef.translatable: true` 启用


## 十七、列表视图高级能力

### 列配置

```typescript
interface ColumnDef {
  field: string
  width?: string              // '200px' | '30%'
  fixed?: 'left' | 'right'   // 列冻结
  sortable?: boolean          // 默认 true
  resizable?: boolean         // 列宽拖拽
  hidden?: boolean            // 默认隐藏（用户可开启）
}
```

### 用户自定义列

```text
列头右键菜单 / 工具栏齿轮图标 → 列配置面板
  ├── 拖拽排序列顺序
  ├── 勾选显示/隐藏
  └── 保存为个人偏好（localStorage / 后端 user_preference）
```

### 虚拟滚动

- 数据量 > 100 行时自动启用虚拟滚动（@tanstack/react-virtual）
- 保持 DOM 节点数恒定，支持万级数据流畅滚动

### 导出

```typescript
listView: {
  exportFormats: ['csv', 'xlsx', 'pdf'],  // 支持的导出格式
  exportFields?: string[],                 // 导出哪些字段（默认 columns）
}
// 导出逻辑：前端构建参数 → 后端生成文件 → 下载
```

### 行拖拽排序

```typescript
listView: {
  draggable: true,            // 启用行拖拽
  orderField: 'sortOrder',    // 排序字段（integer）
}
// @dnd-kit 实现，拖拽完成批量更新 sortOrder
```


## 十八、关联字段深度交互

### 关联选择器

```text
RelationshipPicker 组件：
  ├── 异步搜索下拉（debounce 300ms）
  ├── 最近选择记忆
  ├── 快速创建（弹出简化表单）
  └── hasMany 时：多选 Tag 模式 + 拖拽排序
```

### 关联预览

```text
鼠标悬停关联字段 → 弹出 HoverCard 预览关联记录摘要
点击 → 可选行为：
  - 跳转到关联记录（默认）
  - 侧边抽屉打开（不离开当前页）
```

### 反向关联

```typescript
// 类似 Payload 的 join 字段
{ type: 'reverseRelation', collection: 'comments', foreignKey: 'documentId',
  displayAs: 'list' | 'count' | 'table' }
// 表单中展示"关联到此记录的其他记录"（只读）
```

### 级联选择

```typescript
// 多级关联（如：省 → 市 → 区）
{ type: 'cascader', levels: [
  { relationTo: 'province', label: '省' },
  { relationTo: 'city', label: '市', dependsOn: 'province' },
  { relationTo: 'district', label: '区', dependsOn: 'city' },
]}
```


## 十九、文件与图片

### Upload 字段

```typescript
{ type: 'upload', name: 'attachments',
  accept: 'image/*,.pdf,.docx',   // 允许的文件类型
  maxSize: 10 * 1024 * 1024,      // 10MB
  maxCount: 5,                     // 最多文件数
  multiple: true,
  preview: true,                   // 图片预览
  dragDrop: true,                  // 拖拽上传
}
```

### 上传流程

```text
拖拽/选择文件
  → 前端校验（类型/大小/数量）
  → 显示进度条
  → POST /api/files/upload（multipart）
  → 返回 fileId + url
  → 存入表单字段值
```

### 图片特殊处理

- 缩略图生成（后端）
- 图片裁剪（前端 cropper 组件）
- 图片画廊模式（lightbox 预览）


## 二十、活动流（Chatter）

### 设计

每条记录底部可选挂载活动流，记录操作历史 + 评论 + 活动调度。

### 配置

```typescript
interface EntityDef {
  chatter?: {
    enabled: boolean
    features: ('comments' | 'activityLog' | 'mentions' | 'scheduling')[]
  }
}
```

### UI 结构

```text
表单底部 → ActivityStream 组件
  ├── 操作日志（自动记录：创建/修改/状态变更/审批）
  ├── 评论区（Markdown 输入 + @ 提及用户）
  ├── 活动调度（安排待办：打电话/发邮件/会议，设截止时间）
  └── 时间线展示（混合排列，按时间倒序）
```

### 数据模型

```text
activity_log 表：entity_type + entity_id + action + actor + timestamp + detail(JSON)
comment 表：entity_type + entity_id + author + content + mentions[] + created_at
scheduled_activity 表：entity_type + entity_id + type + assignee + due_date + done
```


## 二十一、搜索与筛选高级能力

### 筛选构建器

```text
工具栏 [+ 添加筛选] → 弹出筛选构建器
  ├── 选择字段 → 选择操作符（等于/包含/大于/为空/...）→ 输入值
  ├── 多条件 AND/OR 组合
  └── [保存为收藏] → 命名 → 存入用户偏好
```

### 筛选收藏

```typescript
// 用户保存的筛选条件
interface SavedFilter {
  id: string
  name: string
  entity: string
  conditions: FilterCondition[]
  isDefault?: boolean          // 默认加载
  isShared?: boolean           // 团队共享
}
```

### 操作符映射

```typescript
// 根据字段类型自动推断可用操作符
operatorsByType = {
  text: ['equals', 'contains', 'startsWith', 'isEmpty', 'isNotEmpty'],
  number: ['equals', 'gt', 'gte', 'lt', 'lte', 'between'],
  date: ['equals', 'before', 'after', 'between', 'thisWeek', 'thisMonth'],
  select: ['equals', 'in', 'notIn'],
  relationship: ['equals', 'in', 'isEmpty'],
  checkbox: ['isTrue', 'isFalse'],
}
```


## 二十二、打印与导出

### 导出

- 列表视图工具栏 [导出] 按钮 → 选择格式（CSV/XLSX/PDF）+ 选择字段
- 后端生成文件流 → 前端下载

### 打印

```typescript
interface EntityDef {
  printTemplates?: {
    key: string               // 'invoice' | 'report' | 'label'
    label: string
    templatePath: string      // 后端模板路径
  }[]
}
// 表单视图 [打印] 按钮 → 选择模板 → 后端渲染 PDF → 浏览器打印/下载
```

### 批量打印

- 列表视图批量选择 → 批量操作 [打印] → 合并为一个 PDF 或逐个下载


## 二十三、移动端适配

### 策略

响应式设计，同一套代码适配桌面/平板/手机。

### 断点规则

```text
≥1280px  桌面：三栏布局（侧边栏 + 主内容 + 可选属性面板）
768-1279 平板：侧边栏可折叠，主内容全宽
<768px   手机：底部 Tab 导航，列表简化为卡片模式
```

### 视图适配

| 视图 | 桌面 | 手机 |
|------|------|------|
| 列表 | 完整表格 | 卡片列表（显示 2-3 个关键字段） |
| 看板 | 多列并排 | 单列滚动 |
| 表单 | tabs 水平排列 | tabs 改为垂直折叠 |
| 图表 | 完整图表 | 简化图表 + 横向滚动 |

### 触摸优化

- 拖拽操作适配 touch 事件（@dnd-kit 原生支持）
- 行内编辑改为点击进入编辑模式（非 hover）
- 批量选择改为长按触发


## 二十四、插件与扩展机制

### 注册新字段类型

```typescript
// 第三方开发者注册自定义字段类型
registerFieldType('colorPicker', {
  fieldComponent: ColorPickerField,
  cellComponent: ColorSwatchCell,
  zodSchema: (field) => z.string().regex(/^#[0-9a-f]{6}$/i),
  defaultProps: { format: 'hex' },
})
```

### 注册新视图类型

```typescript
// 注册自定义视图（如：地图视图、甘特图）
registerViewType('map', {
  component: MapView,
  icon: 'map-pin',
  requiredFields: ['address'],  // 需要实体包含哪些字段类型
})
```

### 注册列表操作

```typescript
// 注册自定义批量操作
registerBatchAction('sendEmail', {
  label: '发送邮件',
  icon: 'mail',
  handler: async (records) => { ... },
  visibleFor: ['contact', 'lead'],  // 仅特定实体可用
})
```

### 生命周期钩子

```typescript
// 实体级钩子（类似 Payload hooks）
interface EntityDef {
  hooks?: {
    beforeCreate?: (data) => data | Promise<data>
    afterCreate?: (record) => void
    beforeUpdate?: (id, data) => data
    afterUpdate?: (record) => void
    beforeDelete?: (ids) => boolean    // 返回 false 阻止删除
    afterDelete?: (ids) => void
    beforeView?: (record) => record    // 可修改展示数据
  }
}
```

### 插件包结构

```text
@aaf/plugin-xxx/
  ├── index.ts          → registerFieldType / registerViewType / registerBatchAction
  ├── components/       → 自定义组件
  └── package.json      → peerDependencies: { '@aaf/entity-engine': '^1.0' }
```



## 二十五、条件可见性（字段动态显示/隐藏）

### 配置

```typescript
interface BaseField {
  visibleWhen?: FieldCondition | FieldCondition[]
  readOnlyWhen?: FieldCondition
  requiredWhen?: FieldCondition
}

interface FieldCondition {
  field: string
  operator: 'eq' | 'neq' | 'in' | 'notIn' | 'isEmpty' | 'isNotEmpty'
  value?: any
}
```

### 示例

```typescript
{ name: 'publishedAt', type: 'date',
  visibleWhen: { field: 'status', operator: 'eq', value: 'published' } }
{ name: 'rejectReason', type: 'textarea',
  visibleWhen: { field: 'status', operator: 'eq', value: 'rejected' },
  requiredWhen: { field: 'status', operator: 'eq', value: 'rejected' } }
```

### 实现

- FormView 监听 `form.watch()` 变化，实时计算每个字段的 visible/readOnly/required
- 条件不满足时字段 DOM 不渲染（非 display:none），避免提交隐藏字段数据
- Zod Schema 动态重建（requiredWhen 触发时切换 optional → required）


## 二十六、智能按钮（Smart Button）

表单顶部的统计快捷按钮，展示关联数据计数，点击跳转。

### 配置

```typescript
interface EntityDef {
  smartButtons?: SmartButton[]
}

interface SmartButton {
  label: string               // "{count} 条评论"
  icon: string
  countField: string          // 后端返回的计数字段
  linkTo: string              // '/workspace/comment?documentId={id}'
  visibleWhen?: FieldCondition
}
```

### UI

```text
FormHeader 下方：
  [💬 5 条评论] [🔀 3 个版本] [📎 2 个附件] [📋 1 个任务]
```

点击跳转到关联实体列表（自动带筛选条件）。


## 二十七、离开确认（Unsaved Changes Guard）

### 行为规则

| 场景 | autosave 开启 | autosave 关闭 |
|------|-------------|-------------|
| 路由切换 | 自动保存后跳转 | 弹出确认对话框 |
| 浏览器关闭/刷新 | 自动保存 | beforeunload 提示 |
| 点击侧边栏菜单 | 自动保存后跳转 | 弹出确认对话框 |

### 确认对话框

```text
"您有未保存的修改"
  [保存并离开]  [放弃修改]  [取消]
```

### 实现

```typescript
// useUnsavedGuard(form: UseFormReturn, autosave: boolean)
// - 监听 form.formState.isDirty
// - Next.js: router.events beforeRouteChange 拦截
// - 浏览器: window.addEventListener('beforeunload')
```


## 二十八、向导（Wizard）弹窗流程

多步骤弹窗表单，用于复杂操作（批量修改、导入、配置向导）。

### 配置

```typescript
interface EntityDef {
  wizards?: WizardDef[]
}

interface WizardDef {
  key: string
  label: string
  trigger: 'batchAction' | 'formAction' | 'menuAction'
  steps: WizardStep[]
}

interface WizardStep {
  title: string
  description?: string
  fields: FieldDef[]
  validate?: (data) => boolean | string
}
```

### UI

```text
Dialog + Stepper:
  Step 1: [选择操作] → Step 2: [填写参数] → Step 3: [确认执行]
  底部：[上一步] [下一步/完成]
```

### 典型场景

- 批量修改状态（选择目标状态 → 填写原因 → 确认）
- CSV 导入（上传文件 → 字段映射 → 预览 → 执行）
- 工作流配置向导


## 二十九、通知系统

### 全局通知

```typescript
interface NotificationService {
  success(message, options?): void
  error(message, options?): void
  warning(message, options?): void
  info(message, options?): void
}

interface NotifyOptions {
  title?: string
  duration?: number           // 0 = 不自动关闭
  action?: { label: string, onClick: () => void }  // 如"撤销"
}
```

### 触发场景

- 保存成功/失败
- 删除操作（带"撤销"按钮）
- 批量操作结果（"已归档 5 条记录"）
- WebSocket 推送（审批通知、协作提醒）
- 网络异常

### 实现

sonner 库（shadcn/ui 推荐），支持堆叠、自动关闭、操作按钮。


## 三十、加载状态与骨架屏

### 分层加载策略

| 场景 | 表现 |
|------|------|
| 首次加载页面 | 骨架屏（Skeleton） |
| 翻页/筛选 | 顶部细线进度条 + 数据区半透明 |
| 表单提交 | 按钮 loading + 表单禁用 |
| 路由切换 | 顶部进度条（NProgress 风格） |
| 长时间操作 | 全屏遮罩 + 进度百分比 |

### 配置

```typescript
interface ListViewConfig {
  skeleton?: 'table' | 'card' | 'custom'
}
```

### 实现

- React Suspense + loading.tsx（路由级）
- TanStack Query isLoading/isFetching 状态
- 骨架屏组件匹配实际布局（列数/行数与配置一致）


## 三十一、Server Actions 前端触发

### 配置

```typescript
interface EntityDef {
  actions?: EntityAction[]
}

interface EntityAction {
  key: string
  label: string
  icon?: string
  type: 'single' | 'batch'
  endpoint: string            // POST /api/{entity}/actions/{key}
  confirmMessage?: string
  visibleWhen?: FieldCondition
  position: 'formHeader' | 'listToolbar' | 'rowAction' | 'contextMenu'
}
```

### UI 位置

```text
表单顶部：[发送邮件] [生成报告] [启动工作流]
列表工具栏：[批量归档] [批量发送]
行操作菜单：⋯ → [复制] [归档] [导出]
右键菜单：同行操作
```

### 执行流程

```text
点击操作按钮
  → confirmMessage 存在则弹确认
  → POST endpoint（携带 recordId 或 ids[]）
  → 显示 loading
  → 成功：Toast + invalidateQueries
  → 失败：错误 Toast + 保持当前状态
```


## 三十二、二维码/条码扫描

### 字段配置

```typescript
interface BaseField {
  scanner?: {
    enabled: boolean
    type: 'qrcode' | 'barcode' | 'both'
    targetField?: string      // 扫码结果写入哪个字段
  }
}
```

### UI

- 移动端：输入框右侧显示扫码图标 → 点击调用摄像头 → 识别结果填入
- 桌面端：隐藏扫码图标（或显示为"粘贴条码"输入框）

### 实现

html5-qrcode 库，支持 QR Code / EAN-13 / Code-128 等格式。


## 三十三、PWA 支持

### 能力

- Service Worker 缓存静态资源（JS/CSS/图片）
- 移动端"添加到主屏幕"（standalone 模式）
- 推送通知（审批提醒、活动到期、协作消息）
- 离线提示（不支持离线编辑，B 端应用网络为前提）

### 配置

```text
manifest.json: { name, short_name, display: 'standalone', start_url, icons }
next.config: next-pwa 插件
```

### 推送通知

```text
后端事件 → WebSocket/SSE → 前端 Notification API
  - 审批待处理
  - 活动到期提醒
  - @ 提及通知
  - 工作流状态变更
```


## 三十四、列表分组操作

### 配置

```typescript
interface ListViewConfig {
  groupBy?: string[]              // 可分组字段列表
  defaultGroupBy?: string         // 默认分组
  groupActions?: GroupAction[]
}

interface GroupAction {
  key: string
  label: string
  handler: 'selectAll' | 'collapseAll' | 'expandAll' | 'exportGroup' | 'custom'
}
```

### UI

```text
分组头：[▼ 已发布 (15)]  ⋯ → [全选本组] [折叠所有] [导出本组]
```

### 交互

- 点击分组头折叠/展开
- 分组头显示聚合信息（计数、求和等）
- 拖拽记录跨分组 = 修改分组字段值（如拖拽到"已发布"组 = 修改 status）


## 三十四点五、用户自定义字段

管理员可在运行时为实体动态添加字段，无需开发介入。

- **谁**：仅管理员角色
- **在哪**：表单/列表视图 → 右上角 ⚙️ 设置菜单 → [自定义字段]
- **如何**：弹窗中选择字段类型（text/number/date/select/checkbox 等）→ 填写标签和配置 → 确认后系统自动加列 + UI 即时渲染新字段
- **删除**：逻辑隐藏（数据保留，可恢复），不物理删除

详细设计（元数据表、前后端协作流程、字段类型映射）见 [用户自定义字段](../../framework/intelligent/core/custom-fields.md)。


## 三十五、AI 感知能力（AI Context Awareness）

> 默认开启。AI 全面了解当前页面上下文及用户操作，主动提供辅助操作和自动完成。优于截图+模拟点击方案——结构化语义数据比像素信息更精确、更高效。

### 设计理念

```text
传统 AI 辅助：截图 → 视觉理解 → 模拟点击（慢、不精确、有延迟）
AAF AI 感知：结构化页面状态 → 语义理解 → 直接操作数据/UI（快、精确、实时）
```

AI 不是"看屏幕"，而是"读懂页面结构"。

### 感知数据模型

```typescript
interface AIPageContext {
  // 页面结构
  currentEntity: EntityDef              // 当前实体配置
  currentView: 'list' | 'form' | 'kanban'
  visibleFields: FieldDef[]             // 当前可见字段
  visibleRecords?: Record<string, any>[] // 列表中可见的记录

  // 用户状态
  focusedField?: string                 // 当前聚焦的字段
  selectedRecords?: string[]            // 选中的记录 ID
  formValues?: Record<string, any>      // 表单当前值（含未保存）
  formDirtyFields?: string[]            // 已修改的字段
  validationErrors?: Record<string, string>

  // 操作历史
  recentActions: UserAction[]           // 最近 N 步操作
  currentWorkflow?: string             // 当前所处的业务流程

  // 环境
  userRole: string
  permissions: string[]
  locale: string
  timestamp: number
}

interface UserAction {
  type: 'navigate' | 'edit' | 'click' | 'search' | 'filter' | 'select'
  target: string                        // 操作目标（字段名/按钮/记录ID）
  value?: any
  timestamp: number
}
```

### AI 辅助能力

| 能力 | 触发条件 | 行为 |
|------|---------|------|
| **字段自动补全** | 用户聚焦空字段 | 根据上下文推断值，显示建议 |
| **智能默认值** | 新建记录 | 根据历史模式预填字段 |
| **操作建议** | 用户停顿 3 秒 | 浮动提示"下一步建议" |
| **批量操作推荐** | 选中多条记录 | 推荐可能的批量操作 |
| **错误修复建议** | 表单校验失败 | 提供修复建议而非仅报错 |
| **筛选建议** | 列表数据量大 | 根据用户意图推荐筛选条件 |
| **关联推荐** | 编辑关联字段 | 推荐最可能的关联记录 |
| **流程引导** | 检测到业务流程 | 提示下一步操作 |
| **异常检测** | 数据不一致 | 主动提醒潜在问题 |

### 实现机制

```typescript
// AI 感知服务（全局单例，默认开启）
interface AIAwarenessService {
  // 状态收集（自动，无需用户触发）
  collectContext(): AIPageContext
  
  // 主动建议（Agent 侧推送）
  getSuggestions(context: AIPageContext): AISuggestion[]
  
  // 用户确认执行
  applySuggestion(suggestion: AISuggestion): void
  
  // 开关控制
  enabled: boolean                      // 默认 true
  sensitivity: 'low' | 'medium' | 'high'  // 建议频率
}

interface AISuggestion {
  type: 'autocomplete' | 'action' | 'fix' | 'navigate' | 'insight'
  confidence: number                    // 0-1 置信度
  description: string                   // 人类可读描述
  preview?: any                         // 预览效果
  apply: () => void                     // 执行函数
  dismiss: () => void                   // 忽略
}
```

### UI 表现

```text
字段自动补全：输入框下方灰色建议文本，Tab 接受
操作建议：右下角浮动气泡，3 秒后自动消失
错误修复：错误信息旁 [AI 修复] 按钮
流程引导：顶部横幅 "下一步：审批此文档 → [执行]"
```

### 隐私与控制

- 用户可全局关闭 AI 感知（设置 → AI 辅助 → 关闭）
- 敏感字段可标记 `aiExclude: true`，不纳入感知上下文
- 感知数据不离开前端（本地推理）或仅发送到用户授权的 Agent
- 操作历史仅保留最近 50 步，不持久化



## 三十六、统一表达式上下文（FieldContext）

### 问题

字段配置中的条件（visibleWhen / dynamicFilter / access）需要引用运行时值（当前表单值、用户信息、URL 参数），当前缺少统一抽象。

### FieldContext 定义

```typescript
interface FieldContext {
  $record: Record<string, any>      // 当前表单所有字段值
  $parent?: Record<string, any>     // 父记录（嵌套表单/子表时）
  $user: { id: string; role: string; permissions: string[] }
  $params: Record<string, string>   // URL 参数
  $env: { locale: string; isMobile: boolean }
}
```

### 引用语法

所有条件表达式中，`$` 前缀表示引用上下文：

```typescript
// 引用当前记录字段（$record 可省略，向后兼容）
{ field: 'status', operator: 'eq', value: 'draft' }       // 等价于 $record.status
{ field: '$record.status', operator: 'eq', value: 'draft' } // 显式写法

// 引用用户信息
{ field: '$user.role', operator: 'eq', value: 'admin' }

// 引用父记录（子表场景）
{ field: '$parent.status', operator: 'eq', value: 'published' }
```

### 应用场景

**1. 条件可见性引用用户角色**

```typescript
{ name: 'salary', type: 'number',
  visibleWhen: { field: '$user.role', operator: 'in', value: ['admin', 'hr'] } }
```

**2. 动态关联过滤**

```typescript
{ name: 'city', type: 'relationship', relationTo: 'city',
  optionsFrom: {
    type: 'entity',
    entity: 'city',
    dynamicFilter: { province_id: '$record.province' }
  }
}
// 用户选择省份后，城市下拉自动只显示该省的城市
```

**3. 动态默认值**

```typescript
{ name: 'assignee', type: 'relationship', relationTo: 'user',
  defaultValue: '$user.id' }

{ name: 'department', type: 'relationship', relationTo: 'department',
  defaultValue: '$user.department' }
```

**4. 预设视图过滤**

```typescript
listView: {
  defaultFilter: { created_by: '$user.id' },  // "我的文档"视图
}
```

### 实现机制

```typescript
// ViewEngine 构建 FieldContext 并注入
function buildFieldContext(form, user, params): FieldContext {
  return {
    $record: form.getValues(),
    $user: { id: user.id, role: user.role, permissions: user.permissions },
    $params: params,
    $env: { locale: getLocale(), isMobile: useIsMobile() },
  }
}

// 表达式求值器
function resolveValue(expr: string, ctx: FieldContext): any {
  // '$record.province' → ctx.$record.province
  // '$user.role' → ctx.$user.role
  const [root, ...path] = expr.split('.')
  return getByPath(ctx[root], path)
}

// dynamicFilter 监听依赖字段变化，自动重新请求
// useFieldOptions 内部 watch 被引用字段，queryKey 包含依赖值
```

### 与 odoo Domain 的对比

| odoo | AAF | 说明 |
|------|-----|------|
| `domain="[('province_id','=',province_id)]"` | `dynamicFilter: { province_id: '$record.province' }` | 等价能力 |
| `attrs="{'invisible':[('state','=','draft')]}"` | `visibleWhen: { field: 'status', operator: 'eq', value: 'draft' }` | 等价 |
| `context="{'default_user_id': uid}"` | `defaultValue: '$user.id'` | 等价 |
| Python 表达式求值 | TypeScript 路径解析 | AAF 更安全（无 eval） |

## 三十七、动态视图注册与无代码编辑

### 设计思路

EntityDef 配置存储在后端数据库，前端启动时加载。ViewEngine 消费配置对象渲染，不关心来源。配合可视化编辑器即实现无代码。

### 架构

```text
┌─────────────────┐     ┌──────────────┐     ┌──────────────┐
│ 无代码编辑器     │ ──→ │ 后端 API      │ ──→ │ sys_entity_def│
│（编辑 EntityDef）│     │ CRUD + 校验   │     │（JSONB 存储） │
└─────────────────┘     └──────────────┘     └──────────────┘
                                                     ↓ 加载
                                              ┌──────────────┐
                                              │ ViewEngine    │
                                              │（直接渲染）    │
                                              └──────────────┘
```

### 后端存储

```sql
CREATE TABLE sys_entity_def (
  id          BIGINT PRIMARY KEY,
  slug        VARCHAR(64) UNIQUE NOT NULL,
  config      JSONB NOT NULL,              -- 完整 EntityDef
  builtin     BOOLEAN DEFAULT FALSE,       -- 系统内置（代码中定义，不可删除）
  enabled     BOOLEAN DEFAULT TRUE,
  version     INT DEFAULT 1,
  created_at  TIMESTAMP,
  updated_at  TIMESTAMP
);
```

### 前端加载

```text
应用启动 → GET /api/entity-defs（全量，缓存 staleTime 长）
         → 合并代码中的内置配置（内置优先，不可被数据库覆盖）
         → entityRegistry 就绪 → 正常渲染
配置变更 → 管理员保存 → invalidate 缓存 → 下次访问自动刷新
```

性能影响：无。一次请求 20-100KB，与 JS bundle 并行加载，运行时 O(1) 内存查找。

### 无代码编辑器（分步实现）

| 阶段 | 编辑方式 | 能力 |
|------|---------|------|
| v0.1 | Monaco Editor 编辑 JSON（带 schema 校验 + 补全） | 开发者/高级用户可用 |
| v0.2 | 表单化编辑（字段列表拖拽 + 属性面板 + 实时预览） | 业务人员可用 |
| v1.0 | 拖拽式 Studio（所见即所得 + AI 辅助生成配置） | 任何人可用 |

### 核心优势

配置驱动架构的红利：**运行时和设计时共享同一套数据结构**（EntityDef），无代码是自然产物而非额外开发。

### AI 辅助生成与调整

AI 可直接生成/修改 EntityDef 配置，因为配置本身是结构化 JSON（对 AI 友好）：

| 场景 | 用户输入 | AI 产出 |
|------|---------|---------|
| 从零创建 | "我需要一个客户管理模块，包含姓名、电话、公司、跟进状态" | 完整 EntityDef JSON |
| 添加字段 | "给文档加一个优先级字段，高中低三个选项" | 追加 FieldDef 到 fields 数组 |
| 调整视图 | "列表默认按创建时间倒序，隐藏描述列" | 修改 listView 配置 |
| 添加逻辑 | "状态为已发布时，标题字段只读" | 添加 readOnlyWhen 条件 |
| 生成关联 | "客户和订单是一对多关系" | 两个实体互加 relationship 字段 |

交互流程：

```text
用户对话："帮我创建一个项目管理模块"
  → Agent 生成 EntityDef JSON
  → 前端实时预览（ViewEngine 直接渲染）
  → 用户："把状态改成看板视图的分列依据"
  → Agent 修改配置，添加 kanbanView
  → 用户确认 → 保存到 sys_entity_def → 生效
```

后端自动处理（用户无感）：

```text
保存 EntityDef 时，后端实体运行时自动执行：
  1. 检测数据库中是否存在对应表 → 不存在则 CREATE TABLE
  2. 对比字段定义与现有列 → 新增字段执行 ALTER TABLE ADD COLUMN
  3. 注册 REST API 端点（自动 CRUD）
  4. 返回成功 → 前端即可正常读写数据
```

无需预先建表、无需写迁移脚本、无需重启服务。用户/AI 定义 EntityDef → 系统自动生成表结构和 API。详见 [用户自定义字段](../../framework/intelligent/core/custom-fields.md)。



## 三十八、在线客服与聊天模块（Livechat & Chatbot）

> 📄 **独立文档**：[chat-livechat-module.md](./chat-livechat-module.md)

基于 assistant-ui 统一架构实现，客服/机器人/AI 助理共享同一套 UI 组件（Thread/ThreadList/Composer），区别仅在 runtime 层。融合第三十五章 AI 感知能力，实现"AI 全程在线"的智能客服体验。

核心要点：
- **统一 UI**：不引入独立聊天框架，复用 assistant-ui 组件 + ExternalStoreRuntime 对接 WebSocket 后端
- **三种 runtime**：AgUiRuntime（AI 助理）/ LivechatRuntime（客服/机器人）/ IMRuntime（内部聊天）
- **机器人脚本**：ChatbotStep 配置驱动 + @xyflow/react 可视化编辑
- **AI 感知融合**：扩展 AIPageContext → LivechatAIContext，为客服人员提供智能回复建议、情绪预警、知识库检索
- **与实体引擎集成**：客服会话作为 EntityDef 注册，享受列表/看板等配置驱动能力


## 三十九、数据导入（Import）

### 设计思路

导入是导出的逆过程，但复杂度远高于导出：需要字段映射、数据校验、冲突处理、错误回滚。通过向导（第二十八章 Wizard）流程实现。

### 导入流程

```text
[上传文件] → [字段映射] → [数据预览+校验] → [冲突策略] → [执行导入] → [结果报告]
```

### 配置

```typescript
interface EntityDef {
  import?: {
    enabled: boolean
    formats: ('csv' | 'xlsx' | 'json')[]
    maxRows?: number                    // 单次最大行数
    uniqueFields?: string[]             // 去重判断字段
    templateDownload?: boolean          // 提供导入模板下载
  }
}
```

### 向导步骤

**Step 1：上传文件**
- 拖拽/选择文件（CSV/XLSX/JSON）
- 自动检测编码、分隔符、表头行

**Step 2：字段映射**
```text
┌─────────────────────────────────────────┐
│ 源列（文件）        →  目标字段（实体）   │
├─────────────────────────────────────────┤
│ "客户名称"          →  [name ▾]          │
│ "联系电话"          →  [phone ▾]         │
│ "所在城市"          →  [city ▾]          │
│ "备注"              →  [-- 跳过 -- ▾]    │
└─────────────────────────────────────────┘
  [自动匹配] ← AI 根据列名+样本数据推荐映射
```

- 自动匹配：根据列名相似度 + 数据类型推断
- 手动调整：下拉选择目标字段
- 支持"跳过"某列

**Step 3：数据预览 + 校验**
```text
┌──────────────────────────────────────────────┐
│ 总计 500 行 │ ✓ 有效 480 │ ⚠ 警告 15 │ ✗ 错误 5 │
├──────────────────────────────────────────────┤
│ 行 23: phone 格式无效 "abc123"               │
│ 行 45: name 为空（必填字段）                  │
│ 行 102: city 值"纽约"不在选项列表中           │
└──────────────────────────────────────────────┘
  [仅导入有效行] [修正后重试] [下载错误报告]
```

- 基于 EntityDef.fields 的 Zod Schema 校验每行
- 关联字段自动匹配（如城市名→城市 ID）
- 选项字段模糊匹配

**Step 4：冲突策略**
```typescript
interface ImportConflictStrategy {
  mode: 'create_only'      // 仅新建，重复跳过
    | 'update_only'        // 仅更新已有记录
    | 'upsert'             // 存在则更新，不存在则新建
    | 'skip_duplicates'    // 跳过重复
  matchBy: string[]        // 判断重复的字段（如 ['email']）
}
```

**Step 5：执行 + 结果**
```text
导入完成：
  ✓ 新建 320 条
  ↻ 更新 160 条
  ⊘ 跳过 15 条（重复）
  ✗ 失败 5 条 [下载失败记录]
```

### 后端接口

```text
POST /api/{entity}/import
  Content-Type: multipart/form-data
  Body: file + mappings + conflictStrategy
  Response: { created, updated, skipped, failed, errorFileUrl? }
```


## 四十、审计日志（Audit Log）

### 与活动流的区别

| 维度 | 活动流（第二十章） | 审计日志 |
|------|------------------|---------|
| 目的 | 用户协作（评论/操作记录） | 合规审计（谁改了什么） |
| 粒度 | 操作级（"张三修改了文档"） | 字段级（title: "旧值"→"新值"） |
| 可见性 | 用户可见（表单底部） | 管理员可见（独立页面） |
| 存储 | activity_log 表 | audit_log 表（不可修改/删除） |
| 保留期 | 可清理 | 按合规要求保留（如 7 年） |

### 配置

```typescript
interface EntityDef {
  audit?: {
    enabled: boolean
    fields?: string[]              // 审计哪些字段（默认全部）
    excludeFields?: string[]       // 排除字段（如 updatedAt）
    retentionDays?: number         // 保留天数
  }
}
```

### 数据模型

```typescript
interface AuditLogEntry {
  id: string
  entityType: string               // 'document'
  entityId: string                 // 记录 ID
  action: 'create' | 'update' | 'delete'
  userId: string
  timestamp: string
  changes: FieldChange[]           // 字段级变更
  ip?: string
  userAgent?: string
}

interface FieldChange {
  field: string
  oldValue: any
  newValue: any
}
```

### UI

```text
/workspace/admin/audit-log → 审计日志列表视图
  筛选：实体类型 / 操作人 / 时间范围 / 操作类型
  列：时间 | 操作人 | 实体 | 记录 | 操作 | 变更摘要

点击展开 → 字段级变更详情：
  title: "季度报告" → "Q2 季度报告"
  status: "draft" → "published"
```

### 实现

- 后端：JPA EntityListener / Hibernate Envers 自动捕获变更
- 前端：审计日志作为 EntityDef 注册（只读），享受列表/筛选能力
- 不可篡改：audit_log 表禁止 UPDATE/DELETE（数据库级约束）


## 四十一、仪表盘（Dashboard）

### 设计思路

仪表盘是跨实体的全局概览，由可配置的 Widget 组成，用户可自定义布局。区别于图表视图（单实体聚合）。

### 配置

```typescript
interface DashboardDef {
  id: string
  name: string
  layout: DashboardWidget[]
  refreshInterval?: number         // 自动刷新间隔（秒）
  access?: { roles?: string[] }    // 可见角色
}

interface DashboardWidget {
  id: string
  type: 'chart' | 'counter' | 'list' | 'progress' | 'shortcut' | 'custom'
  title: string
  position: { x: number; y: number; w: number; h: number }  // 网格坐标
  config: WidgetConfig
}

type WidgetConfig =
  | { type: 'counter'; entity: string; filter?: object; aggregation: 'count' | 'sum'; field?: string; icon?: string; color?: string }
  | { type: 'chart'; entity: string; chartType: 'line' | 'bar' | 'pie' | 'area'; xField: string; yField: string; filter?: object }
  | { type: 'list'; entity: string; columns: string[]; filter?: object; limit?: number; linkTo?: string }
  | { type: 'progress'; label: string; current: number | string; target: number | string }
  | { type: 'shortcut'; items: { label: string; icon: string; href: string }[] }
  | { type: 'custom'; component: string }  // 自定义组件名
```

### UI

```text
┌─────────────────────────────────────────────────────────┐
│ 📊 工作台                    [编辑布局] [+ 添加 Widget]  │
├──────────────┬──────────────┬───────────────────────────┤
│ 📈 本月销售   │ 📋 待办事项   │ 🔔 最新通知              │
│ ¥128万       │ • 审批报销 x3 │ • 张三提交了...           │
│ 环比 +12%    │ • 跟进客户 x5 │ • 系统升级通知            │
│ [折线图]     │ • 回复咨询 x2 │                          │
├──────────────┼──────────────┤                          │
│ 🥧 客户分布   │ ⚡ 快捷入口   │                          │
│ [饼图]       │ [新建客户]    │                          │
│              │ [创建订单]    │                          │
│              │ [查看报表]    │                          │
└──────────────┴──────────────┴───────────────────────────┘
```

### 布局编辑

- 拖拽调整 Widget 位置和大小（react-grid-layout）
- 添加/删除 Widget
- 保存为个人布局或团队共享布局
- 预设模板（销售仪表盘/运营仪表盘/管理仪表盘）

### 数据获取

```typescript
// 每个 Widget 独立查询，互不阻塞
function useWidgetData(widget: DashboardWidget) {
  return useQuery({
    queryKey: ['dashboard', widget.id, widget.config],
    queryFn: () => fetchWidgetData(widget.config),
    refetchInterval: dashboard.refreshInterval * 1000,
  });
}
```

### 路由

```text
app/(workspace)/dashboard/page.tsx       → 默认仪表盘
app/(workspace)/dashboard/[id]/page.tsx  → 指定仪表盘
```


## 四十二、自动化规则（Automation Rules）

### 设计思路

前端配置"当 X 发生时自动执行 Y"，后端由 Flowable 事件监听器执行。UI 复用 [统一流程图编辑器](./flow-editor.md) 的简化版（线性流程，非 DAG）。

### 配置

```typescript
interface AutomationRule {
  id: string
  name: string
  entity: string                   // 触发实体
  enabled: boolean
  trigger: AutomationTrigger
  conditions?: FieldCondition[]    // 复用第二十五章条件表达式
  actions: AutomationAction[]
}

interface AutomationTrigger {
  type: 'on_create'               // 记录创建时
    | 'on_update'                 // 记录更新时
    | 'field_change'              // 特定字段变更时
    | 'schedule'                  // 定时（cron）
    | 'delay'                     // 延迟（创建后 N 天）
  field?: string                  // field_change 时指定字段
  cron?: string                   // schedule 时的 cron 表达式
  delayDays?: number              // delay 时的天数
}

interface AutomationAction {
  type: 'update_field'            // 修改字段值
    | 'send_notification'         // 发送通知
    | 'send_email'                // 发送邮件
    | 'create_record'             // 创建关联记录
    | 'start_workflow'            // 启动审批流
    | 'call_webhook'              // 调用外部接口
    | 'assign_user'               // 分配负责人
  config: Record<string, any>     // 操作参数
}
```

### UI（简化流程编辑器）

```text
┌─────────────────────────────────────────────────────┐
│ 自动化规则：逾期未处理自动提醒                        │
├─────────────────────────────────────────────────────┤
│ 触发：[创建后 3 天 ▾]                                │
│ 条件：[status] [等于] [待处理]                       │
│ 执行：                                              │
│   1. [发送通知 ▾] → 负责人："您有一条待处理记录已逾期" │
│   2. [修改字段 ▾] → priority = "high"               │
│   [+ 添加操作]                                      │
├─────────────────────────────────────────────────────┤
│ [启用 ✓]                          [保存] [测试运行]  │
└─────────────────────────────────────────────────────┘
```

### 典型场景

| 规则 | 触发 | 条件 | 操作 |
|------|------|------|------|
| 逾期提醒 | 创建后 3 天 | status=待处理 | 通知负责人 |
| 自动分配 | 创建时 | 无 | 按轮询分配负责人 |
| 状态联动 | 字段变更(status→完成) | 无 | 更新关联记录状态 |
| 定时报表 | 每周一 9:00 | 无 | 生成周报 + 发送邮件 |
| Webhook 同步 | 更新时 | 无 | 调用外部系统 API |

### 后端实现

- 规则存储：`sys_automation_rule` 表（JSONB）
- 执行引擎：Flowable Event Listener + Spring Scheduler
- 延迟任务：Flowable Timer Event
- 日志：每次执行记录结果（成功/失败/跳过）


## 四十三、多租户与组织切换

### 设计思路

用户可属于多个组织（租户），顶栏切换当前工作组织。数据隔离在后端实现，前端负责组织选择和上下文切换。

### 数据模型

```typescript
interface Organization {
  id: string
  name: string
  slug: string                     // URL 路径标识
  logo?: string
  plan?: 'free' | 'pro' | 'enterprise'
  members: OrgMember[]
}

interface OrgMember {
  userId: string
  orgId: string
  role: 'owner' | 'admin' | 'member' | 'guest'
  joinedAt: string
}

// 用户会话中携带当前组织
interface UserSession {
  userId: string
  currentOrgId: string             // 当前选中的组织
  orgs: { id: string; name: string; role: string }[]
}
```

### UI：组织切换器

```text
AppHeader 左侧：
┌──────────────────┐
│ 🏢 腾讯科技 ▾     │  ← 当前组织
└──────────────────┘
      ↓ 点击展开
┌──────────────────┐
│ ✓ 腾讯科技        │
│   个人工作空间    │
│   AAF 开源社区    │
│ ──────────────── │
│ + 创建组织        │
│ ⚙ 组织设置       │
└──────────────────┘
```

### 切换行为

```text
用户选择另一个组织
  → 更新 session.currentOrgId
  → 设置请求头 X-Org-Id（后续所有 API 携带）
  → invalidateQueries（清空当前数据缓存）
  → 重新加载侧边栏菜单（不同组织可能有不同模块/权限）
  → URL 不变（组织信息在 header 中，非 URL 路径）
```

### 数据隔离（后端）

```text
方案：共享数据库 + org_id 字段（行级隔离）
  - 所有业务表增加 org_id 列
  - 全局 Filter 自动注入 WHERE org_id = :currentOrgId
  - 跨组织数据不可见（除超级管理员）
```

### 组织管理页面

```text
/workspace/admin/organization → 组织设置（EntityDef 注册）
  ├── 基本信息（名称/Logo/域名）
  ├── 成员管理（邀请/移除/角色变更）
  ├── 套餐与计费（plan）
  └── 数据导出/删除（合规）
```

### 个人工作空间

每个用户自动拥有一个"个人工作空间"（特殊组织），用于个人笔记/测试等，不与他人共享。


## 四十四、软删除与回收站

### 设计思路

误删是 B 端最常见的数据事故。所有业务实体默认启用软删除，删除操作仅标记 `deleted_at` 时间戳，数据在保留期内可恢复。回收站提供统一的恢复/彻底删除入口。

### 配置

```typescript
interface EntityDef {
  trash?: {
    enabled: boolean              // 默认 true
    retentionDays?: number        // 保留天数（默认 30，0=永不自动清理）
    hardDeleteRoles?: string[]    // 允许彻底删除的角色（默认仅 admin）
  }
}
```

### 行为规则

| 操作 | 行为 |
|------|------|
| 用户点击 [删除] | 标记 `deleted_at`，记录从列表消失，Toast 显示 [撤销] |
| 撤销（5 秒内） | 清除 `deleted_at`，记录恢复原位 |
| 回收站 → 恢复 | 清除 `deleted_at`，恢复到原实体列表 |
| 回收站 → 彻底删除 | 物理删除（需二次确认 + 权限校验） |
| 超过保留期 | 后端定时任务自动物理删除 |

### UI

```text
侧边栏底部：[🗑️ 回收站]
  → /workspace/trash → 回收站列表视图
  筛选：实体类型 / 删除人 / 删除时间
  列：记录标题 | 实体类型 | 删除人 | 删除时间 | 剩余天数
  操作：[恢复] [彻底删除]
  批量：全选 → [批量恢复] [清空回收站]
```

### 数据层

- 后端：所有查询默认追加 `WHERE deleted_at IS NULL`
- 回收站 API：`GET /api/trash?entity=document&page=1`
- 恢复：`POST /api/trash/restore` body: `{ ids: [...] }`
- 彻底删除：`DELETE /api/trash/purge` body: `{ ids: [...] }`

### 关联数据处理

- 删除主记录时，关联子记录（如评论、附件）同步软删除
- 恢复时同步恢复关联数据
- 若关联记录的父记录已被彻底删除，则关联记录变为孤儿数据，定时清理


## 四十五、计划任务管理

### 设计思路

第四十二章自动化规则中的 `schedule` 触发器和后端定时任务需要统一的管理界面，让管理员可视化查看任务执行状态、手动触发、暂停/恢复。

### 管理视图

```text
/workspace/admin/scheduled-tasks → 计划任务列表
  列：任务名称 | 类型 | Cron/间隔 | 上次执行 | 下次执行 | 状态 | 耗时
  状态：运行中 / 等待中 / 已暂停 / 失败
  操作：[暂停] [恢复] [立即执行] [查看日志]
```

### 任务类型

| 类型 | 来源 | 示例 |
|------|------|------|
| 系统任务 | 框架内置，不可删除 | 回收站清理、会话过期清理、审计日志归档 |
| 自动化规则 | 第四十二章 schedule 触发器 | 定时报表、定时提醒 |
| 用户自定义 | 管理员手动创建 | 数据同步、批量更新 |

### 执行日志

```text
点击 [查看日志] → 侧边抽屉
  ├── 执行时间线（最近 100 次）
  ├── 每次：开始时间 / 耗时 / 状态 / 处理条数
  ├── 失败详情：错误信息 + 堆栈摘要
  └── 统计：成功率 / 平均耗时 / 趋势图
```

### 告警机制

- 任务连续失败 N 次 → 自动暂停 + 通知管理员
- 执行耗时超过阈值 → 告警
- 任务积压（下次执行时间已过但未开始）→ 告警


## 四十六、消息中心（站内信）

### 设计思路

Toast 通知是即时的、易逝的。用户需要一个持久化的消息收件箱，查看历史通知、标记已读、按类型筛选。消息中心是所有通知的汇聚点。

### 消息分类

| 类别 | 来源 | 示例 |
|------|------|------|
| 审批通知 | 工作流引擎 | "张三提交了报销单，等待您审批" |
| 系统通知 | 平台运营 | "系统将于今晚 22:00 维护" |
| 业务提醒 | 自动化规则 | "客户 A 的合同将于 3 天后到期" |
| 协作通知 | 活动流 | "李四在文档中 @了您" |
| 变更通知 | 字段订阅 | "您关注的订单状态已变更为已发货" |

### UI

```text
AppHeader 右侧：🔔 (红点 + 未读数)
  → 点击展开通知面板（下拉）
  ├── 快速预览最近 5 条
  ├── [全部标为已读]
  └── [查看全部] → /workspace/notifications

/workspace/notifications → 消息中心完整视图
  Tab：全部 | 未读 | 审批 | 系统 | 业务 | 协作
  列：图标 | 标题 | 摘要 | 时间 | 状态（已读/未读）
  操作：标为已读 | 删除 | 跳转到关联记录
  批量：全选 → [标为已读] [删除]
```

### 行为规则

- 未读消息：左侧蓝色竖线标记 + 标题加粗
- 点击消息：自动标为已读 + 跳转到关联记录（如审批单）
- 消息保留期：默认 90 天，可配置
- 免打扰：用户可按类别关闭通知（设置 → 通知偏好）

### 推送通道

```text
消息产生 → 写入 notification 表
         → 实时推送：WebSocket → 前端红点更新
         → 可选外部推送：邮件 / 企业微信 / 钉钉（用户配置）
```

### 通知偏好

```text
/workspace/settings/notifications → 通知设置
  ├── 审批通知：[站内 ✓] [邮件 ✓] [企微 ☐]
  ├── 系统通知：[站内 ✓] [邮件 ☐] [企微 ☐]
  ├── 业务提醒：[站内 ✓] [邮件 ✓] [企微 ✓]
  └── 免打扰时段：22:00 - 08:00
```


## 四十七、行级数据权限

### 设计思路

第三章 `EntityAccess` 解决了实体级权限（能否访问该模块），但 B 端常见需求是"只能看自己部门的数据""只能编辑自己创建的记录"。行级数据权限通过声明式规则实现，前端负责配置 UI，后端负责 SQL 注入。

### 规则模型

```typescript
interface EntityDef {
  dataAccess?: DataAccessRule[]
}

interface DataAccessRule {
  id: string
  name: string                        // "只看本部门数据"
  roles: string[]                     // 适用角色
  condition: DataAccessCondition      // 过滤条件
  effect: 'filter' | 'deny'          // filter=自动过滤 deny=拒绝访问
}

interface DataAccessCondition {
  field: string                       // 记录中的字段
  operator: 'eq' | 'in' | 'contains'
  value: string                       // 支持 $user.xxx 表达式
}
```

### 典型规则

| 规则名 | 条件 | 效果 |
|--------|------|------|
| 只看自己的数据 | `created_by eq $user.id` | 非 admin 角色只能看自己创建的记录 |
| 只看本部门 | `department_id in $user.departments` | 部门经理看本部门所有数据 |
| 只看本组织 | `org_id eq $user.currentOrgId` | 多租户隔离（系统内置，不可关闭） |
| 区域限制 | `region in $user.regions` | 区域销售只看负责区域 |

### 管理 UI

```text
/workspace/admin/data-access → 数据权限规则管理
  按实体分组展示规则列表
  每条规则：名称 | 适用角色 | 条件表达式 | 启用/禁用
  [+ 新建规则] → 表单：选择实体 → 选择字段 → 选择操作符 → 选择值来源
```

### 前端感知

- 前端不做行级过滤（后端负责），但需感知规则存在
- 列表查询自动携带用户上下文，后端注入 WHERE 条件
- 用户尝试访问无权限记录 → 404（而非 403，避免信息泄露）
- 管理员视角可切换"以 XX 角色查看"预览数据权限效果


## 四十八、嵌套导入（主从关联导入）

### 设计思路

第三十九章导入是单实体扁平结构。实际业务中常需"导入订单同时导入订单明细""导入客户同时导入联系人"。嵌套导入支持主从关系的批量数据录入。

### 支持的格式

| 格式 | 主从表达方式 |
|------|-------------|
| XLSX 多 Sheet | Sheet1=主表，Sheet2=明细表，通过关联列匹配 |
| CSV + 关联列 | 明细行通过"订单编号"列关联到主记录 |
| JSON 嵌套 | `{ order: {...}, items: [...] }` 天然嵌套 |

### 导入流程（扩展第三十九章向导）

```text
Step 1: 上传文件
Step 2: 识别结构 → 检测到多 Sheet / 嵌套结构
Step 3: 关系映射
  ┌─────────────────────────────────────────────┐
  │ 主实体：[订单 ▾]     Sheet: [订单表 ▾]       │
  │ 子实体：[订单明细 ▾]  Sheet: [明细表 ▾]       │
  │ 关联方式：主表列 [订单编号] ↔ 子表列 [订单号] │
  └─────────────────────────────────────────────┘
Step 4: 分别映射主表字段和子表字段
Step 5: 预览 + 校验（主从关系完整性检查）
Step 6: 执行（事务性：主记录+子记录原子写入）
Step 7: 结果报告
```

### 校验增强

- 孤儿检测：子记录找不到对应主记录 → 报错
- 重复主记录：同一主键出现多次 → 合并或报错（用户选择）
- 级联必填：主记录必填字段 + 子记录必填字段同时校验

### 配置

```typescript
interface EntityDef {
  import?: {
    // ...原有配置
    nested?: {
      childEntity: string           // 子实体 slug
      foreignKey: string            // 子表中的外键字段
      matchBy: string               // 主表中用于匹配的字段
    }[]
  }
}
```


## 四十九、全局搜索（跨实体）

### 设计思路

第二十一章搜索局限于单实体内。用户在 `⌘K` 命令面板中输入关键词时，应能跨所有实体搜索，聚合展示结果并快速跳转。全局搜索是"信息可发现性"的核心基础设施。

### 搜索范围

| 范围 | 数据源 | 示例 |
|------|--------|------|
| 实体记录 | 所有已注册实体的 searchableFields | 文档标题、客户名称、订单编号 |
| 命令 | 系统命令注册表 | "新建文档""导出报表" |
| 导航 | 侧边栏菜单项 | "客户管理""审计日志" |
| 最近访问 | 用户访问历史 | 最近打开的 10 条记录 |

### 搜索架构

```text
用户输入关键词
  → 前端防抖 300ms
  → 并行请求：
      GET /api/search?q=关键词&entities=all&limit=5（每实体最多 5 条）
      + 本地匹配命令/导航/最近访问
  → 聚合结果按相关度排序
  → 分组展示
```

### UI（⌘K 命令面板增强）

```text
┌─────────────────────────────────────────┐
│ 🔍 搜索记录、命令、页面...               │
├─────────────────────────────────────────┤
│ 最近访问                                 │
│   📄 Q2 季度报告          文档  3分钟前   │
│   👤 张三                 客户  1小时前   │
├─────────────────────────────────────────┤
│ 文档 (3)                                 │
│   📄 Q2 季度报告                         │
│   📄 产品设计文档                        │
│   📄 季度总结                            │
├─────────────────────────────────────────┤
│ 客户 (2)                                 │
│   👤 季度合作伙伴 A                      │
│   👤 季度评审委员会                      │
├─────────────────────────────────────────┤
│ 命令                                     │
│   ⚡ 新建文档                            │
│   ⚡ 导出当前列表                        │
└─────────────────────────────────────────┘
```

### 后端实现策略

- 短期：各实体 `LIKE` 查询 + 结果合并（简单，适合数据量 < 10 万）
- 中期：PostgreSQL 全文索引（`tsvector`）+ 统一搜索表
- 远期：Elasticsearch / Meilisearch 独立搜索引擎

### 搜索配置

```typescript
interface EntityDef {
  search?: {
    enabled: boolean              // 是否纳入全局搜索（默认 true）
    weight?: number               // 搜索权重（影响排序，默认 1）
    titleField: string            // 搜索结果显示的标题字段
    subtitleField?: string        // 搜索结果副标题
    searchableFields: string[]    // 参与搜索的字段
  }
}
```

### 键盘交互

- `⌘K` / `Ctrl+K`：唤起命令面板
- 上下箭头：选择结果
- Enter：跳转到选中项
- `⌘K` 后输入 `>`：仅搜索命令（类似 VS Code）
- Esc：关闭面板


## 五十、字段计算与公式

### 设计思路

业务中大量字段值由其他字段派生（小计=单价×数量、年龄=当前日期-出生日期）。公式字段在前端实时计算展示，后端持久化计算结果，避免每次查询重算。

### 字段类型

```typescript
{ type: 'formula', name: 'subtotal',
  expression: '$record.price * $record.quantity',
  resultType: 'number',           // 计算结果类型
  precision?: 2,                  // 小数位数
  displayFormat?: 'currency',     // 展示格式
}
```

### 表达式语法

基于第三十六章 FieldContext，扩展算术和函数支持：

| 类别 | 示例 |
|------|------|
| 算术 | `$record.price * $record.quantity` |
| 条件 | `IF($record.type == 'vip', $record.amount * 0.9, $record.amount)` |
| 聚合（子表） | `SUM($children.items.amount)` |
| 日期 | `DATEDIFF($record.endDate, $record.startDate, 'days')` |
| 文本 | `CONCAT($record.firstName, ' ', $record.lastName)` |
| 引用 | `$record.customer.level`（跨关联取值） |

### 内置函数

| 函数 | 说明 |
|------|------|
| `SUM / AVG / MIN / MAX / COUNT` | 聚合（用于子表明细） |
| `IF(condition, trueVal, falseVal)` | 条件判断 |
| `CONCAT / LEFT / RIGHT / LEN` | 文本处理 |
| `NOW / TODAY / DATEDIFF / DATEADD` | 日期计算 |
| `ROUND / FLOOR / CEIL / ABS` | 数学运算 |
| `LOOKUP(entity, field, condition)` | 跨实体查找 |

### 计算时机

| 场景 | 行为 |
|------|------|
| 表单编辑 | 依赖字段变化 → 实时重算 → 显示结果（只读） |
| 列表展示 | 使用后端持久化值（不实时算） |
| 记录保存 | 后端重算并持久化（防止前端篡改） |
| 批量更新 | 后端异步重算受影响记录 |

### UI 表现

- 公式字段在表单中显示为只读灰底，右侧显示 `fx` 图标
- 鼠标悬停 `fx` 图标 → Tooltip 显示公式表达式
- 管理员可在字段配置中编辑公式（Monaco Editor + 语法高亮）


## 五十一、跨字段校验规则

### 设计思路

单字段 Zod 校验（第五章）无法表达跨字段约束（"结束日期 > 开始日期"）和跨记录唯一性（"同一客户不能有两条进行中的订单"）。实体级校验规则补充这一能力。

### 配置

```typescript
interface EntityDef {
  validationRules?: ValidationRule[]
}

interface ValidationRule {
  id: string
  name: string                        // 规则名称（用于错误提示）
  type: 'cross_field' | 'unique' | 'custom'
  condition: string                   // 表达式，返回 boolean
  message: string                     // 校验失败提示
  messageKey?: string                 // i18n key
  severity: 'error' | 'warning'      // error 阻止提交，warning 仅提示
  triggerOn?: 'submit' | 'blur' | 'change'  // 触发时机
}
```

### 典型规则

| 规则 | 表达式 | 提示 |
|------|--------|------|
| 日期范围 | `$record.endDate > $record.startDate` | "结束日期必须晚于开始日期" |
| 金额上限 | `$record.discount <= $record.amount` | "折扣不能超过总金额" |
| 条件必填 | `$record.status != 'rejected' \|\| $record.rejectReason` | "驳回时必须填写原因" |
| 跨记录唯一 | `UNIQUE($record.email, {status: 'active'})` | "已存在相同邮箱的活跃记录" |

### 校验层次

```text
Layer 1: 单字段 Zod（类型、必填、格式）→ 实时，blur 触发
Layer 2: 跨字段规则（本章）→ submit 前或配置的 triggerOn
Layer 3: 后端业务规则（唯一性、权限、外部依赖）→ 提交后返回
```

### UI 表现

- 跨字段校验失败 → 错误信息显示在表单顶部（非某个字段下方）
- warning 级别 → 黄色提示条，不阻止提交，用户可选择忽略
- 关联字段高亮：错误涉及的字段边框变红


## 五十二、报表与数据透视

### 设计思路

第四十一章仪表盘是固定 Widget 布局，适合管理者概览。报表透视面向业务人员，支持自助拖拽维度/指标生成动态分析视图（类似飞书多维表格统计视图、Excel 数据透视表）。

### 核心概念

| 概念 | 说明 |
|------|------|
| 维度（Dimension） | 分组依据：状态、部门、月份、客户等 |
| 指标（Measure） | 聚合计算：计数、求和、平均、最大/最小 |
| 筛选（Filter） | 数据范围限定 |
| 排序（Sort） | 结果排序方式 |

### 配置

```typescript
interface EntityDef {
  pivotView?: {
    enabled: boolean
    dimensions: string[]            // 可用作维度的字段
    measures: PivotMeasure[]        // 可用指标
    defaultConfig?: PivotConfig     // 默认透视配置
  }
}

interface PivotMeasure {
  field: string
  aggregations: ('count' | 'sum' | 'avg' | 'min' | 'max')[]
  label?: string
}

interface PivotConfig {
  rows: string[]                    // 行维度
  columns?: string[]                // 列维度（交叉表）
  values: { field: string; aggregation: string }[]
  filters?: FilterCondition[]
}
```

### UI

```text
视图切换器新增 [📊 透视] Tab

┌─────────────────────────────────────────────────────────┐
│ 📊 透视视图                                              │
├──────────┬──────────────────────────────────────────────┤
│ 维度面板  │  透视结果                                    │
│          │                                              │
│ 行：      │  部门    | 1月  | 2月  | 3月  | 合计        │
│ [部门 ×]  │  ─────────────────────────────────          │
│ [+ 添加]  │  销售部  | 120  | 135  | 142  | 397        │
│          │  技术部  |  45  |  52  |  48  | 145        │
│ 列：      │  市场部  |  30  |  28  |  35  |  93        │
│ [月份 ×]  │  ─────────────────────────────────          │
│ [+ 添加]  │  合计    | 195  | 215  | 225  | 635        │
│          │                                              │
│ 值：      │  [表格] [柱状图] [折线图] [饼图]            │
│ [订单数   │                                              │
│  COUNT ×] │                                              │
│ [+ 添加]  │                                              │
│          │                                              │
│ 筛选：    │                                              │
│ [状态=    │                                              │
│  已完成]  │                                              │
└──────────┴──────────────────────────────────────────────┘
```

### 交互

- 拖拽字段到行/列/值区域
- 点击聚合方式切换（COUNT → SUM → AVG）
- 结果区域支持表格/图表切换
- 保存为报表模板（命名 + 共享）
- 导出透视结果（CSV/XLSX/PDF）

### 数据获取

```text
前端构建透视查询参数 → POST /api/{entity}/pivot
后端执行 GROUP BY 聚合 → 返回结构化结果
前端渲染表格/图表
```


## 五十三、模板记录（快速创建）

### 设计思路

用户频繁创建相似记录时（如每周例会纪要、同类型合同），模板记录提供预填字段值的快速创建入口，减少重复录入。

### 配置

```typescript
interface EntityDef {
  templates?: {
    enabled: boolean
    allowUserCreate?: boolean       // 普通用户可否创建模板（默认 true）
  }
}
```

### 模板数据模型

```text
sys_record_template 表：
  id | entity_slug | name | description | field_values(JSONB)
     | created_by | is_shared | usage_count | created_at
```

### 创建模板的方式

| 方式 | 操作 |
|------|------|
| 从现有记录 | 记录操作菜单 → [另存为模板] → 输入模板名 |
| 从空白创建 | 模板管理页 → [+ 新建模板] → 填写字段默认值 |
| AI 生成 | 对话中描述 → AI 生成模板配置 |

### UI：使用模板

```text
列表视图 [+ 新建] 按钮展开：
  ├── 空白记录（默认）
  ├── ──────────
  ├── 📋 周会纪要模板
  ├── 📋 标准合同模板
  ├── 📋 Bug 报告模板
  └── [管理模板...]

点击模板 → 新建表单，字段预填模板值 → 用户修改后保存
```

### 模板管理

```text
/workspace/{entity}/templates → 模板列表
  列：模板名 | 创建人 | 使用次数 | 共享范围 | 操作
  操作：[编辑] [复制] [删除] [设为默认]
  共享范围：仅自己 / 团队 / 全组织
```


## 五十四、字段变更订阅

### 设计思路

自动化规则（第四十二章）是管理员配置的全局规则。字段订阅是用户个人级的轻量关注机制——"我关注这条记录的状态变化"，变更时推送通知到消息中心。

### 交互

```text
表单视图 → 记录标题旁 [👁 关注] 按钮
  点击 → 弹出订阅配置：
  ┌─────────────────────────────────┐
  │ 关注此记录的变更                  │
  │ ☑ 所有字段变更                   │
  │ ☐ 仅特定字段：                   │
  │   ☑ 状态                        │
  │   ☑ 负责人                      │
  │   ☐ 标题                        │
  │ 通知方式：[站内 ✓] [邮件 ☐]     │
  └─────────────────────────────────┘
```

### 数据模型

```text
sys_subscription 表：
  id | user_id | entity_type | entity_id | fields(JSONB, null=全部)
     | channels(JSONB) | created_at
```

### 行为

- 被关注记录的订阅字段发生变更 → 生成通知推送到订阅者的消息中心
- 通知内容："{操作人} 将 {记录标题} 的 {字段名} 从 {旧值} 改为 {新值}"
- 用户可在消息中心直接取消订阅
- 列表视图中已关注的记录显示 👁 图标

### 批量关注

- 列表视图批量选择 → 批量操作 [关注] → 统一配置订阅字段
- 支持关注筛选条件（如"关注所有状态为紧急的工单"）→ 动态订阅


## 五十五、@提及与待办联动

### 设计思路

第二十章活动流支持 @提及用户，但缺少闭环——被 @ 后应自动生成待办事项，确保提及不被遗漏。

### 联动流程

```text
用户在评论中输入 @张三 请审阅这份文档
  → 保存评论
  → 系统自动：
    1. 推送通知到张三的消息中心（第四十六章）
    2. 在张三的待办列表中创建一条待办
    3. 待办关联到当前记录 + 评论
```

### 待办数据模型

```text
sys_todo 表：
  id | assignee_id | title | source_type('mention' | 'schedule' | 'manual')
     | source_entity | source_id | source_comment_id
     | status('pending' | 'done' | 'dismissed') | due_date | created_at
```

### 待办视图

```text
/workspace/todos → 我的待办
  Tab：待处理 | 已完成 | 已忽略
  列：标题 | 来源 | 提及人 | 时间 | 操作
  操作：[完成 ✓] [忽略] [跳转到原文]

  也可在仪表盘 Widget 中展示待办计数和列表
```

### @提及交互增强

- 输入 `@` 触发用户搜索下拉（异步搜索，显示头像+姓名+部门）
- 支持 @团队/角色（如 @销售组）→ 组内所有人收到通知+待办
- 评论中的 @用户 渲染为可点击链接（跳转到用户资料）

### 待办与活动调度的关系

- 第二十章活动调度（scheduled_activity）是主动安排的任务
- 本章待办是被动产生的（被 @ 或被系统分配）
- 两者共享同一个"我的待办"视图，按来源区分


## 五十六、子表明细行（Inline Table）

### 设计思路

当前 `relationship` 字段是选择器模式（选择已有记录）。子表明细行是"一对多嵌套编辑"——在父表单内直接增删改子记录行（如订单明细、报销明细、BOM 清单）。

### 字段类型

```typescript
{ type: 'subtable', name: 'items',
  label: '订单明细',
  childEntity: string,              // 子实体 slug（复用其 fields 定义）
  columns: string[],                // 子表显示哪些列
  minRows?: number,                 // 最少行数
  maxRows?: number,                 // 最多行数
  summary?: SubtableSummary[],      // 汇总行
  sortable?: boolean,               // 行拖拽排序
  defaultRow?: Record<string, any>, // 新增行默认值
}

interface SubtableSummary {
  field: string                     // 汇总哪个字段
  aggregation: 'sum' | 'avg' | 'count'
  label?: string                    // "合计"
}
```

### UI

```text
表单中：
┌─────────────────────────────────────────────────────────┐
│ 订单明细                                    [+ 添加行]  │
├─────┬──────────┬──────┬──────┬────────┬────────────────┤
│  #  │ 商品名称  │ 单价 │ 数量 │ 小计   │ 操作          │
├─────┼──────────┼──────┼──────┼────────┼────────────────┤
│  1  │ [输入框]  │ [99] │ [2]  │ 198.00 │ [↕] [🗑]      │
│  2  │ [输入框]  │ [49] │ [3]  │ 147.00 │ [↕] [🗑]      │
│  3  │ [输入框]  │ [__] │ [__] │   0.00 │ [↕] [🗑]      │
├─────┴──────────┴──────┴──────┼────────┼────────────────┤
│                        合计： │ 345.00 │                │
└──────────────────────────────┴────────┴────────────────┘
```

### 交互规则

- [+ 添加行]：在末尾追加空行，焦点移到第一个可编辑单元格
- 行拖拽：左侧 ↕ 手柄拖拽排序
- 行删除：点击 🗑 → 行标记删除（灰色+删除线），保存时才真正删除
- Tab 键：在单元格间横向移动，末尾 Tab 自动添加新行
- 汇总行：实时计算，使用第五十章公式引擎
- 校验：每行独立校验 + 子表整体校验（如 minRows）

### 数据提交

```text
父表单保存时，子表数据作为嵌套数组一并提交：
POST /api/orders/123
Body: { ...orderFields, items: [ {id, product, price, qty}, ... ] }

后端原子处理：更新父记录 + 批量 upsert/delete 子记录
```

### 与 relationship 的区别

| 维度 | relationship | subtable |
|------|-------------|----------|
| 数据归属 | 子记录独立存在 | 子记录依附于父记录 |
| 编辑方式 | 选择器（选已有） | 内联表格（直接编辑） |
| 生命周期 | 独立 CRUD | 随父记录保存/删除 |
| 适用场景 | 多对多关联 | 一对多明细 |


## 五十七、审批委托

### 设计思路

第十五章审批工作流中，审批人可能因出差、请假无法及时处理。审批委托允许用户将审批权限临时转交给指定代理人，确保流程不阻塞。

### 委托类型

| 类型 | 说明 |
|------|------|
| 全权委托 | 指定时间段内，所有审批自动转给代理人 |
| 按流程委托 | 仅特定流程（如报销审批）委托给代理人 |
| 单次委托 | 仅当前这一条审批转交（即"转交"操作） |

### 配置 UI

```text
/workspace/settings/delegation → 审批委托设置
  ┌─────────────────────────────────────────────┐
  │ 审批委托                                     │
  │                                             │
  │ 状态：[未启用 / 已启用]                      │
  │ 代理人：[选择用户 ▾]                         │
  │ 生效时间：[2026-05-15] 至 [2026-05-20]      │
  │ 委托范围：                                   │
  │   ○ 所有审批                                │
  │   ○ 仅以下流程：                            │
  │     ☑ 报销审批                              │
  │     ☑ 请假审批                              │
  │     ☐ 合同审批                              │
  │                                             │
  │ [保存]                                      │
  └─────────────────────────────────────────────┘
```

### 行为规则

- 委托生效期间，新到达的审批自动转给代理人
- 代理人审批时，记录显示"由 XX 代 YY 审批"
- 委托人仍可看到审批记录（只读）
- 委托到期自动失效，无需手动关闭
- 审批时间线中标注委托关系

### 转交（单次）

```text
审批表单 → [转交...] 按钮
  → 选择转交对象 + 填写转交原因
  → 审批任务从当前人移到目标人
  → 通知目标人 + 记录转交日志
```


## 五十八、数据对比视图

### 设计思路

用户需要对比两条记录的差异（合并重复客户、对比两个版本的方案、比较两个产品参数）。数据对比视图提供并排展示 + 差异高亮。

### 触发方式

| 入口 | 操作 |
|------|------|
| 列表视图 | 选中 2 条记录 → 批量操作 [对比] |
| 表单视图 | 操作菜单 → [与其他记录对比] → 选择目标记录 |
| 版本历史 | 选择两个版本对比（复用第十四章 Diff View） |

### UI

```text
┌─────────────────────────────────────────────────────────┐
│ 数据对比：客户 A vs 客户 B                    [合并 ▾]  │
├──────────┬─────────────────┬─────────────────┬─────────┤
│ 字段      │ 客户 A           │ 客户 B           │ 差异   │
├──────────┼─────────────────┼─────────────────┼─────────┤
│ 名称      │ 腾讯科技         │ 腾讯科技有限公司  │ ≠      │
│ 电话      │ 13800138000     │ 13800138000     │ =      │
│ 地址      │ 深圳南山区       │ 深圳市南山区     │ ≈      │
│ 状态      │ 活跃             │ 待跟进          │ ≠      │
│ 创建时间  │ 2026-01-15      │ 2026-03-20      │ ≠      │
└──────────┴─────────────────┴─────────────────┴─────────┘
  筛选：[仅显示差异] [显示全部]
```

### 合并功能

- 对比后可选择"合并为一条"
- 逐字段选择保留哪一侧的值（或手动输入合并值）
- 合并后：保留一条 + 软删除另一条 + 关联数据迁移
- 合并操作记录到审计日志

### 配置

```typescript
interface EntityDef {
  compare?: {
    enabled: boolean
    mergeEnabled?: boolean          // 是否允许合并
    compareFields?: string[]        // 参与对比的字段（默认全部）
    mergeRules?: {
      field: string
      strategy: 'keep_left' | 'keep_right' | 'keep_latest' | 'manual'
    }[]
  }
}
```


## 五十九、数据归档

### 设计思路

业务数据随时间增长，历史数据（如 3 年前的已完成订单）查询频率极低但占用索引和存储。数据归档将冷数据迁移到归档存储，列表默认不展示，用户可切换查看。

### 归档策略

| 策略 | 说明 |
|------|------|
| 按时间 | 超过 N 天/月/年的记录自动归档 |
| 按状态 | 特定终态（已完成、已关闭）的记录归档 |
| 手动 | 用户/管理员手动选择归档 |

### 配置

```typescript
interface EntityDef {
  archive?: {
    enabled: boolean
    autoRules?: {
      condition: FieldCondition      // 满足条件的记录自动归档
      afterDays: number              // 满足条件后 N 天执行归档
    }[]
    viewArchived?: boolean           // 列表是否提供"查看归档"开关
  }
}
```

### UI

```text
列表视图工具栏：
  [筛选 ▾] [排序 ▾] ... [📦 显示归档数据 ☐]

  勾选后 → 列表切换为归档数据视图（灰色背景区分）
  归档记录操作：[恢复到活跃] [彻底删除]

批量操作：选中记录 → [归档]
```

### 存储策略

- 短期：同表 + `archived_at` 字段 + 分区表（按时间分区）
- 中期：归档数据迁移到独立归档表（结构相同，查询走不同数据源）
- 远期：冷数据迁移到对象存储（S3），仅保留索引用于搜索

### 与软删除的区别

| 维度 | 软删除（第四十四章） | 归档 |
|------|---------------------|------|
| 语义 | 用户不想要了 | 数据仍有价值但不活跃 |
| 可见性 | 仅回收站可见 | 列表中可切换查看 |
| 恢复 | 恢复到原列表 | 恢复到活跃状态 |
| 自动触发 | 无（用户主动删除） | 按规则自动归档 |


## 六十、多币种与单位换算

### 设计思路

国际化业务中，金额字段需要支持多币种录入和展示，数量字段需要支持不同计量单位。系统维护汇率/换算表，自动计算等值。

### 字段配置

```typescript
// 币种字段
{ type: 'money', name: 'amount',
  currencies: ['CNY', 'USD', 'EUR', 'JPY'],  // 可选币种
  defaultCurrency: 'CNY',
  showConverted?: boolean,          // 是否显示换算值
  convertTo?: string,               // 换算目标币种
}

// 单位字段
{ type: 'quantity', name: 'weight',
  units: ['kg', 'g', 'lb', 'oz'],   // 可选单位
  defaultUnit: 'kg',
  showConverted?: boolean,
  convertTo?: string,
}
```

### 数据存储

```text
金额字段存储为：{ value: 1000, currency: 'USD' }
列表展示：$1,000.00 USD（≈ ¥7,250.00）
表单输入：[1000] [USD ▾]
```

### 汇率管理

```text
/workspace/admin/exchange-rates → 汇率管理
  - 手动维护固定汇率
  - 或对接外部汇率 API 自动更新（每日/实时）
  - 历史汇率保留（用于历史数据的准确换算）
```

### 聚合计算

- 列表汇总/透视表中，不同币种的金额自动按汇率统一换算后聚合
- 汇总行显示统一币种（组织默认币种）


## 六十一、签名字段

### 设计思路

审批、合同、验收等场景需要电子签名。签名字段提供手写签名板，签名后生成图片存储，具有法律效力标识。

### 字段配置

```typescript
{ type: 'signature', name: 'approverSignature',
  label: '审批人签名',
  required?: boolean,
  width?: number,                   // 签名板宽度（默认 400px）
  height?: number,                  // 签名板高度（默认 200px）
  penColor?: string,                // 笔迹颜色（默认黑色）
  backgroundColor?: string,         // 背景色（默认白色）
  timestamped?: boolean,            // 签名时自动附加时间戳
}
```

### UI

```text
表单中：
┌─────────────────────────────────────────┐
│ 审批人签名                               │
├─────────────────────────────────────────┤
│                                         │
│         [手写签名区域]                   │
│                                         │
├─────────────────────────────────────────┤
│ [清除] [确认签名]     2026-05-13 18:00  │
└─────────────────────────────────────────┘

签名完成后：
┌─────────────────────────────────────────┐
│ 审批人签名                    [重新签名] │
│ [签名图片]                              │
│ 签署时间：2026-05-13 18:00:32           │
└─────────────────────────────────────────┘
```

### 实现要点

- 前端：Canvas 手写板（signature_pad 库），支持触摸和鼠标
- 存储：签名导出为 PNG → 上传到文件服务 → 字段值存 fileId + 签署时间
- 不可篡改：签名后字段锁定，重新签名需要清除旧签名（记录到审计日志）
- 移动端：全屏横屏签名模式，提供更大书写空间


## 六十二、EntityDef 继承与 Mixin

### 设计思路

多个实体共享相同字段集（`createdAt/updatedAt/createdBy`、`org_id`、审计字段等），当前需要每个实体重复定义。通过继承和 Mixin 机制消除重复，保持 DRY。

### Mixin 定义

```typescript
// 预定义 Mixin（框架内置）
const TimestampMixin: FieldDef[] = [
  { name: 'createdAt', type: 'date', readOnly: true, label: '创建时间' },
  { name: 'updatedAt', type: 'date', readOnly: true, label: '更新时间' },
]

const AuditMixin: FieldDef[] = [
  { name: 'createdBy', type: 'relationship', relationTo: 'user', readOnly: true },
  { name: 'updatedBy', type: 'relationship', relationTo: 'user', readOnly: true },
]

const SoftDeleteMixin: FieldDef[] = [
  { name: 'deletedAt', type: 'date', readOnly: true, hidden: true },
]

const OrgMixin: FieldDef[] = [
  { name: 'orgId', type: 'text', readOnly: true, hidden: true },
]
```

### 使用方式

```typescript
interface EntityDef {
  mixins?: string[]                 // 引用预定义 Mixin
  extends?: string                  // 继承另一个 EntityDef 的全部配置
  // ...其他配置
}

// 示例
entityRegistry.contract = {
  slug: 'contract',
  label: '合同',
  mixins: ['timestamp', 'audit', 'softDelete', 'org'],  // 自动注入字段
  fields: [
    // 仅定义业务字段，公共字段由 mixin 注入
    { name: 'title', type: 'text', required: true },
    { name: 'amount', type: 'money' },
  ],
  // ...
}
```

### 继承机制

```typescript
// 基础实体
entityRegistry.baseDocument = {
  abstract: true,                   // 抽象实体，不生成路由和 API
  fields: [ /* 公共字段 */ ],
  listView: { /* 公共列表配置 */ },
}

// 继承实体
entityRegistry.invoice = {
  extends: 'baseDocument',          // 继承所有配置
  slug: 'invoice',
  label: '发票',
  fields: [ /* 追加/覆盖字段 */ ],  // 与父实体 fields 合并
}
```

### 合并规则

| 配置项 | 合并策略 |
|--------|---------|
| fields | Mixin 字段在前，自身字段在后；同名字段自身覆盖 Mixin |
| listView.columns | 自身定义优先，未定义则继承 |
| hooks | 合并执行（Mixin hooks 先执行） |
| access | 自身定义覆盖继承 |

### 自定义 Mixin

管理员可创建业务级 Mixin（如"客户公共字段"），在多个实体间复用。


## 六十三、批量操作异步化

### 设计思路

当前批量操作（删除、归档、导入、导出）假设同步完成。数据量大时（万级以上），同步请求会超时。统一的异步任务模式解决这一问题。

### 阈值规则

| 数据量 | 执行方式 |
|--------|---------|
| ≤ 100 条 | 同步执行，前端等待响应 |
| 101-10000 条 | 异步执行，前端轮询进度 |
| > 10000 条 | 异步执行 + 完成后通知（不轮询） |

### 异步任务流程

```text
用户触发批量操作（如批量删除 5000 条）
  → 前端 POST /api/{entity}/batch/{action}
  → 后端立即返回 { taskId: 'xxx', status: 'pending' }
  → 前端显示进度条组件
  → 前端轮询 GET /api/tasks/{taskId}/progress
    → { status: 'running', progress: 45, total: 5000, processed: 2250 }
  → 完成：{ status: 'completed', result: { success: 4980, failed: 20 } }
  → 前端 Toast "操作完成" + invalidateQueries
```

### 进度 UI

```text
┌─────────────────────────────────────────────┐
│ 批量删除进行中...                            │
│ ████████████░░░░░░░░  2250 / 5000  (45%)   │
│ 预计剩余时间：约 30 秒                       │
│                                   [取消]    │
└─────────────────────────────────────────────┘
```

### 任务管理

- 用户可在"我的任务"中查看所有异步操作的状态
- 支持取消正在执行的任务（后端标记取消，下一批次停止）
- 失败记录可下载错误报告
- 任务完成后推送通知到消息中心（第四十六章）

### 适用场景

| 操作 | 同步上限 | 超过后异步 |
|------|---------|-----------|
| 批量删除 | 100 条 | ✓ |
| 批量更新字段 | 100 条 | ✓ |
| 数据导入 | 500 行 | ✓ |
| 数据导出 | 1000 行 | ✓ |
| 批量打印 | 50 条 | ✓ |


## 六十四、错误边界与降级

### 设计思路

ViewEngine 动态渲染用户配置的字段组件，任何一个组件报错不应导致整个页面崩溃。分层错误边界 + 优雅降级确保系统韧性。

### 错误边界层次

```text
Layer 1: 应用级（app/error.tsx）
  → 整个应用崩溃时的兜底页面（"出错了，请刷新"）

Layer 2: 视图级（ViewEngine 内部）
  → 某个视图渲染失败 → 显示错误卡片 + [重试] 按钮
  → 不影响侧边栏和导航

Layer 3: 字段级（FieldComponent 包裹）
  → 单个字段组件报错 → 该字段显示为"渲染失败"占位符
  → 不影响其他字段和表单提交

Layer 4: Widget 级（仪表盘）
  → 单个 Widget 加载失败 → 显示错误占位 + [重试]
  → 不影响其他 Widget
```

### 字段级降级

```text
正常渲染：[自定义颜色选择器组件]
组件报错后：
┌─────────────────────────────────┐
│ ⚠️ 字段"颜色"渲染异常           │
│ 原始值：#ff6600                  │
│ [以文本框编辑] [重试] [报告问题] │
└─────────────────────────────────┘
```

### 降级策略

| 场景 | 降级行为 |
|------|---------|
| 自定义字段组件报错 | 回退到对应基础类型的默认组件 |
| 自定义视图 override 报错 | 回退到框架默认视图 |
| API 请求失败 | 显示缓存数据（stale）+ 错误提示条 |
| WebSocket 断开 | 降级为轮询 + 顶部黄色提示条 |
| 第三方插件报错 | 隔离插件，不影响核心功能 |

### 错误上报

- 错误自动收集：组件名、错误信息、用户操作路径、EntityDef 配置快照
- 开发环境：控制台详细错误 + 错误覆盖层（显示堆栈）
- 生产环境：静默降级 + 后台上报（Sentry 或自建）
- 管理员可在后台查看错误统计（按组件/实体/频率排序）

### 配置

```typescript
interface EntityDef {
  errorBoundary?: {
    fieldFallback?: 'textInput' | 'readOnly' | 'hidden'  // 字段降级策略
    viewFallback?: 'default' | 'error' | 'empty'         // 视图降级策略
    reportErrors?: boolean           // 是否上报错误（默认 true）
  }
}
```


## 核心优势

| 优势 | 说明 |
|------|------|
| **TypeScript 全链路类型安全** | EntityDef 从定义到渲染全程类型检查，IDE 补全/重构/跳转，编译时发现错误 |
| **AI 原生** | EntityDef 是结构化 JSON，AI 可直接生成/修改整个业务模块；内置 AI 感知，主动辅助用户操作 |
| **无代码开发** | 用户/AI 通过对话或配置界面创建实体 → 系统自动建表 + API + UI，全程零代码 |
| **双模式融合** | 结构化视图管理数据 + 生成式交互创造知识，共享组件注册表和 API 层，对话中可创建/调整结构化视图 |
| **配置驱动** | 新增业务模块 = 注册 EntityDef，不写页面代码；视图引擎自动生成列表/表单/看板/图表/透视 |
| **权限 DSL** | 声明式权限规则（实体级 + 行级），一份定义前后端同时生效，AI 可通过自然语言配置权限 |
| **RSC + 流式渲染** | Next.js Server Components 首屏秒开，PPR 静态壳 + 动态流式注入 |
| **服务端状态分离** | TanStack Query 管服务端缓存，Zustand 仅管 UI 状态，禁止双真理源 |
| **组件注册表扩展** | React 组合 + overrides 覆盖，清晰不冲突，第三方可注册自定义字段类型和视图 |
| **自定义字段** | 用户运行时动态添加字段，ALTER TABLE 瞬间完成，UI 即时渲染，AI 可生成 |
| **DSL 贯穿** | Magic-DSL 统一中间表示——人类可读、AI 可生成、系统可执行，自然语言可达 |
| **多端统一** | 同一套 EntityDef 驱动 Web + UniApp，响应式适配桌面/平板/移动端 |
| **DRY 继承体系** | EntityDef 支持 Mixin + 继承，公共字段/配置一处定义多处复用，消除重复 |
| **企业级韧性** | 分层错误边界 + 优雅降级，单组件故障不影响全局；批量操作自动异步化 + 进度追踪 |
| **全生命周期数据管理** | 软删除回收站 + 数据归档 + 版本历史，数据从创建到归档全程可追溯可恢复 |
