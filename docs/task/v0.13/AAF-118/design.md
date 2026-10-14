---
level: Practice
layer: Model
purpose: AAF-118 Role 可见性与用户自维护绑定的方案设计：数据模型、引用校验、绑定规则、助理加载容错与接口
status: draft
version: 2.0.0
date: 2026-10-14
author: AaronZZH & Kiro
tags:
  - AAF-118
  - Role
  - 可见性
  - 角色绑定
  - 设计
related:
  - requirement.md
  - tasks.md
  - ../../../design/framework/intelligent/action-governance.md
---

# AAF-118 方案设计

> 状态：draft，待人工审核。涉及权限与数据模型，审核通过前不得编码。
> 与 v1.0.0 的区别：放弃"内置 / 公开角色自动到达所有助理 + 覆盖表"，改为"模板初始绑定 + 用户自维护绑定 + 数量上限"。理由见"方案取舍"。

## 现状核对

| 事项 | 现状 | 位置 |
|---|---|---|
| 角色表 | 无可见性字段，有 `owner_id` 但不使用 | `ai_role`（`v2__ai_schema.sql`） |
| 角色查询 | `findAll()`，全局可见 | `AiRoleService.listRoles` |
| 角色管理接口 | 管理端 CRUD，权限 `system:role`，创建不记录所属用户 | `AiRoleController`、`CrudResourceProviderConfiguration` |
| 用户获得角色 | 创建默认助理时复制模板的 `ai_assistant_role`，之后无用户侧维护入口 | `JpaAssistantProvisioningAdapter` |
| 助理加载角色 | 读取已启用绑定，要求恰有一个默认；任一角色非 `active` 即抛错，整个助理不可用 | `JpaAssistantDefinitionAdapter` |
| 角色路由 | 仅在已绑定角色中选择：显式技能唯一命中 > 模型按角色摘要选择 > 默认角色 | `DefaultRoleSelector` |
| 技能引用 | `skill_ids` 存 `skillKey`，保存时不校验可见性 | `RoleSkillBindingJson`、`AiRoleService` |
| 工具引用 | `tool_whitelist` 存工具名，保存时不校验登记与启用状态 | `AiRoleService` |
| 技能可见性 | `PRIVATE / WORKSPACE / PUBLIC / INTERNAL`，并有 `built_in` | `ai_skill_definition` |

## 方案取舍

| 维度 | 方案 A：自动可用 + 覆盖表 | 方案 B（采用）：模板初始绑定 + 用户自维护绑定 |
|---|---|---|
| 改动范围 | 重写助理解析与 `ToolPolicy` 构建，影响核心链路 | 解析链路只做容错，绑定表仍是唯一事实源 |
| 路由候选规模 | 随内置、公开角色增长，模型路由的 Token 与准确率受影响 | 由用户绑定数量决定，有上限，可控 |
| 授权语义 | 隐式（可见即可用） | 显式（绑定即授权），审计与排查直观 |
| 系统受管助理 | 需要特判不继承 | 本来只用显式绑定，无需特判 |
| 新增内置角色 | 自动到达所有人 | 不自动到达，用户在角色库自行绑定 |

采用方案 B：候选可控、授权显式、改动小。唯一缺口是新增内置角色不会自动到达已有用户，见待确认项。

## 目标模型

### 角色可见性

`ai_role` 新增 `visibility VARCHAR(16) NOT NULL`，取值 `SYSTEM / PUBLIC / PRIVATE`（`WORKSPACE` 预留不启用）。

| 可见性 | 谁创建 | 谁可见与可绑定 | 谁可改删 | 可引用的技能 | 可引用的工具 |
|---|---|---|---|---|---|
| SYSTEM | 平台（种子 / 管理员） | 所有用户 | 管理员 | 内置，或 PUBLIC，或 INTERNAL | 目录中已启用的工具 |
| PUBLIC | 管理员发布 | 所有用户 | 管理员 | 同上 | 同上 |
| PRIVATE | 普通用户 | 仅所有者 | 所有者 | PUBLIC / INTERNAL / 内置，加上所有者自己的私有技能 | 目录中已启用的工具，不按风险等级限制（已确认） |

约束：

- `PRIVATE` 必须有 `owner_id`；`SYSTEM / PUBLIC` 的创建人记录在 `create_by`，不占用所有权语义。
- `code` 仍全局唯一且创建后不可改。私有角色 code 由服务端生成 `user.role.{ownerId}.{slug}`，禁止用户使用 `system.role.` 前缀。
- 存量回填：`code LIKE 'system.role.%'` 回填 `SYSTEM`，其余存量人工确认后回填。

### 绑定关系

`ai_assistant_role` 继续是助理 ↔ 角色的唯一事实源，语义不变：有记录即授权。新增的是用户侧的维护规则：

| 规则 | 说明 |
|---|---|
| 可绑定的角色 | 角色库范围：`SYSTEM ∪ PUBLIC ∪ 自己的 PRIVATE`，且 `status=active` |
| 可维护的助理 | 仅 `USER_COPY / USER_OWNED` 且归属当前用户；`SYSTEM_MANAGED`（客服等）拒绝用户修改 |
| 数量上限 | 单个助理已绑定角色总数不超过系统参数 `ai.assistant_max_roles`（`sys_config`，默认 10，管理员可调），含模板初始绑定；同时用来控制路由的候选规模。私有角色的创建数量不限 |
| 默认角色 | 有且仅有一个；不可解绑；切换默认在一个事务内完成，保证始终恰有一个 |
| 重复绑定 | 同一助理同一角色只能有一条（沿用 `(assistant_id, role_id)` 唯一约束） |
| 越权角色 | 他人私有角色、不可见角色一律返回不存在，不泄露存在性 |

创建默认助理时仍复制模板绑定与默认角色，之后由用户维护；模板变更不回写已有用户。新增内置角色只需挂到系统用户模板助理（`ai_assistant_role` 的 `assistant_id=1`），新用户创建助理时自动带上；已有用户在项目未部署阶段通过重建库获得，不做回填。

### 引用完整性校验

新增 `RoleReferenceValidator`，在角色创建、更新、发布时调用，规则如下。

技能引用（按 `skill_ids` 中每个 `skillKey`）：

| 角色可见性 | 允许 |
|---|---|
| SYSTEM / PUBLIC | `built_in=TRUE`，或 `visibility ∈ {PUBLIC, INTERNAL}`，且技能存在、有已批准的当前版本 |
| PRIVATE | 上述，加上 `owner_id = 角色所有者` 的私有技能 |
| 任意角色 | **不得引用 `WORKSPACE` 技能**（本轮不引入工作区维度，见"工作区与组织范围"） |

工具引用（按 `tool_whitelist` 中每个工具名）：

| 条件 | 允许 |
|---|---|
| 目录登记且 `enabled` | 是（所有可见性均不按风险等级限制） |
| 目录未登记或已停用 | 否 |
| 工具声明了已登记的权限码但所有者不持有 | 否（与 `ToolVisibilityFilter` 同口径，保存时按所有者校验；SYSTEM / PUBLIC 角色由管理员保存，不按持有者校验） |

校验失败返回结构化错误，列出越权的 `skillKey` / 工具名，不做部分保存，不降级放行。

### 反向引用保护

`SkillService` 在删除、下线当前版本、或把可见性从 `PUBLIC` 收窄为 `PRIVATE / WORKSPACE` 前，查询所有引用该 `skillKey` 的角色（含私有角色）。存在引用则**直接拒绝**，不做级联清理，提示"需先解除关联"：

- 引用方是 `SYSTEM / PUBLIC` 角色或所有者自己的私有角色：返回角色 code 与名称，由操作者去解除。
- 引用方是他人的私有角色：只返回数量，不泄露其身份和内容。
- 可见性收窄只在会使引用方违规时拦截：被 `SYSTEM / PUBLIC` 角色或他人私有角色引用时拒绝；仅被所有者自己的私有角色引用时放行。

工具被停用时不做拦截（运行时已由 `RegistryToolPortAdapter.resolveOne` 明确失败），仅在管理端提示引用它的角色。

### 助理加载容错

`JpaAssistantDefinitionAdapter` 构建助理时：

- 绑定的角色不存在、非 `active`、或对该助理所有者已不可见（如私有角色被他人持有、公开角色被下架）：**非默认**绑定跳过并记录决策；该角色的工具不进入 `ToolPolicy`。
- **默认角色**失效：明确失败并提示切换默认角色，不静默换角色。
- 过滤后没有任何可用角色，同样明确失败。

这替换了现在"任一角色非 `active` 即整体抛错"的行为。

### 运行时再校验

保存与绑定时的校验可能因后续变更失效，所以执行时再校验一次（执行用户 = 助理所有者）：

- 技能对当前用户不可见：该技能不激活，记录决策事件；若来自 `required` 工具要求则按既有语义在执行前明确失败。
- 工具已停用：由现有 `RegistryToolPortAdapter.resolveOne` 抛出，保持不变。

### 接口

| 接口 | 对象 | 授权 | 说明 |
|---|---|---|---|
| `/api/ai/roles`（现有） | SYSTEM / PUBLIC | `system:role`（管理员） | 管理端 CRUD 与发布，写入前跑引用校验；列表增加可见性筛选；CRUD 资源 `ai.ai-role` 的租户范围改为 `GLOBAL` |
| `/api/ai/my/roles`（新增，普通 Controller，不走 CRUD 管线） | PRIVATE | 登录用户，仅所有者 | 可见性与所有权由 `RoleVisibility` 判断；列表只返回自己的；创建时由服务端写入 `visibility=PRIVATE`、`owner_id`、生成 code；更新和删除校验所有者 |
| `GET /api/ai/roles/library`（新增，普通 Controller，只读） | 可绑定角色 | 登录用户 | 由 `RoleVisibility` 取 `SYSTEM ∪ PUBLIC ∪ 自己的 PRIVATE`（`active`）；分组（内置 / 公开 / 我的），标注是否已绑定到当前助理 |
| `POST /api/ai/my/assistants/{assistantId}/roles` | 绑定 | 助理所有者 | 请求体 `roleId`；校验可见性、`active`、上限、助理类型 |
| `DELETE /api/ai/my/assistants/{assistantId}/roles/{roleId}` | 解绑 | 助理所有者 | 默认角色拒绝解绑 |
| `PUT /api/ai/my/assistants/{assistantId}/roles/{roleId}` | 排序 / 启停 / 设默认 | 助理所有者 | `sort_order`、`enabled`、`is_default`；设默认时事务内切换 |

要点：

- 创建与更新请求体**不接受** `visibility`、`owner_id`、`code` 前缀，全部由服务端决定。
- 所有按 ID 的读写先校验可见性与所有权，越权返回 `NOT_FOUND`。
- 私有角色数量不设上限。
- 删除私有角色：软删除，并解除所有者助理上的绑定；他人不可能绑定它，无需处理他人。

### 发布与下架

- `PRIVATE → PUBLIC` 由管理员发起（从用户角色复制为新的 PUBLIC 角色，不改变原私有角色），复制时跑 `PUBLIC` 的引用校验；引用了私有技能则发布失败，提示先把技能公开。
- 下架 PUBLIC 角色：`status=disabled`。已绑定该角色的用户助理在加载时跳过它（见加载容错），角色库中不再出现。
- 不做用户直接把私有角色转为公开，避免绕过审核。

### 前端

- 角色库：按"内置 / 公开 / 我的"分组，显示是否已绑定，支持绑定 / 解绑，显示"已绑定 n / 上限"。
- 我的助理角色列表：排序、启停、设默认。
- "我的角色"管理页：名称、描述、技能选择（仅可选自己可见的技能）、工具选择（仅可选允许的工具），错误按 `skillKey` / 工具名精确提示。
- 管理端角色页遵循"管理后台统一 EntityDef"，新增可见性列与发布动作写入 `sys_entity_def` 的 `actions`，不手写页面。

## 工作区与组织范围

已确认：本轮角色、技能引用、工具均不按工作区限制。

- `WORKSPACE` 可见性不启用，字段值预留；角色保存和绑定时不读取当前工作区。
- 助理是用户级资源（`ai_assistant.user_id`），角色可用性由"可见性 + 绑定"决定，与用户当前切换到哪个工作区无关，避免同一角色随工作区切换忽隐忽现。
- 工具目录 `ai_tool_catalog.tool_name` 全局唯一，不按工作区区分；角色引用工具只校验"已登记且启用"。
- 技能已存在 `WORKSPACE` 可见性：任何角色都不得引用 `WORKSPACE` 技能（见引用校验表）。角色在助理运行时被加载，那时未必有请求头里的工作区上下文（异步、定时任务），引用结果不能依赖它。

组织与行级过滤（已读代码核实，未运行验证）：

平台对一条实体查询有三层相互独立的过滤机制。本故事对角色的处理是：前两层与角色解耦，行级可见性固定在代码里，不使用第三层。

| 层 | 机制 | 对 `ai_role` 现状的影响 | 本故事的处理 |
|---|---|---|---|
| 1 | Hibernate 过滤器 `org_id = :orgId`，`OrgFilterAspect` 对所有 `JpaRepository` 查询启用；无组织上下文 fail-closed；`@OrgIgnore` 实体豁免 | `Role` 未标 `@OrgIgnore`，系统角色 `org_id` 为 NULL，`NULL = :orgId` 永不成立，查询方法读不到（按 ID 直接加载不受影响） | `Role` 标注 `@OrgIgnore` |
| 2 | CRUD 资源的静态 `TenantScope`：生成租户 Specification，写入后 `validateTenant` 再校验 | 管理端 `ai.ai-role` 目前使用 `ORG_SHARED_WORKSPACE_OPTIONAL`（`org_id = 当前组织`），全局角色被排除；写入时组织不一致会 FORBIDDEN | 管理端角色 CRUD 资源改用 `TenantScope.GLOBAL`（要求 `org_id`、`workspace_id` 均为 NULL） |
| 3 | 记录规则（`sys_data_access_rule`），由 `CrudDataAuthorizationProvider` 编译为 Specification | `ai-role` 没有规则 | **不使用**，行级可见性固定在代码里，见下节 |

由此产生的约束：

- **所有角色的 `org_id`、`workspace_id` 恒为 NULL**（包括 PRIVATE）。`TenantScope.GLOBAL` 的 `validateTenant` 在写入时要求二者为空，因此不能再把创建人的组织记录在 `org_id` 里。创建人信息用 `owner_id` / `create_by` 即可。
- 角色可见性与归属完全由 `visibility` 与 `owner_id` 决定，不再有组织维度。这与"本轮不引入组织 / 工作区维度"一致。
- `ORG` 可见性（组织内共享）只在 `CHECK` 中预留值，保存时拒绝。将来启用需要扩展第 2 层：`TenantScope` 现有选项无法表达"`org_id` 为空，或属于用户所在组织"，需新增选项或为角色资源单独写租户条件。
- `AiAssistantRole` 已标注 `@OrgIgnore`，保持不变。

### 可见性判断固定在代码

已确认：角色的行级可见性不依赖记录规则，而是固定在代码里，由统一的 `RoleVisibility` 负责。

理由：

- 记录规则是数据，管理员可以改，规则缺失时平台直接放行（不限制行），一次误删就会让私有角色泄露；可见性是安全边界，不应依赖可被配置的数据。
- 记录规则只作用于 CRUD 管线，助理加载、绑定校验、引用校验、运行时再校验这些内部路径本来就必须靠代码判断。两套逻辑并存要长期保持同步，容易分叉。
- 规则没有 `is null` 运算符、一条 `allow` 同时作用于读和写，表达角色的可见性与读写分离并不顺手。

做法：

- `RoleVisibility` 是唯一定义：给定用户与角色，判断能否看见、能否绑定、能否修改；也提供仓储查询条件（`visibility in (SYSTEM, PUBLIC)` 或 `owner_id = 当前用户`，且 `status` 满足要求）。
- 所有读取 `Role` 的入口都通过它：用户面接口（`/api/ai/my/roles`、`/api/ai/roles/library`）是普通 Controller 加 Service，不走 `BaseCrudService` 管线；助理加载、绑定接口、引用校验、运行时再校验同样调用它。
- 移除对外暴露的无条件读取方法（`findAll`、`findByCode` 等）；`AiRoleService.listRoles` 现有的 `findAll()` 一并替换。
- 越权统一返回不存在，不泄露存在性。
- 管理端 `ai.ai-role` 仍是 CRUD 资源，由 `system:role` 功能权限控制，超级管理员可管理全部角色，不设记录规则。
- 纵深防御：私有角色在仓储层和服务层按 `owner_id` 各校验一次。

## 数据模型变更

| 变更 | 说明 |
|---|---|
| `ai_role.visibility` | 新增，非空，`CHECK (visibility IN ('SYSTEM','PUBLIC','PRIVATE','WORKSPACE'))` |
| `ai_role` 约束 | `CHECK (visibility <> 'PRIVATE' OR owner_id IS NOT NULL)` |
| 索引 | `(visibility, status)`、`(owner_id) WHERE visibility='PRIVATE'` |
| `ai_assistant_role` | 表结构不变，沿用 `enabled / is_default / sort_order / (assistant_id, role_id)` |
| 种子 | 系统角色回填 `SYSTEM`；新增内置角色同时挂到系统用户模板；`sys_config` 新增 `ai.assistant_max_roles`（默认 10） |

SQL 落地方式（已确认）：直接修改现有 `v2__ai_schema.sql` 与 `v12__init_seed_data.sql`，不新增迁移文件（项目未部署，与 AAF-117 做法一致）。
`ai.assistant_max_roles` 需要在 `SysConfigKeys.Ai` 登记常量（`aaf-common`，改 common 需协调者评估），读取走 `configService.getInteger(key, 10)`，与 `ai.token_markup_rate` 同一做法。

## 风险与取舍

| 风险 | 说明 | 处理 |
|---|---|---|
| 新增内置角色不会自动到达已有用户 | 已有用户不含新角色 | 新增内置角色挂到系统用户模板，新用户创建时自动带上；项目未部署，已有用户通过重建库获得 |
| 私有角色可引用高风险工具 | 已确认不按风险等级限制，普通用户可在私有角色里配置 `HIGH / CRITICAL` 工具（如 `script.execute.javascript`） | 保存仍要求工具已登记启用、权限码按所有者校验；调用期的权限码、确认要求（`require_confirm`）与 `ToolPolicy` 门控不变；后续如出现滥用，再按风险等级收紧 |
| 绑定接口越权 | 绑定他人私有角色、修改他人助理、修改系统受管助理 | 服务端统一校验可见性与所有权，越权返回不存在；系统受管助理一律拒绝 |
| 默认角色被破坏 | 解绑或失效导致助理无默认角色 | 解绑拒绝；切换在事务内；加载时默认角色失效明确失败 |
| 路由成本随绑定数量增加 | 候选越多模型路由越慢越贵 | 绑定上限由系统参数控制，默认 10 |
| 助理加载容错改变 | 从"整体抛错"改为"跳过非默认失效角色" | 记录决策事件，默认角色与无可用角色仍明确失败 |
| 存量数据 | 存量角色 `owner_id` 为空 | 全部为 `system.role.*`，回填 SYSTEM |
| 去掉组织过滤后私有角色泄露 | `Role` 标注 `@OrgIgnore` 后，组织过滤器不再保护私有角色，任何无条件读取都可能越权 | 角色读取入口统一走带可见性与所有者条件的仓储方法；移除对外暴露的 `findAll` / `findByCode`；越权访问返回不存在 |

## 已确认决策

- SQL 落地方式：直接修改现有 `v2__ai_schema.sql` 与 `v12__init_seed_data.sql`，不新增迁移文件。
- 绑定上限：默认 10，通过系统参数 `ai.assistant_max_roles` 配置；私有角色创建数量不限。
- 私有角色可引用工具的风险等级：不限。
- 新增内置角色：挂到系统用户模板，新用户创建时自动带上，不做对已有用户的回填。
- 被角色引用的技能：无法删除或下线，需先解除关联；不做级联清理。
- 工作区：本轮角色、工具不按工作区限制，`WORKSPACE` 可见性不启用；任何角色不得引用 `WORKSPACE` 技能。

## 待人工确认

- 无。设计审核通过后进入编码。
