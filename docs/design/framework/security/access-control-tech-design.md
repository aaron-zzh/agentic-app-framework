---
level: Practice
layer: Model
purpose: AAF 当前访问控制运行时技术设计，定义权威业务 PDP、CRUD PEP、策略快照和一次性 challenge
status: draft
version: 1.1.0
date: 2026-07-23
author: AaronZZH & Kiro
---

# 访问控制运行时技术设计

> 本文描述当前源码已经实现的授权运行时。功能愿景见 [access-control.md](access-control.md)；实现以 `apps/service/` 下源码为准。

## 设计结论

- `AuthorizationService` 是资源型、数据型和动态策略型业务授权的权威 PDP；Spring Security 的 `isAuthenticated()` 与固定 `hasRole/hasAnyRole` 可作为认证和粗粒度角色门禁，但不能替代需要对象、数据范围或动态策略的业务 PDP。
- `AuthorizationMethodSecurityAdapter` 是 Spring Method Security 的 `PermissionEvaluator` 适配器，只组装请求，不实现独立裁决。
- 标准 `BaseCrudController` 只用 `isAuthenticated()` 建立认证门槛；CRUD 的权威 PEP 是 `CrudEnforcementService`，执行编排由 `BaseCrudService` 保证。
- CRUD 的 record 和 field 约束由 `CrudDataAuthorizationProvider` 通过 `AuthorizationService` 的 L3 决策返回；CRUD PEP 只应用类型安全约束，并与 tenant、personal 组成 JPA `Specification` 下推 SQL。
- 列表、查询窗口、批量读取、选择器、导出、更新和删除不会自动合并 L2 共享记录。
- 单对象读取默认先按完整 L3 加载；无论默认 L3 是否命中，返回前都以服务端 `CURRENT` 和 objectId 执行对象级 L4。只有业务 Service 对 `GET` 显式返回 `relationRequirement` 时，才可在默认 L3 未命中后把 L2 合并到同一次 target PDP，并按 `id + tenant scope` 重查。
- CREATE 在保存前绑定 `CREATED + 原请求摘要`；UPDATE 绑定 `CURRENT + PROPOSED + 原请求摘要`；DELETE 绑定 `CURRENT`。批量 CREATE 将全部服务端快照绑定到一次 target PDP，整个批次最多产生一个 challenge。
- Todo 协作者读取复用标准 `GET /api/todos/{id}`，不另设协作详情端点或共享访问模式。Todo 的 UPDATE/DELETE 不走关系替代路径；分享使用具名 UPDATE 命令，在 target L4 通过后才写关系元组。
- L4 只接受由 `PolicyDslCompiler` 编译、`PolicyExpressionEvaluator` 求值的严格 JSON DSL，不执行 SpEL、反射或任意方法调用。
- 策略生命周期为 `DRAFT / SHADOW / ENFORCE / DISABLED`。发布产生不可变 snapshot；运行时先按 target 过滤策略并以固定 64 位数据库签名版本缓存，Redis 版本仅参与签名而不是缓存正确性的唯一来源。
- challenge 持久化绑定主体、目标、请求摘要、触发策略 ID/版本和 snapshot 版本；批准与消费使用条件更新，消费是一次性 CAS。仅当同一快照仍包含该触发策略版本时，同一请求中多条 CHALLENGE 才可由一次确认恢复。

## 运行时组件

| 组件 | 位置 | 职责 |
| --- | --- | --- |
| `AuthorizationService` | `framework.security.authorization` | 唯一 PDP 门面；授权、批准 challenge、恢复授权 |
| `DefaultAuthorizationService` | `framework.security.authorization` | 按显式 `AuthorizationPlan` 执行并合并 L1-L4 |
| `AuthorizationMethodSecurityAdapter` | `framework.security.authorization` | 将 `hasPermission(...)` 转换成 `AuthorizationRequest` |
| `CrudEnforcementService` | `framework.crud.enforcement` | CRUD PEP：构造请求、调用 PDP 并应用 L3 类型安全约束 |
| `BaseCrudService` | `framework.crud` | 保证 CRUD 查询和变更使用 PEP 决策，不允许业务入口漏掉 SQL scope |
| `PolicyDslCompiler` | `framework.security.authorization` | 校验并编译严格 JSON DSL 为受限 AST |
| `PolicyExpressionEvaluator` | `framework.security.authorization` | 对 `AuthorizationRequest` 的固定 facts 求值 |
| `AuthorizationPolicyProvider` | `framework.security.authorization` | 按授权目标加载不可变运行时 snapshot |
| `AuthorizationChallengeStore` | `framework.security.authorization` | challenge 创建、批准、查询和一次性消费 SPI |
| `JpaAuthorizationChallengeStore` | `module.system.authorization` | challenge 的 PostgreSQL/JPA 持久化实现 |

## 权威业务 PDP 与 Spring 门禁

`AuthorizationService` 是资源对象、数据范围和动态策略授权的唯一权威决策门面；Spring Security 的认证与固定角色门禁是它之前的粗粒度边界，不与其竞争业务裁决。公共接口如下：

```java
public interface AuthorizationService {
    AuthorizationDecision authorize(AuthorizationRequest request);

    boolean approveChallenge(UUID challengeId);

    ContinuationSelection selectContinuation(UUID challengeId, AuthorizationRequest request);

    AuthorizationDecision resume(UUID challengeId, AuthorizationRequest request);
}
```

`AuthorizationRequest` 由主体、目标、显式执行计划、facts 和 challenge TTL 构成。`DefaultAuthorizationService` 补齐当前 `OperatorContext` 后按 L1、L2、L3、L4 依次求值，再按效果强度合并。

未在 `AuthorizationPlan` 中声明的层返回 `NOT_APPLICABLE`。已声明层缺少 Provider、Provider 调用失败或策略求值失败时返回 `INDETERMINATE`；同一 L2/L3 计划的其他 requirement 仍会继续求值，以保留完整审计和 `DENY > INDETERMINATE > CHALLENGE > ALLOW` 的安全优先级。

```text
固定角色门禁 / PEP
  → AuthorizationRequest
  → AuthorizationService
      → L1 FunctionPermissionChecker
      → L2 RelationPermissionChecker（仅显式计划）
      → L3 DataAuthorizationProvider（仅显式计划）
      → L4 AuthorizationPolicyProvider（仅显式计划）
  → AuthorizationDecision
  → PEP 执行 / 拒绝 / 返回 challenge
```

存在固定且无资源范围的管理操作时可单独使用 `hasRole/hasAnyRole`。涉及 objectId、tenant/workspace、关系、数据范围、L4 或 challenge 的操作必须进入 Service/PEP；`hasRole` 只能收紧，不能替代这些业务检查。

## Method Security 适配

`AuthorizationMethodSecurityAdapter` 实现 Spring Security `PermissionEvaluator`：

```java
@PreAuthorize("hasPermission(null, 'system:user:create')")
```

该形式创建 L1 功能权限计划，目标对象可选；适配器调用 `AuthorizationService.authorize(...)` 并把 `decision.allowed()` 返回给 Spring Security。

```java
@PreAuthorize("hasPermission(#id, 'document', 'can_read')")
```

该形式创建 `AUTHENTICATED` L1 与单项 L2 关系计划。适配器仍只负责转换，不读取关系表，也不复制 PDP 组合规则。

动态策略内容不放进 `@PreAuthorize` 表达式。需要 L4 目标、facts 或 challenge 的入口由 Service/PEP 组装完整请求。

## CRUD PEP

### Controller 边界

`BaseCrudController` 的标准端点只声明：

```java
@PreAuthorize("isAuthenticated()")
```

这只保证请求已经认证。Controller 不生成 CRUD 权限码，不执行租户、记录、个人、字段或关系权限判断。

标准端点进入 `BaseCrudService` 后，由 `CrudEnforcementService.enforce(...)` 形成不可变的 `CrudEnforcementDecision`。因此 Controller 的认证注解不能被解释为完整授权。

### L1 与 L4

每次 CRUD 操作先由资源定义校验能力，再由 `CrudEnforcementService` 组装：

- L1：`definition.permissionCode(operation.action())`；
- L4：显式 `PolicyPlan`；
- target：`entitySlug + action`；
- facts：tenant/field 可用性和 `accessMode`。

请求交给唯一 `AuthorizationService`。未注册权限码、拒绝、challenge 未恢复或不确定结果都不会进入数据操作。

`ADMIN_MAINTENANCE` 与 `SYSTEM_JOB` 还需要各自的 access-mode 权限。`SYSTEM_JOB` 必须存在带 reason 的 `PermissionExecutionContext`。

### 对象阶段、批量绑定与写锁

单对象的 preflight 只执行 L1 与 L3；实体按完整 SQL scope 加载后才执行 target L4。GET/DELETE 使用服务端 `CURRENT`，UPDATE 使用 `CURRENT + PROPOSED + 原 DTO 摘要`，CREATE 使用应用 tenant/owner 后的 `CREATED + 原 DTO 摘要`。客户端不能提交这些事实。

UPDATE、DELETE 与具名自定义 UPDATE 在固定 `CURRENT` 前获取 `PESSIMISTIC_WRITE` 并按同一 scope 重查；批量删除/归档先按稳定 ID 顺序加锁，再按原 scope 复核完整集合。批量 CREATE 只做一次无 L4 的前置决策，然后以全部 `CREATED` 快照、逐项 DTO 摘要和批次数量执行一个 target L4，故整个批次最多产生一个 challenge。

### L3 SQL scope 与字段策略

L1/L4 通过后，`CrudEnforcementService` 编译：

```text
tenantScopeSpecification
AND recordRule.specification
AND personalScopeSpecification
```

该组合保存在 `CrudEnforcementDecision.scopeSpecification()`，并由 `BaseCrudService` 与业务筛选、客户端筛选和 ID 条件一起交给 Repository。数据库只返回授权范围内的记录，不先查全量再在内存中过滤。

L3 的职责分布如下：

| 约束 | 执行位置 | 行为 |
| --- | --- | --- |
| tenant | `CrudEnforcementService.tenantSpec(...)` | 生成 org/workspace SQL 条件；上下文不完整时拒绝 |
| record | `RecordRuleSupport.compile(...)` | 返回非空 `RecordRule`、`Specification` 和 access version；不可用时拒绝 |
| personal | `PersonalScope` | 默认追加当前 subject 的属性条件 |
| field | `FieldAccessSupport` + 编译后的字段策略 | 限制读、写、筛选、排序、聚合、导出、引用和关系输出 |

列表端点只按这套 L3 scope 查询。L2 关系不是列表 union 条件，因此不能声称“共享给我的记录会自动出现在列表”。需要展示协作记录时，必须设计独立的受控查询语义，而不能让普通列表隐式扩大范围。

### 单对象读取

`BaseCrudService.requireEntity(...)` 的当前顺序是：

```text
1. 使用 id + 完整 L3 scope 查询
   tenant AND record AND personal
2. 命中：返回实体
3. 未命中：仅当 operation=GET 且 accessMode=DEFAULT 时继续
4. 调用业务 Service 的 relationRequirement(id, GET)
5. 未显式声明、objectId 不等于当前 id，或 L2 拒绝：统一返回资源不存在
6. 用 L1 GET 权限 + L2 requirement + L4 策略再次调用 AuthorizationService
7. 通过后使用 id + tenant scope 重查
8. 仍未命中：统一返回资源不存在
```

关系兜底只替代 record/personal 的未命中，不绕过 tenant，不改变字段策略，也不把关系记录注入列表窗口。

`relationRequirement(...)` 默认返回 `null`。业务 Service 只有在资源确实支持协作者读取时才覆写，并且只为 `CrudOperation.GET` 返回与当前 ID 绑定的要求。

### 更新和删除

UPDATE、DELETE、批量删除及其他变更始终按对应操作的完整 L3 scope 加载记录。关系权限不替代写入范围：

```text
UPDATE / DELETE
  → 对应 L1 + L4
  → tenant + record + personal SQL scope
  → 安全加载实体
  → 字段/引用/关系校验
  → 变更
```

即使某主体拥有 `can_read` 关系，也不会因此获得更新或删除权限。

## Todo 协作者读取

`TodoService` 覆写：

```java
@Override
protected AuthorizationPlan.RelationRequirement relationRequirement(
        Long id, CrudOperation operation) {
    if (operation != CrudOperation.GET) {
        return null;
    }
    return new AuthorizationPlan.RelationRequirement(
            "todo", String.valueOf(id), "can_read");
}
```

因此 Todo 的行为是：

- 协作者调用标准 `GET /api/todos/{id}`；
- 默认 L3 能读时直接返回；
- 默认 L3 不能读时，检查 `todo:{id}#can_read`，通过后在同一 tenant scope 内重查；
- 标准 Todo 列表不会自动包含仅通过 L2 关系可读的 Todo；
- 不另设协作详情端点或共享访问模式；
- UPDATE/DELETE 不声明 relation requirement；
- 分享前先以默认 UPDATE 范围安全加载 Todo，再写关系元组，关系写入不能绕过记录范围。

## L4 严格 JSON DSL

### 编译与求值

`PolicyDslCompiler` 只接受 JSON 对象。组合节点只能声明 `and`、`or`、`not` 之一；叶子节点只能声明 `field`、`op`、`value`，`exists` 不接受 `value`。

支持的操作符是：

```text
eq / ne / in / not_in / gt / gte / lt / lte / contains / exists
```

可读取的内建 facts 是：

```text
operatorId / subjectId / tenantId / workspaceId
resource / action / objectId
attributes.*
```

`attributes.*` 必须存在于发布 snapshot 的 `PolicyFactSchema`。编译器限制 JSON 长度、嵌套深度、节点数、数组长度和字符串长度，并校验字面量类型。

`PolicyExpressionEvaluator` 只解释编译后的受限 AST。授权策略不执行 SpEL，不访问 Bean，不使用反射，也不能调用任意 Java 方法。

### 生命周期与不可变 snapshot

| 生命周期 | 运行时行为 |
| --- | --- |
| `DRAFT` | 可编辑，不进入运行时 snapshot |
| `SHADOW` | 从不可变 snapshot 求值并审计，不改变最终决定 |
| `ENFORCE` | 从不可变 snapshot 求值并参与最终决定 |
| `DISABLED` | 不进入运行时 snapshot |

`AccessPolicyService.publish(...)` 只允许发布为 `SHADOW` 或 `ENFORCE`。发布前用 `PolicyDslCompiler` 校验条件和 fact schema，随后写入新的 `sys_access_policy_snapshot` 版本。运行时按当前 lifecycle 和 published version 精确加载 snapshot；缺失或生命周期不一致视为运行时错误。

策略创建、更新、删除、发布、禁用和撤回都在事务提交成功后执行以下动作：

```text
afterCommit
  → PermissionVersionService.bumpPolicyVersion()
  → snapshotCache.clear()
  → 写策略生命周期审计
```

事务回滚不会提前发布版本或清除运行时缓存。运行时 snapshot 是只读副本，策略编辑不会原地修改已发布版本。

迁移 `v104__authorization_runtime.sql` 将历史启用策略统一转为 `SHADOW` 并物化版本 1 snapshot；历史策略不会未经重新校验和显式发布就进入 `ENFORCE`。

### SHADOW 与 ENFORCE 故障语义

- SHADOW 命中或编译/求值失败只写 `ShadowDecision` 和审计，不改变最终决定。
- ENFORCE 命中参与最终合并。
- ENFORCE 编译或求值失败返回 `INDETERMINATE`，PEP 拒绝继续执行。

## 持久化 challenge

### 绑定内容

PDP 创建 challenge 时写入：

- `operatorId / subjectId / tenantId / workspaceId`；
- `resource / action / objectId`；
- `requestDigest`；
- `policyId / policyVersion / snapshotVersion`；
- 状态和过期时间。

`requestDigest` 由完整 `AuthorizationRequest` 的结构化上下文生成，恢复时重新计算。存储拒绝主体、目标、摘要、策略版本、snapshot 版本或过期时间不完整的 challenge。

### 一次性状态机

```text
PENDING --approve CAS--> APPROVED --consume CAS--> CONSUMED
```

批准条件包含 `id + subjectId + PENDING + 未过期`；消费条件包含 `id + subjectId + APPROVED + 未过期`。并发恢复只有一次消费能成功。

恢复顺序：

```text
1. 以当前 subject 查询 APPROVED 且未过期的 challenge
2. 比较 challengeId、完整 subject、target 和 requestDigest
3. 条件更新为 CONSUMED
4. 携带 challenge 的 `policyId / policyVersion / snapshotVersion` 重新执行 `AuthorizationService`
5. 只有当前 snapshotVersion 相同且仍包含该触发策略版本时，当前请求中全部仍匹配的 ENFORCE CHALLENGE 才同时转为 ALLOW
```

challenge 是完整请求与策略快照的一次性确认，不是单条策略或旧决定的缓存。主体、租户、工作区、目标、facts、授权计划、策略版本或当前权限发生变化时，都不能沿用旧批准绕过重新裁决。

## 存储与缓存边界

| 数据 | 真理源 | 运行时边界 |
| --- | --- | --- |
| L1 角色与权限码 | PostgreSQL | `FunctionPermissionChecker` 可使用版本化缓存 |
| L2 关系元组 | PostgreSQL `sys_permission_tuple` | `RelationPermissionChecker` 读取；关系变更需失效版本/缓存 |
| L3 记录与字段规则 | PostgreSQL | 规则可缓存，JPA `Specification` 在请求期编译并下推 SQL |
| L4 策略 | `sys_access_policy` + 不可变 snapshot | 只缓存带全局版本的 snapshot，不缓存动态最终决定 |
| challenge | `sys_authorization_challenge` | 持久化状态机和条件更新 |
| 授权审计 | `sys_authorization_audit` | 记录最终决定、SHADOW 结果、challenge 和生命周期事件 |

## 开发约束

- 新授权入口依赖 `AuthorizationService`，不得创建第二个决策门面。
- Method Security 使用 `AuthorizationMethodSecurityAdapter`，不得在适配器复制 L1-L4 规则。
- 标准 CRUD Controller 保持仅认证；完整授权必须留在 Service/PEP。
- 自定义 CRUD 查询必须复用 `BaseCrudService` 受控入口，不能直接 Repository 查询后手工过滤。
- 只有单对象默认 GET 可声明 relation fallback；列表、UPDATE、DELETE 不得套用该语义。
- 策略条件必须是严格 JSON DSL；不得把 SpEL 或任意表达式执行引入策略内容。
- challenge 必须持久化并完整绑定，不得用仅内存、可重复消费或只校验 challengeId 的实现替代。

## 相关文档

- [访问控制设计](access-control.md)
- [CRUD 模块代码导览](../../../../apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/crud/README.md)
- [如何扩展类型化 CRUD 资源](../../../guide/development/entity-extension.md)
- [低代码 CRUD 资源定义重构方案](../data/low-code-crud-resource-definition-refactor.md)
