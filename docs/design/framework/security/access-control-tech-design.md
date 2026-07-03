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

> 本文是 [access-control.md](access-control.md)（功能设计）的**完整技术落地方案**：把四层模型（RBAC + ReBAC + 记录规则 + ABAC）连同认证、AI 委托、实时授权、组织隔离，一次性给出可实现的接口签名、数据模型、枚举管线、存储与同步。**不分阶段**——本文是完整技术蓝图；实现先后由任务拆分（tasks）安排，但设计一次定全。

## 范围与原则

- 覆盖愿景全部范围：认证、Operator 主体、Layer 1–4、AI 委托收窄、实时交互授权、组织隔离、存储架构、检查流程、缓存。
- **复用优先、禁止并行抽象**：已存在的实现直接接入（下表），仅对真实缺口新建组件，并明确去重既有重复（两套 Permission / 两套 PermissionService / 未用的 @AccessControl）。

## 既有资产清单（复用，禁止重造）

| 关注点 | 既有实现 | 层 | 状态 |
|------|---------|----|------|
| 主体抽象 | `OperatorContext`（`currentOperatorId/currentOwnerId/currentOperatorType/isAuthenticated`） | — | ✅ |
| 认证 | `SecurityConfig`（JWT OAuth2 RS + `ApiKeyAuthFilter` + `@EnableMethodSecurity`），`MockTokenConfig` | 认证 | ✅ |
| RBAC 数据模型 | `Role`(sys_role)、`Permission`(sys_menu_permission，待拆为 `sys_menu` + `sys_permission_code`)、`RolePermission`(sys_role_permission)、`UserRole` | L1 | ✅ 模型在，未接 PermissionEvaluator |
| 记录规则引擎 | `DataAccessService.buildSpecification(entitySlug,userId)` → JPA `Specification`（allow/deny、`$user.xxx`；无规则默认放行），`DataAccessRule`(sys_data_access_rule) | L3 | ✅ 引擎在，未自动注入 |
| 字段级权限 | `Permission`(sys_permission, entitySlug/action/fieldAccess JSONB) | L3 | 🚧 模型在，未强制 |
| AI 委托 | `PermissionScope`(record) + `AssistantPermissionEvaluator`(evaluateToolCall/evaluateOperation→EvalResult) | 委托 | ✅（M34 fail-open 待修） |
| 实时授权 | `HumanApprovalService` + `HumanApprovalController` + SSE | 实时 | ✅ requestId UUID、resolve 授权校验、SSE 推送 |
| 默认拒绝门控 | `ControllerAuthorizationTest`（FreezingArchRule） | L1 | ✅（ab62e06） |
| 未用机制 | `@AccessControl` + `AccessControlAspect` | L1 | ⚠️ 0 使用，本方案退役 |
| 关系权限 ReBAC | —（无 `permission_tuple` 与 PG 递归 CTE 查询） | L2 | ❌ 新建 |
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

调用关系：`@PreAuthorize` 场景统一进入 `AafPermissionEvaluator`；Agent 工具调用统一进入 `AssistantPermissionEvaluator`；批处理、内部服务、非注解场景调用 `AccessDecisionService`。`AccessDecisionService` 可以复用 `AafPermissionEvaluator` / `PolicyEngine` / `DataAccessService`，但 `AafPermissionEvaluator` 不反向依赖 `AccessDecisionService`，避免循环。

> **AG-UI 权限控制**：助理通过 AG-UI 接口对话时，工具调用走 `AssistantPermissionEvaluator`，以委托者身份进行完整四层权限检查，详见"AI 委托权限模型"章节。

## 认证层

| 方式 | 组件 | 说明 |
|------|------|------|
| JWT | `SecurityConfig`（已实现） | claims：`sub`=userId、`operatorType`、`roles`、`orgId`；校验 issuer/aud/黑名单；STATELESS |
| API Key | `ApiKeyAuthFilter`（已实现，待补强） | 须强制 `scope`/`allowedTables`（修 M9：当前仅授 ROLE_API_KEY 不校验 scope） |
| OAuth2 第三方 | Spring Authorization Server | 第三方登录 |
| 多端 | — | Web/小程序 JWT 2h + Refresh 7d；CLI API Key 长期；Agent 会话级，无独立 token |

**Authority 注入（新建 `JwtAuthenticationConverter`）**：JWT → `ROLE_*`（角色）+ `permissionVersion`。功能权限码不默认全量写入 JWT，避免权限变更必须等待 token 过期；`PermissionEvaluator` 优先读 Redis/本地缓存，发现 JWT 中 `permissionVersion` 落后于服务端版本时拒绝旧上下文并要求刷新认证。若内网短生命周期 token 需要极致性能，可选择性注入 `PERM_模块:资源:动作` authority，但必须绑定 `permissionVersion` 校验。Agent 无独立 token——通过请求头 `X-Assistant-Id` 或会话，`SecurityOperatorContext` 解析为 `assistant:{id}`，`currentOwnerId` 回落委托者。

API Key、第三方 OAuth、多端 token 均与用户绑定，`currentOwnerId` 始终回落到 userId。

## Operator 主体模型

复用 `OperatorContext`：`currentOperatorId`（user 或 assistant）、`currentOwnerId`（始终 user，AI 时为委托者）、`currentOperatorType`。所有层以 `currentOwnerId` 作数据归属、以 `currentOperatorId` 作审计主体。

### 权限执行上下文

内部批处理、AI 工具、管理员代办等场景可通过 `PermissionExecutionService.runAsOwner(userId, reason, ...)` 临时以目标用户的权限边界执行。该机制只覆盖 `OperatorContext.currentOwnerId()`，不改变 `currentOperatorId()` 与 `currentOperatorType()`，因此：

- 权限判定、记录规则、数据归属按目标用户执行。
- 审计主体仍是真实操作者或助理，不能用于伪造操作者身份。
- 实现基于 `ThreadLocal`，必须使用服务封装或 try-with-resources 自动清理；异步、WebFlux、消息队列链路需要显式传递上下文。

该上下文优先级高于 AI 委托上下文：如果当前线程已标记代某用户执行，则 `currentOwnerId()` 返回该用户；否则 AI 请求返回委托者，普通请求返回 JWT 用户。

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
- **权限码**（`sys_permission_code`）：安全边界，控制用户能做什么操作，角色关联权限码集合

现有 `sys_menu_permission` 把两者混在一张表，需要拆分（见开放问题 5）。拆分后：
- 菜单可见性：角色 → 菜单（直接关联，参考 quarkus-starter 的 `system_role_menu_rel`）
- 操作权限：角色 → 权限码（`@PreAuthorize("hasPermission(null, 'document:publish')")`）
- 菜单可以引用 `sys_permission_code.code`（可选，用于前端按钮级显隐），但不强制绑定

> 前端隐藏菜单/按钮不是安全边界，接口层的 `@PreAuthorize` 才是真正的安全边界。

菜单管理实体本身接入通用 CRUD：

```text
GET /api/system/menus/my-tree   当前用户菜单树，仅要求登录，按 sys_role_menu 过滤
GET /api/system/menus/tree      管理端完整菜单树，要求 system:menu:manage
GET /api/system/menus           通用分页查询，要求 system:menu:manage
GET /api/system/menus/_query    通用查询窗口，要求 system:menu:manage
POST/PUT/DELETE /api/system/menus... 通用 CRUD，要求 system:menu:manage
```

因此 `sys_menu.permission_code` 仍只是 UI 辅助字段；菜单入口授权的主关系是 `sys_role_menu`。

**角色体系**：

| 角色码 | Spring 权限 | 说明 |
|--------|-------------|------|
| `SUPER_ADMIN` | `ROLE_SUPER_ADMIN` | 跨组织超管 |
| `ADMIN` | `ROLE_ADMIN` | 组织管理员（B9 现用） |
| `MEMBER` | `ROLE_MEMBER` | 普通成员 |
| `GUEST` | `ROLE_GUEST` | 只读 |
| `AGENT` | `ROLE_AGENT` | AI 主体（不独立放大权限，仅用于主体类型/审计识别） |

- **`RoleHierarchy` bean**：`SUPER_ADMIN > ADMIN > MEMBER > GUEST` → `hasRole('ADMIN')` 对超管自动放行。
- 角色命名映射：业务/数据库角色码可使用 `super_admin`、`org_admin`、`member`、`guest`、`agent`；Spring authority 统一映射为 `ROLE_SUPER_ADMIN`、`ROLE_ADMIN`、`ROLE_MEMBER`、`ROLE_GUEST`、`ROLE_AGENT`。
- **SpEL 出口（新建 `PermissionEvaluator`）**：统一 L1 功能权限和 L2 对象级权限的检查入口：

```java
// L1 功能权限（权限码）
@PreAuthorize("hasPermission(null, 'system:user:create')")

// L2 对象级权限（ReBAC）
@PreAuthorize("hasPermission(#id, 'document', 'can_read')")
```

- **粗→细迁移**：B9 当前 `hasRole('ADMIN')`/`isAuthenticated()` 是合法的 L1 实现；迁移仅把表达式换成 `hasPermission(...)`，注解位置不变。
- 自定义角色：组织管理员可建，基于权限码组合，不得超 `org_admin`；支持继承内置角色。内置角色是基础骨架，实际系统以动态自定义角色为主。

### 通用 CRUD 动态权限

继承 `BaseCrudController` 的标准实体接口不要求业务 Controller 为每个 CRUD 方法覆写 `@PreAuthorize`。基类统一使用 Spring Security 表达式：

```java
@PreAuthorize("@crudAuth.can(#root.getThis(), 'read')")
```

`CrudPermissionAuthorizer` 从 Controller 解析到对应 `BaseCrudService`，再由 Service 生成最终权限码：

```text
{permissionModule}:{permissionResource}:{action}
```

默认：

- `permissionModule()` = `system`
- `permissionResource()` = `entitySlug()`
- `permissionCode(action)` = `{module}:{resource}:{action}`

标准动作映射：

| 接口类别 | 动作 |
|------|------|
| `page` / `_query` / `get` / `_batch-read` / `_options` / `_meta` / `_group` | `read` |
| `create` / `_import` / `_validate` | `create` |
| `update` / `_restore` | `update` |
| `delete` / `_batch-delete` / `_archive` | `delete` |
| `_export` | `export` |

业务实体只需在 Service 覆写资源段或特殊聚合权限：

```java
@Override
protected String permissionResource() {
    return "role"; // system:role:create
}

@Override
protected String permissionCode(String action) {
    return "system:data-access-rule:manage"; // 管理型资源统一 manage
}
```

这样安全出口仍然是 `@PreAuthorize`，底层仍走 `AccessDecisionService` / `PermissionSecurityService`，但减少纯粹为了权限注解产生的 Controller 覆写。特殊业务动作（如 publish/approve/execute）仍由业务 Controller 显式声明 `@PreAuthorize("hasPermission(null, '...')")`。

## Layer 2 — 关系权限（ReBAC，新建）

借鉴 Zanzibar 关系元组 `<object>#<relation>@<subject>`，PostgreSQL 为真理源并通过递归 CTE 完成起步查询。Neo4j 仅作为未来关系复杂度提升后的可替换查询后端，当前实现不引入 Neo4j 同步链路。

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
public interface RelationQueryService {            // PG 递归 CTE 查询
    boolean hasPath(String subjType,String subjId,String objType,String objId,String permission,int maxDepth);
}
// Spring 集成：自定义 PermissionEvaluator 调 RelationQueryService
//   @PreAuthorize("hasPermission(#id, 'document', 'can_read')")
@Component public class AafPermissionEvaluator implements PermissionEvaluator { ... }
```

**缓存失效**：`permission_tuple` 写 → 领域事件 → 失效 Redis 权限缓存。后续若引入 Neo4j，再增加 PG → Neo4j 的投影同步监听器。

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
    return switch (node.get("op").asString()) {
        case "eq"  -> cb.equal(root.get(field), resolvedValue);
        case "in"  -> root.get(field).in((Collection<?>) resolvedValue);
        // ...
    };
}
```

### 无规则时的行为

**没有配置记录规则的实体不受影响**：

- `entitySlug()` 返回 `null` → 该 Service 明确不接入 L3，直接跳过
- `buildSpecification` 查不到该 `entitySlug` 的规则 → 返回 `null`
- `BaseCrudService` 检查 `null` → 直接跳过注入，不生成任何额外 WHERE 条件
- 查询行为与未接入 L3 完全一致，即**无规则默认放行**

```java
// BaseCrudService 中的 null 短路
Specification<E> ruleSpec = dataAccessService.buildSpecification(entitySlug(), userId);
if (ruleSpec != null) spec = spec.and(ruleSpec);  // null 时跳过，零开销
```

### 列表查询重复执行问题

**问题**：`page()` 查询时，每次都要查 `sys_data_access_rule` 表计算规则，高并发下重复执行。

**Odoo 的解法**：`_compute_domain` 加 `@ormcache(uid, model, mode)`，同一用户同一模型的规则只计算一次，存进进程内缓存，规则变更时 `clear_cache()`。

**我们的对应方案**：两级缓存，但缓存对象分层：

```
请求进来
  ↓
L1：ThreadLocal 请求级缓存
    - key = data_rule:{entitySlug}:{userId}:{ruleVersion}
    - value = 本请求内可复用的 normalizedDomain / Specification 编译结果
    - 请求结束必须 clear，避免线程复用污染
  ↓ miss
L2：Redis 缓存
    - key = data_rule:{entitySlug}:{userId}:{ruleVersion}
    - value = normalizedDomain JSON / 无规则标记
    - TTL 5min
  ↓ miss
查 sys_data_access_rule 表，合并 allow/deny 规则，标准化后写入 Redis
```

Redis **不缓存** JPA `Specification` / `Predicate` 对象；它们和 Criteria 上下文绑定，只能在请求内编译使用。规则变更时（管理员新增/修改/删除规则）：更新 `ruleVersion` 并主动删除相关 Redis key；由于 L3 是无规则默认放行，新增规则也必须清理“无规则标记”缓存。

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

绕过 `BaseCrudService` 的自定义查询必须显式调用 `DataAccessService.buildSpecification` 或在技术设计中说明豁免原因；涉及用户数据的 Repository 直查不允许静默绕过 L3。

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

**实现**：ABAC 策略独立建模为 `sys_access_policy`（或同等策略表），用 SpEL 动态求值，规则热加载。`sys_data_access_rule` 只承载 L3 行级数据规则，避免行级规则和动态策略混层。与 `ConfidenceGate` 联动：Agent 执行时复用其风险等级判断。

ABAC 只缓存策略定义和编译后的 SpEL 表达式，不缓存最终决策结果。最终决策依赖时间、IP、风险等级、置信度、Agent 会话等动态上下文，除非缓存 key 包含完整上下文，否则禁止缓存 allow/deny 结果。

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
  3. 检查会话授权（见下节）→ 解析本次会话的临时 scope 扩展
  4. PermissionSecurityService.hasPermission(delegatorId, 对应权限码)
     → 委托者没有 → denied（不能超越委托者权限边界）
  5. effectiveScope.isToolAllowed(toolName) → 不在白名单 → denied/ASK
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
- 存 Redis：`session_tool_trust:{sessionId}:{toolName} = {userId}` TTL=会话时长
- 同一会话内该工具后续调用自动放行，不再弹审批
- **批量级联**：用户信任某工具时，当前批次中同一工具的所有待审批调用自动放行（对应 Kiro 的 Allow Always 级联）

**级别 3：会话全量授权**
- 用户通过对话指令 `@助理 授权全部权限` 或 API `POST /api/chat/sessions/{sessionId}/grant-full-delegation`
- 存 Redis：`session_delegation:{sessionId} = {userId}` TTL=会话时长
- 语义是**用户在当前会话临时扩展助理 scope 到委托者权限上限**，不是绕过委托模型
- 会话内 `effectiveScope = 委托者实际权限范围`；委托者实际权限约束（步骤 4）不可绕过
- L4 风险门控仍然生效：HIGH 必须人工确认，CRITICAL 必须人工执行
- 会话结束自动失效，不持久化

**信任优先级**（高→低）：
```
1. session_delegation:full（会话全量临时扩展 scope）
2. session_tool_trust:{tool}（会话工具级）
3. AssistantDefinition.permissionScope（助理定义的静态白名单）
4. 默认：逐次审批
```

### AI 能力目录与工具路由

AI 可调用能力分为两类，底层分表、分执行器，上层统一发现：

```text
业务动作 ACTION
  -> ai_action_catalog
  -> business_action.execute(action, entity, params)
  -> BaseCrudService / 业务 Service
  -> 业务权限码：system:role:update / workflow:approve

通用工具 TOOL
  -> ai_tool_catalog
  -> tool.execute(toolName, arguments)
  -> ToolCallback / MCP / GeneratedTool
  -> 工具权限码：tool:{toolName}:execute / tool:default:execute
```

不合并 `ai_action_catalog` 与 `ai_tool_catalog`：

- action 面向业务对象，有实体、字段集、行级/字段级数据权限。
- tool 面向能力调用，有来源、工具类型、分类、MCP/脚本/HTTP/生成式能力、只读标记和工具风险。
- action 权限必须回到真实业务权限；tool 权限使用工具权限码。

AI 对外可通过统一能力清单发现 action/tool：

```text
type: ACTION | TOOL
key: system-role.query | executeBusinessAction | web_search
displayName
description
inputSchema
riskLevel
requireConfirm
category
```

当前实现已落地：

- `ai_action_catalog`：业务动作目录，存在 SQL Provider 时 fail-closed，未配置/未启用动作不可见且不可执行。
- `ai_tool_catalog`：工具目录，存在 SQL Provider 时 fail-closed，未配置/未启用工具不可执行。
- `ToolCallDispatcher`：工具调用统一入口，执行顺序为工具存在 → SQL catalog enabled → 永久工具权限 → 置信度门控 → 会话/风险门控 → 类型专用门控 → 权益/积分预检 → `ToolCallback.call` → 成功后扣费 → `tool_call_audit`。
- `AssistantPermissionEvaluator`：工具权限码优先读取 `ai_tool_catalog.permission_code`，否则回退 `tool:{toolName}:execute` 和 `tool:default:execute`。
- `ContentGenerationTool`：生成式内容工具注册点，当前开放 `generateImage`、`generateVideo`，由 `ai_tool_catalog.tool_type=GENERATIVE` 与 `category=IMAGE_GENERATION/VIDEO_GENERATION` 控制展示、权限、确认和额度。
- `ContentSafetyService`：生成前内容审查 SPI。默认实现普通请求放行；高风险生成式请求进入统一 HITL 内容复审。生产环境应替换为模型审查、策略审查或人工复审组合实现。

通用工具权限建议：

```text
低风险默认工具：tool:default:execute
业务动作总入口：tool:business-action:execute
高风险工具：tool:http-request:execute / tool:file-write:execute / tool:code-execute:execute
生成式能力：tool:image-generate:execute / tool:video-generate:execute
```

`tool:default:execute` 只作为低风险兜底，不允许覆盖高风险工具。高风险工具必须在 `ai_tool_catalog` 中显式配置专属 `permission_code`、`risk_level` 和 `require_confirm`。

工具调用返回统一 JSON，AI 根据 `code` 和 `resume.strategy` 判断是否等待并恢复执行：

```json
{
  "success": false,
  "code": "PENDING_APPROVAL",
  "message": "工具 generateVideo 需要用户确认",
  "pendingApproval": true,
  "recoverable": true,
  "authorization": {
    "mode": "USER_APPROVAL",
    "approvalId": "approval-uuid",
    "requiredBy": "用户即时确认"
  },
  "resume": {
    "strategy": "WAIT_APPROVAL",
    "token": "approval-uuid",
    "instruction": "用户确认后使用相同参数重试"
  }
}
```

常见阻塞码：

- `FORBIDDEN`：永久权限不足，不能由会话临时授权绕过，需要管理员给 owner 授予业务/工具权限。
- `PENDING_APPROVAL`：当前用户可即时确认，确认后同会话同工具可恢复执行。
- `INSUFFICIENT_CREDITS`：积分或权益不足，需要充值、升级套餐或额度恢复后重试。
- `PENDING_CONTENT_REVIEW`：内容安全审查等待中，审查通过后使用相同参数恢复执行；拒绝后相同参数返回稳定拒绝。
- `TOOL_DISABLED` / `TOOL_NOT_REGISTERED`：能力未开放或代码未注册，AI 应停止调用并解释原因。

生成式内容作为 TOOL，不作为 ACTION。原因是图片、视频、音乐、3D 等消耗外部模型成本，核心治理是工具权限、额度、风险确认、内容审查和资产审计，不依赖业务实体行级权限。若生成结果需要发布、入库审批或修改业务数据，则后续步骤再通过 ACTION 或业务 Service 执行。

Action 链和 Tool 链的权限处理方式不同：

- Action 面向业务对象，必须回到业务权限码，并通过 `BaseCrudService` 应用行级/字段级数据权限。
- Tool 面向能力调用，必须回到工具权限码，并按 `tool_type/category` 应用工具风险、内容安全、额度、沙箱等治理。

Action 链执行顺序：

```text
AI 身份已认证，owner/delegator 已绑定
-> executeBusinessAction 工具入口权限
-> 解析 action/entity/params
-> ai_action_catalog 已启用
-> 当前 owner 拥有业务 permissionCode
-> 置信度门控（低置信且不可验证则等待用户确认）
-> Action 风险等级与 requireConfirm
-> Action 权益/积分预检
-> BaseCrudService 执行业务，自动应用 L2/L3 数据权限和字段权限
-> 成功后扣费/记录用量
-> 工具审计；后续补 Action 专用审计
```

Tool 链执行顺序：

```text
工具注册存在
-> ai_tool_catalog 已启用
-> 当前 owner 拥有 permission_code
-> 置信度门控（低置信且不可验证则等待用户确认）
-> Assistant/Role 委托边界、会话授权与工具风险确认
-> 工具风险等级与 require_confirm
-> 按 tool_type/category 执行专用门控（如内容安全、沙箱、网络策略）
-> 权益/积分预检（仅 entitlement_code 有值且 cost_expression > 0 的工具）
-> 执行 ToolCallback
-> 成功后扣费/记录用量
-> 写入审计
```

工具执行不采用一条固定流程，而是按 `ai_tool_catalog.tool_type/category` 选择门控编排：

| 类型 | 典型 category | 执行顺序 |
|------|---------------|----------|
| 业务动作入口 | `BUSINESS_ACTION` | 工具入口权限 → 委托/会话授权 → 置信度门控 → 工具风险确认 → 调 `executeBusinessAction` → 进入 Action 链 |
| 只读工具 | `RETRIEVAL` / `SEARCH` | 工具权限 → 委托/会话授权 → 置信度门控 → 只读自动通过 → 执行工具 → 审计 |
| 写入/外部副作用工具 | `FILE_WRITE` / `HTTP_MUTATION` / `WORKFLOW` | 工具权限 → 委托/会话授权 → 置信度门控 → 风险确认 → 权益/积分预检 → 执行工具 → 成功后扣费/记录用量 → 审计 |
| 生成式工具 | `IMAGE_GENERATION` / `VIDEO_GENERATION` / `MUSIC_GENERATION` / `MODEL3D_GENERATION` | 工具权限 → 委托/会话授权 → 置信度门控 → 风险确认 → 内容安全预检 → 权益/积分预检 → 执行生成 → 成功后扣费/记录用量 → 审计 |
| 代理/Agent 工具 | `AGENT_DELEGATION` | 工具权限 → 委托边界检查 → 置信度门控 → 会话授权/风险确认 → 子 Agent 执行 → 审计 |

实现约束：

- 风险确认在扣费前执行。用户拒绝或超时不应消耗额度。
- 置信度门控在风险确认和扣费前执行。低置信且不可验证时直接进入人工确认。
- 生成式工具的内容安全审查在扣费前执行。被拒绝或等待复审时不扣费。
- HITL 是统一人工介入层，触发来源包括 `TOOL_PERMISSION`、`ACTION_CONFIRM`、`LOW_CONFIDENCE`、`CONTENT_REVIEW`、`CREDIT_RECOVERY`。前三类审批通过后按 `grantScope` 写入会话授权；低置信授权使用独立 `confidence:{subject}` 键，不会顺带绕过工具或 Action 的风险确认；内容复审通过后按 `sessionId + userId + toolName + prompt` 记住同一请求的审查结果。
- Tool 链是否走权益/积分由 `ai_tool_catalog.entitlement_code` 与 `cost_expression` 决定；未配置权益的免费工具不扣费。
- `executeBusinessAction` 工具入口通常不扣费，具体业务动作是否收费由 Action 链的 `ai_action_catalog.entitlement_code` 决定。
- 收费工具只在工具返回成功后扣费；如果工具返回结构化 `success=false`，不扣费。
- `FORBIDDEN` 代表永久权限不足，不能通过会话临时授权绕过。
- `PENDING_APPROVAL`、`PENDING_CONTENT_REVIEW`、`INSUFFICIENT_CREDITS` 都是可恢复状态，AI 应等待外部状态变化后用相同参数重试。

### 完整请求流程

```
用户发消息（JWT + X-Assistant-Id）
  ↓
AssistantAuthFilter → AssistantContextHolder{assistantId, delegatorId}
  ↓
SecurityOperatorContext：currentOwnerId = delegatorId
  ↓
ResilientChatService / TokenMeteringHook → AiCreditGuard.precheck(delegatorId)  ← 积分以委托者计费
  ↓
LLM 决定调用工具
  ↓
AgentScopeToolGovernanceService 包装 AgentTool
  → ToolPermissionGuard
  → AssistantPermissionEvaluator.evaluateToolCall()
  → ToolPermissionChecker / ConfidenceGate / ContentSafety / CreditService
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

复用并修复 `HumanApprovalService`：**M35** 已完成（resolve 加授权校验 + requestId 不可预测 UUID）、**M36** 已通过 `HumanApprovalController` + SSE 端到端打通。临时会话授权 Redis：`session_tool_trust:{sessionId}:{toolName}`、`session_delegation:{sessionId}` TTL=会话。

审批完成后发布 `ApprovalResolvedEvent`：

- `TOOL_PERMISSION` / `LOW_CONFIDENCE` / `ACTION_CONFIRM` 通过后，`HitlApprovalGrantListener` 按 `grantScope=ONCE|SESSION|PATTERN` 写入 `ToolPermissionChecker`；其中 `TOOL_PERMISSION` 的工具授权同时写入 `AssistantSessionTrustService`，AI 使用相同参数重试即可恢复。
- `CONTENT_REVIEW` 通过后，`ContentSafetyService` 记录内容复审 key；相同会话、用户、工具、prompt 的重试不再重复创建复审单。
- `CREDIT_RECOVERY` 预留给充值、套餐升级、额度恢复等外部状态变化。额度恢复后 AI 使用原参数重试，由权益/积分预检重新判定。

### HITL 与治理链路结合点

HITL 不直接替代权限、内容安全或计费，它只负责把“可由人类/外部状态恢复”的阻塞点统一成审批单、事件和重试语义。各链路的结合点如下：

| 触发场景 | 所在链路 | 触发组件 | 审批类型 | 审批通过后的状态 | 恢复方式 |
|----------|----------|----------|----------|------------------|----------|
| 工具风险确认 / `require_confirm` | Tool | `ToolPermissionChecker.checkDetailed` | `TOOL_PERMISSION` | 写入 `ToolPermissionChecker` 会话授权；同步 `AssistantSessionTrustService` 工具信任 | AI 使用相同 `sessionId + toolName + arguments` 重试 |
| 工具低置信 | Tool | `ToolCallDispatcher` / `ToolPermissionGuard` + `ConfidenceGate` | `LOW_CONFIDENCE` | 写入 `confidence:{toolName}` 会话授权 | 重试后跳过本次低置信阻塞，仍继续执行工具风险、内容安全和计费 |
| 业务动作风险确认 | Action | `AiBusinessActionExecutor.checkRisk` | `ACTION_CONFIRM` | 写入 `{entitySlug}.{action}` 会话授权 | 重试后继续执行 Action 权益/积分预检和业务服务 |
| 业务动作低置信 | Action | `AiBusinessActionExecutor.checkConfidence` + `ConfidenceGate` | `LOW_CONFIDENCE` | 写入 `confidence:{entitySlug}.{action}` 会话授权 | 重试后跳过本次低置信阻塞，仍继续执行 Action 风险确认 |
| 生成式内容复审 | Tool 专用门控 | `ContentSafetyService.reviewBeforeGeneration` | `CONTENT_REVIEW` | 记录 `sessionId + userId + toolName + prompt` 的复审结果 | 审查通过后相同 prompt 重试进入额度预检和生成执行 |
| 额度不足 / 套餐恢复 | Tool / Action | `CreditService` 预检 | `CREDIT_RECOVERY`（预留） | 不写权限授权，等待额度外部状态变化 | 充值、升级或额度恢复后用原参数重试 |

AgentScope 集成点：

- `AgentScopeRuntime` 构建 `Toolkit` 后调用 `AgentScopeToolGovernanceService.apply`，把每个 `AgentTool` 包装为受治理工具。
- 包装工具在 `AgentTool.callAsync` 内适配为 Spring AI `ToolCallback`，统一进入 `ToolPermissionGuard`。因此 AgentScope 本地工具、MCP 工具、子 Agent 工具与外部 `ToolCallDispatcher` 使用同一套权限、HITL、内容安全和额度门控。
- AgentScope 自带 HITL 保留为执行暂停协议：`ToolSuspendException` / `GenerateReason.TOOL_SUSPENDED` 用来暂停 ReAct 循环。AAF 治理链返回 `PENDING_APPROVAL`、`PENDING_CONTENT_REVIEW`、`INSUFFICIENT_CREDITS` 等可恢复 JSON 时，包装工具抛出 `ToolSuspendException`，由 AgentScope 停在 pending tool 状态；审批、复审或额度恢复后使用相同会话继续执行。
- `AafToolWhitelistHook` 仍保留为模型调用前的轻量白名单/目录 enabled 保护；真正安全边界在 `ToolPermissionGuard`。
- `ResilientChatService` 和 `TokenMeteringHook` 结算时都优先使用 `OperatorContext.currentOwnerId()`，没有执行上下文时才回退到调用参数中的 userId；因此普通用户、AI 委托、管理员代办的积分归账口径一致。
- `TokenMeteringHook` 在 `PreReasoningEvent` 做 `AiCreditGuard.precheck`，在 `PostCallEvent` 记录 token 并调用 `AiCreditGuard.settle`。同步 Chat 由 `ResilientChatService.call` 处理；流式 Chat 在 `stream` 入口预检，并在流结束后按收到的最大 usage 发布用量事件。
- 每次模型调用生成唯一 `usageId`：`ai_token_usage.usage_id` 记录 token 用量，`credit_transaction.biz_id` 记录对应积分扣减流水，二者可按 `usageId` 做审计关联。token 到积分的换算由 `credit_token_rule` 生效规则决定，扣费时按 `CreditTokenRuleService.calculateCredits` 向上取整。
- 会员订阅与积分充值已经接入计费域：充值通过业务订单和支付单入账积分，订阅支付成功后激活 `subscription` 并实例化权益额度。当前支付通道以 MOCK 同步成功为第一版闭环，真实支付回调接入后复用相同的 `onPaySuccess` 入口。

结合边界：

- `FORBIDDEN` 是永久权限不足，不进入用户即时确认，必须由管理员补角色权限码或工具权限码。
- `PENDING_APPROVAL` 只解决当前会话可授权事项，不扩大 owner 的永久业务权限。
- `PENDING_CONTENT_REVIEW` 只解决内容安全复审，不跳过工具风险确认和额度预检。
- `INSUFFICIENT_CREDITS` 不应由工具审批直接放行，除非后续实现了明确的赠额、透支或管理员补额流程。
- 低置信授权键与风险授权键分离，避免“确认模型不确定”被误用为“同意执行高风险动作”。

## 存储架构

```
PostgreSQL（真理源）                         Redis（高速）
 用户/角色/权限码（sys_permission_code）      权限判定缓存(正/负)
 permission_tuple（PG 递归 CTE 查询）         会话临时权限
 sys_data_access_rule / sys_access_policy    授权请求
 审计日志/业务数据
```

同步：PG 写 → 领域事件 → 失效 Redis。L1/L2 权限判定缓存正负结果均存，TTL 5min，super_admin 跳过缓存，变更主动失效。Neo4j 若后续引入，只作为 `permission_tuple` 的查询投影，不改变 PG 真理源。

缓存 key 约定：

| 缓存 | key | value | 失效条件 |
|------|-----|-------|----------|
| L1 功能权限 | `perm:{userId}:{permissionCode}:{permissionVersion}` | allow/deny | 角色、权限码、角色权限关系、用户角色关系变更 |
| L2 关系权限 | `rebac:{subjectType}:{subjectId}:{objectType}:{objectId}:{permission}:{schemaVersion}` | allow/deny | `permission_tuple`、权限 Schema、对象归属关系变更 |
| L3 记录规则 | `data_rule:{entitySlug}:{userId}:{ruleVersion}` | normalizedDomain JSON / 无规则标记 | `sys_data_access_rule`、用户角色、组织/团队归属变更 |
| L4 ABAC 策略 | `access_policy:{policyVersion}` | 策略定义 / 编译表达式 | `sys_access_policy` 变更 |

不允许跨层混用缓存 key；版本号进入 key，用于避免变更风暴下误命中旧授权结果。

## 统一权限检查流程（四阶段，落到组件）

| Phase | 职责 | 组件 |
|------|------|------|
| 1 快速 | JWT 有效 + 用户态 + super_admin 快速通道 | `SecurityConfig` + `RoleHierarchy` |
| 2 功能/关系 | `hasPermission(null, code)`（RBAC）+ `hasPermission(id, type, rel)`（ReBAC） | `AafPermissionEvaluator` |
| 3 数据 | 查询注入行级 `Specification` | `DataAccessService` |
| 4 条件 | 置信度/时间/IP（Agent） | `PolicyEngine` |

性能目标：admin <1ms、缓存命中 <2ms、未命中 5–15ms。

`super_admin` 通过 JWT authority `ROLE_SUPER_ADMIN` 进入快速通道：L1 功能权限、L2 关系权限直接允许，L3 记录规则返回空过滤条件。L4 ABAC/置信度门控保留为风险控制层，除非具体策略明确允许超管跳过。

缓存边界：

- JWT 只作为认证和粗粒度角色上下文，不作为长期权限真理源；权限码缓存以服务端 `permissionVersion` 为准。
- L1/L2 可缓存 allow/deny，必须有主动失效和短 TTL 双保险。
- L3 Redis 只缓存规则 JSON / 标准化 domain，不缓存 `Specification` / `Predicate`。
- L4 只缓存策略定义，不缓存动态最终决策。
- ThreadLocal 缓存必须在请求结束清理；WebFlux/异步链路优先使用 Reactor Context 或显式上下文对象，避免线程切换导致上下文丢失。

## 既有重复收敛（禁并行抽象）

| 重复 | 厘清 |
|------|------|
| `Permission`(sys_menu_permission, code) vs `Permission`(sys_permission, entitySlug/action/field) | 前者迁移为 `sys_permission_code`，作为 **L1 功能权限码**来源（`PermissionEvaluator`）；后者保留为 **L3 字段/操作级**数据权限。职责分明，**不合并表**，实体命名建议改为 `PermissionCode` / `DataPermission` 消歧 |
| 两套 `PermissionService`（role 模块 vs permission 模块） | 归一为单一功能权限出口（`PermissionSecurityService` 后端），删冗余（审查「重复2」） |
| `@AccessControl` + `AccessControlAspect` | **退役鉴权用途**；feature-toggle/rate-limit 若需保留，拆为独立横切注解 |
| `Role`(sys_role) vs `AiRole`(ai_role) | 不同语义：系统角色 vs AI 能力集（技能/工具白名单），保留两者 |

## 关键接口与类清单（新建汇总）

| 组件 | 类型 | 职责 |
|------|------|------|
| `AccessDecisionService` | 接口 | 四层统一外观（批处理/内部服务等非注解场景调用） |
| `AafPermissionEvaluator` | `PermissionEvaluator` | L1 `hasPermission(null, code)` + L2 `hasPermission(id, type, rel)` |
| `RelationTupleService` / `RelationQueryService` | 接口 | ReBAC 写入源（PG）/ 查询（PG 递归 CTE） |
| `RelationTupleEventListener` | 监听器 | `permission_tuple` 变更后失效 Redis 权限缓存 |
| `RecordRuleSupport` | BaseCrudService 支撑组件 | L3 自动注入 `buildSpecification` |
| `PolicyEngine` | 接口 | L4 ABAC 动态条件 |
| `HumanApprovalService` / `HumanApprovalController` | 服务 + REST/SSE | 实时授权请求、SSE 推送、用户确认处理 |
| `AssistantSessionTrustService` | 服务 | 会话工具信任 + 会话全量委托 Redis TTL |
| `JwtAuthenticationConverter` | bean | JWT claims → ROLE_*/PERM_* authorities |
| `RoleHierarchy` | bean | 角色层级（超管绕过） |

## 落地与迁移（一次性设计，一次性实现）

- **L1 即 B9**：鉴权矩阵 + FreezingArchRule 为 L1 落地执行体；先 `hasRole`/`isAuthenticated` 清零 blocker，再统一切 `hasPermission(null, code)`（表达式替换，注解不动）。
- **迁移脚本**：`permission_tuple` 建表；`sys_permission_code` 建表/迁移；角色层级与内置角色 seed；功能权限码 seed（对齐菜单）；`sys_data_access_rule` 已有。
- **ReBAC**：先用 PG 递归 CTE 实现 `RelationQueryService`；ReBAC `@PreAuthorize("hasPermission(...)")` 接入对象级接口（document/space/file 等）。Neo4j 留作后续查询后端替换，不纳入本轮。
- **AI/实时**：增强 `AssistantPermissionEvaluator` 叠加委托者实际权限；修 M34/M35/M36。

## 开放问题（待评审确认）

1. ~~规范注解 `@PreAuthorize`（推荐）+ `@AccessControl` 退役。~~ ✅ 确认
2. ~~ReBAC 是否必须 Neo4j，还是先 PG 递归 CTE 起步。~~ ✅ 先 PG 递归 CTE
3. ~~L3 自动注入采用「JPA Repository 基类」还是「Hibernate @Filter+拦截器」。~~ ✅ BaseCrudService 统一注入
4. ~~**功能权限码命名是否统一为 `模块:资源:动作`**。~~ ✅ 确认三段式，模块前缀不可省略
5. ~~**`sys_menu_permission` 拆分为 `sys_menu`（菜单）+ `sys_permission_code`（权限码）是否纳入本轮**。~~ ✅ 权限码表命名为 `sys_permission_code`

## 决策记录

| 日期 | 决策 | 结论 | 理由 |
|------|------|------|------|
| 2026-05-30 | 规范鉴权注解 | `@PreAuthorize`（含 `hasPermission` 扩展） | 与愿景 §10.2 一致、SpEL 覆盖 RBAC+ReBAC+SELF、零自研、存量+门控就位 |
| 2026-05-30 | `@AccessControl` | 退役鉴权用途 | 与 Spring 授权重复，正交关注点不混入授权注解 |
| 2026-05-30 | L3 记录规则 | 复用 `DataAccessService`，补自动注入 | 引擎已实现，禁重造 |
| 2026-05-30 | AI 委托主体 | 双主体：JWT 认证用户，`X-Assistant-Id` 标识助理，`currentOwnerId` 始终是委托者 | 助理无独立 token，权限归属必须回落委托者 |
| 2026-05-30 | AI 委托权限检查 | 委托者实际权限（L1/L2/L3）∩ scope 白名单，fail-closed | 不能超越委托者自身权限；M34 fail-open 修复 |
| 2026-05-30 | 会话授权三级模型 | 逐次审批 → 会话工具信任 → 会话全量授权，参考 Kiro 信任机制 | 会话全量授权是用户临时扩展 scope 到委托者权限上限，不绕过委托者实际权限和 L4 风险门控 |
| 2026-05-30 | L1 权限模型选型 | 权限码（`模块:资源:动作`）而非 Odoo 模型权限（4个布尔） | AAF 是平台框架，业务操作不止 CRUD，需要 publish/approve/export 等任意扩展 |
| 2026-05-30 | 菜单与权限分离 | `sys_menu`（菜单）和 `sys_permission_code`（权限码）独立，不强制绑定 | 菜单是 UI 导航，权限是安全边界，两者职责不同；前端隐藏不是安全边界 |
| 2026-05-30 | SpEL 出口 | 用 Spring 标准 `PermissionEvaluator` 替代 `@ss` bean | 统一 L1 功能权限和 L2 对象级权限入口，更符合 Spring Security 规范 |
| 2026-05-30 | ReBAC 存储 | 先 PG 递归 CTE，Neo4j 留待业务复杂后迁移 | 零新增依赖起步，图遍历需求强时再迁 |

## 相关文档

- [access-control.md](access-control.md) — 功能/愿景设计（上游）
- [security.md](security.md) — 加密/脱敏/审计
- [operator.md](../operator.md) — Operator（Human/Agent）主体模型
- [docs/design/audit/2026-05-30-service-review/10-authorization-matrix.md](../../audit/2026-05-30-service-review/10-authorization-matrix.md) — B9 鉴权矩阵（L1 落地工单）
