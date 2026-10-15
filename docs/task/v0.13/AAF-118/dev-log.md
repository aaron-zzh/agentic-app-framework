# 开发记录：AAF-118 Role 可见性与用户自维护绑定

执行者：AI/developer-service + developer-webui

> 约束：本轮实现不编译、不运行任何测试与 lint，下列条目均为"已实施、未运行验证"，由人类或后续 check 阶段验证。

## 完成记录

- ✅ #11801 `ai_role` 可见性与归属 — `visibility` + CHECK + 索引，`skill_ids` 改 JSONB 加 GIN，`Role` 标注 `@OrgIgnore`，`ai.ai-role` 租户范围改 `GLOBAL`（2026-10-14）
- ✅ #11802 种子与参数 — v12 五个内置角色回填 `SYSTEM`，新增 `ai.assistant_max_roles`（默认 10）与 `SysConfigKeys.Ai.ASSISTANT_MAX_ROLES`（2026-10-14）
- ✅ #11814 统一可见性判断 — `RoleVisibility` + `AiRoleRepository` 的带可见性条件的查询；`RoleStoreImpl`、`JpaRoleDefinitionAdapter`、`JpaAssistantDefinitionAdapter` 收口（2026-10-14）
- ✅ #11803 引用校验 — `RoleReferenceValidator` + `SkillAccessQuery`，失败整体拒绝并列出越权的技能与工具名（2026-10-14）
- ✅ #11804 技能反向引用保护 — 删除、可见性收窄、修改 code 前用 JSONB `@>` 反查，409 拒绝，他人私有角色只返回数量（2026-10-14）
- ✅ #11805 管理端接入 — `AiRoleCrudService` 引用校验、`visibility` 仅 `SYSTEM / PUBLIC`、私有角色只可调整状态，删除死代码 `AiRoleService`（2026-10-14）
- ✅ #11806 私有角色接口 — `/api/ai/my/roles`，code 与所有者由服务端决定，默认绑定的私有角色拒绝删除（2026-10-14）
- ✅ #11807 角色库接口 — `GET /api/ai/roles/library?assistantId`（2026-10-14）
- ✅ #11808 绑定维护接口 — `/api/ai/my/assistants/{id}/roles`，数量上限、默认角色不可解绑与停用、切换默认先清旧默认再落新默认（2026-10-14）
- ✅ #11809 助理加载容错 — 非默认失效绑定跳过并打 warn 日志，默认角色失效明确失败（2026-10-14）
- ✅ #11810 运行时再校验 — `SkillStoreImpl` 用 `OperatorContext.currentOwnerId()` 按技能可见性过滤，不改端口签名（2026-10-15）
- ✅ #11811 发布 — `POST /api/ai/roles/actions/publish`，复制为新的 PUBLIC 角色（2026-10-14）
- ✅ #11812 前端 — `settings/assistant-roles` 页面（角色库、我的角色），`lib/api/rest/ai/role.ts`，管理端 `ai-role` EntityDef 追加到 v106（2026-10-14）
- ✅ #11813 文档同步 — AAF-118 需求、设计、任务与 backlog 已同步；`action-governance.md` 与 `improvements.md` 见下（2026-10-14）

## 实现文件

| 范围 | 文件 |
|------|------|
| 数据 | `v2__ai_schema.sql`、`v12__init_seed_data.sql`、`v106__intelligent_governance_entity_def.sql`、`SysConfigKeys` |
| framework | `Role`、`RoleVisibilityLevel`、`RoleVisibility`、`AiRoleRepository`、`AiAssistantRoleRepository`、`JpaAssistantDefinitionAdapter`、`JpaRoleDefinitionAdapter`（及 `JpaRoleDefinitionAdapterTest`） |
| aaf-api 角色 | `AiRoleController`、`AiRoleCrudService`、`RoleVO`、`RoleCreateDTO`、`PublishRoleDTO`、`MyRole*`、`AssistantRoleBinding*`、`BindRoleDTO`、`UpdateRoleBindingDTO`、`RoleLibraryVO`、`RoleReferenceValidator` |
| aaf-api 技能 | `SkillService`、`SkillAccessQuery`、`SkillStoreImpl`（仓储增加 `findByCodeIn`）、`RoleStoreImpl` |
| 配置 | `CrudResourceProviderConfiguration` |
| 前端 | `lib/api/rest/ai/role.ts`、`features/settings/assistant-roles/*`、`app/(workspace)/settings/assistant-roles/page.tsx`、`settings/layout.tsx` |

## 实现决策

- 技能版本不可变，`publish` 只切换当前版本、不存在退役当前版本的入口，所以 `#11804` 不拦截这两项，只拦截删除、可见性收窄和修改 `code`（比设计多拦了 `code` 改名，因为它就是引用键）。
- 内置工具（`ContextAwareToolHandler`）不经 `ai_tool_catalog`，引用校验视为已登记（设计只写了目录登记，此处按 `ToolVisibilityFilter` 同口径扩展）。
- 通用 EntityAction 只提交 `{id}`，所以发布接口改为 `POST /api/ai/roles/actions/publish`，`code` 缺省由服务端生成（设计原为 `/{id}/publish` 且必填 code）。
- 管理端对私有角色允许调整状态（下架与恢复），不只是下架。
- `RoleStoreImpl` 通过 `OperatorContext` 取当前用户，无上下文时只能读到 `SYSTEM / PUBLIC` 角色，私有角色按不存在处理（fail-closed）；该接口只有 `ToolResolver`、`ToolRegistry` 两个引擎层调用方。
- 新增 `GET /api/ai/my/roles/tool-options`（设计未列），供前端工具选择使用。
- 助理加载容错的"记录决策"目前是 `log.warn`，没有写决策事件。

## #11810 运行时再校验技能可见性

`SkillStoreImpl` 的四个按名/按 ID 读取方法，通过 `OperatorContext.currentOwnerId()` 取当前用户，复用 `SkillAccessQuery.SkillAccess` 的可见性规则（全员可引用放行、PRIVATE 仅所有者、WORKSPACE 不放行），不改 `SkillStore` / `SkillCatalogPort` 签名，与 `RoleStoreImpl` 同一模式。`currentOwnerId()` 已覆盖请求线程、`ScheduledTaskExecutor` 与 `runAsOwner` 异步入口；没有用户上下文时只能读到全员可引用的技能（fail-closed）。

- 摘要投影 `SkillSummaryProjection` 增加 `visibility`、`ownerId`；`SkillStoreImplTest` 补了 `OperatorContext` mock，未新增用例。
- 不可见技能按不存在处理，没有写决策事件。
- 未核实：助理 TASK 的响应式路径（`subscribeOn(boundedElastic)`）是否传播 `SecurityContext`，若不传播，私有技能在该路径会读不到，需要人工在联调时确认。

## 内置技能种子迁移为 Markdown（顺带变更，不属于 AAF-118 任务）

- `v12__init_seed_data.sql` 删除全部技能相关种子：29 个内置技能的根对象与版本、7 组工具要求（共 22 条）、`ai_system_skill_binding` 的 5 条默认绑定、`ai_skill_category_relation` 的文案分类关联；`ai_skill_category` 的 `copywriting` 保留在 SQL。
- 29 个技能改为 `aaf-framework/src/main/resources/aaf/skills/{code}.md`（prompt 源文件同步改名为 `aaf/prompts/`，扫描路径随之调整）。frontmatter 含 `code / name / summary / version / visibility / toolAccessMode / changeSummary`，可选 `instancePrompt`、`systemBinding`（`激活方式 | 排序`），可重复 `category`、`tool`（`工具名 | 排序 | 用途`）；正文即技能内容。
- `SkillMarkdownSourceSynchronizer`（`ApplicationRunner`，`@OrgIgnore @Transactional`）：`ai_skill_definition` 为空才执行，非空整体跳过；顺序为先建全部技能（根对象 + 已批准版本）→ 再绑定工具要求 → 最后写系统默认绑定，三步同一事务。文件名必须等于 code，code 重复、分类不存在、frontmatter 非法都会启动失败。
- 与 Prompt 同步器的差异：技能同步默认开启、不比对 hash、不做版本升级，已有数据不会被改动；改技能正文要在运行中的库里走技能版本流程，改 md 只影响空库。
- 未编译、未运行：重点复核 `SkillMarkdownSourceParser` 的 frontmatter 解析、`SkillCategoryRepository.findByCodeIn` 的 `Set` 入参，以及启动期无用户上下文时 `AuditingEntityListener` / `OperatorEntityListener` 是否正常（与 `BuiltinRoleInitializer` 同一场景）。
- 副作用：技能数据从迁移期变为启动后才有，只跑 Flyway 的场景读不到内置技能；`docs/design/apps/webui/user-studio-mvp.md` 中"seed 进 `ai_skill_definition`"的描述属 `docs/design`，由协调者同步。

## 注意事项

- 未编译、未运行测试：重点复核 `AiRoleRepository` 中带 `RoleVisibilityLevel` 全限定名字面量的 JPQL、原生 `@>` 查询对 `Role` 实体的映射、`skill_ids` JSONB 映射是否与 `AiModel.stepRatios` 的现有用法一致。
- `v12__init_seed_data.sql` 含 AAF-117 的未提交改动，本故事的改动叠加在其上，提交时注意区分。
- 需更新文档：`docs/design/framework/intelligent/action-governance.md`（角色与工具白名单的实现态：角色可见性、引用校验、用户自维护绑定），属 `docs/design`，由协调者同步。
- 改进意见已在 `docs/prd/improvements.md` 登记：权限码未注册不构成授权的收紧方向；私有角色可引用高风险工具的滥用监测。
- 工作区可见性的技能不支持被角色引用；管理端 `ai-role` EntityDef 的 `readOnlyWhen` 使用 `$record.id` 判断编辑态（表达式引擎只支持单个比较或真值）。
- 异步任务事件与实时通道（AAF-119）由另一位开发者处理，本故事未触及。
