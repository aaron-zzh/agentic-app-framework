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
| 🔴 blocker | 3 | 是 |
| 🟠 major | 3 | 是 |
| 🟡 minor | 6 | 否 |

> 注：数量按本报告当前保留条目统计；完整现状以 [分区复审 README](2026-05-30-service-review/README.md) 为准。本次为抽样审查，未覆盖每一个文件。

## Blocker（必须修复，阻塞发布）

### B1 多租户隔离仍有仓储覆盖与 workspace 行过滤缺口

- 位置：租户过滤基础设施、framework 非标准仓储与 workspace 数据访问链路
- 剩余风险：framework 中不经过标准 CRUD/租户切面的非标准仓储尚未统一覆盖；`workspace_id` 维度缺少一致的行级过滤策略。
- 修复：将 framework 非标准仓储纳入统一租户约束，并在资源定义与查询执行层补齐 workspace 行级隔离。

### B4 AutoDev webhook 无验签且部署环境无白名单

- 位置：`aaf-auto-dev/.../git/GitController.java`、`CiCdService.java`
- 剩余风险：`/webhook/github` 未校验 `X-Hub-Signature-256`，可伪造事件污染构建状态；部署接口的 `environment` 仍缺少服务端白名单。
- 修复：webhook 增加 HMAC 验签；部署环境使用服务端白名单并按环境分级授权。

### B5 ScriptSandbox 名为沙箱实则无隔离 + 黑名单可绕过

- 位置：`aaf-framework/.../engine/tool/ScriptSandbox.java`
- 现象：类注释声称"资源限制、文件系统隔离"，但 `executePython` 直接 `ProcessBuilder("python3", ...)` 起子进程，**无文件系统/网络/资源隔离**，脚本可读写 JVM 用户可达的任意文件。`executeShell` 用 `isDangerous` 关键词黑名单（`rm -rf`/`dd if=` 等）防护，黑名单对命令注入是反模式，极易绕过（多空格、`find -delete`、`python -c`、base64 等）。
- 对比：同包 `GraalVmScriptExecutor` 用 `HostAccess.NONE/IOAccess.NONE/allowNativeAccess(false)` 才是真隔离。两套并存且能力不对等。
- 修复：要么统一走 GraalVM 受限上下文，要么子进程方案配合 OS 级隔离（容器/seccomp/独立低权用户），并把误导性的"沙箱"注释改成与实现一致；shell 执行改白名单或直接禁用。

## Major（严重，应当修复）

### M1 当前用户接口仍保留危险 userId/fallback 签名

- 位置：`module/pay/controller/CreditController.java` 等。
- 剩余风险：接口仍暴露 `@RequestParam Long userId`，并在上下文缺失时作为 fallback；危险身份签名和降级路径尚未清理。
- 修复：当前用户接口删除 userId 参数与 fallback；管理员代查另设显式鉴权接口。

### M6 service 层向上返回 Entity，违反分层约束

- 位置：`WebhookService.listActive()`/`create()` 返回 `WebhookConfig`，`ChannelConfigService` 返回 `ChannelConfig`。
- 依据：架构约束"service 层禁止返回 Entity 给 controller（必须转 VO/DTO）"。
- 修复：补 VO 转换；至少 controller 出参不得为实体。

### M7 CiCdService 使用静态 HttpClient + 阻塞轮询

- 位置：`aaf-auto-dev/.../git/CiCdService.java`
- 现象：HTTP 客户端仍为 `static` 实例，与项目依赖注入风格不一致；`queryLatestRunId` 用 `Thread.sleep(2000)` 等待 GitHub 创建 run，阻塞当前执行线程。
- 修复：统一注入 `HttpClient`；改用异步轮询或调度回查，避免同步 sleep。

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
| 5 | 注册 vs 注销 | ✅ 租户上下文在 finally 中清理，对称正确 |
| 6 | 资源申请 vs 释放 | ⚠️ `ScriptSandbox` 临时文件成功路径删除，异常路径未 `deleteIfExists`（轻微泄漏） |
| 7 | 状态变更 vs 通知 | ✅ 支付状态迁移已原子化，积分入账有 `idempotency_key` 唯一约束兜底 |
| 8 | 认证 vs 鉴权 | 🔴 租户仓储覆盖、部署约束和危险 userId fallback 仍不完整（B1/B4/M1） |
| 11 | 缓存写入 vs 失效 | ✅ PermissionCache evict/evictAll 对称 |
| 13 | 已有模式 vs 新建抽象 | 🔴 `ScriptExecutor`/`ScriptSandbox` 并行抽象（B5） |

## 重构建议（系统级）

- 身份基线统一：当前用户只从 `OperatorContext` 取，删除控制器 userId 参数及 fallback；管理员代查使用独立鉴权接口。
- 鉴权基线：所有写操作与管理端点默认需要 `@PreAuthorize`，公开 webhook 以签名作为来源认证。
- 租户隔离补齐：将 framework 非标准仓储纳入 org 过滤，并明确 workspace 行级隔离策略。
- 脚本执行收敛为单一受限实现（优先 GraalVM 受限上下文），删除误导性"沙箱"。
- 清理投机性抽象：未进入实现阶段的引擎接口（space/evolution/semanticcalc/dsl/...）按需再建，降低理解与维护成本。
- 财务一致性：积分/权益所有增减走悲观锁或乐观锁 + 数据库唯一幂等键，补充并发回调验收测试。（充值入账与权益扣减已落地，其余资金链路继续对齐）

## 需人类决策的事项（🔴 高风险）

- B1（租户覆盖）、B4（webhook 与部署约束）属架构/安全/资金安全级变更，按协作规范须**人类审核后**再进入开发修复。
- MOCK 鉴权（`MockTokenConfig`/`MockTokenFilter`）：确认部署层生产隔离，并增加代码侧环境硬约束（MOCK 支付渠道已加 `@Profile("!prod")`）。

## 备注

- 本报告为跨模块抽样审查，未逐文件覆盖。修复 B1/M1 时应按"模式"全量排查同类控制器、服务与非标准仓储。
- 若按任务推进修复，建议拆分为：安全与隔离（B1/B2/B4/M1）、并发与幂等（M3/M4）、脚本与抽象清理（B5/m2/m5）、分层与规范细节（M6/M7 及其余 minor）四组。
