---
level: Practice
layer: Model
purpose: 组织下多工作区数据隔离设计——新增 Workspace 实体，修正 B1 租户隔离遗留问题
status: draft
version: 0.1.0
date: 2026-07-12
author: AaronZZH
changelog:
  - 2026-07-12 | 完善：DataAccessService.buildUserContext() 的 orgId/workspaceId 均改为集合语义（orgIds/workspaceIds，查 sys_org_member/sys_workspace_member 归属关系表），移除语义错误的单值字段，与 orgFilter/workspaceSpec() 的"当前请求过滤"分工明确
  - 2026-07-12 | 同步后端落地状态：workspaceSpec 实际签名、OperatorEntityListener 自动填充、OrgFilter 校验均已实现，更新变更影响面清单（前端待办暂不动）
  - 2026-07-12 | 需求确认：成员显式邀请、创建者即管理者（无独立角色）、workspaceId=NULL 默认可见（无需迁移）
  - 2026-07-12 | 精简正文表述，合并重复论证段落，去除冗余说明
  - 2026-07-12 | 初版：Workspace 实体设计 + 隔离机制选型 + orgId/workspaceId 自动填充方案
gains:
  - 能理解组织（Organization）与工作区（Workspace）的层级关系与各自职责边界
  - 能理解为何数据隔离改用显式 Specification 拼接而非 Hibernate Filter + AOP
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

- `Organization` 保持现状（`personal`/`team`），承担计费主体、最外层组织边界职责，不改动 `OrganizationService.createPersonalOrg`。
- `Workspace` 挂在 `Organization` 下，用户在任意所属组织下可自建多个工作区。
- 隔离粒度从"组织"下沉一层到"工作区"：`org_id` 定位组织，`workspace_id` 定位组织内哪个工作区，两者同时生效、不互相替代。

**不引入系统级默认组织**：`sys_notice`/`sys_data_access_rule` 等种子数据存在 `org_id` 缺失问题，本质是全局配置或全局可见内容，不属于任何组织；正确解法是标注 `@OrgIgnore` 豁免隔离，而非编造一个默认组织归属。

**保留 personal 组织**：解决"保证每个用户都有 `org_id` 可用"的兜底问题，与工作区要解决的"组织内部协作空间划分"是不同层级，不互斥。移除会导致注册流程被迫插入"先建组织"步骤，是体验倒退。**调整点**：用户注册自动创建 `personal` 组织时，同步在该组织下自动创建一个默认工作区，把"注册即可用"延伸到工作区层。

## 数据模型

```text
sys_workspace
  id              BIGINT PK
  org_id          BIGINT FK → sys_organization  NOT NULL
  name            VARCHAR
  slug            VARCHAR
  + BaseEntity 审计字段（create_by 即工作区创建者/管理者）

sys_workspace_member
  id              BIGINT PK
  workspace_id    BIGINT FK → sys_workspace  NOT NULL
  user_id         BIGINT FK → sys_user  NOT NULL
  + BaseEntity 审计字段
```

组织成员**不自动**加入组织下的工作区，需显式邀请/加入，与 `sys_org_member` 是同一套模式。

**工作区权限模型**：不设独立角色层级，`Workspace.createBy` 即该工作区的管理者，拥有邀请/移除成员、改名、删除工作区等全部管理权限；`sys_workspace_member` 只记录归属关系，不需要 `role` 字段。

**`workspace_id` 隔离语义**：`NULL` 表示"组织级共享，不特定某个工作区"，在任何工作区视角下都默认可见，不是"缺失"或"不可见"。查询条件为 `workspaceId IS NULL OR workspaceId = 当前工作区`。因此历史数据不需要批量回填默认工作区——`workspace_id = NULL` 本身就是合法状态，不需要类似 `v16__workspace_schema.sql` 中回填 personal org 的迁移脚本。

## 隔离机制选型：显式 Specification，不用 Hibernate Filter + AOP

当前 `org_id` 隔离基于 `BaseEntity` 的 Hibernate `@Filter` + `OrgFilterAspect`。该机制暴露过三类问题（本迭代已修复）：

1. `OrgFilterAspect` 用 `getDeclaringType()` 判断实体类型，对 `findAllById` 等继承方法会解析到错误的父接口，误判为"非全局实体"。
2. Hibernate Filter 状态绑定在 Session 而非单次查询，一次误启用会残留污染后续查询。
3. 全局配置类实体需逐一标注 `@OrgIgnore` 才能豁免，遗漏即导致查询静默过滤为空——表现为"数据消失"而非报错，排查成本高。

工作区隔离**不复用该机制**，改为显式拼接 JPA `Specification`，与 `BaseCrudService.buildAccessSpec()`（L3 行级权限，已验证工作正常）同一模式：

```java
Specification.allOf(idSpec(id), buildAccessSpec(), workspaceSpec())

// workspaceSpec()：从 OrgContext.getCurrentWorkspaceId() 取当前请求的工作区 ID（由 OrgFilter 从
// X-Workspace-Id 头解析并校验归属后写入）；未携带该头时返回 null（不叠加过滤，工作区维度可选）。
// workspace_id 为 NULL 表示"组织级共享，不特定某个工作区"，任何工作区视角下都默认可见；
// 否则要求精确匹配当前工作区。
protected Specification<E> workspaceSpec() {
    var workspaceId = OrgContext.getCurrentWorkspaceId();
    if (workspaceId == null) {
        return (root, query, cb) -> null;
    }
    return (root, query, cb) -> cb.or(
            cb.isNull(root.get("workspaceId")),
            cb.equal(root.get("workspaceId"), workspaceId));
}
```

优势：条件在调用点可见，出问题读代码即可定位；不存在 Session 状态残留；不需要为每个实体判断"要不要豁免"。

**已知取舍**：覆盖面依赖调用路径是否经过 `BaseCrudService`。部分 Service（如 `UserService`/`TodoService` 的自定义查询）直接调用 repository，这些路径需业务代码显式加 workspace 条件。这是有意的权衡：用较窄的覆盖面换取机制的简单性和可排查性。

## orgId / workspaceId 自动填充

`ownerId`/`orgId`/`workspaceId` 均已由 `OperatorEntityListener`（`@PrePersist` 全局监听器）兜底填充，业务层未显式设置时才生效（不覆盖已有值）。`orgId`/`workspaceId` 取值分别来自 `OrgContext.getCurrentOrgId()`/`OrgContext.getCurrentWorkspaceId()`，HTTP 请求场景由 `OrgFilter` 从 `X-Org-Id`/`X-Workspace-Id` 头解析、校验用户归属（查 `sys_org_member`/`sys_workspace_member`，不属于则 403）后写入。

不做创建时 fail-closed——该监听器全局生效会覆盖后台任务、种子数据初始化等合法的"无 workspace 上下文"场景；缺失时业务数据会在查询侧被显式 `Specification` 条件（`workspaceSpec()`）过滤，属于纵深防御。

## 前端现状

`WorkspaceSwitcher.tsx` 当前是"过渡期按组织映射默认工作区"（组件名叫工作区切换器，实际切换的是 `orgId`）。`ui-store.ts` 中 `currentWorkspace`/`workspaces` 状态已搭好骨架但无数据源写入。`Workspace` 实体落地后，前端切换到真实工作区列表。

## 行级权限规则的 orgIds / workspaceIds 支持

`sys_data_access_rule` 行级规则条件表达式支持 `$user.orgIds`/`$user.workspaceIds`：`DataAccessService.buildUserContext()` 查 `sys_org_member`/`sys_workspace_member` 汇总用户实际所属的组织/工作区 ID 集合（`List<Long>`），放入规则求值上下文，配合 `op: "in"` 使用，如：

```json
{"field": "orgId", "op": "in", "value": "$user.orgIds"}
```

**为何是集合而非单值**：用户可同时属于多个组织/工作区（`sys_org_member`/`sys_workspace_member` 均为多对多归属关系，参见 `OrganizationService.listByUser()`）。早期实现曾直接取 `BaseEntity.orgId`/`workspaceId`（该用户记录本身归属于哪个组织/工作区，数据隔离用的单值字段）当作"用户所属组织/工作区"，语义错误——已修正为查归属关系表得到的集合。`teamIds` 目前仍是空占位（`List.of()`），团队功能落地后按同一模式补齐。

**与 `orgFilter`/`workspaceSpec()` 的分工**（两者是"当前请求参数化过滤"，与行级规则的"用户归属集合判断"是两个不同维度，按需叠加，不互相替代）：

- `orgFilter`（Hibernate Filter + `OrgFilterAspect`）/`workspaceSpec()`（`BaseCrudService`）：过滤"当前请求限定在哪个组织/工作区"，取值来自 `OrgContext.getCurrentOrgId()`/`getCurrentWorkspaceId()`（`X-Org-Id`/`X-Workspace-Id` 请求头，已由 `OrgFilter` 校验归属），是单值、请求维度的过滤，`orgFilter` 全局强制、`workspaceSpec()` 未携带头时不叠加（可选）。
- 行级规则中的 `$user.orgIds`/`$user.workspaceIds`：表达"该用户归属于哪些组织/工作区"，与当前请求选了哪个组织/工作区无关，用于跨组织/工作区的归属判断场景（如"只有属于组织 A 的用户能看到 A 的某类数据，不受当前切换到哪个组织影响"）。

两者在 `BaseCrudService.buildEffectiveSpec()` 中通过 `Specification.allOf(buildSpec(pageDTO), buildAccessSpec(), workspaceSpec())` 合并生效；`orgFilter` 在 Hibernate Session 层面全局叠加。

## 变更影响面（后续技术设计需覆盖）

后端已落地：

- [x] 新增 `Workspace`/`WorkspaceMember` 实体 + 迁移脚本（`v16__workspace_schema.sql`）
- [x] 新建/加入/退出工作区的 API（`WorkspaceController`/`WorkspaceService`）
- [x] `OrgFilter` 同款逻辑复刻解析/校验 `X-Workspace-Id`
- [x] `BaseCrudService` 新增 `workspaceSpec()`，在 `buildEffectiveSpec()` 中与 `buildAccessSpec()` 叠加生效
- [x] `OperatorEntityListener` 追加 `workspaceId` 自动填充
- [x] `DataAccessService` 行级规则上下文补齐 `orgIds`/`workspaceIds` 集合语义

前端待覆盖：

- [ ] 前端页面（工作区创建/切换/成员管理）
- [ ] `WorkspaceSwitcher`/`ui-store.ts` 接入真实数据源
