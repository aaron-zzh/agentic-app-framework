---
level: Practice
layer: Model
purpose: AAF 访问控制完整技术方案（四层模型的可实现技术规范：接口/数据模型/枚举管线/存储同步）
status: draft
version: 1.0.0
date: 2026-05-30
author: AaronZZH & Kiro
---

# 访问控制完整技术方案

> 本文是 [access-control.md](access-control.md)（功能/愿景设计）的**完整技术落地方案**：把四层模型（RBAC + ReBAC + 记录规则 + ABAC）连同认证、AI 委托、实时授权、组织隔离，一次性给出可实现的接口签名、数据模型、枚举管线、存储与同步。**不分阶段**——本文是完整技术蓝图；实现先后由任务拆分（tasks）安排，但设计一次定全。

## 范围与原则

- 覆盖愿景全部范围：认证、Operator 主体、Layer 1–4、AI 委托收窄、实时交互授权、组织隔离、存储架构、检查流程、缓存。
- **复用优先、禁止并行抽象**：已存在的实现直接接入（下表），仅对真实缺口新建组件，并明确去重既有重复（两套 Permission / 两套 PermissionService / 未用的 @AccessControl）。

## 既有资产清单（复用，禁止重造）

| 关注点 | 既有实现 | 层 | 状态 |
|------|---------|----|------|
| 主体抽象 | `OperatorContext`（`currentOperatorId/currentOwnerId/currentOperatorType/isAuthenticated`） | — | ✅ |
| 认证 | `SecurityConfig`（JWT OAuth2 RS + `ApiKeyAuthFilter` + `@EnableMethodSecurity`），`MockTokenConfig` | 认证 | ✅ |
| RBAC 数据模型 | `Role`(sys_role)、`Permission`(sys_menu_permission, `@Entity MenuPermission`，含 code)、`RolePermission`(sys_role_permission)、`UserRole` | L1 | ✅ 模型在，未接 @ss |
| 记录规则引擎 | `DataAccessService.buildSpecification(entitySlug,userId)` → JPA `Specification`（allow/deny、`$user.xxx`、无匹配→拒绝全部=404），`DataAccessRule`(sys_data_access_rule) | L3 | ✅ 引擎在，未自动注入 |
| 字段级权限 | `Permission`(sys_permission, entitySlug/action/fieldAccess JSONB) | L3 | 🚧 模型在，未强制 |
| AI 委托 | `PermissionScope`(record) + `AssistantPermissionEvaluator`(evaluateToolCall/evaluateOperation→EvalResult) | 委托 | ✅（M34 fail-open 待修） |
| 实时授权 | `HumanApprovalService` | 实时 | 🚧（M35/M36 待修） |
| 默认拒绝门控 | `ControllerAuthorizationTest`（FreezingArchRule） | L1 | ✅（ab62e06） |
| 未用机制 | `@AccessControl` + `AccessControlAspect` | L1 | ⚠️ 0 使用，本方案退役 |
| 关系权限 ReBAC | —（无 Neo4j 关系图） | L2 | ❌ 新建 |
| ABAC 策略引擎 | —（置信度门控散落） | L4 | ❌ 新建 |
| 功能权限 SpEL 出口 | —（无 `@ss` bean） | L1 | ❌ 新建 |

## 总体架构：统一鉴权管线

一次请求/Agent 执行，依次穿过四层（任一拒绝即止）：

```text
入口(REST/WS/A2A/MCP) → 认证(SecurityConfig: JWT/ApiKey → OperatorContext)
  → [Phase1] 快速检查：登录态 + super_admin 快速通道
  → [Phase2] L1 功能权限 @PreAuthorize("@ss.hasPermission('模块:资源:动作')")
  → [Phase2] L2 关系权限 @PreAuthorize("hasPermission(#id,'document','can_read')")（ReBAC，对象级）
  → [Phase3] L3 记录规则：查询自动注入 DataAccessService.buildSpecification（行级）
  → [Phase4] L4 ABAC/置信度：PolicyEngine 评估动态条件（Agent 风险/时间/IP）
  → 执行 / 拒绝 / 实时申请确认
门控：ControllerAuthorizationTest 保证写接口必带 L1 注解（默认拒绝基线）
AI 主体：AssistantPermissionEvaluator 在上述之上叠加「委托者权限 ∩ scope」收窄
```

统一决策外观（新建，聚合各层，供非注解场景/Agent 调用）：

```java
public interface AccessDecisionService {
    /** 功能权限：模块:资源:动作 */
    boolean hasPermission(String permissionCode);
    /** 关系权限：对象级 */
    boolean hasPermission(String objectType, String objectId, String relationPermission);
    /** 记录规则：返回行级过滤 Specification */
    <T> Specification<T> recordRuleSpec(String entitySlug);
    /** ABAC：动态条件评估 */
    PolicyResult evaluatePolicy(AccessContext ctx);
}
```

> 问题：智能助理对话时通过的是 AG-UI 接口，如何在执行任务时受权限控制，需要单独处理吧？

## 认证层

| 方式 | 组件 | 说明 |
|------|------|------|
| JWT | `SecurityConfig`（已实现） | claims：`sub`=userId、`operatorType`、`roles`、`orgId`；校验 issuer/aud/黑名单；STATELESS |
| API Key | `ApiKeyAuthFilter`（已实现，待补强） | 须强制 `scope`/`allowedTables`（修 M9：当前仅授 ROLE_API_KEY 不校验 scope） |
| OAuth2 第三方 | Spring Authorization Server | 第三方登录 |
| 多端 | — | Web/小程序 JWT 2h + Refresh 7d；CLI API Key 长期；Agent 会话级，无独立 token |

**Authority 注入（新建 `JwtAuthenticationConverter`）**：JWT → `ROLE_*`（角色）+ 功能权限 authority（`PERM_模块:资源:动作`，可选，供 @ss 快速判定）。Agent 无独立 token——通过请求头 `X-Assistant-Id` 或会话，`SecurityOperatorContext` 解析为 `assistant:{id}`，`currentOwnerId` 回落委托者。

> 问题：API Key/第三方/多端 都是与用户绑定的吧

## Operator 主体模型

复用 `OperatorContext`：`currentOperatorId`（user 或 assistant）、`currentOwnerId`（始终 user，AI 时为委托者）、`currentOperatorType`。所有层以 `currentOwnerId` 作数据归属、以 `currentOperatorId` 作审计主体。

## Layer 1 — 功能权限（RBAC）

**规范注解 = `@PreAuthorize`**（裁决见决策记录）。退役 `@AccessControl` 的鉴权用途。

**角色体系**：

| 角色码 | Spring 权限 | 说明 |
|--------|-------------|------|
| `SUPER_ADMIN` | `ROLE_SUPER_ADMIN` | 跨组织超管 |
| `ADMIN` | `ROLE_ADMIN` | 组织管理员（B9 现用） |
| `MEMBER` | `ROLE_MEMBER` | 普通成员 |
| `GUEST` | `ROLE_GUEST` | 只读 |
| `AGENT` | `ROLE_AGENT` | AI 主体 |

- **`RoleHierarchy` bean**：`SUPER_ADMIN > ADMIN > MEMBER > GUEST` → `hasRole('ADMIN')` 对超管自动放行。
- **功能权限码**：`模块:资源:动作`（`system:user:create`），来源 `Role →(sys_role_permission)→ Permission(sys_menu_permission).code`。
- **SpEL 出口（新建 `@ss` = `PermissionSecurityService`）**：

```java
@Component("ss")
public class PermissionSecurityService {
    boolean hasPermission(String code);                 // 模块:资源:动作；super_admin 短路 true
    boolean hasAnyPermission(String... codes);
    boolean hasRole(String roleCode);
    boolean isOwner(String entitySlug, Long id);        // SELF 归属：比对 currentOwnerId
}
// 用法：@PreAuthorize("@ss.hasPermission('system:user:create')")
//      @PreAuthorize("@ss.isOwner('document', #id)")
```

- **粗→细迁移**：B9 当前 `hasRole('ADMIN')`/`isAuthenticated()` 是合法的 L1 实现；迁移仅把表达式换成 `@ss.hasPermission(...)`，注解位置不变。
- 自定义角色：组织管理员可建，基于权限码组合，不得超 `org_admin`；支持继承内置角色。

> 问题：角色应该是动态的吧，不是固定那几个吧

## Layer 2 — 关系权限（ReBAC，新建）

借鉴 Zanzibar 关系元组 `<object>#<relation>@<subject>`，PostgreSQL 为写入源、Neo4j 为查询图。

**数据模型（新建）**：

```sql
CREATE TABLE permission_tuple (
  id BIGSERIAL PRIMARY KEY,
  object_type VARCHAR(50) NOT NULL, object_id VARCHAR(100) NOT NULL,
  relation VARCHAR(50) NOT NULL,
  subject_type VARCHAR(50) NOT NULL, subject_id VARCHAR(100) NOT NULL,
  subject_relation VARCHAR(50), expires_at TIMESTAMP, granted_by VARCHAR(100),
  created_at TIMESTAMP DEFAULT NOW(),
  UNIQUE(object_type,object_id,relation,subject_type,subject_id));
```

**权限 Schema**：愿景 §5.6 的 types/relations/permissions（document/space/file/directory/tool）编译为继承检查规则（声明式，热加载）。

**接口（新建）**：

```java
public interface RelationTupleService {            // 写入源（PG）+ 发布同步事件
    void grant(String objType,String objId,String relation,String subjType,String subjId,Instant expiresAt);
    void revoke(...);
}
public interface RelationGraph {                   // Neo4j 查询
    boolean hasPath(String subjType,String subjId,String objType,String objId,String permission,int maxDepth);
}
// Spring 集成：自定义 PermissionEvaluator 调 RelationGraph
//   @PreAuthorize("hasPermission(#id, 'document', 'can_read')")
@Component public class AafPermissionEvaluator implements PermissionEvaluator { ... }
```

**同步**：`permission_tuple` 写 → 领域事件 → 监听器同步 Neo4j + 失效 Redis 缓存（PG 为真理源）。

> 与 `AssistantPermissionEvaluator` 区分：后者是 AI scope 白名单（模式匹配），属「委托收窄」；ReBAC 是人/Agent 统一的对象级关系权限。两者叠加（见 AI 委托节）。

## Layer 3 — 记录规则（数据权限）

**直接复用** `DataAccessService.buildSpecification(entitySlug, userId)`（已实现 allow/deny、`$user.xxx`、无匹配→拒绝全部）。需补两点：

- **自动注入（新建）**：实体标注 `@ModelName("user")`（或复用 `entitySlug`），提供 `RecordRuleRepository` 基类 / `@PostFilter` 之外的 Specification 自动附加——查询时自动 `and` 上 `buildSpecification`，避免每处手写。推荐：自定义 JPA `Repository` 基类在 `findAll`/分页处合并 Specification；或 Hibernate `@Filter` + 拦截器注入。
- **组织隔离变量**：扩展 `DataAccessService.buildUserContext` 注入 `orgId/deptPath/teamIds/groupId`，支持愿景 §5.9 的 group/org/dept/team/personal 五级（`org_id = ${user.orgId}` 等）。
- **字段级**：`Permission(sys_permission).fieldAccess` → 响应序列化时脱敏/裁剪（Jackson `@JsonView` 或自定义 `BeanSerializerModifier`）。

> 问题：给出具体方案，是否参考 odoo 的 模型 增删改查 权限 和 记录规则

## Layer 4 — ABAC + 置信度门控（新建）

轻量策略引擎，评估动态条件（不入图）：

```java
public interface PolicyEngine {
    PolicyResult evaluate(AccessContext ctx); // 主体/操作/对象/风险/置信度/时间/IP
}
```

置信度门控（与 Agent 执行路径联动）：

| 风险 | 置信度要求 | 策略 |
|------|-----------|------|
| LOW | 无 | 直接执行 |
| MEDIUM | >0.7 | 低于阈值需确认 |
| HIGH | — | 必须人工确认 |
| CRITICAL | — | 必须人工执行 |

> ABAC 如何实现？

## AI 委托权限模型

```
AI 实际权限 = 委托者(L1 ∩ L2 ∩ L3) ∩ PermissionScope 白名单
```

复用 `PermissionScope` + `AssistantPermissionEvaluator`，但需增强：当前 evaluator 只查 scope 白名单——补「委托者实际权限」检查（调 `AccessDecisionService` 以委托者身份判 L1/L2/L3），再 ∩ scope。**修 M34**：`AssistantDefinition` 未找到时由 fail-open 改 fail-closed（拒绝 + 记审计）。超出按 `OverLimitAction`（ASK/SKIP/PAUSE）。

> 问题：用户应该可以在一次对话中授权给助理自己的全部权限，否则需要每次申请，参考 kiro 工具审批过程，

## 实时交互授权

`overLimitAction=ASK` 触发：

```
AuthorizationRequest{ requestId, assistantId, delegatorId, sessionId, resource, operation, riskLevel, reason, expiresAt(默认5min) }
  → WebSocket 推送委托者 → 确认: 授临时权限(once/session, Redis) / 拒绝: 跳过 / 超时: 失效
```

复用并修复 `HumanApprovalService`：**修 M35**（resolve 加授权校验 + requestId 不可预测 UUID）、**M36**（推送端到端打通）。临时权限 Redis：`session_perm:{sessionId}:{resource}:{operation}` TTL=会话。

## 存储架构

```
PostgreSQL（真理源）         Neo4j（关系图）        Redis（高速）
 用户/角色/权限码           permission_tuple 镜像   权限判定缓存(正/负)
 permission_tuple           继承路径遍历            会话临时权限
 sys_data_access_rule       —                       授权请求
 审计日志/业务数据
```

同步：PG 写 → 领域事件 → ①同步 Neo4j ②失效 Redis。缓存正负结果均存，TTL 5min，super_admin 跳过缓存，变更主动失效。

## 统一权限检查流程（四阶段，落到组件）

| Phase | 职责 | 组件 |
|------|------|------|
| 1 快速 | JWT 有效 + 用户态 + super_admin 快速通道 | `SecurityConfig` + `RoleHierarchy` |
| 2 功能/关系 | `@ss.hasPermission`（RBAC）+ `hasPermission(obj)`（ReBAC，Neo4j+缓存） | `PermissionSecurityService` / `AafPermissionEvaluator` |
| 3 数据 | 查询注入行级 `Specification` | `DataAccessService` |
| 4 条件 | 置信度/时间/IP（Agent） | `PolicyEngine` |

性能目标：admin <1ms、缓存命中 <2ms、未命中 5–15ms。

## 既有重复收敛（禁并行抽象）

| 重复 | 厘清 |
|------|------|
| `Permission`(sys_menu_permission, code) vs `Permission`(sys_permission, entitySlug/action/field) | 前者=**L1 功能权限码**来源（@ss）；后者=**L3 字段/操作级**数据权限。职责分明，**不合并表**，但命名建议改为 `MenuPermission` / `DataPermission` 消歧 |
| 两套 `PermissionService`（role 模块 vs permission 模块） | 归一为单一功能权限出口（`PermissionSecurityService` 后端），删冗余（审查「重复2」） |
| `@AccessControl` + `AccessControlAspect` | **退役鉴权用途**；feature-toggle/rate-limit 若需保留，拆为独立横切注解 |
| `Role`(sys_role) vs `AiRole`(ai_role) | 不同语义：系统角色 vs AI 能力集（技能/工具白名单），保留两者 |

## 关键接口与类清单（新建汇总）

| 组件 | 类型 | 职责 |
|------|------|------|
| `AccessDecisionService` | 接口 | 四层统一外观（非注解/Agent 调用） |
| `PermissionSecurityService`(`@ss`) | bean | L1 SpEL：hasPermission/hasRole/isOwner |
| `AafPermissionEvaluator` | `PermissionEvaluator` | L2 hasPermission(obj) → RelationGraph |
| `RelationTupleService` / `RelationGraph` | 接口 | ReBAC 写入源（PG）/ 查询（Neo4j） |
| `RelationTupleSyncListener` | 监听器 | PG→Neo4j + 缓存失效 |
| `RecordRuleSupport` | JPA 基类/切面 | L3 自动注入 `buildSpecification` |
| `PolicyEngine` | 接口 | L4 ABAC 动态条件 |
| `AuthorizationRequestService` | 服务 | 实时授权请求 + WebSocket + Redis 临时权限 |
| `JwtAuthenticationConverter` | bean | JWT claims → ROLE_*/PERM_* authorities |
| `RoleHierarchy` | bean | 角色层级（超管绕过） |

## 落地与迁移（一次性设计，一次性实现）

- **L1 即 B9**：鉴权矩阵 + FreezingArchRule 为 L1 落地执行体；先 `hasRole`/`isAuthenticated` 清零 blocker，再统一切 `@ss.hasPermission`（表达式替换，注解不动）。
- **迁移脚本**：`permission_tuple` 建表；角色层级与内置角色 seed；功能权限码 seed（对齐菜单）；`sys_data_access_rule` 已有。
- **Neo4j**：引入图 schema + 同步监听；ReBAC `@PreAuthorize("hasPermission(...)")` 接入对象级接口（document/space/file 等）。
- **AI/实时**：增强 `AssistantPermissionEvaluator` 叠加委托者实际权限；修 M34/M35/M36。

## 开放问题（待评审确认）

1. 规范注解 `@PreAuthorize`（推荐）+ `@AccessControl` 退役 → 确认。
2. ReBAC 是否必须 Neo4j，还是先 PG 递归 CTE 起步（愿景倾向 Neo4j）。
3. L3 自动注入采用「JPA Repository 基类」还是「Hibernate @Filter+拦截器」。
4. 功能权限码与现有 `sys_menu_permission.code` 命名是否统一为 `模块:资源:动作`。
5. 两个 `Permission` 实体改名消歧（MenuPermission/DataPermission）是否纳入本轮。

## 决策记录

| 日期 | 决策 | 结论 | 理由 |
|------|------|------|------|
| 2026-05-30 | 规范鉴权注解 | `@PreAuthorize`（含 `@ss`/`hasPermission` 扩展） | 与愿景 §10.2 一致、SpEL 覆盖 RBAC+ReBAC+SELF、零自研、存量+门控就位 |
| 2026-05-30 | `@AccessControl` | 退役鉴权用途 | 与 Spring 授权重复，正交关注点不混入授权注解 |
| 2026-05-30 | L3 记录规则 | 复用 `DataAccessService`，补自动注入 | 引擎已实现，禁重造 |
| 2026-05-30 | AI 委托 | 复用 `PermissionScope`/`AssistantPermissionEvaluator` + 叠加委托者实际权限 | 禁重造，修 fail-open |
| 2026-05-30 | 关系权限存储 | Neo4j 镜像、PG 真理源、事件同步 | 图遍历优于递归 CTE |

## 相关文档

- [access-control.md](access-control.md) — 功能/愿景设计（上游）
- [security.md](security.md) — 加密/脱敏/审计
- [operator.md](../operator.md) — Operator（Human/Agent）主体模型
- [docs/design/audit/2026-05-30-service-review/10-authorization-matrix.md](../../audit/2026-05-30-service-review/10-authorization-matrix.md) — B9 鉴权矩阵（L1 落地工单）
