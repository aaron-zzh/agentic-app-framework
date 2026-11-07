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

## 角色知识库按需检索（#11816 ~ #11819）

> 按人类指定：不补设计稿、不编译、不跑测试与 lint、不新增单测，下列条目均为"已实施、未运行验证"。

- ✅ #11818 移除 L1 知识预检索 — 删 `ContextScope.KNOWLEDGE`、`ContextRequest.knowledgeQuery` 及校验；L1 仅检记忆（2026-11-05）
- ✅ #11817 内置注册与执行契约 — 范围非空即并入内置工具，规则只读免授权；契约动作统一 `knowledge_search`（2026-11-05）
- ✅ #11816 `knowledge_search` 工具 — `KnowledgeSearchToolHandler` 只取可信范围，与语音共用检索；`includePublic=false`（2026-11-05）
- ✅ #11819 提示词 — 小五、客服 Persona 补"先调用 knowledge_search"（2026-11-05）

| 范围 | 文件 |
|------|------|
| framework | `ContextRequest`、`DefaultL1ContextCollaborator`、`AssistantApplicationService`、`BuiltinControlToolProfile`、`InvocationContext` |
| aaf-api | `KnowledgeSearchToolHandler`（新增，`module.ai.assistant.service`）、`RealtimeKnowledgeSearchService`、`AssistantExecutionService`、`DefaultKnowledgeSearchApi`、`v12__init_seed_data.sql` |
| 测试（仅修编译） | `DefaultL1ContextCollaboratorTest`、`ContextLoadToolTest` |

实现决策：

- 范围不在工具内重新加载 `AssistantDefinition`（任务原文写法），改为服务端算好后冻结进 `InvocationContext.knowledgeBaseIds`（缺省空集合），工具只读该字段。注册与传参同用 `AssistantApplicationService.effectiveKnowledgeBaseIds`，聚合者恒为空。
- 共用检索留在 `RealtimeKnowledgeSearchService`，新增 `search(SearchScope, reason, query, topK)`，语音原入口委托过去，语音事件与返回不变；类名未改。
- `knowledge_search` 计入 `BuiltinControlToolProfile.contains` 与 `contractActions`，任务合同（对话任务、工作流 `AgentNode`）统一放行；`AssistantExecutionService` 中硬编码的 `knowledge.search` 删除。
- `AssistantExecutionService.appendKnowledgeCandidates` 仅为旧校验存在，已删除；`KnowledgeMode` 校验与 `ContextPlan.knowledgeQuery` 保留，用于决定工具范围。

注意事项：

- 未核实：`AutomationDelegatedDispatchAdapter` 使用模板合同，若模板未含 `knowledge_search`，自动化任务调用时会被合同拒绝。
- 工具检索结果未写入引用来源（manifest / citation），由 #11820 登记改进意见。
- v12 种子用 `ON CONFLICT DO NOTHING`，已有库的 Persona 提示词不会更新，需重建库；v12 checksum 变化。
- 需更新文档（协调者，#11820）：`cognition/retrieval.md`、`cognition/Readme.md`、`action-governance.md` 等。

## 产品服务资料与文档导入知识库（#11821 ~ #11823）

> 按人类指定：不编译、不跑测试与 lint、不新增单测，下列条目均为"已实施、未运行验证"。

- ✅ #11821 产品服务介绍资料 — `bootstrap/aaf-products-and-services.md` 已存在（2026-11-05）
- ✅ #11822 文档导入知识库 — `POST /{id}/documents/from-docs`；复用 `uploadResource`，按版本幂等替换（2026-11-05）
- ✅ #11823 启动登记并导入 KB 301 — `PlatformGuideKnowledgeInitializer`；v21 删旧向导文档/批次/切块（2026-11-05）

| 范围 | 文件 |
|------|------|
| document | `DocumentSourceApi`（加 `findBySourceKey`）、`DocumentReferenceApi`（`OwnedDocument` 加 `documentType`）、`DocumentService`、`DocumentRepository` |
| knowledge | `KnowledgeBaseService`、`KnowledgeBaseController`、`KnowledgeDocumentUploadService`（`uploadResource` 加 `sourceType`）、`KnowledgeDocumentRepository`、`AdminFrameworkKnowledgeInitializer`（传 `SYSTEM`）、`PlatformGuideKnowledgeInitializer`（新增）、`vo/KnowledgeDocumentLibraryImportDTO`、`vo/KnowledgeDocumentLibraryImportVO`（新增） |
| SQL | `v21__knowledge_seed.sql` |

实现决策：

- 导入方法放 `KnowledgeBaseService`：撤销删除与 `deleteDocument` 共用私有方法 `revokeAndDeleteDocument`，避免拆服务后循环依赖。
- 跳过知识来源文档需要文档类型，`OwnedDocument` 增加 `documentType`（唯一构造点在 `DocumentService`）。
- 导入知识文档 `source_type=BUSINESS_OBJECT`；来源文档仍按 `uploadResource` 归属知识库 owner。
- 启动初始化失败沿用 `AdminFrameworkKnowledgeInitializer` 风格直接抛出，不吞异常。
- v21 投影 checkpoint 与 `projection_watermark` 保持不变：空库无投影内容，首次发布时水位自然推进。

注意事项：

- 来源文档登记要求当前用户即知识库 owner，非 owner 的协作者导入会被 `register` 拒绝（FORBIDDEN）。
- 旧版本导入仍在处理中时替换返回 409，整批回滚。
- 内置文档 `file_path=classpath:bootstrap/...`；用户在文档库编辑该文档时 `writeToLocalFile` 会按此路径写本地文件（Windows 下含冒号会抛 `InvalidPathException`）。已处理：协调者在 `DocumentService.writeToLocalFile` 加守卫，只回写 `docs/` 前缀路径（2026-11-05）。
- v21 checksum 变化，已有库需重建；需更新文档（协调者）：知识库接口说明补充 `from-docs`。

- ✅ #11824 前端"从文档库导入" — `DocumentLibraryImport` 弹窗：标题搜索、多选≤20、过滤 `knowledge_source`，结果与跳过原因 toast；`ProjectDocumentPicker` 加 `emptyText` 复用（2026-11-05）

| 范围 | 文件 |
|------|------|
| webui | `lib/api/rest/knowledge/knowledge.ts`（`importFromDocs`、`useImportKnowledgeFromDocs`）、`features/knowledge/components/DocumentLibraryImport.tsx`（新增）、`features/studio/content/ProjectDocumentPicker.tsx`、`app/studio/knowledge/[id]/page.tsx` |

- ✅ #11823 补充：内置资料统一导入平台向导库 — `PlatformGuideKnowledgeInitializer` 改为资源清单（框架介绍 + 产品服务介绍），均先登记文档再导入 KB 301，文档类型统一 `platform-guide`；删除 `AdminFrameworkKnowledgeInitializer`（admin 默认助理与个人库由登录 `provisionDefaultAssistant` 幂等补齐）（2026-11-05）
- ✅ #11823 调整：启动只登记文档不导入 — 改名 `PlatformGuideDocumentInitializer`，仅写文档库；由管理员在知识库页"从文档库导入"，避免每次清库启动消耗向量化与实体抽取额度（2026-11-05）
- ✅ 知识库上传先入文档库 — md/txt/pdf 经 `DocImportService.importFile` 生成正文版本后按版本导入；pdf 为只读提取文本；文档库导入不再另建 `knowledge_source`；按 `doc:{id}:v{ver}` 固定版本不追加解析版本；知识文档加来源文档跳转；PDF 文档编辑页预览原文件（抽出 `components/pdf` 复用 dev 示例的 react-pdf）（2026-11-05）
- ✅ 知识文档处理改 SSE — `KnowledgeDocumentQueueService` 向上传人发用户级 `knowledge.document.processing/completed/failed`；前端删轮询改订阅失效；文档库导入 `uploadedBy` 改为操作人（2026-11-05）
- ✅ 管理员免知识库数量额度 — `KnowledgeBaseService.entitlementCode()` 对 ADMIN/SUPER_ADMIN 返回 null，创建不扣、删除不还（2026-11-05）

> 遗留：权益归还按删除操作人计，管理员删除普通用户的知识库时不会退回创建人额度（既有问题，未改）。
