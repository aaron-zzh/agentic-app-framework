---
level: Practice
layer: Model
purpose: 组织下多工作区数据隔离设计——新增 Workspace 实体，修正 B1 租户隔离遗留问题
status: draft
version: 0.2.0
date: 2026-08-13
author: AaronZZH
changelog:
  - 2026-08-13 | 调整：个人组织同步创建默认工作区（默认工作区/default），管理权改由 owner_id 表达，工作区 DDL 回并 v1 基线
  - 2026-08-13 | 安全：工作区 owner/成员可见范围进入统一 CRUD L3 scope，覆盖分页、详情、选项、批量和写入口
  - 2026-07-12 | 完善：DataAccessService.buildUserContext() 的 orgId/workspaceId 均改为集合语义（orgIds/workspaceIds，查 sys_org_member/sys_workspace_member 归属关系表）
  - 2026-07-12 | 同步后端落地状态：OperatorEntityListener 自动填充、OrgFilter 校验均已实现
  - 2026-07-12 | 需求确认：成员显式邀请、ownerId 标识管理者、workspaceId=NULL 默认可见
  - 2026-07-12 | 精简正文表述，合并重复论证段落，去除冗余说明
  - 2026-07-12 | 初版：Workspace 实体设计 + 隔离机制选型 + orgId/workspaceId 自动填充方案
gains:
  - 能理解组织（Organization）与工作区（Workspace）的层级关系与各自职责边界
  - 能定位统一 CRUD tenant/L3/personal scope 的工作区隔离边界
  - 能定位 orgId/workspaceId 自动填充的实现位置与生效条件
  - 能理解 personal 组织为何保留、系统级默认组织为何不引入
related:
  - ./identity-and-membership.md
  - ../../audit/2026-05-30-service-review/01-security-and-authz.md
  - ../../framework/security/access-control.md
---

# 工作区隔离设计（Workspace Isolation）

> 组织（Organization）是组织边界，工作区（Workspace）是组织内部的协作空间划分。用户可在所属组织下自建多个工作区，项目与业务数据按工作区隔离。

## 背景与动机

[identity-and-membership.md](identity-and-membership.md) v0.2.0（2026-05-29）曾判断"租户层不引入"，`workspace_id` 字段保留但不使用；安全审计 [B1](../../audit/2026-05-30-service-review/01-security-and-authz.md) 同期指出该字段"完全未隔离"，列为架构级遗留问题。本设计基于新产品需求（用户可自建工作区，数据按工作区隔离）推进落地，**更新前序文档的判断**，`identity-and-membership.md` 中"租户层"相关决策以本文档为准。

> 代码中原 `Tenant*` 命名（`TenantContext`/`TenantFilter`/`TenantIgnore` 等）已统一重命名为 `Org*`：AAF 的隔离边界本质是"组织"（用户可自由创建、加入、切换多个组织），不是租户间完全孤立的多租户 SaaS，沿用"租户"命名会传递错误的心智模型。历史审计报告记录审查当时的实际类名，不追溯修改。

## 与现有模型的关系

```text
Organization（组织，现有不变）
  │ 1:N
  ▼
Workspace（工作区，新增）
  │ 1:N
  ▼
WorkspaceMember（新增）
```

- `Organization` 保持 `personal`/`team` 类型与计费主体、最外层组织边界职责；`OrganizationService.createPersonalOrg` 在原有组织及 owner 成员关系之外，同事务创建默认工作区及其 owner 成员关系。
- `Workspace` 挂在 `Organization` 下，用户在任意所属组织下可自建多个工作区。
- 隔离粒度从"组织"下沉一层到"工作区"：`org_id` 定位组织，`workspace_id` 定位组织内哪个工作区，两者同时生效、不互相替代。

**不引入系统级默认组织**：`sys_notice`/`sys_data_access_rule` 等种子数据存在 `org_id` 缺失问题，本质是全局配置或全局可见内容，不属于任何组织；正确解法是标注 `@OrgIgnore` 豁免隔离，而非编造一个默认组织归属。

**保留 personal 组织**：解决"保证每个用户都有 `org_id` 可用"的兜底问题，与工作区要解决的"组织内部协作空间划分"是不同层级，不互斥。移除会导致注册流程被迫插入"先建组织"步骤，是体验倒退。**调整点**：用户注册自动创建 `personal` 组织时，同步在该组织下自动创建 `name=默认工作区`、`slug=default` 的工作区，把"注册即可用"延伸到工作区层。

## 数据模型

```text
sys_workspace
  id              BIGINT PK
  org_id          BIGINT FK → sys_organization  NOT NULL
  owner_id        BIGINT FK → sys_user          NOT NULL（工作区管理者）
  name            VARCHAR
  slug            VARCHAR
  + BaseEntity 其余审计字段

sys_workspace_member
  id              BIGINT PK
  workspace_id    BIGINT FK → sys_workspace  NOT NULL
  user_id         BIGINT FK → sys_user  NOT NULL
  + BaseEntity 审计字段
```

组织成员**不自动**加入组织下的工作区，需显式邀请/加入，与 `sys_org_member` 是同一套模式。

**工作区权限模型**：不设独立角色层级，`Workspace.ownerId` 即该工作区的管理者，拥有邀请/移除成员、改名、删除工作区等全部管理权限；`createBy` 仅记录实际操作者，不能承载业务权限；`sys_workspace_member` 只记录归属关系，不需要 `role` 字段。

**成员移除约束**：将用户移出组织时，同事务软删除其在该组织下的全部 `sys_workspace_member` 关系，避免重新加入组织后旧授权自动恢复；若用户仍是该组织某个工作区的 `ownerId`，则拒绝移除，必须先处理工作区归属，避免产生无人可管理的工作区。

**`workspace_id` 隔离语义**：`NULL` 表示"组织级共享，不特定某个工作区"，在任何工作区视角下都默认可见，不是"缺失"或"不可见"。查询条件为 `workspaceId IS NULL OR workspaceId = 当前工作区`。因此历史业务数据不需要批量回填默认工作区——`workspace_id = NULL` 本身就是合法状态；当前尚未部署，工作区最终 DDL 直接维护在 `v1__system_schema.sql`，不保留存量修复迁移。

## 隔离机制：统一 CRUD Scope + L3 行级规则

`org_id` 外层隔离仍由 `BaseEntity` 的 Hibernate `@Filter` 与 `OrgFilterAspect` 强制执行。工作区维度不再由业务 Service 自行拼接分页专用条件，而是进入统一 CRUD 安全管线：

```java
Specification.allOf(
    decision.scopeSpecification(), // tenantScope + L3 recordScope + personalScope
    buildFilterSpec(filters, context),
    buildSpec(pageDTO)              // 仅业务筛选
)
```

- `CrudEnforcementService` 根据资源的 `TenantScope`、当前 `OrgContext` 和 L3 数据权限规则生成 `decision.scopeSpecification()`。
- `BaseCrudService` 的 PAGE、GET、OPTIONS、BATCH、UPDATE、DELETE 等标准入口统一消费该 scope，避免分页与详情授权不对称。
- `Workspace` 自身是组织下的作用域根，数据库约束其 `workspace_id IS NULL`；其可见范围由 `v13__access_rules.sql` 的 L3 规则定义：`ownerId = $user.id OR id IN $user.workspaceIds`。超级管理员按统一授权规则绕过，其他用户必须是工作区 owner 或显式成员。
- 当前工作区请求头仍由 `OrgFilter` 校验用户归属后写入 `OrgContext`；业务数据的 `workspace_id = NULL` 表示组织级共享，否则按当前工作区范围过滤。

自定义 Service 若绕过 `BaseCrudService` 直接访问 Repository，必须显式复用相同的 CRUD enforcement 决策或执行等价范围校验；不能只在列表接口追加条件。

## orgId / workspaceId 自动填充

`ownerId`/`orgId`/`workspaceId` 均已由 `OperatorEntityListener`（`@PrePersist` 全局监听器）兜底填充，业务层未显式设置时才生效（不覆盖已有值）。`orgId`/`workspaceId` 取值分别来自 `OrgContext.getCurrentOrgId()`/`OrgContext.getCurrentWorkspaceId()`，HTTP 请求场景由 `OrgFilter` 从 `X-Org-Id`/`X-Workspace-Id` 头解析、校验用户归属（查 `sys_org_member`/`sys_workspace_member`，不属于则 403）后写入。

不做创建时 fail-closed——该监听器全局生效会覆盖后台任务、种子数据初始化等合法的"无 workspace 上下文"场景；缺失时业务数据会在查询侧被统一 CRUD scope 过滤，属于纵深防御。

## 前端现状

`WorkspaceSwitcher.tsx` 已通过 `useWorkspaces` 加载真实工作区列表，并通过 `useOrgStore.setOrgContext` 原子同步 `X-Org-Id`、`X-Workspace-Id` 与持久化客户端选择状态；支持具体工作区、当前组织全部工作区和全部组织三种视角。

Studio 进入时调用 `GET /api/system/orgs/default-context`，该引导接口仅依赖认证用户，返回其 personal 组织及 `slug=default` 的默认工作区。前端在渲染实体元数据和业务组件前写入两个 Header，保证首批业务请求已具备完整组织与工作区上下文。工作区创建与成员管理页面仍待补齐。

## 行级权限规则的 orgIds / workspaceIds 支持

`sys_data_access_rule` 行级规则条件表达式支持 `$user.orgIds`/`$user.workspaceIds`：`DataAccessService.buildUserContext()` 查 `sys_org_member`/`sys_workspace_member` 汇总用户实际所属的组织/工作区 ID 集合（`List<Long>`），放入规则求值上下文，配合 `op: "in"` 使用，如：

```json
{"field": "orgId", "op": "in", "value": "$user.orgIds"}
```

**为何是集合而非单值**：用户可同时属于多个组织/工作区（`sys_org_member`/`sys_workspace_member` 均为多对多归属关系，参见 `OrganizationService.listByUser()`）。早期实现曾直接取 `BaseEntity.orgId`/`workspaceId`（该用户记录本身归属于哪个组织/工作区，数据隔离用的单值字段）当作"用户所属组织/工作区"，语义错误——已修正为查归属关系表得到的集合。`teamIds` 目前仍是空占位（`List.of()`），团队功能落地后按同一模式补齐。

**与请求范围和 L3 规则的分工**：

- `OrgFilter`/`OrgFilterAspect` 强制当前组织外层隔离，并校验请求头中的组织、工作区归属。
- `CrudEnforcementService` 根据当前 `OrgContext` 生成 tenant scope，表达"本次请求限定在哪个组织/工作区"。
- L3 规则中的 `$user.orgIds`/`$user.workspaceIds` 表达"用户实际属于哪些组织/工作区"；工作区资源用它与 `ownerId` 组成显式可见范围。

三者在 `decision.scopeSpecification()` 和 Hibernate org filter 两层叠加：请求范围不能扩大用户归属，用户归属规则也不能跨越当前组织。

## 变更影响面（后续技术设计需覆盖）

后端已落地：

- [x] 新增 `Workspace`/`WorkspaceMember` 实体，最终 DDL 位于 `v1__system_schema.sql` org 子域
- [x] 新建/加入/退出工作区的 API（`WorkspaceController`/`WorkspaceService`）
- [x] `OrgFilter` 同款逻辑复刻解析/校验 `X-Workspace-Id`
- [x] `CrudEnforcementService` 统一生成 tenant/L3/personal scope，`BaseCrudService` 全入口复用
- [x] `OperatorEntityListener` 追加 `workspaceId` 自动填充
- [x] `DataAccessService` 行级规则上下文补齐 `orgIds`/`workspaceIds` 集合语义

前端状态：

- [x] `WorkspaceSwitcher`/`ui-store.ts` 接入真实工作区列表与切换状态
- [ ] 工作区创建与成员管理页面
