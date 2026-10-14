---
level: Practice
layer: Model
purpose: AAF-118 Role 可见性与用户自维护绑定的技术任务
status: draft
version: 2.0.0
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
> 负责人：architect 拆分 | 创建：10-14 | 更新：10-14（改为模板初始绑定 + 用户自维护绑定）

## 任务约束

- 涉及权限与数据模型，[设计](design.md)经人工审核通过后才能编码；设计与任务冲突时先改设计。助理解析链路只做容错改动，不重写。
- 项目未发布：直接替换，不保留旧路径，不加 fallback 或兼容层。
- SQL 已确认：直接修改现有 `v2__ai_schema.sql` 与 `v12__init_seed_data.sql`，不新增迁移文件。
- 已确认决策：绑定上限默认 10，通过系统参数 `ai.assistant_max_roles` 配置；私有角色数量不限；私有角色可引用的工具风险等级不限；新增内置角色挂到系统用户模板，新用户创建时自动带上，不回填已有用户；被角色引用的技能无法删除或下线，需先解除关联。
- 不新增非必要单元测试；安全边界（引用校验、所有权校验、系统受管助理拒绝修改、默认角色保护）如需用例，仅补最小集。
- 批量修改文件超过 5 个或改接口签名，由协调者评估后再做。
- 编码前需加载 `developer-service`（后端）与 `developer-webui`（前端）的 `resources`。

## 任务列表

### 数据模型

1. ⏳ #11801 `ai_role` 增加可见性与归属约束
   - 新增 `visibility`（`SYSTEM / PUBLIC / PRIVATE`，`WORKSPACE`、`ORG` 预留不启用，保存时拒绝）、`CHECK (visibility <> 'PRIVATE' OR owner_id IS NOT NULL)`、`(visibility, status)` 与私有 `owner_id` 索引；`Role` 实体同步并标注 `@OrgIgnore`；所有角色 `org_id`、`workspace_id` 恒为 NULL（含 PRIVATE）；`ai.ai-role` CRUD 资源租户范围改为 `TenantScope.GLOBAL`；直接修改 `v2__ai_schema.sql`
   - verify: 空库启动成功；插入无 `owner_id` 的 PRIVATE 角色被数据库拒绝；带组织上下文的请求能经 `ai.ai-role` 列出 SYSTEM 角色；写入组织非空的角色被 `validateTenant` 拒绝
2. ⏳ #11802 种子回填内置角色与参数
   - `v12__init_seed_data.sql` 中 `system.role.*`（平台向导、内容创作者、客服专员、通用助理、银龄健康顾问）全部设为 `SYSTEM`；新增内置角色挂到系统用户模板助理；`sys_config` 新增 `ai.assistant_max_roles`（默认 10）；`SysConfigKeys.Ai` 登记常量（改 `aaf-common`，需协调者评估）
   - verify: 重建库后 `ai_role` 无 `visibility` 为空的行；SYSTEM 角色数量与种子一致；新用户创建助理后带有模板上的全部内置角色；管理后台可修改上限

### 引用校验

3. ⏳ #11803 `RoleReferenceValidator`
   - 按设计的"引用完整性校验"规则校验 `skill_ids` 与 `tool_whitelist`；任何角色不得引用 `WORKSPACE` 技能；失败返回结构化错误，列出越权的 `skillKey` / 工具名，不做部分保存
   - 依赖：#11801
   - verify: SYSTEM / PUBLIC 角色引用私有技能被拒；PRIVATE 引用他人私有技能被拒；任何角色引用 `WORKSPACE` 技能被拒；引用未登记或已停用工具被拒；PRIVATE 角色引用 `HIGH / CRITICAL` 工具保存成功（不按风险等级限制）
4. ⏳ #11804 技能变更的反向引用保护
   - `SkillService` 在删除、下线当前版本、或把可见性从 `PUBLIC` 收窄前查询所有引用该 `skillKey` 的角色（含私有角色）；存在引用直接拒绝，提示需先解除关联，不做级联清理；引用方是 `SYSTEM / PUBLIC` 或所有者自己的私有角色时返回 code 与名称，他人私有角色只返回数量
   - 依赖：#11801
   - verify: 被任何角色引用的技能无法删除或下线；被 PUBLIC 角色或他人私有角色引用的 PUBLIC 技能无法改为私有；仅被所有者自己的私有角色引用时可收窄；错误中不泄露他人私有角色身份

### 角色接口

5. ⏳ #11805 管理端角色接口接入可见性与引用校验
   - `/api/ai/roles` 创建 / 更新写入前调用 #11803；列表增加可见性筛选；保存时由服务端设置 `visibility`，请求体不接受该字段
   - 依赖：#11803
   - verify: 管理员创建 SYSTEM / PUBLIC 角色成功；引用私有技能保存失败；非管理员访问被拒
6. ⏳ #11806 用户私有角色接口
   - 新增 `/api/ai/my/roles`（普通 Controller 加 Service，不走 CRUD 管线；可见性与所有权通过 `RoleVisibility` 判断）：列表仅返回自己的；创建时服务端写 `visibility=PRIVATE`、`owner_id`、生成 `user.role.{ownerId}.{slug}`，`org_id`、`workspace_id` 保持 NULL；更新 / 删除校验所有者，越权返回 `NOT_FOUND`；私有角色数量不限；删除时软删并解除所有者助理上的绑定
   - 依赖：#11803、#11814
   - verify: 用户 B 无法通过 ID 或 code 读取、修改、删除用户 A 的私有角色；请求体传 `visibility` / `owner_id` 被忽略或拒绝

### 绑定维护

7. ⏳ #11807 角色库查询接口
   - 新增 `GET /api/ai/roles/library`（普通 Controller 加 Service，只读，通过 `RoleVisibility` 取数）：返回 `SYSTEM ∪ PUBLIC ∪ 自己的 PRIVATE` 且 `active` 的角色，分组（内置 / 公开 / 我的），标注是否已绑定到当前助理
   - 依赖：#11801、#11806、#11814
   - verify: 用户 A 看不到用户 B 的私有角色；已绑定标记与 `ai_assistant_role` 一致；下架的角色不出现
8. ⏳ #11808 绑定、解绑、排序、设默认接口
   - 新增 `POST / DELETE / PUT /api/ai/my/assistants/{assistantId}/roles[...]`：校验助理归属且为 `USER_COPY / USER_OWNED`、角色可见且 `active`、绑定总数不超过系统参数 `ai.assistant_max_roles`（默认 10）、默认角色不可解绑、设默认在事务内切换保证恰有一个
   - 依赖：#11807
   - verify: 超过上限绑定被拒；解绑默认角色被拒；切换默认后恰有一个默认；修改他人助理或系统受管助理被拒；绑定他人私有角色返回不存在

### 助理加载与运行时

9. ⏳ #11809 助理加载容错
   - `JpaAssistantDefinitionAdapter`：非默认绑定的角色不存在、非 `active` 或对所有者不可见时跳过并记录决策，其工具不进入 `ToolPolicy`；默认角色失效或过滤后无可用角色则明确失败并提示切换默认
   - 依赖：#11801
   - verify: 下架一个非默认角色后，用户助理仍可正常对话且有跳过决策记录；默认角色下架后对话明确失败
10. ⏳ #11810 运行时再校验技能可见性
    - 执行时对角色引用的技能做可见性再校验：不可见技能不激活并记录决策事件；`required` 工具要求来源的技能不可见则按既有语义在执行前明确失败
    - 依赖：#11809
    - verify: 私有技能被收回后，使用该私有角色发起对话，不可见技能未被激活且有决策事件

### 发布

11. ⏳ #11811 PUBLIC 角色发布与下架
    - 管理员从用户私有角色复制为新的 PUBLIC 角色，复制时按 PUBLIC 规则校验引用；下架设 `status=disabled`，已绑定用户在加载时按 #11809 跳过
    - 依赖：#11803、#11805、#11809
    - verify: 引用私有技能的私有角色发布失败并提示；发布后角色库对所有用户可见；下架后角色库不再出现，已绑定用户对话不受影响

### 可见性判断

14. ⏳ #11814 统一可见性判断 `RoleVisibility`
    - 一个类定义"某用户能否看到 / 绑定 / 修改某角色"，并提供仓储查询条件（`visibility in (SYSTEM, PUBLIC)` 或 `owner_id = 当前用户`，按需要过滤 `status`）；所有读取 `Role` 的入口统一通过它（用户面接口、助理加载、绑定接口、引用校验、运行时再校验）；移除对外暴露的无条件 `findAll` / `findByCode`（`AiRoleService.listRoles` 的 `findAll()` 一并替换）；不使用记录规则；私有角色在仓储层与服务层按 `owner_id` 各校验一次；越权返回不存在
    - 依赖：#11801
    - verify: 用户 B 经任何读取入口（用户面接口、助理加载、绑定校验）都拿不到用户 A 的私有角色；SYSTEM / PUBLIC 对所有用户可见；代码里不再有无条件读取 `Role` 的对外方法

### 前端与文档

12. ⏳ #11812 角色库与"我的角色"页面
    - 角色库按"内置 / 公开 / 我的"分组，显示是否已绑定和"已绑定 n / 上限"，支持绑定 / 解绑；助理角色列表支持排序、启停、设默认；"我的角色"管理页中技能和工具只列可选项，错误按 `skillKey` / 工具名精确提示；管理端角色页通过 `sys_entity_def` 增加可见性列与发布动作，不手写页面
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

## 待人工确认

- 无。设计审核通过后进入编码。
