# service 安全修复与启动修复 — 剩余任务交接

> 本文是 2026-05-30 service 复审修复工作的**交接清单**，供新对话接续。
> 记录截至 2026-05-30 的真实状态：已提交 / 工作树未提交 / 未开始 三类。
> 真理源：本目录 [README](README.md)、[12 修复设计](12-blocker-remediation-design.md)、[10 鉴权矩阵](10-authorization-matrix.md)。

## 当前 git 状态（接续前先确认）

- HEAD：`eafa9a0`（webui 提交链在上）。
- 9 个安全 blocker **已提交**：`b4f745a fix(security): 修复审查发现的 9 个 blocker（B12-B20）`（16 文件，已确认是 HEAD 祖先）。
- **D2 启动修复 + 测试债 + Flyway 配置 = 全部在工作树，未提交**（见下「待提交清单」）。⚠️ 新对话第一件事建议先提交，避免丢失。

## 已完成并已提交（b4f745a）

9 个安全 blocker B12–B20，按 [设计 12](12-blocker-remediation-design.md) 四组实施：

- 组 A SQL 标识符注入 B16/B18：`DynamicTableService` 标识符白名单 + 列名 schema 比对；`DataRouter.insertToTable` 委托 `DynamicTableService.insertRow`。
- 组 B 引擎沙箱化 B17/B19/B20：`ConditionEvaluator` UEL 字段白名单 + 单引号转义；`MessageTemplateEngine` FreeMarker `SAFER_RESOLVER`；`FlowableConfig` 禁 scriptTask + 安全 BPMN + `RejectScriptTaskParseHandler`；`WorkflowController#deploy` 加 `@PreAuthorize(ADMIN)`。
- 组 C 危险默认收敛 B12/B15：`MockPayChannelAdapter` `@ConditionalOnProperty`（默认关，dev/test yaml 开）；`ToolCallDispatcher.dispatch()` 降包私有 + `ToolService` 走 `dispatchWithPermission`。
- 组 D 存储输入校验 B13/B14：`FileService` 类型/大小白名单；`LocalStorageService.resolveSafe` 防路径穿越。

验证：framework 23 测试绿；9 改动零回归。

## 工作树未提交（D2 启动修复，已验证编译通过、除 AafApplicationTest 外测试全绿）

> 此前发现：doc 13 dev-log 曾声称已实施 9 blocker 但代码无改动（空头日志）；后已真实落地为 b4f745a。
> D2 解决的是**第二层缺陷**：全层重复 bean 导致 Spring 上下文无法加载（app 根本起不来）。

修复内容：

- **重复控制器**：`AgentController` 合并（删陈旧根包 5 文件，`updateStatus` 移入 `.controller` 版并加 `@PreAuthorize(ADMIN)`）；`AssistantController` 删陈旧根包集（5 文件）。
- **撞名控制器/服务加显式 bean 名**：`billing.SubscriptionController`→`billingSubscriptionController`、`role.PermissionController`→`rolePermissionController`、`billing.SubscriptionService`→`billingSubscriptionService`、`role.PermissionService`→`rolePermissionService`。
- **重复仓储接口重命名 + 改调用方**：`livechat.ChatMessageRepository`→`LivechatChatMessageRepository`、`livechat.ChatSessionRepository`→`LivechatChatSessionRepository`、`system.task.TaskExecutionRepository`→`SysTaskExecutionRepository`、`notify.SubscriptionRepository`→`NotifySubscriptionRepository`、`permission.PermissionRepository`→`MenuPermissionRepository`、`company.automation.AutomationRuleRepository`→`CompanyAutomationRuleRepository`；`RoleStoreImpl` 内部冗余 `RoleRepository` 删除，改复用 framework `AiRoleRepository`。
- **重复实体加 `@Entity(name=)` 消歧**（不改类名/表名）：`livechat.ChatMessage`→`LivechatChatMessage`、`livechat.ChatSession`→`LivechatChatSession`、`system.task.TaskExecution`→`SysTaskExecution`、`notify.Subscription`→`NotifySubscription`、`permission.Permission`→`MenuPermission`、`company.automation.AutomationRule`→`CompanyAutomationRule`。
- **删孤儿子包集**：`system.role.relation` 的 `domain/`+`repository/`+`vo/` 重复集（4 文件，扁平集才是被接线的）。
- **framework**：删陈旧 `security.PermissionCacheService`（注入无实现的 SPI，本就不可实例化），保留 `security.cache.PermissionCacheService`。
- **测试债**：`AuthServiceTest` 补 `SystemConfigService` mock + 更新陈旧断言（`templateCode=auth.verify_code.register`，删已不存在的 companyName/subject 断言）+ 按决策(ii)将 `should_not_throw_when_email_send_fails` 改写为 `should_throw_when_email_send_fails`（发信失败应抛出以通知用户重试）；`UserControllerTest` `@WebMvcTest` 排除 `StorageWebConfig`/`RequestMetricsFilter`/`SecurityConfig`/`ApiKeyAuthFilter` + `@AutoConfigureMockMvc(addFilters=false)`。
- **Flyway 配置**（进行中，见任务 A）：`application.yaml` 加 `spring.flyway.sql-migration-prefix: v`。

验证（`pnpm nx test service --skip-nx-cache`）：所有 bean 定义冲突已清除，`AafApplicationTest` 现已加载完整上下文 + Hibernate + 连上 Postgres + 跑 Flyway；AuthServiceTest 2/2、UserControllerTest 5/5、UserServiceTest 10、framework 23、ControllerAuthorizationTest/LayeringTest 等全绿。唯一剩余红：`AafApplicationTest`（见任务 A）。

### 待提交清单（精确文件）

tracked 修改/删除：`ai/agent/{AgentController,AgentCreateDTO,AgentManagementService,AgentUpdateDTO,AgentVO}.java`(D)、`ai/agent/controller/AgentController.java`、`ai/agent/service/AgentManagementService.java`、`ai/skill/RoleStoreImpl.java`、`billing/controller/SubscriptionController.java`、`billing/service/SubscriptionService.java`、`company/automation/domain/AutomationRule.java`、`company/automation/repository/AutomationRuleRepository.java`(D)、`livechat/domain/{ChatMessage,ChatSession}.java`、`livechat/repository/{ChatMessageRepository,ChatSessionRepository}.java`(D)、`livechat/service/{BotReplyService,ChatSessionService,SeatService}.java`、`system/notify/domain/Subscription.java`、`system/notify/repository/SubscriptionRepository.java`(D)、`system/notify/service/SubscriptionService.java`、`system/permission/domain/Permission.java`、`system/permission/repository/PermissionRepository.java`(D)、`system/permission/service/PermissionService.java`、`system/role/controller/PermissionController.java`、`system/role/service/PermissionService.java`、`system/task/controller/TaskManagementController.java`、`system/task/domain/TaskExecution.java`、`system/task/repository/TaskExecutionRepository.java`(D)、`resources/application.yaml`、`test/.../AuthServiceTest.java`、`test/.../UserControllerTest.java`。

untracked 新增（需 git add）：`company/automation/repository/CompanyAutomationRuleRepository.java`、`livechat/repository/{LivechatChatMessageRepository,LivechatChatSessionRepository}.java`、`system/notify/repository/NotifySubscriptionRepository.java`、`system/permission/repository/MenuPermissionRepository.java`、`system/task/repository/SysTaskExecutionRepository.java`。

另：`system/role/relation/domain|repository|vo` 下 4 个孤儿文件已 `git rm`（在 tracked 删除中体现的对应项）；framework `security/PermissionCacheService.java` 已 `git rm`。

未跟踪但**非本次产物**（前序会话遗留，勿混入提交，归任务 B/C 核实）：`test/.../arch/ControllerAuthorizationTest.java`、`archunit_store/`、`test/resources/`、`test/.../module/ai/`、`test/.../module/knowledge/`。

## 未完成任务（新对话接续）

| 任务 | 说明 | 优先级 |
|------|------|--------|
| **A. 完成 Flyway/迁移修复让 AafApplicationTest 转绿** | 多层既有缺陷，见下 | 🔴 阻塞门控 |
| **B. 为 9 个 blocker 补单测** | 原计划 step3，未开始 | 高 |
| **C. B9 鉴权矩阵批量** | 原计划 step4，未开始 | 🔴 影响面最大 |
| **D. 顺带同类 major** | 原计划 step5，未开始 | 中 |
| **E. 重写 doc 13 为真实日志** | 现为空头日志 | 中 |
| **F. 重复抽象收尾** | 非阻塞 code smell | 低 |

### 任务 A — Flyway/迁移修复（多层既有缺陷，与重复 bean 去重无关）

按发现顺序的层层缺陷：

- **缺陷 1（已处理）**：迁移文件用小写 `v` 前缀（`v1__system_schema.sql`…`v7__channel_platform_schema.sql`），但默认 Flyway 只认大写 `V` → 全部建表迁移被忽略 → schema 未建。**已加** `spring.flyway.sql-migration-prefix: v`（工作树）。
- **缺陷 2（待处理）**：`target/classes/db/migration` 残留陈旧迁移（`R__test_data.sql`、`v8__*`、`v9__*`、多个 `v6/v7` 变体），这些**已从 src 移除但 target 未清**。`pnpm nx clean service` **未真正清除**（疑似 nx 缓存层，未触发 mvn clean）→ 加前缀后报 `Found more than one migration with version 7`。需**真正 mvn clean** 删 `apps/service/aaf-api/target`，再跑。
- **缺陷 3（待核实，风险）**：src 仅 `v1__`…`v7__`（7 个，无 R__test_data、无 v8/v9）。但工程中存在 stats/profile/livechat/channel/tool_call_audit 等模块的 `@Entity`，其建表迁移已从 src 消失。若未并入 v1–v7，则 `ddl-auto: validate` 会因缺表失败。**需核对 v1–v7 是否建全所有实体对应表**（对比 `@Table` 名 vs 迁移 CREATE TABLE）。
- **缺陷 4（设计）**：`AafApplicationTest` 是全 `@SpringBootTest`，需真实 Postgres/Redis/Neo4j（本环境有 Postgres，已连上）。本质是集成冒烟测试却命名为 Surefire `*Test`。建议改为 `*IT`（Failsafe）或加测试容器/`@ActiveProfiles`，否则 `pnpm nx test` 永远依赖外部 DB。

验证：真正 `mvn clean` 后 `pnpm nx test service --skip-nx-cache`，`AafApplicationTest` 绿（需 DB + schema 完整）。

### 任务 B — 为 9 个 blocker 补单测（Surefire `*Test.java`）

用例见 [设计 12](12-blocker-remediation-design.md) 各组「验证」小节：

- `DynamicTableServiceTest`：slug/列名含 `;`/`"`/`OR 1=1`/未知列 → `BusinessException`。
- `ConditionEvaluatorTest`：field/value 含 `'`/`.getClass(` → 拒绝或安全转义。
- `MessageTemplateEngineTest`：渲染含 `?new()` 的 SSTI 模板 → 抛异常而非执行。
- scriptTask 拒绝：含 scriptTask 的 BPMN 部署 → 解析失败（`RejectScriptTaskParseHandler`）。
- `WorkflowController` deploy：非 ADMIN → 403。
- `LocalStorageServiceTest`：`download("../../etc/passwd")` → `StorageException`。
- `FileServiceTest`：超大/非白名单类型 → 拒绝。
- `ToolServiceTest`：高风险工具无权限 → DENIED/PENDING，不执行。

注：工作树有未跟踪 `test/.../module/ai/`、`test/.../module/knowledge/` 目录，需先核实是否前序会话留的 blocker 单测雏形。

### 任务 C — B9 鉴权矩阵批量（🔴 高风险）

按 [10 鉴权矩阵](10-authorization-matrix.md) 角色映射给缺失控制器加 `@PreAuthorize`，P0（资金/账号/运维）先行；分批每批 ≤1 模块 / ≤5–8 文件，每批 `pnpm check:affected` 全绿再交接，禁止 broad refactor。

- 已有未跟踪 `ControllerAuthorizationTest.java`（FreezingArchRule 默认拒绝基线）+ `archunit_store/` + `test/resources/`：冻结存量违规、只拦新增未鉴权写接口；存量逐个加注解后冻结基线自动收缩。**接续前先确认它能跑绿并纳入提交**。
- 本次已加 `@PreAuthorize` 的：`WorkflowController#deploy`(ADMIN)、`AgentController#updateStatus`(ADMIN)、既有 `ApiKeyController`/`LogLevelController`。

### 任务 D — 顺带同类 major（每组独立提交）

- M37：`WebhookTriggerController` 验签 + per-process 鉴权。
- M38：`DataTableController` per-resource 鉴权 + 租户隔离。
- M40：`DataIngestController` scope 校验 fail-open（JWT/null 时）收敛为 fail-closed。
- M24：Midjourney 回调验签。
- M28：支付回调验签上提到接口层（避免绕过）。
- M44：`ResilientChatService` pre-call 配额门控。
- M53：AI 能力服务（video/image/embedding 等）pre-call 配额门控。

### 任务 E — 重写 doc 13 为真实开发日志

[13-blocker-fix-dev-log.md](13-blocker-fix-dev-log.md) 当前是空头日志（曾声称已实施但代码无改动）。9 blocker 现已真实落地为 `b4f745a`，按真实改动重写；并补记本文档的 D2 启动修复。

### 任务 F — 重复抽象收尾（非阻塞 code smell）

- 重复 VO/DTO（非 bean，不阻塞）：`ChatMessageVO`/`ChatSessionVO`（ai.chat vs livechat）、`RoleCreateDTO`/`RoleVO`（ai.role vs system.role）、`UserProfileVO`（stats vs system.user）。
- 重复 2：permission 模块与 role 模块的 `PermissionService`/`PermissionController` 职责重叠——本次仅加显式 bean 名消除启动冲突，真正职责合并/去重未做。
- `EmbeddingService` 同名（`knowledge.embedding` @Service 类 vs `intelligent.ai.embedding` 接口）——非 bean 冲突，命名 code smell。

## 验证状态快照（截至交接）

| 测试 | 状态 |
|------|------|
| framework 全部（23 等） | ✅ 绿 |
| AuthServiceTest | ✅ 2/2 |
| UserControllerTest | ✅ 5/5 |
| UserServiceTest / PasswordEncoderTest / ImportExecutorTest | ✅ 绿 |
| ControllerAuthorizationTest / LayeringTest（ArchUnit） | ✅ 绿（0 违规/冻结） |
| **AafApplicationTest.contextLoads** | ❌ 任务 A（Flyway 迁移，非代码 bean 缺陷） |

> 结论：app 启动的全层重复 bean 缺陷已彻底修复（上下文可加载、连库、跑迁移）。剩余唯一红是独立的 Flyway 迁移/schema 既有问题（任务 A）。
