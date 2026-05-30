# 后端 service 代码审查报告

> 全量审查 `apps/service/`（aaf-common / aaf-framework / aaf-auto-dev / aaf-api 四模块）。
> 采用抽样 + 系统级模式识别，聚焦架构约束、安全、对称性、重复与规范合规。
> 审查依据：[代码审查规范](../../reference/dev/code-review-standard.md)、[架构约束](../../reference/dev/architecture-constraints.md)、[编码规范硬约束](../../../.kiro/skills/coding-standards/SKILL.md)。

## 元信息

| 字段 | 值 |
|------|-----|
| 审查人 | AI/architect |
| 审查日期 | 2026-05-30 |
| 范围 | apps/service 全部 4 个 Maven 模块 |
| 方法 | 核心基类全读 + 安全/财务/AI 引擎重点抽样 + 目录级模式识别 |
| 结论 | **不通过**（存在 blocker，门控要求 blocker=0 且 major≤2） |

## 严重级别统计

| 级别 | 数量 | 是否阻塞 |
|------|------|---------|
| 🔴 blocker | 6 | 是 |
| 🟠 major | 8 | 是 |
| 🟡 minor | 6 | 否 |

> 注：本次为抽样审查，未覆盖每一个文件。同类问题（如 IDOR、缺鉴权）大概率在未抽样的控制器中复现，修复时应按模式全量排查。

## Blocker（必须修复，阻塞发布）

### B1 多租户隔离由客户端请求头控制且 fail-open

- 位置：`aaf-api/.../config/TenantFilter.java`、`TenantFilterAspect.java`、`TenantContext.java`
- 现象：租户 `orgId` 直接取自请求头 `X-Org-Id`，无任何"该用户是否属于此 org"的校验。任意登录用户改这个头即可读写**其他组织**的数据（横向越权 / IDOR）。
- 叠加风险（fail-open）：`X-Org-Id` 缺省时 `orgId=null` → `TenantFilterAspect` 不启用 `tenantFilter` → 查询**返回全部组织数据**。失败方向是"放行"而非"拒绝"。
- 另：切面仅匹配 `com.xuejiai.aaf.module..repository.*`，framework 层 repository（如积分、序列）不受租户过滤；`workspace_id` 维度完全未隔离。
- 修复：orgId 必须从已认证身份（`OperatorContext` / JWT claim）推导，并校验用户与 org 的归属关系；过滤器缺省应 fail-closed（无 org 上下文时拒绝跨租户查询，或强制限定到当前用户）。

### B2 充值接口可被任意用户为任意账户免费铸造积分

- 位置：`aaf-api/.../module/pay/controller/PayOrderController.java#recharge`、`module/pay/service/RechargeService.java`
- 现象：`recharge(@RequestParam Long userId, @RequestParam long amount, channelCode=MOCK)` —— userId 和 amount 全部由客户端传入，默认走 MOCK 渠道同步成功，链路 `initiateRecharge → onPaySuccess → creditService.earn`。任意登录用户可给**任意 userId 充任意金额积分**，且无需真实付款。
- 修复：userId 必须来自 `OperatorContext`，不可由参数传入；MOCK 渠道严禁在非 dev 环境可用（用 `@Profile`/配置门控）；金额必须来源于服务端订单而非客户端。

### B3 支付回调无渠道签名校验，可伪造支付成功

- 位置：`aaf-api/.../module/pay/controller/PayOrderController.java#notify`、`module/pay/service/PayOrderService.java#handleNotify`
- 现象：`/api/pay/orders/notify` 接收 `PayNotifyDTO{success, merchantOrderNo, channelOrderNo}`，`handleNotify` 直接信任 `dto.success()` 将订单置为 SUCCESS，无任何渠道签名/验签。能访问该端点者即可把任意订单标记为已支付并触发积分入账。
- 附带矛盾：该路径未列入 `SecurityConfig.PUBLIC_PATHS`，真实支付网关无法携带用户 JWT 回调 → 要么真实回调打不通，要么被迫开放则完全无保护。
- 修复：实现渠道侧验签（如微信/支付宝的 sign 校验）；明确回调端点的认证方式（验签即认证）；状态机幂等已部分到位（WAITING→SUCCESS 守卫），保留。

### B4 git/CI/部署接口对任意登录用户开放，无角色鉴权

- 位置：`aaf-auto-dev/.../git/GitController.java`
- 现象：`/api/autodev/git/**` 仅需登录、无 `@PreAuthorize`。任意登录用户可 commit / push 到远程 / 建分支 / 建 PR / **触发 CI** / **触发任意环境部署**（`/ci/deploy` environment 由客户端指定）。属高危运维操作。
- 附带：`/webhook/github` 未校验 `X-Hub-Signature-256`，可伪造 workflow_run 事件污染 `buildCache`。
- 修复：全部端点加管理员级鉴权（`@PreAuthorize`）；webhook 加 HMAC 验签；部署环境做白名单与权限分级。

### B5 ScriptSandbox 名为沙箱实则无隔离 + 黑名单可绕过

- 位置：`aaf-framework/.../engine/tool/ScriptSandbox.java`
- 现象：类注释声称"资源限制、文件系统隔离"，但 `executePython` 直接 `ProcessBuilder("python3", ...)` 起子进程，**无文件系统/网络/资源隔离**，脚本可读写 JVM 用户可达的任意文件。`executeShell` 用 `isDangerous` 关键词黑名单（`rm -rf`/`dd if=` 等）防护，黑名单对命令注入是反模式，极易绕过（多空格、`find -delete`、`python -c`、base64 等）。
- 对比：同包 `GraalVmScriptExecutor` 用 `HostAccess.NONE/IOAccess.NONE/allowNativeAccess(false)` 才是真隔离。两套并存且能力不对等。
- 修复：要么统一走 GraalVM 受限上下文，要么子进程方案配合 OS 级隔离（容器/seccomp/独立低权用户），并把误导性的"沙箱"注释改成与实现一致；shell 执行改白名单或直接禁用。

### B6 PermissionCacheService 重复定义两份，Bean 名冲突 + Redis 类型不兼容

- 位置：`aaf-framework/.../security/PermissionCacheService.java`（String CSV，含 PermissionLoader SPI）与 `aaf-framework/.../security/cache/PermissionCacheService.java`（Redis Set）
- 现象：两个同名类均标注 `@Service`，默认 Bean 名都是 `permissionCacheService` → 同被组件扫描时 `ConflictingBeanDefinitionException`，启动即挂；二者还共用同一 key 前缀 `permission:user:` 但分别用 String / Set 存储，互相写入会触发 Redis `WRONGTYPE`。属并行抽象（违反硬约束"禁止并行抽象"）。
- 修复：删除其中一份，保留 SPI 版本（`security/PermissionCacheService`，带 PermissionLoader 更内聚）；调用方统一迁移；确认启动恢复正常。

## Major（严重，应当修复）

### M1 控制器普遍以 userId 作为入参（IDOR 模式）

- 位置：`module/pay/controller/CreditController.java`（getBalance/getTransactions `@RequestParam Long userId`）等，B2 同源。
- 现象：`OperatorContext`/`SecurityOperatorContext` 已能从 JWT 取当前用户，但多个控制器仍从参数取 userId，可越权查他人积分余额/流水。
- 修复：凡"当前用户"语义的 userId 一律从 `OperatorContext` 取；确为管理员查他人时加鉴权。建议全量 grep `@RequestParam Long userId` 排查。

### M2 敏感管理端点缺鉴权

- 位置：`module/pay/controller/CreditTokenRuleController.java`（积分转 Token 规则 create/update/delete 无 `@PreAuthorize`），同类疑似存在于 billing/level、channel 配置等管理接口。
- 现象：直接影响计费的规则可被任意登录用户增删改。
- 修复：管理类写操作统一加角色鉴权。

### M3 充值幂等缺失，存在重复入账风险

- 位置：`module/pay/service/RechargeService.java#onPaySuccess` + `CreditServiceImpl#earn`
- 现象：MOCK 同步成功路径已调用 `onPaySuccess`，若真实异步回调对同一单再次触发，`onPaySuccess` 未校验业务订单是否已入账，`earn` 也无按 `bizId` 的幂等去重 → 重复入账。
- 修复：`earn`/`onPaySuccess` 以 `bizId`/订单状态做幂等；`markPaid` 前校验当前状态。

### M4 EntitlementService 扣减无并发保护（TOCTOU / 丢失更新）

- 位置：`module/billing/service/EntitlementService.java#consume/deduct`
- 现象：`check`（readOnly）与 `consume` 分两个事务，`deduct` 直接读改写 quota 无悲观锁/版本校验（积分侧有 `findByUserIdForUpdate`，权益侧没有）。并发请求可同时通过 check 各自扣减 → remain 变负。
- 修复：quota 扣减加行级锁（`findByUserIdAndEntIdForUpdate`）或依赖 `@Version` 乐观锁 + 重试。

### M5 Webhook 入站验签 fail-open + 非常量时间比较

- 位置：`module/channel/service/WebhookService.java#verifyInboundSignature`
- 现象：签名为空 → 返回 true；配置无 secret → 返回 true（跳过校验）。攻击者省略签名头即绕过。且 `computed.equals(signature)` 非常量时间比较，存在时序侧信道。
- 修复：要求验签的渠道必须配置 secret 且签名缺失即拒绝；比较用 `MessageDigest.isEqual`。

### M6 service 层向上返回 Entity，违反分层约束

- 位置：`WebhookService.listActive()`/`create()` 返回 `WebhookConfig`，`EntitlementService.listUserQuotas` 返回 `EntitlementQuota` 等。
- 依据：架构约束"service 层禁止返回 Entity 给 controller（必须转 VO/DTO）"。
- 修复：补 VO 转换；至少 controller 出参不得为实体。

### M7 CiCdService 构建缓存无界 + JSON 字符串拼接

- 位置：`aaf-auto-dev/.../git/CiCdService.java`
- 现象：`buildCache`（ConcurrentHashMap）只增不删，长期运行内存泄漏（对称性：写入无淘汰）；`triggerWorkflow` 用 `"{"ref":"%s",...}".formatted(ref, ...)` 拼 JSON，ref 含引号即破坏/注入。另 `static HttpClient/ObjectMapper` 与项目注入风格不一致，`queryLatestRunId` 用 `Thread.sleep(2000)` 阻塞。
- 修复：缓存改有界（LRU/TTL）；JSON 用 ObjectMapper 构建；统一依赖注入。

### M8 JWT 密钥处理隐患

- 位置：`aaf-framework/.../security/SecurityConfig.java#jwtSecretKey`、`JwtProperties`
- 现象：`properties.secret().getBytes()` 用平台默认字符集（应显式 UTF-8）；Hս256 要求 ≥256bit（32 字节）密钥，配置过短会运行时报错或安全性不足，无启动期长度校验。
- 修复：显式 `StandardCharsets.UTF_8`；启动校验密钥长度并给出明确报错。

## Minor（建议改进）

| # | 位置 | 问题 | 建议 |
|---|------|------|------|
| m1 | 多数 `*Repository` 查询方法（如 `findByStatusAndDeletedFalse`） | `BaseEntity` 已有 `@SQLRestriction("deleted=false")` 全局过滤，方法名再带 `DeletedFalse` 冗余且风格不一 | 去掉冗余 `DeletedFalse`，统一依赖全局过滤 |
| m2 | `engine/space`、`evolution`、`semanticcalc`、`dsl`、`metadata`、`monitor` 等 | 大量"v0.2+/v0.3+ 实现"的占位接口，无实现 | 违反"简洁优先/禁占位"——按需创建，未到实现阶段先不声明接口 |
| m3 | `module/ai/role`、`module/ai/skill` 等 | 包内结构不统一（文件平铺 vs controller/service/domain 分层混用） | 统一模块内分层结构 |
| m4 | `aaf-api/.../module/examples/**` | 示例/demo 代码混入主 api 模块并参与构建 | 隔离到独立 example profile 或移除 |
| m5 | `framework/security/OperatorContext#currentUserId` | `default` 方法注释"兼容旧调用"，与"禁兼容层"精神相悖 | 直接统一调用方后删除别名 |
| m6 | `config/GlobalExceptionHandler` | 未显式处理 `AuthenticationException`/`AccessDeniedException`，`ConstraintViolationException` 直接回传 `e.getMessage()` 可能泄露内部信息 | 补 401/403 处理，约束信息做脱敏 |

## 对称性检查结果（审查清单逐条）

| # | 检查项 | 结论 |
|---|--------|------|
| 2 | 创建 vs 删除 | ⚠️ Webhook 失败累计停用有，但 `buildCache` 写入无淘汰（M7） |
| 5 | 注册 vs 注销 | ✅ `TenantFilter` finally 中 `TenantContext.clear()` 对称，正确 |
| 6 | 资源申请 vs 释放 | ⚠️ `ScriptSandbox` 临时文件成功路径删除，异常路径未 `deleteIfExists`（轻微泄漏） |
| 7 | 状态变更 vs 通知 | ⚠️ 充值入账与订单状态变更非幂等（M3） |
| 8 | 认证 vs 鉴权 | 🔴 多处 authenticated 通过后未按资源归属做 authorization（B1/B2/B4/M1/M2） |
| 9 | 成功路径 vs 错误路径 | ⚠️ 验签/租户过滤错误路径 fail-open（B1/M5） |
| 11 | 缓存写入 vs 失效 | ✅ PermissionCache evict/evictAll 对称；但存在重复实现（B6） |
| 13 | 已有模式 vs 新建抽象 | 🔴 PermissionCacheService、ScriptExecutor/ScriptSandbox 并行抽象（B5/B6） |

## 重构建议（系统级）

- 安全基线统一：建立"当前操作者只从 `OperatorContext` 取"的硬约束，控制器禁止接受 `userId` 作为身份入参；补一条 ArchUnit/审查规则。
- 鉴权基线：所有写操作与管理端点默认需要 `@PreAuthorize`，公开端点白名单集中在 `SecurityConfig` 显式声明。
- 租户隔离重做：orgId 来自身份而非请求头，fail-closed；framework 层 repository 一并纳入；明确 workspace 维度策略。
- 脚本执行收敛为单一受限实现（优先 GraalVM 受限上下文），删除误导性"沙箱"。
- 清理投机性抽象：未进入实现阶段的引擎接口（space/evolution/semanticcalc/dsl/...）按需再建，降低理解与维护成本。
- 财务一致性：积分/权益所有增减走悲观锁或乐观锁 + 幂等键（bizId），补充并发与重复回调的验收测试。

## 需人类决策的事项（🔴 高风险）

- B1（租户模型）、B2/B3（支付与积分铸造）、B4（部署接口鉴权）属架构/安全/资金安全级变更，按协作规范须**人类审核后**再进入开发修复。
- MOCK 鉴权（`MockTokenConfig`/`MockTokenFilter`）与 MOCK 支付渠道：确认是否已有部署层保证绝不在生产启用；建议追加 `@Profile("!prod")` 双保险。

## 备注

- 本报告为跨模块抽样审查，未逐文件覆盖。修复 B1/B2/M1/M2 时应按"模式"全量排查同类控制器/服务。
- 若按任务推进修复，建议拆分为：安全鉴权（B1/B2/B3/B4/M1/M2/M5）、并发与幂等（M3/M4）、重复与抽象清理（B5/B6/m2/m5）、规范细节（其余 minor）四组。
