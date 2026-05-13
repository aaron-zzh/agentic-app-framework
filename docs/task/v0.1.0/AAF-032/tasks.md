---
level: Practice
layer: Product
purpose: AAF-032 权限与流程的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-13
author: AaronZZH
---

# 权限与流程（AAF-032）

> 设计：[结构化交互模式设计](../../../design/apps/webui/interaction-mode-structured-view.md) 章节十五、四十、四十二、四十四、四十五、四十七、五十七、五十九
> 负责人：architect + developer-api + developer-web | 创建：05-13

## 任务列表

> **执行策略**：先建权限体系（RBAC + 行级），再建数据生命周期（软删除 + 归档），再建工作流和自动化。
> 前置：AAF-029 #3（通用 CRUD API）+ AAF-030 #9（条件可见性）完成。

### 权限体系

1. [ ] #1 RBAC 权限后端 — developer-api
   - 扩展 AAF-023 #30 的 Spring Security 骨架
   - 角色表 + 权限表 + 角色-权限关联表
   - 实体级权限计算：根据用户角色返回 EntityAccess（read/create/update/delete + fieldAccess）
   - API：`GET /api/permissions/entity/{slug}` 返回当前用户对该实体的权限
   - verify: 不同角色用户获取不同 EntityAccess

2. [ ] #2 权限驱动 UI — developer-web (依赖: #1)
   - 请求 EntityAccess 并注入 ViewEngine
   - 无 create 权限 → 隐藏 [+ 新建]
   - 无 update 权限 → 表单只读
   - 无 delete 权限 → 隐藏删除按钮
   - fieldAccess.visible=false → 不渲染；editable=false → 只读
   - verify: 普通用户看不到管理员按钮

3. [ ] #3 行级数据权限 — developer-api (依赖: #1)
   - sys_data_access_rule 表：entity / roles / condition(JSONB) / effect
   - 条件支持 $user.xxx 表达式（如 `created_by eq $user.id`）
   - 全局 JPA Filter 自动注入 WHERE 条件
   - 无权限记录返回 404（非 403）
   - verify: 普通用户只能查到自己创建的记录

4. [ ] #4 行级权限管理 UI — developer-web (依赖: #3)
   - `/workspace/admin/data-access` 管理页面
   - 按实体分组展示规则
   - 新建规则表单：选择实体 → 字段 → 操作符 → 值来源
   - 管理员"以 XX 角色查看"预览
   - verify: 创建规则后数据过滤生效

### 数据生命周期

5. [ ] #5 软删除与回收站后端 — developer-api
   - 所有业务表 `deleted_at` 字段（BaseEntity 内置）
   - 全局查询追加 `WHERE deleted_at IS NULL`
   - 回收站 API：`GET /api/trash` / `POST /api/trash/restore` / `DELETE /api/trash/purge`
   - 关联数据级联软删除/恢复
   - 定时任务：超过 retentionDays 自动物理删除
   - verify: 删除后列表不可见，回收站可恢复

6. [ ] #6 回收站前端 — developer-web (依赖: #5)
   - 侧边栏底部 [🗑️ 回收站] 入口
   - `/workspace/trash` 列表视图：筛选实体类型/删除人/时间
   - 操作：[恢复] [彻底删除]（需权限）
   - 删除时 Toast 显示 [撤销]（5 秒内）
   - verify: 删除 → 撤销恢复 / 回收站恢复 / 彻底删除全链路

7. [ ] #7 数据归档 — developer-api + developer-web (依赖: #5)
   - `archived_at` 字段 + 归档规则配置
   - 自动归档：满足条件 + afterDays 天后执行
   - 列表工具栏 [📦 显示归档数据] 开关
   - 归档记录操作：[恢复到活跃]
   - verify: 满足条件的记录自动归档，切换开关可查看

### 审批工作流

8. [ ] #8 Flowable 集成 — developer-api
   - 引入 Flowable Spring Boot Starter
   - 流程定义部署 API
   - 通用审批流程模板（提交→审批→通过/驳回）
   - 审批 API：`POST /api/{entity}/workflow/start` / `complete` / `reject`
   - verify: 启动流程 → 审批通过 → 状态变更

9. [ ] #9 审批工作流前端 — developer-web (依赖: #8)
   - FormHeader 显示当前流程状态 + 操作按钮（审批通过/驳回/转交）
   - 操作前确认 + 意见输入框（commentRequired）
   - 表单底部审批时间线（提交人→审批人1→...）
   - `visibleWhen` 控制按钮在特定状态下显示
   - verify: 审批操作后状态正确流转，时间线更新

10. [ ] #10 审批委托 — developer-api + developer-web (依赖: #8)
    - sys_delegation 表：delegator / delegate / start_date / end_date / process_keys / status
    - `/workspace/settings/delegation` 设置页面
    - 委托生效期间新审批自动转给代理人
    - 审批记录标注"由 XX 代 YY 审批"
    - 单次转交：[转交...] 按钮
    - verify: 设置委托后新审批自动转给代理人

### 自动化规则

11. [ ] #11 自动化规则后端 — developer-api
    - sys_automation_rule 表：entity / trigger / conditions / actions / enabled
    - 触发器实现：on_create / on_update / field_change / schedule(cron) / delay
    - 操作实现：update_field / send_notification / create_record / start_workflow / call_webhook
    - 执行日志记录（成功/失败/跳过）
    - verify: 创建记录触发自动化规则执行

12. [ ] #12 自动化规则前端 — developer-web (依赖: #11)
    - `/workspace/admin/automations` 管理页面
    - 简化流程编辑器 UI：触发 → 条件 → 操作链
    - [启用/禁用] 开关 + [测试运行]
    - 执行日志查看
    - verify: 配置规则后触发条件满足时自动执行

### 审计日志

13. [ ] #13 审计日志后端 — developer-api
    - audit_log 表：entity_type / entity_id / action / user_id / timestamp / changes(JSONB) / ip
    - JPA EntityListener 自动捕获字段级变更
    - 表级约束：禁止 UPDATE/DELETE
    - API：`GET /api/admin/audit-log`（分页+筛选）
    - verify: 修改记录后 audit_log 产生字段级变更记录

14. [ ] #14 审计日志前端 — developer-web (依赖: #13)
    - `/workspace/admin/audit-log` 列表视图
    - 筛选：实体类型 / 操作人 / 时间范围 / 操作类型
    - 点击展开字段级变更详情（oldValue → newValue）
    - verify: 审计日志正确展示变更历史

### 计划任务管理

15. [ ] #15 计划任务管理 — developer-api + developer-web (依赖: #11)
    - `/workspace/admin/scheduled-tasks` 管理视图
    - 列：任务名 / 类型 / Cron / 上次执行 / 下次执行 / 状态
    - 操作：[暂停] [恢复] [立即执行] [查看日志]
    - 告警：连续失败 N 次自动暂停 + 通知管理员
    - verify: 暂停/恢复/手动触发任务正确

<!-- 状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 -->
