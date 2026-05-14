---
level: Practice
layer: Product
purpose: AAF-028 视图引擎核心的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-13
author: AaronZZH
---

# 视图引擎核心（AAF-028）

> 设计：[结构化交互模式设计](../../../design/apps/webui/interaction-mode-structured-view.md) 章节一~十二、二十四、三十、六十二、六十四
> 负责人：architect + developer-web | 创建：05-13

## 任务列表

> **执行策略**：自底向上——先建类型系统和注册表，再建渲染引擎，最后搭布局壳。
> 前置：AAF-023 #18 前端目录结构落地 + 依赖安装完成。

### 类型系统与注册表

1. ✅ #32 EntityDef 类型定义 — developer-web
   - 定义 `EntityDef` / `FieldDef` / `ListViewConfig` / `FormViewConfig` / `KanbanViewConfig` 完整 TypeScript 接口
   - 定义 `EntityAccess` / `FieldAccess` 权限接口
   - 放置于 `apps/webui/src/lib/entity-engine/types/`
   - verify: `pnpm nx run webui:typecheck` 通过

2. [ ] #33 Mixin 与继承机制 — developer-web (依赖: #32)
   - 实现 `resolveMixins(def)` 函数：合并 mixin 字段到 EntityDef
   - 实现 `resolveExtends(def, registry)` 函数：继承父实体配置
   - 内置 Mixin：TimestampMixin / AuditMixin / SoftDeleteMixin / OrgMixin
   - 合并规则：同名字段自身覆盖 Mixin，hooks 合并执行
   - verify: 单元测试覆盖合并/覆盖/继承场景

3. [ ] #34 实体注册表（Entity Registry） — developer-web (依赖: #33)
   - 实现 `entityRegistry` 对象：注册/查找/列举实体
   - 启动时自动解析 mixins + extends
   - 导出 `getEntityDef(slug)` / `getAllEntities()` / `getEntitiesByGroup()`
   - verify: 注册 2-3 个示例实体（document/user/task），查找正确

4. [ ] #35 组件注册表（Component Registry） — developer-web (依赖: #32)
   - 实现 `fieldComponents` 映射：字段类型 → 表单组件
   - 实现 `cellComponents` 映射：字段类型 → 列表单元格组件
   - 支持 `registerFieldType()` / `registerViewType()` / `registerBatchAction()` 扩展 API
   - 组件 Props 契约：`FieldProps<T>` / `CellProps<T>` 接口
   - verify: 注册自定义字段类型后可通过 registry 获取

### 视图引擎

5. [ ] #36 ViewEngine 核心渲染器 — developer-web (依赖: #34, #35)
   - 实现 `ViewEngine` 组件：根据 URL `?view=` 参数选择渲染器
   - 支持视图类型：list / form / kanban（graph/chart/calendar 占位）
   - 支持 `entity.overrides?.[view]` 自定义视图覆盖
   - verify: 访问 `/workspace/document?view=list` 正确路由到 ListView

6. [ ] #37 动态路由搭建 — developer-web (依赖: #36)
   - 创建 `app/(workspace)/[module]/page.tsx`（列表/看板入口）
   - 创建 `app/(workspace)/[module]/[id]/page.tsx`（表单入口）
   - 路由参数解析 → 查找 EntityDef → 传入 ViewEngine
   - 404 处理：slug 不存在时显示"实体未注册"
   - verify: 动态路由正确解析 module 参数并渲染对应实体

7. [ ] #38 ListView 基础实现 — developer-web (依赖: #36)
   - 基于 shadcn/ui DataTable 实现列表视图
   - 从 `entity.listView.columns` 读取列配置
   - 每列根据字段类型从 cellComponents 获取渲染组件
   - 支持 `defaultSort` 排序
   - 空状态 / 加载骨架屏
   - verify: document 实体列表正确渲染列和 mock 数据

8. [ ] #39 KanbanView 基础实现 — developer-web (依赖: #36)
   - 基于 @dnd-kit 实现看板视图
   - 从 `entity.kanbanView.statusField` 读取分列字段
   - 卡片标题从 `cardTitle` 字段取值
   - 拖拽卡片触发状态变更（optimistic update）
   - verify: task 实体看板正确分列，拖拽触发状态更新

9. [ ] #40 FormView 基础实现 — developer-web (依赖: #36)
   - 基于 react-hook-form + Zod 实现表单视图
   - `buildZodSchema(fields)` 自动生成校验 schema
   - 按 `formView.layout` 渲染 tabs/group/row 布局（无 layout 时线性渲染）
   - 每个字段从 fieldComponents 获取表单组件
   - verify: document 实体表单正确渲染字段并校验

### 工作区布局

10. [ ] #41 工作区布局壳 — developer-web (依赖: #37)
    - 实现 `AppHeader`：logo + ⌘K 命令面板入口 + 用户菜单
    - 实现 `Sidebar`：从 entityRegistry 自动生成菜单（按 group 分组）
    - 实现 `ViewSwitcher`：列表/看板/图表 Tab 切换
    - 实现面包屑：路由 + entity.label + 记录标题
    - verify: 侧边栏正确显示注册实体，点击切换路由

11. [ ] #42 视图切换与 Toolbar — developer-web (依赖: #41, #38)
    - ViewSwitcher 切换 `?view=` 参数
    - Toolbar：搜索框 + [+ 新建] 按钮 + 视图切换图标
    - [+ 新建] 根据 `access.create` 控制显示/隐藏
    - verify: 切换视图 URL 参数变化，ViewEngine 重新渲染

### 错误边界与降级

12. [ ] #43 分层错误边界 — developer-web (依赖: #36, #40)
    - Layer 1: `app/error.tsx` 应用级兜底
    - Layer 2: ViewEngine 内部 ErrorBoundary（视图级）
    - Layer 3: FieldComponent 包裹 ErrorBoundary（字段级）
    - 字段降级：报错时回退到 TextInput 或 readOnly 展示原始值
    - verify: 模拟字段组件抛错，验证降级渲染而非页面崩溃

### 基础字段组件

13. [ ] #44 基础字段组件集 — developer-web (依赖: #35)
    - 表单组件：TextInput / TextareaInput / NumberInput / DatePicker / CheckboxInput / SelectInput
    - 列表单元格：TextCell / DateCell / BadgeCell / CheckCell
    - 所有组件遵循 `FieldProps<T>` / `CellProps<T>` 契约
    - 支持 `disabled` / `error` 状态
    - verify: 各组件独立渲染正确，Props 类型安全

14. [ ] #45 加载状态与骨架屏 — developer-web (依赖: #38, #40)
    - 列表骨架屏：匹配列数/行数
    - 表单骨架屏：匹配字段数
    - 路由切换顶部进度条
    - TanStack Query isLoading/isFetching 状态集成
    - verify: 首次加载显示骨架屏，数据到达后切换为真实内容

### 工程化

15. [ ] #46 targetDefaults 统一配置 — developer-web (依赖: #32)
    - 在 nx.json 添加 `targetDefaults`：build（`^build` + production inputs）、test（test inputs）、typecheck（`^build`）
    - 各 project.json 中移除与 targetDefaults 重复的 inputs/outputs/dependsOn
    - verify: `pnpm check:affected` 全绿，缓存行为不变

16. [ ] #47 Playwright E2E 测试栈 — developer-web (依赖: #37)
    - 来源：AAF-023 #22，前置页面就绪后引入
    - Vitest 单测已就绪，本任务补充 Playwright E2E
    - verify: `pnpm nx run webui-e2e:e2e` 跑通

17. [ ] #48 UniApp 参考调研 + 完整目录结构 — architect + developer-app
    - 来源：AAF-023 #19
    - 参考 kids-app 项目结构
    - 创建 [docs/design/apps/uniapp/tech-stack.md](../../../design/apps/uniapp/tech-stack.md)
    - verify: 目录结构创建完成，`package.json` 就绪

<!-- 状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 -->
