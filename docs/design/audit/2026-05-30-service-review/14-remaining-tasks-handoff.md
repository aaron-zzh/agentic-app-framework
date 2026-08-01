# service 安全修复与启动修复 — 剩余任务交接

> 本文是 2026-05-30 service 复审修复工作的交接清单，已于 2026-07-31 汇总前四阶段复核结果。
> 当前剩余任务只列 **OPEN/PARTIAL**；已确认 **FULL_FIXED** 的问题仅保留在“历史完成记录”，不再进入未完成清单。
> 真理源：本目录 [README](README.md)、各分区复审文档与 [10 鉴权矩阵](10-authorization-matrix.md)。

## 交接口径

- 本文原有 HEAD、提交与工作树信息是 **2026-05-30 历史现场**，未在本轮重新查询 Git，不应当作当前工作树事实。
- FULL_FIXED：从“未完成/剩余”移除，但保留提交、设计和实施历史。
- PARTIAL：只保留分区复核确认的残余，不再重复已经完成的主路径修复。
- OPEN：继续保留，按风险与依赖安排独立修复任务。

## 历史现场与完成记录

### 历史 Git 快照

- 当时 HEAD：`eafa9a0`（webui 提交链在上）。
- 当时已提交安全修复：`b4f745a fix(security): 修复审查发现的 9 个 blocker（B12-B20）`（16 文件，已确认是当时 HEAD 的祖先）。
- 当时 D2 启动修复、测试债与 Flyway 配置位于工作树、尚未提交；当前状态须由接续者重新查询，不得沿用此结论。

### b4f745a 已实施历史

已完成的 9 个安全 blocker 按四组实施：

- SQL 标识符：`DynamicTableService` 增加标识符白名单与列 schema 比对，`DataRouter.insertToTable` 委托 `DynamicTableService.insertRow`。
- 引擎沙箱：`ConditionEvaluator` 增加字段白名单与单引号转义；`MessageTemplateEngine` 使用 FreeMarker `SAFER_RESOLVER`；`FlowableConfig` 禁止 scriptTask 并接入 `RejectScriptTaskParseHandler`；`WorkflowController#deploy` 增加 ADMIN 鉴权。
- 危险默认：Mock 支付适配器默认关闭并限制配置启用；ToolCallDispatcher 的无鉴权入口收窄，ToolService 改走权限调度。
- 存储输入：FileService 增加类型/大小白名单；LocalStorageService 增加安全路径解析。

后续分区复核确认：该历史提交覆盖的问题中，B13 仍有非 MultipartFile/底层入口残余，B17 仍有 UEL value/方法调用残余；其余同批问题已从当前剩余清单移除。

### 前四阶段确认完成的问题

以下问题已确认 FULL_FIXED，仅作历史记录：

- Blocker：B3、B6、B11、B12、B14、B15、B16、B18、B19、B20。
- Major：M2、M5、M8、M10、M11、M12、M13、M14、M20、M24、M30、M33、M34、M35、M38、M40、M41、M43、M44、M47、M48、M52。
- Minor/重复：m27、m28、m34、m35、重复3。

### 02 区（支付/计费/积分）修复记录（2026-07-31）

原 `02-payments-billing-credit.md` 的 B2、M3、M4 已修复，分区文档随之删除，修复要点如下（代码内以 `B2:`/`M3:`/`M4:` 注释标注）：

- **B2 客户端定价 + Mock 铸币**：删除 `PayOrderController#recharge` 与 `RechargeService#initiateRecharge`（客户端提交 amount 的唯一入口），充值统一走 `CreditPackageController#purchase`，金额取 `credit_package.price`；前端同步删除失效的 `endpoints.pay.recharge` 常量。`MockPayChannelAdapter` 增加 `@Profile("!prod")` 与 `application-prod.yaml` 显式 `aaf.pay.mock.enabled=false` 双保险，prod 下 Bean 不注册、MOCK 渠道对结算引擎不可见。
- **M3 并发回调与入账幂等**：`PayOrderRepository` 新增 `transitionStatus`/`transitionStatusOnly` 原子状态迁移（`UPDATE ... WHERE status = WAITING`），`markSuccess`/`handleNotify` 改为按更新行数判定是否触发后续入账；`credit_transaction` 新增 `idempotency_key`（`accountId:source:bizId`）及部分唯一索引（迁移 `v202__credit_earn_idempotency.sql`），`CreditService.earn` 一次性入账走幂等键，`earnBatch` 的周期性发放保持 NULL 不受约束。
- **M4 权益扣减并发保护**：`EntitlementQuotaRepository#findByUserIdAndEntIdForUpdate`（`PESSIMISTIC_WRITE`）+ `EntitlementService#consume` 持锁后重校验 remain，与积分侧 `findByUserIdForUpdate` 同一并发策略。
- 单测：`PayOrderServiceTest`（原子迁移命中/未命中）、`CreditServiceImplTest`（幂等跳过/首次写键/周期发放不写键）、新增 `EntitlementServiceTest`（行锁读取、持锁重校验拒绝）。
- 遗留：本轮按人类指示未运行 `pnpm nx test service` / `check:affected`，需在下次门控时补跑。

02 区原"待确认"项转入下方剩余任务，未随文档删除而丢失。

### D2 启动修复历史

当时已完成并验证到“除 AafApplicationTest 外测试全绿”的内容：

- 合并重复 AgentController，删除陈旧 Agent 根包文件；删除陈旧 AssistantController 根包集。
- 为 billing/role 的同名 Controller 与 Service 增加显式 bean 名。
- 重命名 livechat、task、notify、permission、company automation 的重复 Repository，并同步调用方。
- 为对应重复实体增加显式 `@Entity(name=...)` 消歧，不改类名与表名。
- 删除 `system.role.relation` 孤儿重复子包；删除 framework 陈旧 PermissionCacheService。
- 更新 AuthServiceTest 与 UserControllerTest 的依赖、断言和 WebMvcTest 隔离。
- 在 `application.yaml` 增加 `spring.flyway.sql-migration-prefix: v`。

历史验证记录：framework 23、AuthServiceTest 2/2、UserControllerTest 5/5、UserServiceTest、ImportExecutorTest、ControllerAuthorizationTest、LayeringTest 当时均通过；AafApplicationTest 因 Flyway/schema 问题仍失败。该记录仅说明当时现场，本轮未复跑。

### 历史未提交文件范围

当时 tracked 修改/删除包括：

- `ai/agent/{AgentController,AgentCreateDTO,AgentManagementService,AgentUpdateDTO,AgentVO}.java`、`ai/agent/controller/AgentController.java`、`ai/agent/service/AgentManagementService.java`、`ai/skill/RoleStoreImpl.java`。
- billing Subscription Controller/Service，company automation domain/repository，livechat domain/repository/service。
- system notify/permission/role/task 的 domain/repository/service/controller。
- `resources/application.yaml`、AuthServiceTest、UserControllerTest。
- `system/role/relation` 孤儿文件及 framework 陈旧 PermissionCacheService 删除。

当时 untracked 新增包括：

- `CompanyAutomationRuleRepository.java`。
- `LivechatChatMessageRepository.java`、`LivechatChatSessionRepository.java`。
- `NotifySubscriptionRepository.java`、`MenuPermissionRepository.java`、`SysTaskExecutionRepository.java`。

当时另有非本任务产物：`ControllerAuthorizationTest.java`、`archunit_store/`、`test/resources/`、`test/.../module/ai/`、`test/.../module/knowledge/`。接续者必须先核实归属，禁止直接混入提交。

## 当前剩余任务

### 08 区（对话/统计/企业运营）修复记录（2026-08-01）

08 分区的 M18、M19、占位、m13、m14、m15 已修复，M15 的 Company/Channel 请求绑定与 Company 实体出参已收敛为 DTO/VO；全局仍保留其他模块实体出参残余。详见 [08 分区文档 #修复记录](08-ai-chat-tools-company-stats.md)。要点：会话/消息归属校验、ORG_ADMIN
去平台化 + `sys_user_event.org_id` 组织过滤（迁移 `v203`）、未实现能力显式报错、JSON 序列化与 senderId 常量化、workflow 三套抽象边界说明。

M16 已完成：用户密码、OAuth access/refresh token、Channel/Webhook 密钥、SMTP 密码、模型及供应商 API Key 均增加 `@JsonIgnore`。

### 环境与迁移门控

| 任务 | 状态 | 剩余 |
|------|------|------|
| Flyway/迁移与 AafApplicationTest | OPEN | 重新确认当前 Git/构建现场；清理 target 陈旧迁移；核对 v1–v7 与实体表完整性；决定 AafApplicationTest 归 Surefire 还是 Failsafe/Testcontainers |

历史已处理：小写 `v` 前缀配置已加入当时工作树。仍需验证：

- 真正清理 `apps/service/aaf-api/target` 后，确认不再出现重复 migration version。
- 对比所有 `@Table` 与 v1–v7 `CREATE TABLE`，确认 stats/profile/livechat/channel/tool_call_audit 等表未缺失。
- 明确 AafApplicationTest 的测试分层：若依赖真实 PostgreSQL/Redis/Neo4j，应改为 `*IT` 或提供可重复测试容器/测试 Profile。

### 剩余 blocker

| 编号 | 状态 | 下一步 |
|------|------|--------|
| B1 | PARTIAL | 将 framework 非标准仓储纳入 org 租户过滤，并设计统一 workspace 行级隔离 |
| B4 | PARTIAL | GitHub webhook 加 HMAC 验签；部署 environment 使用服务端白名单和分级授权 |
| B5 | OPEN | 收敛脚本执行为受限基线，删除裸子进程/关键词黑名单旁路 |
| B7 | OPEN | 配置接口改 VO 并脱敏；敏感凭证字段增加序列化防御 |
| B8 | OPEN | 校验 codegen module/name，规范化后强制输出路径位于 outputDir |
| B9 | PARTIAL | 不再按旧 Controller 总数扫注解；只处理角色过宽、SELF 归属、org 边界和冻结基线残余 |
| B10 | PARTIAL | 为 `viewSource` 与工具列表补 owner/org/share scope 资源级授权 |
| B13 | PARTIAL | 为 URL、byte[]、Base64 和底层 StorageService 增加统一策略；拒绝或隔离 SVG/HTML 主动内容 |
| B17 | PARTIAL | 禁止 value 构造可执行 UEL，明确关闭方法调用并增加恶意 value 回归用例 |
| B-mock | OPEN（条件） | 增加 `@Profile("!prod")`/构建隔离，避免仅靠配置关闭 Mock Token |

### 剩余 PARTIAL major/minor

| 主题 | 编号 | 只处理的残余 |
|------|------|-------------|
| 身份与租户 | M1、M9 | 删除 userId fallback；API Key 继承真实角色（M19 组织过滤已闭环） |
| 文件/短信/API 分层 | M6、M21、M22、M29 | Channel/Webhook Entity 出参；短信测试环境隔离；SMS Repository/Entity 边界；framework 裸 key API |
| 架构与 DTO | M15 | Company 与 Channel/Webhook 输入已 DTO 化、Company 输出已 VO 化；SMS、AI Output、Document、Team 等仍有实体出参 |
| 资金与成本 | M23、M26、M53 | image-to-image/edit 接回统一扣减；退款并发与稳定幂等键；framework 配额旁路 |
| OAuth/回调 | M28、M31、M37 | 适配器验签契约；账号绑定闭环与强制 state/nonce；per-flow execute、Webhook HMAC 与防重放 |
| HITL/知识库/任务 | M36、M45、M49 | 删除或统一旧 HITL 链；移除危险两参检索重载；补齐副作用到 ACK 的业务幂等 |
| 潜在语义风险 | m32 | 分布式锁获取失败返回 null 的语义仍不明确 |

### 仍为 OPEN 的 major

| 分组 | 编号 | 修复方向 |
|------|------|---------|
| 资金与权益 | M25、M27 | 部分退款金额语义、真实账单下载/明确失败 |
| 架构与 DTO | M7、M17、重复2 | 非阻塞 CI 调用、角色分配语义、权限模块去重（M15 已转 PARTIAL，M16 已完成） |
| License/工作流 | M32、M39 | 生产公钥强制配置；HttpNode SSRF 防护 |
| AI/抓取 | M42、M46 | 外部数据提示词边界；URL/协议/地址白名单和响应体上限 |
| 分布式基础设施 | M50、M51 | 跨节点缓存失效；审计参数/响应脱敏 |
| 过早占位 | 占位 | 删除无真实用例的空接口，待实现阶段再设计 |

### 02 区转入的待确认项（原 02 分区文档）

- `ReconcileService`/`PayRefundService` 未深读：退款金额上限校验、对账差异处理、重复退款幂等需补审（与 M25/M26/M27 同域）。
- `BizOrderService#create` 需确认 merchantOrderNo 唯一性与重复下单约束；同时 `POST /api/biz/orders` 目前接受客户端 orderType/totalAmount，虽不绑定支付单因而不会入账，仍应收敛为服务端货架下单。

### 仍为 OPEN 的 minor/结构项

- m1、m7–m10：软删除查询重复、代理头信任、API Key 热点写、回调 executor 背压、非常量时间比较。
- m36：内容安全关键词黑名单外置，并规划语义级能力。
- m18–m20、m24–m25：支付查询/金额、预签名约束、权限默认和 Node 子进程。
- m29–m30：LLM fallback 范围、Redis KEYS。
- 包结构、示例、兼容、异常、并行抽象：按 [README 当前问题总表](README.md#当前问题总表) 逐项收敛。

## 验证任务

### 残余 blocker 的针对性验证

已关闭问题不再作为“待补单测”列出。当前只为 B13/B17 残余与其他剩余 blocker 增加验证：

- B13：URL、byte[]、Base64、底层 StorageService 绕过；SVG/HTML 主动内容；统一大小/类型策略。
- B17：恶意 value 中的引号、方法调用、类型访问和表达式片段必须被拒绝，不能只验证 field。
- B1/B9/B10：org、workspace、SELF、owner/share scope 的正反授权矩阵。
- B4/B5/B7/B8/B-mock：Webhook 验签、受限脚本、敏感字段、路径规范化和生产 Profile。

测试命名继续遵循：developer 单测 `*Test.java`，tester 验收/集成 `*IT.java` 或 `*AcceptanceTest.java`。

### B9 鉴权矩阵接续

- 以 [10 鉴权矩阵](10-authorization-matrix.md) 当前冻结基线为准，不再使用任何旧 Controller 数量估算。
- 优先验证 P0 资金/账号/运维资源，但修复目标是资源级边界：角色最小化、SELF 归属、org 强制过滤、owner/share scope。
- 每批限制在单模块和可审查文件数内；禁止 broad refactor。
- 历史未跟踪的 ControllerAuthorizationTest/archunit_store/test resources 必须先确认来源与当前可运行性，再决定是否纳入。

## 历史文档边界

- 当前状态只更新本交接单、README、实际代码任务的 dev-log/test-report/review；不得用修改历史文档替代真实修复。

## 接续顺序

1. 重新查询 Git 状态，确认历史工作树内容是否已提交、丢失或变化。
2. 处理 Flyway/AafApplicationTest 环境门控，建立可重复验证基线。
3. 按剩余 blocker 表逐项建任务；B1/B9/B10 的授权边界优先，B5/B17 的执行注入风险同级。
4. 补针对性单测并运行相应项目 test；任务完工前再运行 `pnpm check:affected`。
5. 分批处理 PARTIAL major，再处理 OPEN major 与结构性债务。
6. 更新本交接单与当前任务产出，不修改 12/13 历史文档。

## 本轮汇总验证

- 仅复读并交叉核对 README、14 与前四阶段分区结果。
- 按用户要求，本轮未运行测试、check、构建，也未修改任何源码。
