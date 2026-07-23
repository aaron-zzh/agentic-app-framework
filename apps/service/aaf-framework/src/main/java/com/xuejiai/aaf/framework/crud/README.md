# CRUD 模块代码导览

本目录提供类型化 CRUD 资源、标准 REST 端点、启动期资源编译、统一安全执行、筛选、引用、关系、输出视图和导出能力。业务模块通过资源定义与 `BaseCrudService` 扩展点接入；不要复制租户、记录、字段或授权逻辑。

## 阅读入口

按一条请求的执行顺序阅读：

1. [BaseCrudController.java](BaseCrudController.java)：标准 HTTP 端点。所有标准端点只要求 `isAuthenticated()`。
2. [BaseCrudService.java](BaseCrudService.java)：CRUD 编排和不可绕过的安全查询模板。
3. [enforcement/CrudEnforcementService.java](enforcement/CrudEnforcementService.java)：CRUD PEP，组装授权请求、调用权威 PDP，并应用 L3 约束。
4. [enforcement/CrudDataAuthorizationProvider.java](enforcement/CrudDataAuthorizationProvider.java)：唯一 CRUD L3 Provider，编译记录范围、字段拒绝集合与访问版本。
5. [definition/CrudResourceDefinition.java](definition/CrudResourceDefinition.java)：资源能力、权限命名空间、tenant/personal 范围、字段与视图契约。
6. [resource/CrudResourceCompiler.java](resource/CrudResourceCompiler.java)：启动期校验并发布不可变资源目录。
7. [resource/CrudResourceRegistry.java](resource/CrudResourceRegistry.java)：请求期使用的唯一资源索引。

统一 PDP 位于 `com.xuejiai.aaf.framework.security.authorization.AuthorizationService`。CRUD 模块只通过该接口发起授权，不维护第二套决策服务。

## 两条主链路

### 启动期

```text
CrudResourceDefinitionProvider
  → CrudResourceDefinition
  → CrudResourceCompiler
  → CrudResourceRegistry
  → CrudResourceAccessRegistry
```

`CrudResourceCompiler` 收集资源定义和显式端点绑定，校验资源键、Controller 泛型、API 路径、字段、引用、关系目标和所需 Bean。全部校验通过后才一次性发布目录；不存在部分发布或请求期 fallback。

### 请求期

```text
BaseCrudController（仅认证或额外固定角色门禁）
  → BaseCrudService
  → CrudEnforcementService（CRUD PEP）
      → AuthorizationService（L1 + L3 + 请求级或对象级 L4）
      → CrudDataAuthorizationProvider 返回 SQL scope / 字段约束
  → Repository + JPA Specification
  → 引用/关系处理
  → 输出字段裁剪
```

Controller 不执行资源授权。`BaseCrudService` 在每个标准操作前调用 `CrudEnforcementService`，再把安全决策中的 SQL scope 与 ID、业务筛选和客户端筛选组合后交给 Repository。

## 授权与数据范围

### Controller 只负责认证

`BaseCrudController` 的 page、`_query`、get、`_batch-read`、`_options`、`_meta`、create、update、delete、import、export、group 等标准端点统一使用：

```java
@PreAuthorize("isAuthenticated()")
```

该注解不是完整授权。L1 权限码、L4 动态策略、tenant/record/personal 范围和字段权限都由 Service/PEP 执行。

### L1 与 L4 权威 PEP

`CrudEnforcementService.enforce(...)`：

1. 校验资源是否声明当前 `CrudOperation`；
2. 从 `OperatorContext.currentOwnerId()` 取得 subject；
3. 校验 tenant context 是否满足资源的 `TenantScope`；
4. 用资源定义生成当前操作的 L1 权限码；
5. 显式启用 L4 `PolicyPlan`；
6. 将完整请求交给 `AuthorizationService`；
7. 对拒绝、不确定和未恢复 challenge 统一阻断。

非默认 `AccessMode` 还需额外的 access-mode 权限。当前只有：

```text
DEFAULT / ADMIN_MAINTENANCE / SYSTEM_JOB
```

没有额外的共享访问模式。

### L3 下推 SQL

`CrudEnforcementService` 编译：

```text
tenantScopeSpecification
AND recordRule.specification
AND personalScopeSpecification
```

`BaseCrudService` 将其保存为本次操作的安全范围，并在 Repository 查询中组合：

```text
安全范围
AND id / ids
AND 客户端白名单筛选
AND 业务 buildSpec
```

因此 tenant、record 和 personal 由 SQL 过滤，不是查询后的内存裁剪。`RecordRuleSupport` 或 `FieldAccessSupport` 缺失、返回空结果或编译失败时 fail-closed。

字段策略控制 `READ / WRITE / FILTER / SORT / AGGREGATE / EXPORT / REFERENCE`。框架在查询字段、输入字段、引用/关系加载和最终输出处执行同一策略。

## 列表与单对象读取

### 列表不会自动合并 L2 共享记录

以下入口只查询完整 L3 scope：

```text
page / _query / _batch-read / _options / export / group
```

L2 关系元组不会作为 OR 条件注入列表 SQL。不要把标准列表描述为“自动包含共享给我的记录”。如果产品需要协作列表，必须定义独立、可审计且可下推的查询语义。

### 默认单对象读取

`BaseCrudService.requireEntity(...)` 先执行：

```text
id AND tenant AND record AND personal
```

命中即返回。未命中默认按资源不存在处理，避免泄露记录是否存在。

### 显式 GET 关系兜底

业务 Service 可覆写：

```java
protected AuthorizationPlan.RelationRequirement relationRequirement(
        Long id, CrudOperation operation)
```

默认返回 `null`。只有满足以下条件才会尝试关系读取：

- 当前操作是 `CrudOperation.GET`；
- 当前模式是 `AccessMode.DEFAULT`；
- Service 显式返回 requirement；
- requirement 的 `objectId` 等于当前记录 ID；
- `CrudEnforcementService.allowsRelationRead(...)` 的 L1+L2+L4 决策允许。

通过后只使用：

```text
id AND tenantScopeSpecification
```

重查实体。该路径仅替代默认 record/personal 未命中，不绕过 tenant，也不改变已编译字段策略。

UPDATE、DELETE 和批量变更不进入关系兜底，始终使用对应操作的完整 L3 scope。

### Todo 参考

`TodoService` 仅对 GET 返回：

```java
new AuthorizationPlan.RelationRequirement("todo", id.toString(), "can_read")
```

协作者直接调用标准 `GET /api/todos/{id}`。Todo 不另设协作详情端点或共享访问模式；标准 Todo 列表不会自动出现仅通过 L2 可读的记录。Todo 的 UPDATE/DELETE 返回 `null`，关系只授予读取，不授予写入或删除。

## 资源契约

### 资源定义

`CrudResourceDefinition` 聚合：

- `ResourceKey` 与 `CrudResourceDescriptor`；
- Entity/CreateDTO/UpdateDTO/VO/PageDTO 类型；
- `CrudCapabilityDefinition` 与 `CrudOperation`；
- Query、Mutation、View、字段能力；
- `TenantScope` 与 `PersonalScope`；
- 引用、关系和暴露面。

资源定义给出服务端能力上限。EntityDef、客户端和动态字段策略只能收窄，不能扩大。

### 推荐的差量声明

```java
private static final CrudResourceTypeContract<Todo> TYPES =
        CrudResourceTypeContract.fromCrudController(TodoController.class, Todo.class);

public static final CrudResourceDefinition<Todo> DEFINITION =
        CrudResourceDefinition.standard(
                KEY,
                TYPES,
                new CrudResourceDescriptor(
                        "待办", BASE_PATH, "system:todo"),
                CrudCapabilityDefinition.forTypes(TYPES)
                        .without(
                                CrudOperation.IMPORT,
                                CrudOperation.RESTORE,
                                CrudOperation.ARCHIVE),
                query,
                CrudMutationDefinition.forTypes(TYPES),
                view,
                TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                PersonalScope.byProperty("assigneeId"));
```

省略表示沿用框架默认，不使用 `null` 或 `"*"` 表示默认。Controller 泛型决定 API 类型；DTO 推导默认写字段；VO 推导默认视图字段；Query、Mutation、View、引用和关系共同推导字段能力。

## BaseCrudService 扩展边界

业务 Service 至少实现：

```java
protected abstract CrudEntityRepository<E> getRepository();
protected abstract V toVO(E entity);
protected abstract E toEntity(C createDTO);
protected abstract void updateEntity(E entity, U updateDTO);
```

这些方法只处理业务转换与领域规则。不要在其中：

- 手工拼接通用 tenant/record/personal 条件；
- 直接保存或重新查询实体以绕过安全加载；
- 复制字段权限判断；
- 手工同步已声明的通用关系；
- 返回未经过框架字段裁剪的外部响应。

领域筛选可覆写 `buildSpec(P)`，但只能追加条件。管理员维护和系统任务使用带真实 `CrudOperation` 与明确 `AccessMode` 的受控入口；不能直连 Repository。

只有资源确实支持协作者单对象读取时，才覆写 `relationRequirement(id, operation)`，并仅对 GET 返回与 ID 绑定的要求。

## 引用与关系

固定目标引用使用 `@CrudReference(targetResource = "...")`；多态引用使用 `resourceProperty`。框架先通过目标资源的 `EntityReferenceAccess` 建立读取/引用基线，附加 `ReferencePolicy` 只能收窄。

纯 M2M 关系在轻量关联实体上使用 `@CrudAssociation`。`GenericRelationHandler` 负责校验、同步与父记录删除清理；`GenericRelationLoader` 负责批量读取。关系输入使用 `Patch<List<Long>>` 区分未传、清空和替换。

关系输出仍受当前字段集和 `READ` 字段策略约束。没有字段权限时，不应先加载再删除。

## 输出视图与 QueryToken

`CrudViewDefinition` 定义 `list/detail/picker/export` 字段集。存在引用或关系且未指定业务 Mapper 时，使用 `DefaultCrudViewMapper`；计算字段或跨聚合输出才实现自定义 Mapper。

`QueryToken` 绑定 subject、org、workspace、资源、字段集、查询摘要和 access version。它只证明列表窗口上下文，不能替代详情的实时 L1/L3/L4 授权，也不能让列表外或失权记录继续可见。

## 按问题定位

| 现象 | 首先查看 |
| --- | --- |
| Controller 已认证但请求仍被拒绝 | [enforcement/CrudEnforcementService.java](enforcement/CrudEnforcementService.java) |
| 列表缺少某条协作记录 | 确认产品是否错误假设 L2 自动进入列表；标准列表只查 L3 |
| Todo 协作者详情被拒绝 | `TodoService.relationRequirement(...)`、L2 元组与 tenant context |
| UPDATE/DELETE 意外尝试关系放行 | [BaseCrudService.java](BaseCrudService.java)；变更不应走 relation fallback |
| 资源、字段、关系启动校验失败 | [resource/CrudResourceCompiler.java](resource/CrudResourceCompiler.java) |
| 筛选字段或操作符被拒绝 | [filter/CrudFilterSchema.java](filter/CrudFilterSchema.java) |
| 引用或关系数据不可见 | [reference](reference/) 与 [relation](relation/) |
| VO 字段缺失 | [view](view/) 与编译后的字段策略 |

## 包导航

| 位置 | 职责 |
| --- | --- |
| 根目录 | 标准 Controller、Service 模板、Repository 契约和元数据组装 |
| [definition](definition/) | 资源身份、能力、查询、变更、视图和数据范围契约 |
| [resource](resource/) | 声明收集、启动期编译、不可变目录和快照 |
| [enforcement](enforcement/) | L1/L4 PEP、L3 scope、字段策略和访问模式 |
| [filter](filter/) | 筛选白名单与 JPA 条件构建 |
| [reference](reference/) | 资源引用读取与建立引用授权 |
| [relation](relation/) | 关系声明、校验、同步和批量加载 |
| [view](view/) | 字段集、依赖加载、映射和最终字段裁剪 |
| [runtime](runtime/) | 已发布资源与实际 Service 的运行时绑定 |
| [web](web/) | 嵌套资源和选择器补充端点 |
| [dto](dto/) | 通用请求、响应和批处理载体 |
| [export](export/) | 导出格式化扩展 |
