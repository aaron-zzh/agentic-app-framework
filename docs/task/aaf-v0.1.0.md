---
level: Practice
layer: Product
purpose: AAF v0.1.0 版本迭代计划
status: active
version: "0.1.0"
date: 2026-05-13
author: AaronZZH
scope:
  includes:
    - v0.1.0 业务需求与进度
gains:
  - 能了解当前版本开发进度
---

# AAF v0.1.0 迭代计划

> **目标**：搭建**配置驱动的结构化视图引擎**——以 EntityDef 为核心，实现"注册配置即生成完整 CRUD 应用"的前后端框架。同时验证 AI 协作开发流程，为后续无代码/元引擎打地基。
>
> **阶段定位**：v0.1.0 交付一个**完整可用的配置驱动低代码后台框架**。前端采用 TypeScript 配置 + ViewEngine 渲染架构（编译时类型安全），后端提供通用 CRUD API + 权限 + 工作流集成。v0.2+ 在此基础上引入运行时动态 EntityDef（无代码）和 DSL 引擎。
>
> **核心假设**：配置驱动视图引擎是元引擎的自然前置——先用 TypeScript 配置验证"EntityDef → 自动生成 UI"的模式，再迁移到运行时 JSON + 数据库存储。
>
> **周期**：2026-05-03 ~ 2026-06-15（6 周）
>
> **设计文档**：[结构化交互模式设计](../design/apps/webui/interaction-mode-structured-view.md) | [补充设计](../design/apps/webui/structured-view-supplements.md) | [后端技术选型](../design/apps/service/tech-stack.md)

## 用户故事总览

| 编号 | 名称 | 依赖 | 状态 |
|------|------|------|------|
| AAF-023 | 项目基础框架搭建 | 无 | 后端完成，前端进行中 |
| AAF-028 | 视图引擎核心 | AAF-023 | 待开始 |
| AAF-029 | 数据交互层 | AAF-028 | 待开始 |
| AAF-030 | 表单引擎 | AAF-028 | 待开始 |
| AAF-031 | 协作与通知 | AAF-028 | 待开始 |
| AAF-032 | 权限与流程 | AAF-029, AAF-030 | 待开始 |
| AAF-033 | 平台能力 | AAF-032 | 待开始 |

### 依赖图

```text
AAF-023（基础框架）
  ↓
AAF-028（视图引擎核心）
  ↓
┌─────────────┬─────────────┐
AAF-029       AAF-030       AAF-031
（数据交互）   （表单引擎）   （协作通知）
└──────┬──────┘             │
       ↓                    │
AAF-032（权限与流程）        │
       ↓                    │
AAF-033（平台能力） ←────────┘
```

029/030/031 在 028 完成后可并行开发。

## 业务需求

> 每条业务需求对应 backlog 中一个用户故事（AAF-XXX），技术任务拆分在各用户故事目录下的 `tasks.md`。

### AAF-023：项目基础框架搭建

搭建前后端开发骨架：后端 Maven 多模块（aaf-dependencies / aaf-common / aaf-framework / aaf-auto-dev / aaf-api）+ Flyway + CI；前端 Next.js 16 + TypeScript + Nx 集成。

- 技术任务：[tasks.md](v0.1.0/AAF-023/tasks.md)
- 状态：后端 #17~#31 全部完成，前端 #18 设计完成待落地

### AAF-028：视图引擎核心

实现配置驱动的视图渲染框架核心：

- **EntityDef 注册表**：实体配置的定义、注册、查找
- **ViewEngine**：根据 URL 参数 + EntityDef 自动选择渲染器（列表/表单/看板）
- **组件注册表**：字段类型 → UI 组件映射 + 自定义覆盖
- **Mixin/继承**：EntityDef 支持 mixins 和 extends，消除重复配置
- **插件机制**：registerFieldType / registerViewType / registerBatchAction
- **错误边界**：分层错误边界（应用级/视图级/字段级/Widget级）+ 优雅降级
- **工作区布局**：AppHeader + Sidebar + ViewSwitcher + Toolbar
- **动态路由**：`[module]/page.tsx` + `[module]/[id]/page.tsx`

覆盖设计文档章节：一~十二、二十四、三十、六十二、六十四

### AAF-029：数据交互层

实现视图引擎的数据获取、展示和操作能力：

- **通用 Hooks**：useEntityList / useEntityRecord / useEntityMutation / useEntityDelete
- **URL 状态管理**：nuqs 管理 view/page/sort/search/filter 参数
- **高级列表**：列配置、虚拟滚动、行拖拽排序、分组操作、行内编辑
- **搜索与筛选**：筛选构建器、操作符映射、筛选收藏、全局跨实体搜索
- **导入导出**：CSV/XLSX/PDF 导出、向导式导入、嵌套导入
- **Server Actions**：前端触发后端操作（单条/批量）
- **透视报表**：拖拽维度/指标生成数据透视表
- **批量异步化**：大数据量操作自动异步 + 进度轮询

覆盖设计文档章节：六、七、十七、二十一、二十二、三十一、三十四、三十九、四十八、四十九、五十二、六十三

### AAF-030：表单引擎

实现完整的表单渲染、校验和交互能力：

- **字段体系**：text/number/date/select/relationship/richText/upload/code/json 等全类型
- **关联字段**：异步搜索、快速创建、反向关联、级联选择
- **文件上传**：拖拽上传、进度条、图片裁剪/预览
- **条件可见性**：visibleWhen / readOnlyWhen / requiredWhen
- **统一表达式上下文**：FieldContext（$record/$user/$parent/$params/$env）
- **公式字段**：前端实时计算 + 后端持久化
- **跨字段校验**：实体级 ValidationRule（cross_field / unique / custom）
- **子表明细行**：一对多嵌套编辑（订单明细模式）
- **向导弹窗**：多步骤 Wizard 流程
- **离开确认**：未保存修改拦截
- **Smart Button1**：关联数据计数快捷按钮
- **二维码扫描**：移动端扫码填入字段
- **签名字段**：Canvas 手写签名
- **多币种/单位**：金额和数量字段的多币种/单位支持

覆盖设计文档章节：十八、十九、二十五~二十八、三十二、三十六、五十、五十一、五十六、六十、六十一

### AAF-031：协作与通知

实现多人协作和通知体系：

- **实时协作**：乐观锁 + WebSocket 在线感知 + 字段级编辑提示
- **CRDT 协同**：richText 字段 Yjs 实时协同编辑
- **版本历史**：自动版本快照 + 字段级 Diff + 一键回滚
- **活动流**：操作日志 + 评论 + @提及 + 活动调度
- **通知系统**：Toast（sonner）即时通知
- **消息中心**：持久化站内信 + 分类 + 已读/未读 + 通知偏好
- **PWA 推送**：Service Worker + Notification API
- **字段订阅**：用户关注记录/字段变更 → 推送通知
- **@待办联动**：被 @提及自动生成待办事项

覆盖设计文档章节：十三、十四、二十、二十九、三十三、四十六、五十四、五十五

### AAF-032：权限与流程

实现企业级权限控制和业务流程能力：

- **RBAC 权限**：EntityAccess（实体级 read/create/update/delete + 字段级 visible/editable）
- **行级数据权限**：声明式 DataAccessRule + $user 表达式 + 后端 SQL 注入
- **审批工作流**：Flowable 集成 + 前端状态展示 + 审批操作按钮 + 审批时间线
- **审批委托**：全权/按流程/单次委托 + 转交
- **自动化规则**：触发器（创建/更新/字段变更/定时/延迟）+ 条件 + 操作链
- **审计日志**：字段级变更记录 + 不可篡改 + 管理员视图
- **软删除回收站**：deleted_at 标记 + 恢复 + 定时清理 + 关联级联
- **计划任务管理**：任务列表 + 执行日志 + 告警 + 手动触发
- **数据归档**：按时间/状态自动归档 + 归档视图切换

覆盖设计文档章节：十五、四十、四十二、四十四、四十五、四十七、五十七、五十九

### AAF-033：平台能力

实现 SaaS 平台级能力：

- **多租户**：组织切换器 + X-Org-Id 隔离 + 组织管理 + 个人工作空间
- **仪表盘**：可配置 Widget（counter/chart/list/progress/shortcut）+ 拖拽布局
- **无代码编辑器**：v0.1 Monaco JSON 编辑（带 schema 校验）→ 后续表单化/拖拽式
- **AI 感知**：AIPageContext 收集 + 主动建议 + 字段补全 + 操作推荐
- **国际化**：next-intl + 字段标签/选项/校验错误多语言
- **响应式移动端**：断点适配 + 卡片模式 + 触摸优化
- **模板记录**：从现有记录/空白创建模板 + 快速新建
- **数据对比**：两条记录并排对比 + 差异高亮 + 合并
- **Livechat**：assistant-ui 统一架构 + 客服/机器人/AI 助理三种 runtime

覆盖设计文档章节：十六、二十三、三十五、三十七、三十八、四十一、四十三、五十三、五十八

## 迭代范围决策

### v0.1.0 引入的技术

| 技术 | 用途 | 故事 |
|------|------|------|
| TanStack Query | 服务端状态管理 | AAF-028 |
| Zustand | 客户端 UI 状态 | AAF-028 |
| nuqs | URL 状态管理 | AAF-029 |
| shadcn/ui | 基础 UI 组件 | AAF-028 |
| @dnd-kit | 拖拽（看板/排序） | AAF-029 |
| react-hook-form + Zod | 表单 + 校验 | AAF-030 |
| sonner | Toast 通知 | AAF-031 |
| Yjs | CRDT 实时协同 | AAF-031 |
| Flowable | 审批工作流 | AAF-032 |
| next-intl | 国际化 | AAF-033 |
| react-grid-layout | 仪表盘布局 | AAF-033 |

### v0.1.0 不引入的技术

| 技术 | 原因 | 计划版本 |
|------|------|----------|
| PgVector/向量库 | 无语义检索场景 | v0.2（知识库引擎） |
| Elasticsearch/Meilisearch | 全局搜索先用 PostgreSQL tsvector | v0.3（数据量增长后） |
| Magic-DSL 完整语言 | 需要词法/语法解析器，当前用 JSON + 表达式求值器覆盖 | v0.3+（元引擎） |
| Agent Sandbox | 无代码执行隔离场景 | v2.0 |
| actormesh (C++) | 性能引擎，当前 Java 足够 | v2.0 |

### 不做什么

- 不做 Magic-DSL 完整语言解析器（多范式/分层/分域），用 JSON 配置 + 表达式求值器替代
- 不做多 Agent 并行执行（顺序编排足够）
- 不做沙箱代码执行
- 不做 UniApp 功能实现（仅保留目录结构）
- 不做微服务拆分
- 不做 CRDT 以外的离线编辑能力

## 变更记录

| 日期 | 变更内容 | 原因 |
|------|---------|------|
| 2026-05-03 | 初始版本，确定迭代范围 | 版本规划讨论 |
| 2026-05-05 | 新增 AAF-024 协作基础设施优化 | 迭代中期协作实践反思 |
| 2026-05-06 | 补充"传统 MVC 过渡期"阶段定位 | 明确 v0.1 定位 |
| 2026-05-10 | AAF-023 #18 前端调研阶段完成 | 产出 6 份设计文档 |
| 2026-05-13 | **重大重规划**：v0.1.0 目标从"传统 MVC 验证"改为"配置驱动视图引擎"。归档 AAF-018/019/020/022，新增 AAF-028~033 六个结构化视图引擎用户故事。原因：结构化交互模式设计（64 章）已形成完整的配置驱动低代码框架设计，原有按模块拆分的用户故事无法覆盖，需按架构层次重新组织 | 前端架构设计完成后的自然演进 |
