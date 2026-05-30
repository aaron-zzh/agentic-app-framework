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
| RBAC 数据模型 | `Role`(sys_role)、`Permission`(sys_menu_permission, `@Entity MenuPermission`，含 code)、`RolePermission`(sys_role_permission)、`UserRole` | L1 | ✅ 模型在，未接 PermissionEvaluator |
| 记录规则引擎 | `DataAccessService.buildSpecification(entitySlug,userId)` → JPA `Specification`（allow/deny、`$user.xxx`、无匹配→拒绝全部=404），`DataAccessRule`(sys_data_access_rule) | L3 | ✅ 引擎在，未自动注入 |
| 字段级权限 | `Permission`(sys_permission, entitySlug/action/fieldAccess JSONB) | L3 | 🚧 模型在，未强制 |
| AI 委托 | `PermissionScope`(record) + `AssistantPermissionEvaluator`(evaluateToolCall/evaluateOperation→EvalResult) | 委托 | ✅（M34 fail-open 待修） |
| 实时授权 | `HumanApprovalService` | 实时 | 🚧（M35/M36 待修） |
| 默认拒绝门控 | `ControllerAuthorizationTest`（FreezingArchRule） | L1 | ✅（ab62e06） |
| 未用机制 | `@AccessControl` + `AccessControlAspect` | L1 | ⚠️ 0 使用，本方案退役 |
| 关系权限 ReBAC | —（无 Neo4j 关系图） | L2 | ❌ 新建 |
| ABAC 策略引擎 | —（置信度门控散落） | L4 | ❌ 新建 |
| 功能权限 SpEL 出口 | —（无 `PermissionEvaluator` 实现） | L1 | ❌ 新建 |

## 总体架构：统一鉴权管线

一次请求/Agent 执行，依次穿过四层（任一拒绝即止）：

```text
入口(REST/WS/A2A/MCP) → 认证(SecurityConfig: JWT/ApiKey → OperatorContext)
  → [Phase1] 快速检查：登录态 + super_admin 快速通道
  → [Phase2] L1 功能权限 @PreAuthorize("hasPermission(null,'模块:资源:动作')")
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

> **AG-UI 权限控制**：助理通过 AG-UI 接口对话时，工具调用走 `AssistantPermissionEvaluator`，以委托者身份进行完整四层权限检查，详见"AI 委托权限模型"章节。

## 认证层

| 方式 | 组件 | 说明 |
|------|------|------|
| JWT | `SecurityConfig`（已实现） | claims：`sub`=userId、`operatorType`、`roles`、`orgId`；校验 issuer/aud/黑名单；STATELESS |
| API Key | `ApiKeyAuthFilter`（已实现，待补强） | 须强制 `scope`/`allowedTables`（修 M9：当前仅授 ROLE_API_KEY 不校验 scope） |
| OAuth2 第三方 | Spring Authorization Server | 第三方登录 |
| 多端 | — | Web/小程序 JWT 2h + Refresh 7d；CLI API Key 长期；Agent 会话级，无独立 token |

**Authority 注入（新建 `JwtAuthenticationConverter`）**：JWT → `ROLE_*`（角色）+ 功能权限 authority（`PERM_模块:资源:动作`，可选，供 `PermissionEvaluator` 快速判定）。Agent 无独立 token——通过请求头 `X-Assistant-Id` 或会话，`SecurityOperatorContext` 解析为 `assistant:{id}`，`currentOwnerId` 回落委托者。

API Key、第三方 OAuth、多端 token 均与用户绑定，`currentOwnerId` 始终回落到 userId。

## Operator 主体模型

复用 `OperatorContext`：`currentOperatorId`（user 或 assistant）、`currentOwnerId`（始终 user，AI 时为委托者）、`currentOperatorType`。所有层以 `currentOwnerId` 作数据归属、以 `currentOperatorId` 作审计主体。

## Layer 1 — 功能权限（RBAC）

**规范注解 = `@PreAuthorize`**（裁决见决策记录）。退役 `@AccessControl` 的鉴权用途。

### 权限码设计（vs Odoo 模型权限）

我们采用**权限码**方式，而非 Odoo/quarkus-starter 的模型级权限（`perm_read/write/create/delete` 四个布尔）。

| | Odoo 模型权限 | 我们的权限码 |
|--|-------------|------------|
| 动作粒度 | 固定 4 个（CRUD） | 任意扩展 |
| 能否有"发布"权限 | ❌ 只有 write | ✅ `document:publish` |
| 能否有"审核"权限 | ❌ 没有 | ✅ `document:approve` |
| 能否按钮级控制 | ❌ 模型级 | ✅ `document:batch_delete` |
| 适合场景 | ERP（业务基本是 CRUD） | 平台框架（业务操作任意） |

Odoo 够用是因为 ERP 业务基本都是 CRUD。AAF 作为平台框架需要支持任意业务操作，权限码更合适。

**权限码格式**：`模块:资源:动作`（三段式，不可省略模块前缀，避免跨模块同名冲突）

```
system:user:create      — 系统模块，用户管理，创建
ai:agent:deploy         — AI 模块，Agent 管理，部署
kb:document:publish     — 知识库模块，文档，发布
billing:subscription:cancel — 计费模块，订阅，取消
```

### 菜单与权限分离

**菜单 ≠ 权限**，两者独立：

- **菜单**（`sys_menu`）：UI 导航结构，控制用户看到什么入口，角色直接关联菜单
- **权限**（`sys_menu_permission`，待改名为 `sys_permission`）：安全边界，控制用户能做什么操作，角色关联权限码集合

现有 `sys_menu_permission` 把两者混在一张表，需要拆分（见开放问题 5）。拆分后：
- 菜单可见性：角色 → 菜单（直接关联，参考 quarkus-starter 的 `system_role_menu_rel`）
- 操作权限：角色 → 权限码（`@PreAuthorize("hasPermission(null, 'document:publish')")`）
- 菜单可以引用权限码（可选，用于前端按钮级显隐），但不强制绑定

> 前端隐藏菜单/按钮不是安全边界，接口层的 `@PreAuthorize` 才是真正的安全边界。

**角色体系**：

| 角色码 | Spring 权限 | 说明 |
|--------|-------------|------|
| `SUPER_ADMIN` | `ROLE_SUPER_ADMIN` | 跨组织超管 |
| `ADMIN` | `ROLE_ADMIN` | 组织管理员（B9 现用） |
| `MEMBER` | `ROLE_MEMBER` | 普通成员 |
| `GUEST` | `ROLE_GUEST` | 只读 |
| `AGENT` | `ROLE_AGENT` | AI 主体 |

- **`RoleHierarchy` bean**：`SUPER_ADMIN > ADMIN > MEMBER > GUEST` → `hasRole('ADMIN')` 对超管自动放行。
- **SpEL 出口（新建 `PermissionEvaluator`）**：统一 L1 功能权限和 L2 对象级权限的检查入口：

```java
// L1 功能权限（权限码）
@PreAuthorize("hasPermission(null, 'system:user:create')")

// L2 对象级权限（ReBAC）
@PreAuthorize("hasPermission(#id, 'document', 'can_read')")
```

- **粗→细迁移**：B9 当前 `hasRole('ADMIN')`/`isAuthenticated()` 是合法的 L1 实现；迁移仅把表达式换成 `hasPermission(...)`，注解位置不变。
- 自定义角色：组织管理员可建，基于权限码组合，不得超 `org_admin`；支持继承内置角色。内置角色是基础骨架，实际系统以动态自定义角色为主。

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

**直接复用并升级** `DataAccessService.buildSpecification(entitySlug, userId)`。

### 规则定义格式（升级）

现有 `DataAccessRule.condition` 只支持单条件，升级为支持 AND/OR/NOT 组合的结构化 domain（参考 Odoo `ir.rule` 的 domain 表达式）：

```json
// 单条件（叶子节点，向下兼容现有格式）
{"field": "createBy", "op": "eq", "value": "$user.id"}

// AND 组合
{"and": [
  {"field": "orgId",  "op": "eq", "value": "$user.orgId"},
  {"field": "status", "op": "eq", "value": "ACTIVE"}
]}

// OR 组合（同角色多条规则 OR 合并，不同角色规则 AND 叠加，与 Odoo 一致）
{"or": [
  {"field": "createBy", "op": "eq", "value": "$user.id"},
  {"field": "orgId",    "op": "eq", "value": "$user.orgId"}
]}

// NOT
{"not": {"field": "status", "op": "eq", "value": "DELETED"}}
```

支持的 op：`eq`、`ne`、`gt`、`lt`、`in`、`like`

支持的 value 变量（动态上下文，运行时解析）：
- `$user.id` → 当前用户 ID（`currentOwnerId`）
- `$user.orgId` → 当前用户组织 ID
- `$user.teamIds` → 当前用户所在团队 ID 列表

### 实现：DomainSpecification

`DataAccessService.buildSpecification` 内部改用 `DomainSpecification`，递归解析 domain JSON 生成 JPA `Predicate`：

```java
// 伪代码，展示核心逻辑
private Predicate buildPredicate(JsonNode node, Root<?> root, CriteriaBuilder cb, Map<String, Object> ctx) {
    if (node.has("and")) return cb.and(/* 递归 */);
    if (node.has("or"))  return cb.or(/* 递归 */);
    if (node.has("not")) return cb.not(/* 递归 */);
    // 叶子：解析 field/op/value，value 中 $user.xxx 替换为实际值
    return switch (node.get("op").asText()) {
        case "eq"  -> cb.equal(root.get(field), resolvedValue);
        case "in"  -> root.get(field).in((Collection<?>) resolvedValue);
        // ...
    };
}
```

### 无规则时的行为

**没有配置记录规则的实体不受影响**：

- `buildSpecification` 查不到该 `entitySlug` 的规则 → 返回 `null`
- `BaseCrudService` 检查 `null` → 直接跳过注入，不生成任何额外 WHERE 条件
- 查询行为与未接入 L3 完全一致

```java
// BaseCrudService 中的 null 短路
Specification<E> ruleSpec = dataAccessService.buildSpecification(entitySlug(), userId);
if (ruleSpec != null) spec = spec.and(ruleSpec);  // null 时跳过，零开销
```

### 列表查询重复执行问题

**问题**：`page()` 查询时，每次都要查 `sys_data_access_rule` 表计算规则，高并发下重复执行。

**Odoo 的解法**：`_compute_domain` 加 `@ormcache(uid, model, mode)`，同一用户同一模型的规则只计算一次，存进进程内缓存，规则变更时 `clear_cache()`。

**我们的对应方案**：两级缓存

```
请求进来
  ↓
L1：ThreadLocal 请求级缓存（同一请求内多次调用 buildSpecification 只查一次）
  ↓ miss
L2：Redis 缓存（key = data_rule:{entitySlug}:{userId}，TTL 5min）
  ↓ miss
查 sys_data_access_rule 表，结果写入 Redis
```

规则变更时（管理员修改规则）：主动删除对应 Redis key，下次请求重新计算。

### 落地点：BaseCrudService

所有业务 CRUD 都走 `BaseCrudService`，在此统一注入规则，对业务代码透明：

| 方法 | 实现 |
|------|------|
| `page()` | `buildSpec(request).and(ruleSpec)`，SQL 过滤 |
| `getById()` | `findOne(idSpec.and(ruleSpec))`，找不到统一返回 404（不暴露是否存在） |
| `update()` | `exists(idSpec.and(ruleSpec))` 校验后再改 |
| `delete()` | `exists(idSpec.and(ruleSpec))` 校验后再删 |
| `create()` | 不校验（新建时无记录可校验） |

`entitySlug()` 返回 `null` 的 Service 完全跳过 L3，零额外开销。

### 其他补充

- **组织隔离变量**：扩展 `buildUserContext` 注入 `orgId/deptPath/teamIds`，支持五级隔离。
- **字段级**：`DataPermission.fieldAccess` → 响应序列化时脱敏/裁剪（Jackson `@JsonView`）。
- **模型级 CRUD 权限**（参考 Odoo `ir.model.access`）：`EntityDef` 上自动生成增删改查四项 `MenuPermission`，与 L1 功能权限码统一管理。

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

**实现**：规则存 `sys_data_access_rule`（扩展 `condition_type=ABAC`），用 SpEL 动态求值，规则热加载。与 `ConfidenceGate` 联动：Agent 执行时复用其风险等级判断。

## AI 委托权限模型

### 主体模型

```
请求主体分两类：
  Human 请求：JWT → userId → 走标准 L1/L2/L3/L4
  Assistant 请求：JWT(userId) + X-Assistant-Id → 双主体
    - operatorId  = assistantId（审计主体）
    - ownerId     = delegatorId（委托者，权限归属）
    - 实际权限    = 委托者(L1 ∩ L2 ∩ L3) ∩ PermissionScope 白名单
```

### 新建组件

**`AssistantContextHolder`**（ThreadLocal）：存当前请求的 `{assistantId, delegatorId}`。

**`AssistantAuthFilter`**（`OncePerRequestFilter`，在 `ApiKeyAuthFilter` 之后）：
```
读 X-Assistant-Id 请求头
  → 无：跳过（普通用户请求）
  → 有：查 AssistantDefinitionRepository
      → 找不到：403 fail-closed（修 M34，原为 fail-open）
      → 找到：取 delegatorId，存 AssistantContextHolder
```

**`SecurityOperatorContext`**（改造）：
```
currentOperatorId()  → 有 AssistantContext → assistantId；无 → JWT userId
currentOperatorType()→ 有 AssistantContext → AI；无 → HUMAN
currentOwnerId()     → 有 AssistantContext → delegatorId；无 → JWT userId
```

**`AssistantPermissionEvaluator`**（改造，补委托者实际权限检查）：
```
evaluateToolCall(assistantId, toolName, riskLevel):
  1. 查 AssistantDefinition → 找不到 → denied（fail-closed）
  2. 取 delegatorId
  3. 检查会话授权（见下节）→ 全量授权则跳过步骤 4
  4. PermissionSecurityService.hasPermission(delegatorId, 对应权限码)
     → 委托者没有 → denied（不能超越委托者权限边界）
  5. scope.isToolAllowed(toolName) → 不在白名单 → denied/ASK
  6. 风险等级检查 → 超限 → denied/ASK
  7. 全部通过 → granted
```

### 会话授权机制（参考 Kiro 三级信任模型）

用户无需每次为助理的工具调用逐一审批，支持三级授权粒度：

**级别 1：逐次审批（默认）**
- 每次工具调用触发 `overLimitAction=ASK`，通过 WebSocket 推送用户确认
- 用户选择：**允许一次**（仅本次）/ **本次会话信任**（级别 2）/ **拒绝**

**级别 2：会话级工具信任**
- 用户对某个工具选择"本次会话信任"，或通过对话指令 `@助理 信任工具 image_search`
- 存 Redis：`session_tool_trust:{sessionId}:{toolName} = true` TTL=会话时长
- 同一会话内该工具后续调用自动放行，不再弹审批
- **批量级联**：用户信任某工具时，当前批次中同一工具的所有待审批调用自动放行（对应 Kiro 的 Allow Always 级联）

**级别 3：会话全量授权**
- 用户通过对话指令 `@助理 授权全部权限` 或 API `POST /api/chat/sessions/{sessionId}/grant-full-delegation`
- 存 Redis：`session_delegation:{sessionId} = full` TTL=会话时长
- 会话内跳过 scope 白名单检查（步骤 5），但委托者实际权限约束（步骤 4）不可绕过
- 会话结束自动失效，不持久化

**信任优先级**（高→低）：
```
1. session_delegation:full（会话全量）
2. session_tool_trust:{tool}（会话工具级）
3. AssistantDefinition.permissionScope（助理定义的静态白名单）
4. 默认：逐次审批
```

### 完整请求流程

```
用户发消息（JWT + X-Assistant-Id）
  ↓
AssistantAuthFilter → AssistantContextHolder{assistantId, delegatorId}
  ↓
SecurityOperatorContext：currentOwnerId = delegatorId
  ↓
ResilientChatService → AiCreditGuard.precheck(delegatorId)  ← 积分以委托者计费
  ↓
LLM 决定调用工具
  ↓
AssistantPermissionEvaluator.evaluateToolCall()
  → 委托者 L1 权限（PermissionSecurityService）
  → 会话授权检查（Redis）
  → scope 白名单 / 风险等级
  ↓
通过 → 工具执行
  → 数据查询：DataAccessService.buildSpecification(delegatorId)  ← 行级权限以委托者过滤
不通过 → ASK → WebSocket 推送用户 → 确认/拒绝/超时失效
```

### 审计

- `operatorId = assistantId`（谁执行的）
- `ownerId = delegatorId`（谁的数据）
- 会话授权变更记审计日志（何时授权、何时撤销）

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
| 2 功能/关系 | `hasPermission(null, code)`（RBAC）+ `hasPermission(id, type, rel)`（ReBAC） | `AafPermissionEvaluator` |
| 3 数据 | 查询注入行级 `Specification` | `DataAccessService` |
| 4 条件 | 置信度/时间/IP（Agent） | `PolicyEngine` |

性能目标：admin <1ms、缓存命中 <2ms、未命中 5–15ms。

## 既有重复收敛（禁并行抽象）

| 重复 | 厘清 |
|------|------|
| `Permission`(sys_menu_permission, code) vs `Permission`(sys_permission, entitySlug/action/field) | 前者=**L1 功能权限码**来源（`PermissionEvaluator`）；后者=**L3 字段/操作级**数据权限。职责分明，**不合并表**，但命名建议改为 `MenuPermission` / `DataPermission` 消歧 |
| 两套 `PermissionService`（role 模块 vs permission 模块） | 归一为单一功能权限出口（`PermissionSecurityService` 后端），删冗余（审查「重复2」） |
| `@AccessControl` + `AccessControlAspect` | **退役鉴权用途**；feature-toggle/rate-limit 若需保留，拆为独立横切注解 |
| `Role`(sys_role) vs `AiRole`(ai_role) | 不同语义：系统角色 vs AI 能力集（技能/工具白名单），保留两者 |

## 关键接口与类清单（新建汇总）

| 组件 | 类型 | 职责 |
|------|------|------|
| `AccessDecisionService` | 接口 | 四层统一外观（非注解/Agent 调用） |
| `AafPermissionEvaluator` | `PermissionEvaluator` | L1 `hasPermission(null, code)` + L2 `hasPermission(id, type, rel)` |
| `AafPermissionEvaluator` | `PermissionEvaluator` | L2 hasPermission(obj) → RelationGraph |
| `RelationTupleService` / `RelationGraph` | 接口 | ReBAC 写入源（PG）/ 查询（Neo4j） |
| `RelationTupleSyncListener` | 监听器 | PG→Neo4j + 缓存失效 |
| `RecordRuleSupport` | JPA 基类/切面 | L3 自动注入 `buildSpecification` |
| `PolicyEngine` | 接口 | L4 ABAC 动态条件 |
| `AuthorizationRequestService` | 服务 | 实时授权请求 + WebSocket + Redis 临时权限 |
| `JwtAuthenticationConverter` | bean | JWT claims → ROLE_*/PERM_* authorities |
| `RoleHierarchy` | bean | 角色层级（超管绕过） |

## 落地与迁移（一次性设计，一次性实现）

- **L1 即 B9**：鉴权矩阵 + FreezingArchRule 为 L1 落地执行体；先 `hasRole`/`isAuthenticated` 清零 blocker，再统一切 `hasPermission(null, code)`（表达式替换，注解不动）。
- **迁移脚本**：`permission_tuple` 建表；角色层级与内置角色 seed；功能权限码 seed（对齐菜单）；`sys_data_access_rule` 已有。
- **Neo4j**：引入图 schema + 同步监听；ReBAC `@PreAuthorize("hasPermission(...)")` 接入对象级接口（document/space/file 等）。
- **AI/实时**：增强 `AssistantPermissionEvaluator` 叠加委托者实际权限；修 M34/M35/M36。

## 开放问题（待评审确认）

1. ~~规范注解 `@PreAuthorize`（推荐）+ `@AccessControl` 退役。~~ ✅ 确认
2. ~~ReBAC 是否必须 Neo4j，还是先 PG 递归 CTE 起步。~~ ✅ 先 PG 递归 CTE
3. ~~L3 自动注入采用「JPA Repository 基类」还是「Hibernate @Filter+拦截器」。~~ ✅ BaseCrudService 统一注入
4. ~~**功能权限码命名是否统一为 `模块:资源:动作`**。~~ ✅ 确认三段式，模块前缀不可省略
5. **`sys_menu_permission` 拆分为 `sys_menu`（菜单）+ `sys_permission`（权限码）是否纳入本轮**。

## 决策记录

| 日期 | 决策 | 结论 | 理由 |
|------|------|------|------|
| 2026-05-30 | 规范鉴权注解 | `@PreAuthorize`（含 `hasPermission` 扩展） | 与愿景 §10.2 一致、SpEL 覆盖 RBAC+ReBAC+SELF、零自研、存量+门控就位 |
| 2026-05-30 | `@AccessControl` | 退役鉴权用途 | 与 Spring 授权重复，正交关注点不混入授权注解 |
| 2026-05-30 | L3 记录规则 | 复用 `DataAccessService`，补自动注入 | 引擎已实现，禁重造 |
| 2026-05-30 | AI 委托主体 | 双主体：JWT 认证用户，`X-Assistant-Id` 标识助理，`currentOwnerId` 始终是委托者 | 助理无独立 token，权限归属必须回落委托者 |
| 2026-05-30 | AI 委托权限检查 | 委托者实际权限（L1/L2/L3）∩ scope 白名单，fail-closed | 不能超越委托者自身权限；M34 fail-open 修复 |
| 2026-05-30 | 会话授权三级模型 | 逐次审批 → 会话工具信任 → 会话全量授权，参考 Kiro 信任机制 | 避免每次弹审批影响体验，同时保留委托者实际权限约束不可绕过 |
| 2026-05-30 | L1 权限模型选型 | 权限码（`模块:资源:动作`）而非 Odoo 模型权限（4个布尔） | AAF 是平台框架，业务操作不止 CRUD，需要 publish/approve/export 等任意扩展 |
| 2026-05-30 | 菜单与权限分离 | `sys_menu`（菜单）和 `sys_permission`（权限码）独立，不强制绑定 | 菜单是 UI 导航，权限是安全边界，两者职责不同；前端隐藏不是安全边界 |
| 2026-05-30 | SpEL 出口 | 用 Spring 标准 `PermissionEvaluator` 替代 `@ss` bean | 统一 L1 功能权限和 L2 对象级权限入口，更符合 Spring Security 规范 |
| 2026-05-30 | ReBAC 存储 | 先 PG 递归 CTE，Neo4j 留待业务复杂后迁移 | 零新增依赖起步，图遍历需求强时再迁 |

## 相关文档

- [access-control.md](access-control.md) — 功能/愿景设计（上游）
- [security.md](security.md) — 加密/脱敏/审计
- [operator.md](../operator.md) — Operator（Human/Agent）主体模型
- [docs/design/audit/2026-05-30-service-review/10-authorization-matrix.md](../../audit/2026-05-30-service-review/10-authorization-matrix.md) — B9 鉴权矩阵（L1 落地工单）
