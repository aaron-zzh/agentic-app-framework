
---
level: Practice
layer: Model
purpose: AAF-126 角色分类的设计要点与技术任务
status: active
version: 1.0.0
date: 2026-11-09
author: AaronZZH & Kiro
tags:
  - AAF-126
  - Role
  - 角色分类
  - 记录规则
related:
  - ../AAF-118/design.md
  - ../../../guide/development/entity-extension.md
---

# AAF-126 技术任务

风险等级：🔴 高（新表、接口签名新增字段、权限种子）。设计已于 2026-11-09 在对话中经人类确认，先实现后端。

## 背景

角色（`ai_role`）只有 `SYSTEM / PUBLIC / PRIVATE` 可见性，没有分类。角色库只能按可见性分组，养老等垂直场景只能靠 code 前缀区分，用户也无法给自己的私有角色归类。后续智慧农业演示场景需要一组开箱归在"智慧农业"下的内置角色。

目标：

- 系统预置分类由管理员维护，所有用户可见。
- 用户可以创建私有分类，只有本人可见、可改、可删。
- 角色由其所有者归类：内置和公开角色只能归入系统分类；私有角色可以归入系统分类或所有者自己的分类。

## 设计要点

### 方案选择

采用 BaseCrud + 记录规则 + 服务层写入校验，与提示词模板、技能的混合可见性做法一致。AAF-118 没有对 `ai_role` 用记录规则，是因为角色可见性是安全边界且运行时必须靠代码判断；分类只是浏览标签，助理加载与路由不读取，所以可以用记录规则。

### 数据模型

`ai_role_category` 的 JPA 实体命名为 `AiRoleCategory`，避免与未来通用角色分类概念混淆；Service、Controller、Resource、DTO、VO 保持 `RoleCategory*`，与当前 `Role` / `RoleVO` 命名习惯一致。

| 列 | 类型 | 说明 |
|---|---|---|
| `code` | `VARCHAR(64)` | 稳定业务码，系统分类必填，私有分类为空 |
| `name` | `VARCHAR(64) NOT NULL` | 显示名称 |
| `sort_order` | `INTEGER NOT NULL DEFAULT 100` | 升序排序 |
| `visibility` | `VARCHAR(16) NOT NULL` | `SYSTEM` 或 `PRIVATE` |

约束与索引：

- `CHECK (visibility IN ('SYSTEM', 'PRIVATE'))`
- `CHECK (visibility <> 'PRIVATE' OR owner_id IS NOT NULL)`
- `CHECK (visibility <> 'SYSTEM' OR code IS NOT NULL)`
- `code` 部分唯一索引（`deleted = FALSE AND code IS NOT NULL`）
- 系统分类名唯一（`visibility = 'SYSTEM' AND deleted = FALSE`）；私有分类 `(owner_id, name)` 唯一（`visibility = 'PRIVATE' AND deleted = FALSE`）

系统分类以 `visibility` 判断，不以 `owner_id IS NULL` 判断：管理员经 CRUD 创建时框架会把 `owner_id` 填为管理员。VO 对系统分类不返回 `ownerId`。

`ai_role` 新增 `category_id BIGINT NULL REFERENCES ai_role_category(id)` 并建索引，为空表示未分类。

### CRUD 资源

| 项 | 值 |
|---|---|
| 资源键 | `ai.role-category`（记录规则 slug `role-category`） |
| 路径 | `/api/ai/role-categories` |
| 权限命名空间 | `system:role-category` |
| 租户范围 | `TenantScope.GLOBAL` |
| 个人范围 | 不启用（`PersonalScope` 会滤掉系统分类） |
| 操作 | list、get、options、create、update、delete；去掉批量删除、导入、恢复、归档 |

治理 Header `X-AAF-Access-Mode: admin-maintenance` 只由 `RoleCategoryController` 识别，参照 `SkillController.governanceRequested()`，命中时调用服务的治理方法（`*WithAccess(..., ADMIN_MAINTENANCE)`）。

### 读取范围

记录规则（默认访问模式生效，治理模式由框架绕过）：

```json
{"or": [
  {"field": "visibility", "op": "eq", "value": "SYSTEM"},
  {"field": "ownerId", "op": "eq", "value": "$user.id"}
]}
```

`buildSpec(page, accessMode)` 追加条件：

| 访问模式 | 追加条件 | 原因 |
|---|---|---|
| `DEFAULT` | `visibility = SYSTEM OR owner_id = 当前用户` | `super_admin` 绕过记录规则，不加时会混入他人私有分类；规则缺失时也能兜底 |
| `ADMIN_MAINTENANCE` | 只叠加客户端的 `visibility` 筛选 | 治理页默认筛选系统分类，可切换查看私有分类（只读） |

### 写入校验

| 操作 | 默认访问模式（用户） | 治理模式（管理员） |
|---|---|---|
| 创建 | `visibility = PRIVATE`，`owner_id` 为当前用户，`code` 为空 | `visibility = SYSTEM`，`code` 必填 |
| 更新 | 只能改自己的私有分类；系统分类返回无权限 | 只能改系统分类；私有分类拒绝 |
| 删除 | 同更新；删除前清空自己私有角色上的该分类 | 只能删系统分类；被任何角色引用时拒绝并提示数量 |
| 不可改字段 | `visibility`、`code`、`owner_id` | 同左 |

- 创建 DTO 不含 `visibility`，由访问模式决定。`toEntity` 拿不到访问模式，由普通与治理两个创建入口确定可见性（参照 `SkillService.createVersioned / createVersionedGovernance`）。
- 名称重复由唯一索引兜底，服务层先查重给出明确提示。

### 角色归类

- `RoleVO`、`RoleCreateDTO`（管理端）、`MyRoleSaveDTO`（用户端）新增可空的 `categoryId`；`RoleLibraryVO` 不变。
- 分类校验加到 `RoleReferenceValidator`：`SYSTEM / PUBLIC` 角色只能用未删除的系统分类；`PRIVATE` 角色可用系统分类或角色所有者的私有分类。不满足时返回"分类不存在"。
- 发布私有角色为公开角色时：原分类是系统分类则保留，是私有分类则新角色不带分类。
- 助理加载、角色路由不读分类，`RoleStoreImpl`、`JpaAssistantDefinitionAdapter`、`DefaultRoleSelector` 不改。

### 管理后台

- 新增 EntityDef `role-category`：`resource: ai.role-category`，`accessMode: admin-maintenance`，字段 `code`（创建后只读）、`name`、`visibility`（只读）、`ownerId`（只读）、`sortOrder`、`updateTime`；按 `visibility` 筛选，默认 `SYSTEM`，避免管理员默认看到全部用户的私有分类。不加 `customViews` 和 `actions`。默认筛选值的配置方式以 entity-engine 现有支持为准，不支持时在前端任务补齐。
- 角色治理 EntityDef `ai-role` 增加 `categoryId` 字段，选项来自系统分类；引用选择字段写法以 entity-engine 现有支持为准。
- 治理菜单挂在"智能治理"分组，授予与 `system:role` 相同的管理角色。

### 种子

系统分类：

| code | name | sort_order |
|---|---|---|
| `system.role-category.general` | 通用 | 10 |
| `system.role-category.content` | 内容创作 | 20 |
| `system.role-category.elder` | 智慧养老 | 30 |
| `system.role-category.agriculture` | 智慧农业 | 40 |

存量内置角色回填：

| 角色 code | 分类 |
|---|---|
| `system.role.generalist`、`system.role.platform-guide`、`system.role.customer-service` | 通用 |
| `system.role.content-creator` | 内容创作 |
| `system.role.elder-care-advisor`、`system.role.elder-companion`、`system.role.elder-family-service`、`system.role.elder-ops-assistant` | 智慧养老 |

智慧农业内置角色（写在 `v12__init_seed_data.sql` 中 `ai_role` sequence 同步之后，使用生成 ID）：

| 角色 code | 名称 | 分类 |
|---|---|---|
| `system.role.agri-advisor` | 农技专家 | 智慧农业 |
| `system.role.agri-data-analyst` | 农场数据分析师 | 智慧农业 |
| `system.role.agri-marketing` | 农产品营销助手 | 智慧农业 |
| `system.role.agri-booking` | 农场预约助手 | 智慧农业 |

每个农业角色挂一个 `INTERNAL` 技能（`aaf/skills/system.skill.agri-*.md`，`ALWAYS` 激活），不挂默认用户模板，用户从角色库自行绑定；农技知识库待建。

| 角色 | 技能 | 工具白名单 |
|---|---|---|
| 农技专家 | `system.skill.agri-advisor` | `context.load`、`webSearch`、`queryWeather` |
| 农场数据分析师 | `system.skill.agri-data-analysis` | `context.load`、`agriSensorTrend`、`agriSensorAlerts`、`agriSalesSummary` |
| 农产品营销助手 | `system.skill.agri-marketing` | `context.load`、`content.draft.upsert`、`agriSalesSummary`、`agriBookableProducts` |
| 农场预约助手 | `system.skill.agri-booking` | `context.load`、`agriBookableProducts`、`agriDraftBooking`、`agriConfirmBooking` |

演示工具实现在 `module.agri.agent.tool.AgriDemoTool`，数据由 `AgriDemoData` 按固定规则生成，库存、预约草稿与预约单只存内存，重启后恢复。内置故事线：2 号棚（番茄）近三天空气湿度高于 90%；番茄近 30 天销量环比下降 18.2%，库存 1200 kg。`agriConfirmBooking` 在工具目录中为 HIGH 风险、需人工确认，与养老确认类工具一致。

农业知识库（`v21__knowledge_seed.sql`，`SYSTEM_PUBLIC`，归属 admin 个人组织）：

| stable_id | 名称 | 绑定角色 | 资料（`bootstrap/agri/`） |
|---|---|---|---|
| `20000000-0000-4000-8000-000000000001` | 智慧农业农技知识库 | 农技专家 | 设施番茄病虫害防治、设施黄瓜栽培、温室草莓栽培、大棚环境调控 |
| `20000000-0000-4000-8000-000000000002` | 农场经营资料 | 营销助手、预约助手 | 农场产品与产地介绍、采摘与配送须知、售后与常见问题 |

- 资料由 `BootstrapKnowledgeDocumentInitializer`（原 `PlatformGuideDocumentInitializer`，改为按知识库分组）启动时登记到 admin 文档库；管理员在知识库页面"从文档库导入"后才切片与向量化。
- 角色绑定知识库后自动获得 `knowledge_search`，不写入工具白名单。
- 资料均为演示内容，农场名称、价格与规则为虚构，与演示工具数据一致；农技内容正式演示前应由客户或当地农技部门审核。
### 已确认决策

- SQL 直接修改 `v2__ai_schema.sql` 与现有种子文件，不新增迁移文件。
- 治理页默认筛选系统分类，可切换查看私有分类，私有分类只读。
- 不做用户把内置或公开角色放进自己的分类；不做多级分类、多标签、公开分类。

## 任务

- [x] **#12601** 角色分类表与种子 — developer-service
  - `v2__ai_schema.sql`：新增 `ai_role_category`（列、约束、索引、注释），`ai_role` 增加 `category_id` 与索引。建表顺序保证 `ai_role_category` 在 `ai_role` 之前。
  - 种子：系统分类写入早于角色种子的文件；内置角色按上表回填，养老角色在其种子文件（`v402__elder_duty_seed.sql`）中按分类 code 回填。
  - 种子：权限码与角色授权、记录规则（`WHERE NOT EXISTS`）、治理菜单，参照 `v23__intelligent_governance_seed.sql`。
  - 种子：EntityDef `role-category` 与 `ai-role` 的 `categoryId` 字段，放 `db/seed/view/v106__intelligent_governance_entity_def.sql`。

- [x] **#12602** 角色分类 CRUD 资源 — developer-service（依赖 #12601）
  - `AiRoleCategory` 实体、`RoleCategoryRepository`、DTO 与 VO、资源定义、`RoleCategoryService`、`RoleCategoryController`，步骤见 [实体扩展指南](../../../guide/development/entity-extension.md)。
  - 读取范围、写入校验、治理 Header 按上文实现。

- [x] **#12603** 角色归类与校验 — developer-service（依赖 #12602）
  - `Role` 实体、`RoleVO`、`RoleCreateDTO`、`MyRoleSaveDTO` 增加 `categoryId`；管理端与用户端保存路径写入分类。
  - `RoleReferenceValidator` 增加分类校验；发布时按上文处理分类。

- [ ] **#12604** 前端：角色库分类筛选与分类管理 — developer-webui（依赖 #12603，后续安排）
  - 分类数据经 TanStack Query 读取 `/api/ai/role-categories`，不进 Zustand。
  - 角色库在可见性分组上方加分类筛选（全部、各分类、未分类）；我的角色表单加分类选择与"管理分类"对话框。
  - 治理页默认筛选值如 entity-engine 不支持，在此补齐。

- [ ] **#12605** 验收测试 — tester（依赖 #12601～#12604）
  - 用户只看到系统分类和自己的私有分类；按 ID 访问他人私有分类返回不存在。
  - 用户创建分类恒为私有；不能修改或删除系统分类；同一范围内名称不重复。
  - 治理页默认只列系统分类，切换后可看私有分类且只读；管理员创建的分类为系统分类。
  - 私有角色可选自己的分类，选他人私有分类被拒；内置与公开角色选私有分类被拒。
  - 发布私有角色时，私有分类不带出，系统分类保留。
  - 删除私有分类后角色变为未分类；删除被引用的系统分类被拒绝。
  - 新库中养老内置角色归"智慧养老"，内容创作者归"内容创作"。

## 非目标

- 用户把内置或公开角色放进自己的分类（需要"用户-角色-分类"关系表）。
- 多级分类、多标签、公开分类。
- 智慧农业场景的角色、技能、工具与界面（另立用户故事）。
- `ai_persona` 归属规则未落实（已登记改进意见）。
