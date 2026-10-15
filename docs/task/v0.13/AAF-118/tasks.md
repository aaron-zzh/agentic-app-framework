---
level: Practice
layer: Model
purpose: AAF-118 Role 可见性与用户自维护绑定的技术任务
status: active
version: 2.1.0
date: 2026-10-14
author: AaronZZH & Kiro
tags:
  - AAF-118
  - Role
  - 技术任务
related:
  - requirement.md
  - design.md
  - ../AAF-117/tasks.md
---

# 技术任务：Role 可见性与用户自维护绑定（AAF-118）

> 需求：[requirement.md](requirement.md)　设计：[design.md](design.md)
> 负责人：architect 拆分 | 创建：10-14 | 更新：10-14（设计已确认；按代码核对补充管理端权限、发布接口、读取入口、JSONB 反查）

## 任务约束

- 涉及权限与数据模型，[设计](design.md)已于 2026-10-14 确认，可按任务开工；设计与任务冲突时先改设计。助理解析链路只做容错改动，不重写。
- 项目未发布：直接替换，不保留旧路径，不加 fallback 或兼容层。
- SQL 已确认：直接修改现有 `v2__ai_schema.sql` 与 `v12__init_seed_data.sql`，不新增迁移文件。
- 已确认决策：绑定上限默认 10，通过系统参数 `ai.assistant_max_roles` 配置；私有角色数量不限；私有角色可引用的工具风险等级不限；新增内置角色挂到系统用户模板，新用户创建时自动带上，不回填已有用户；被角色引用的技能无法删除或下线，需先解除关联。
- 不新增非必要单元测试；安全边界（引用校验、所有权校验、系统受管助理拒绝修改、默认角色保护）如需用例，仅补最小集。
- 批量修改文件超过 5 个或改接口签名，由协调者评估后再做。
- 编码前需加载 `developer-service`（后端）与 `developer-webui`（前端）的 `resources`。

## 任务列表

### 数据模型

1. ✅ #11801 `ai_role` 增加可见性与归属约束
   - 新增 `visibility`（`SYSTEM / PUBLIC / PRIVATE`，`WORKSPACE`、`ORG` 仅在 `CHECK` 中预留，服务端保存时拒绝）、`CHECK (visibility <> 'PRIVATE' OR owner_id IS NOT NULL)`、`(visibility, status)` 与私有 `owner_id` 索引；`skill_ids` 由 `TEXT` 改为 `JSONB` 并建 GIN 索引（`jsonb_path_ops`），`Role.skillIds` 按 `AiModel.stepRatios` 的做法映射（`@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"`，字段仍为 `String`），开工时全量扫描 `skill_ids` 的读写点；`Role` 实体同步并标注 `@OrgIgnore`；所有角色 `org_id`、`workspace_id` 恒为 NULL（含 PRIVATE）；`ai.ai-role` CRUD 资源租户范围改为 `TenantScope.GLOBAL`；直接修改 `v2__ai_schema.sql`
   - verify: 空库启动成功；插入无 `owner_id` 的 PRIVATE 角色被数据库拒绝；带组织上下文的请求能经 `ai.ai-role` 列出 SYSTEM 角色；写入组织非空的角色被 `validateTenant` 拒绝；`skill_ids @> '[{"skillKey":"x"}]'` 能命中且走 GIN 索引；种子角色与现有 `RoleStoreImpl`、`JpaRoleDefinitionAdapter`、`JpaAssistantDefinitionAdapter` 的解析仍通过
2. ✅ #11802 种子回填内置角色与参数
   - `v12__init_seed_data.sql` 中 `system.role.*`（平台向导、内容创作者、客服专员、通用助理、银龄健康顾问）全部设为 `SYSTEM`；新增内置角色挂到系统用户模板助理；`sys_config` 新增 `ai.assistant_max_roles`（默认 10）；`SysConfigKeys.Ai` 登记常量（改 `aaf-common`，需协调者评估）
   - verify: 重建库后 `ai_role` 无 `visibility` 为空的行；SYSTEM 角色数量与种子一致；新用户创建助理后带有模板上的全部内置角色；管理后台可修改上限

### 引用校验

3. ✅ #11803 `RoleReferenceValidator`
   - 按设计的"引用完整性校验"规则校验 `skill_ids` 与 `tool_whitelist`；任何角色不得引用 `WORKSPACE` 技能；失败返回结构化错误，列出越权的 `skillKey` / 工具名，不做部分保存
   - 依赖：#11801、#11814
   - verify: SYSTEM / PUBLIC 角色引用私有技能被拒；PRIVATE 引用他人私有技能被拒；任何角色引用 `WORKSPACE` 技能被拒；引用未登记或已停用工具被拒；工具声明了权限码但 PRIVATE 角色所有者不持有时被拒（SYSTEM / PUBLIC 由管理员保存，不按持有者校验）；PRIVATE 角色引用 `HIGH / CRITICAL` 工具保存成功（不按风险等级限制）；校验类放 `aaf-api` 的 `module.ai.role`
4. ✅ #11804 技能变更的反向引用保护
   - `SkillService` 中会让引用失效的入口在改动生效前查询所有引用该 `skillKey` 的角色（含私有角色），存在引用直接拒绝，提示需先解除关联，不做级联清理：`beforeDelete`；`validateUpdate`（`beforeUpdate`、`updateVersioned`、`updateVersionedGovernance` 共用）中的可见性收窄与修改 `code`；经核对代码，技能版本不可变、`publish` 只切换当前版本、不存在退役当前版本的入口，所以这两项不拦截（见设计）；引用方是 `SYSTEM / PUBLIC` 或所有者自己的私有角色时返回 code 与名称，他人私有角色只返回数量；反查用 `AiRoleRepository` 的 `skill_ids @> '[{"skillKey":"x"}]'` 查询（依赖 #11801 的 JSONB 与 GIN 索引）
   - 依赖：#11801
   - verify: 被任何角色引用的技能无法删除；被 PUBLIC 角色或他人私有角色引用的 PUBLIC 技能无法改为私有；仅被所有者自己的私有角色引用时可收窄；错误中不泄露他人私有角色身份；经管理端 `*Governance` 路径同样被拦截；被引用的技能可以发布新版本

### 角色接口

5. ✅ #11805 管理端角色接口接入可见性与引用校验
   - `/api/ai/roles` 的真实入口是 `AiRoleCrudService`（`BaseCrudService`）：在 `toEntity / updateEntity` 调用 #11803；请求体接受 `visibility`，仅允许 `SYSTEM / PUBLIC`，传 `PRIVATE` 及预留值拒绝；`code` 前缀 `system.role.` / `public.role.`，禁止 `user.role.`；对 PRIVATE 角色只允许调整 `status`（下架或恢复），编辑与删除拒绝；列表增加可见性筛选；删除没有任何引用的 `AiRoleService`
   - 依赖：#11803、#11814
   - verify: 管理员创建 SYSTEM / PUBLIC 角色成功；创建时传 `PRIVATE` 被拒；引用私有技能保存失败；管理员可下架私有角色，但改名、改技能、删除被拒；非管理员访问被拒；`grep AiRoleService` 在 `apps/` 下无结果
6. ✅ #11806 用户私有角色接口
   - 新增 `/api/ai/my/roles`（普通 Controller 加 Service，不走 CRUD 管线；可见性与所有权通过 `RoleVisibility` 判断）：列表仅返回自己的；创建时服务端写 `visibility=PRIVATE`、`owner_id`、生成 `user.role.{ownerId}.{slug}`，`org_id`、`workspace_id` 保持 NULL；更新 / 删除校验所有者，越权返回 `NOT_FOUND`；私有角色数量不限；删除时软删并解除所有者助理上的绑定，若它是所有者助理的默认绑定则拒绝删除并提示先切换默认角色
   - 依赖：#11803、#11814
   - verify: 用户 B 无法通过 ID 或 code 读取、修改、删除用户 A 的私有角色；请求体传 `visibility` / `owner_id` 被忽略或拒绝；删除作为默认绑定的私有角色被拒；删除非默认绑定的私有角色后绑定被解除

### 绑定维护

7. ✅ #11807 角色库查询接口
   - 新增 `GET /api/ai/roles/library`（普通 Controller 加 Service，只读，通过 `RoleVisibility` 取数）：可选参数 `assistantId`，缺省取当前用户默认助理，传入时校验归属；返回 `SYSTEM ∪ PUBLIC ∪ 自己的 PRIVATE` 且 `active` 的角色，分组（内置 / 公开 / 我的），标注是否已绑定到该助理
   - 依赖：#11801、#11806、#11814
   - verify: 用户 A 看不到用户 B 的私有角色；已绑定标记与 `ai_assistant_role` 一致；下架的角色不出现；传入他人的 `assistantId` 返回不存在
8. ✅ #11808 绑定、解绑、排序、设默认接口
   - 新增 `POST / DELETE / PUT /api/ai/my/assistants/{assistantId}/roles[...]`：校验助理归属且为 `USER_COPY / USER_OWNED`（`user_id=0` 的 `SYSTEM_MANAGED` 一律拒绝）、角色可见且 `active`、绑定总数不超过系统参数 `ai.assistant_max_roles`（默认 10）、默认角色不可解绑也不可停用（`enabled=false` 拒绝）、设默认在事务内切换保证恰有一个
   - 依赖：#11807
   - verify: 超过上限绑定被拒；解绑默认角色被拒；停用默认角色被拒；切换默认后恰有一个默认；修改他人助理或系统受管助理被拒；绑定他人私有角色返回不存在

### 助理加载与运行时

9. ✅ #11809 助理加载容错
   - `JpaAssistantDefinitionAdapter`：非默认绑定的角色不存在、非 `active` 或对所有者不可见时跳过并记录决策，其工具不进入 `ToolPolicy`；默认角色失效或过滤后无可用角色则明确失败并提示切换默认
   - 依赖：#11801、#11814
   - verify: 下架一个非默认角色后，用户助理仍可正常对话且有跳过决策记录；默认角色下架后对话明确失败；管理员下架的私有角色，其所有者助理按同一规则处理
10. ✅ #11810 运行时再校验技能可见性
    - 做法：`SkillStoreImpl` 的 `findByCode / findBySkillId / findSummaryByCode / findSummaryBySkillId` 通过 `OperatorContext.currentOwnerId()` 取当前用户，复用 `SkillAccessQuery.SkillAccess` 的可见性规则：全员可引用的放行，PRIVATE 仅所有者，WORKSPACE 不放行；无用户上下文时 fail-closed。不改端口签名，与 `RoleStoreImpl` 同一模式
    - `currentOwnerId()` 已覆盖请求线程、`ScheduledTaskExecutor`（`TaskExecutionContextHolder`）与各 `runAsOwner` 异步入口
    - 未做：不可见技能没有写决策事件（与 #11809 一致，目前只在查询层按不存在处理）
    - 依赖：#11809、#11814
    - verify: 私有技能被收回后，使用该私有角色发起对话，不可见技能未被激活

### 发布

11. ✅ #11811 PUBLIC 角色发布与下架
    - 新增 `POST /api/ai/roles/actions/publish`（`system:role:create`；通用 EntityAction 只提交 `{id}`，所以请求体为 `{id, code?, name?}`，`code` 缺省由服务端生成 `public.role.` 前缀）：管理员从用户私有角色复制为新的 PUBLIC 角色，复制时按 PUBLIC 规则校验引用，每次发布生成新副本，不记录来源角色；下架经现有 CRUD 把 `status` 改为 `disabled`，已绑定用户在加载时按 #11809 跳过
    - 依赖：#11803、#11805、#11809
    - verify: 引用私有技能的私有角色发布失败并提示；发布后角色库对所有用户可见；原私有角色不变；下架后角色库不再出现，已绑定用户对话不受影响；`code` 使用 `system.role.` 或 `user.role.` 前缀被拒

### 可见性判断

14. ✅ #11814 统一可见性判断 `RoleVisibility`
    - 一个类定义"某用户能否看到 / 绑定 / 修改某角色"，并提供仓储查询条件（`visibility in (SYSTEM, PUBLIC)` 或 `owner_id = 当前用户`，按需要过滤 `status`）；放 `aaf-framework` 的 `framework.intelligent.assistant.role` 包（`Role` 实体与 `JpaAssistantDefinitionAdapter` 同在 framework）；不使用记录规则；私有角色在仓储层与服务层按 `owner_id` 各校验一次；越权返回不存在。所有读取 `Role` 的入口统一通过它，逐个处理：
      - `AiRoleCrudService`（继承的通用 list / get）：只列 `SYSTEM / PUBLIC` 加只读的 `PRIVATE`
      - `RoleStoreImpl.getSkillCodes / getToolWhitelist`
      - `JpaRoleDefinitionAdapter` / `RoleDefinitionPort.findByCode`：无条件 `findByCode` 删除，改为 `findByCodeAndVisibilityIn(code, [SYSTEM, PUBLIC])`；开工前先核实 `RoleDefinitionPort` 的全部调用方，同步修改 `JpaRoleDefinitionAdapterTest`
      - `JpaAssistantDefinitionAdapter.role()`
      - `AiRoleRepository.findByAssistantIdAndStatus`：加可见性条件，或确认无调用后删除
    - 依赖：#11801
    - verify: 用户 B 经任何读取入口（用户面接口、助理加载、绑定校验）都拿不到用户 A 的私有角色；SYSTEM / PUBLIC 对所有用户可见；代码里不再有无条件读取 `Role` 的对外方法（`findAll`、`findByCode`）；`RoleDefinitionPort` 的调用方行为不变

### 前端与文档

12. ✅ #11812 角色库与"我的角色"页面
    - 角色库按"内置 / 公开 / 我的"分组，显示是否已绑定和"已绑定 n / 上限"，支持绑定 / 解绑；助理角色列表支持排序、启停、设默认（默认角色的停用与解绑置灰并提示）；"我的角色"管理页中技能和工具只列可选项（工具选项来自 `GET /api/ai/my/roles/tool-options`），错误按 `skillKey` / 工具名精确提示；管理端角色页通过 `sys_entity_def`（追加到现有 `v106__intelligent_governance_entity_def.sql`）增加可见性列与发布动作（调用 `POST /api/ai/roles/actions/publish`），不手写页面；REST 封装放 `lib/api/rest/ai/role.ts`，页面为 `settings/assistant-roles`，由 `features/settings/assistant-roles/` 组装
    - 依赖：#11805、#11806、#11807、#11808
    - verify: 普通用户看不到他人私有角色；达到上限时绑定按钮提示上限；管理端可发布和下架；保存越权引用时界面提示具体项
13. ⏳ #11813 文档与改进意见同步
    - 更新 `action-governance.md` 中角色与工具白名单相关实现态；`improvements.md` 登记"权限码未注册不构成授权"的后续收紧方向，以及"私有角色可引用高风险工具"的滥用监测与按风险等级收紧的后续评估
    - 依赖：#11809
    - verify: 文档实现态与代码一致

## 建议顺序

```text
#11801 → #11802 → #11814 → #11803 → #11804 → #11805 → #11806 → #11807 → #11808 → #11809 → #11810 → #11811 → #11812 → #11813
```

先落字段、校验与接口，再做绑定维护，最后做加载容错与运行时校验。#11809 只改容错分支，可与 #11807、#11808 并行验证。

## 已确认决策

- SQL 落地方式：直接修改现有 `v2__ai_schema.sql` 与 `v12__init_seed_data.sql`，不新增迁移文件。
- 绑定上限：默认 10，系统参数 `ai.assistant_max_roles` 可配置；私有角色创建数量不限。
- 私有角色可引用的工具风险等级：不限。
- 新增内置角色：挂到系统用户模板，新用户创建时自动带上，不回填已有用户。
- 被角色引用的技能：无法删除或下线，需先解除关联。

- 工作区：角色、工具不按工作区限制，`WORKSPACE` 可见性不启用；任何角色不得引用 `WORKSPACE` 技能。
- 管理端：创建接受 `visibility`（仅 `SYSTEM / PUBLIC`）；对 PRIVATE 只读，只允许调整状态（下架或恢复）。
- 发布：`POST /api/ai/roles/actions/publish`（EntityAction 只提交 `{id}`），每次生成新副本，不记录来源角色。
- `findByCode`：改为 `findByCodeAndVisibilityIn(code, [SYSTEM, PUBLIC])`，无条件版本删除。
- 角色库：`assistantId` 可选，缺省取当前用户默认助理。
- 反向引用：`skill_ids` 改 JSONB 加 GIN 索引，用 `@>` 查询。
- 运行时再校验：在 `SkillStoreImpl` 内用 `OperatorContext.currentOwnerId()` 判断技能可见性，不改端口签名。
- 默认角色：不可解绑、不可停用；删除作为默认绑定的私有角色被拒绝。
- 模块归属：`RoleVisibility` 放 `aaf-framework`，`RoleReferenceValidator` 放 `aaf-api`；删除无引用的 `AiRoleService`。

## 待人工确认

- 无。
