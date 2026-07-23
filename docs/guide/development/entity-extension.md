---
level: Practice
layer: Model
purpose: 指导开发者为类型化业务实体声明 CRUD 资源，并接入当前统一授权与 EntityDef
status: draft
version: 1.4.0
date: 2026-07-23
author: AaronZZH & Kiro
tags:
  - CRUD 资源目录
  - EntityDef
  - 授权
  - 输出视图
  - Todo
related:
  - ./how-to-create-module.md
  - ../../design/framework/security/access-control-tech-design.md
  - ../../design/framework/data/low-code-crud-resource-definition-refactor.md
scope:
  includes:
    - 类型化 CRUD 资源、Repository、Service 和 Controller 的接入步骤
    - CRUD PEP、L3 SQL 范围和可选 GET 关系读取
    - 输出视图、关系和 EntityDef 的协作边界
  excludes:
    - 运行时动态建表和通用记录 CRUD
    - 自定义领域状态机的完整设计
    - 策略管理后台操作说明
gains:
  - 能声明可由 CrudResourceCompiler 校验的类型化资源
  - 能让标准 CRUD 正确经过 L1/L4 与 L3 SQL 安全管线
  - 能为确有需要的单对象 GET 声明关系读取且不扩权到列表或变更
---

# 如何扩展类型化 CRUD 资源并接入 EntityDef

## 目标

为已由 Java、Flyway 和类型化 REST API 承载的业务实体声明 `CrudResourceDefinition`，接入标准 CRUD、安全执行、输出视图、引用/关系和 EntityDef。

Todo 是关系读取参考：资源键 `system.todo`，标准路径 `/api/todos`，实现位于 `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/system/task/`。

## 前置条件

- 已明确实体字段、租户范围、个人范围、字段能力和业务不变量。
- 已使用 Flyway 管理表、索引与约束。
- 已创建 Entity、Create/Update/Page DTO、VO 和 Repository。
- 已确认资源适合标准 CRUD；复杂流程使用显式业务 Service 方法。
- 已理解标准 Controller 只做认证，完整授权由 Service/PEP 执行。

权威关系如下：

```text
Flyway → JPA Entity → DTO / VO → BaseCrudService / Controller
                                  ↓
                     CrudResourceDefinitionProvider
                                  ↓
                     CrudResourceCompiler / Registry
                                  ↓
                     EntityDef UI overlay
```

EntityDef 只能描述和收窄 UI，不能建表、增加 API、扩大字段能力或替代服务端授权。

## 步骤

### 创建类型化数据模型

Repository 只继承统一契约：

```java
public interface InvoiceRepository extends CrudEntityRepository<Invoice> {}
```

不要重复继承 `JpaRepository` 或 `JpaSpecificationExecutor`，也不要提供绕过 `BaseCrudService` 的通用查询入口。

DTO 负责输入存在性与 Bean Validation；Entity 负责持久化；VO 负责外部输出。关系或局部更新使用 `Patch<T>` 区分未传、显式清空和设置值。

### 声明资源身份与能力

在业务包创建 `XxxResource`，集中声明 `ResourceKey`、路径与资源定义：

```java
private static final CrudResourceTypeContract<Todo> TYPES =
        CrudResourceTypeContract.fromCrudController(TodoController.class, Todo.class);

private static final CrudViewDefinition VIEW =
        CrudViewDefinition.forTypes(TYPES)
                .withFieldSet(
                        "list",
                        Set.of(
                                "id",
                                "version",
                                "title",
                                "category",
                                "status",
                                "dueDate",
                                "createTime",
                                "assignee",
                                "participants"))
                .withFieldSet("picker", "id", "title");

public static final CrudResourceDefinition<Todo> DEFINITION =
        CrudResourceDefinition.standard(
                KEY,
                TYPES,
                new CrudResourceDescriptor("待办", BASE_PATH, "system:todo"),
                CrudCapabilityDefinition.forTypes(TYPES)
                        .without(
                                CrudOperation.IMPORT,
                                CrudOperation.RESTORE,
                                CrudOperation.ARCHIVE),
                query,
                CrudMutationDefinition.forTypes(TYPES),
                VIEW,
                TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                PersonalScope.byProperty("assigneeId"));
```

只声明相对默认值的差异：

- Controller 泛型提供 API 类型；
- DTO 推导默认 Mutation 字段；
- VO 推导默认视图字段；
- Query、Mutation、View、引用和关系共同推导字段能力；
- `TenantScope` 与 `PersonalScope` 明确数据边界；
- descriptor 的权限命名空间用于生成 L1 权限码。

省略表示使用默认，不用 `null` 或 `"*"` 表达默认。筛选字段必须来自真实 `CrudFilterSchema`。

### 发布资源和端点绑定

实现 `CrudResourceDefinitionProvider<E>`：

```java
@Component
public final class InvoiceCrudResourceDefinitionProvider
        implements CrudResourceDefinitionProvider<Invoice> {

    private static final CrudResourceEndpointBinding ENDPOINT =
            CrudResourceEndpointBinding.crud(
                    InvoiceResource.KEY,
                    InvoiceController.class,
                    "/api/invoices");

    @Override
    public CrudResourceDefinition<Invoice> definition() {
        return InvoiceResource.DEFINITION;
    }

    @Override
    public CrudResourceEndpointBinding endpointBinding() {
        return ENDPOINT;
    }
}
```

保持资源键、Definition、Provider、端点和关系目标使用同一常量。`CrudResourceCompiler` 会在启动期校验全部资源并一次性发布；失败时不发布部分目录。

### 实现 BaseCrudService

业务 Service 继承 `BaseCrudService<E,V,C,U,P>` 并实现：

```java
protected CrudEntityRepository<E> getRepository();
protected V toVO(E entity);
protected E toEntity(C createDTO);
protected void updateEntity(E entity, U updateDTO);
```

这些方法只处理业务转换和领域规则。不要手工：

- 拼接 tenant、record 或 personal 条件；
- 检查通用字段权限；
- 直接保存后从 Repository 重查；
- 同步已声明的通用关系；
- 返回未经字段裁剪的响应。

业务筛选覆写 `buildSpec(P)`，只能追加条件。管理员维护或系统任务使用带真实 `CrudOperation` 与 `ADMIN_MAINTENANCE` / `SYSTEM_JOB` 的受控入口。

### 理解标准授权顺序

标准 `BaseCrudController` 只使用：

```java
@PreAuthorize("isAuthenticated()")
```

不要把它改写成每个端点自建一套权限表达式。标准请求进入 Service 后：

```text
CrudEnforcementService
  → L1 资源操作权限 + L4 策略
  → tenant + record + personal Specification
  → field policy
BaseCrudService
  → 将安全 Specification 下推 Repository SQL
  → 执行字段、引用、关系和输出裁剪
```

唯一 PDP 是 `framework.security.authorization.AuthorizationService`。CRUD Service 不直接依赖其他决策门面，也不复制 PDP 规则。

### 配置 L3 数据范围

为资源选择正确 `TenantScope`：

- `GLOBAL`：只匹配全局记录；
- `ORG_REQUIRED`：必须有 org，SQL 限定当前 org；
- `WORKSPACE_REQUIRED`：必须有 org/workspace，SQL 同时限定两者；
- `ORG_SHARED_WORKSPACE_OPTIONAL`：限定当前 org，并匹配共享 workspace 或当前 workspace。

需要个人视角时声明 `PersonalScope.byProperty("...")`。记录规则由 `RecordRuleSupport` 编译。三者组成：

```text
tenant AND record AND personal
```

列表、详情默认读取、批量读取、更新与删除都使用该 SQL 范围。字段策略另行限制读取、写入、筛选、排序、聚合、导出和引用。

### 仅在必要时声明 GET 关系读取

默认不要覆写 `relationRequirement(...)`。只有资源确实允许协作者查看单条记录时才声明：

```java
@Override
protected AuthorizationPlan.RelationRequirement relationRequirement(
        Long id, CrudOperation operation) {
    if (operation != CrudOperation.GET) {
        return null;
    }
    return new AuthorizationPlan.RelationRequirement(
            "invoice", String.valueOf(id), "can_read");
}
```

运行时语义固定为：

```text
先查 id + tenant + record + personal
  → 命中：返回
  → 未命中：仅默认 GET 继续
      → 执行 L1 GET + 显式 L2 + L4
      → 通过后查 id + tenant
```

必须遵守：

- requirement 的 objectId 必须绑定当前 ID；
- 只允许 GET；UPDATE/DELETE 返回 `null`；
- tenant 永远保留；
- 字段策略继续生效；
- 标准列表不会自动 union 关系记录；
- 不创建专用协作详情端点；
- 不添加额外的共享访问模式。

Todo 使用的就是该标准 GET 方案。协作者访问 `GET /api/todos/{id}`，而不是专用协作详情接口。

### 接入标准 Controller

Controller 继承 `BaseCrudController<E,V,C,U,P>`，使用与 Provider 一致的 `@RequestMapping`，并实现 `getService()`。

Controller 只处理 HTTP 接线和输入校验。不要在 Controller：

- 直接访问 Repository；
- 手工实现 L2/L3；
- 为标准 CRUD 创建平行授权路径；
- 根据客户端传入资源名决定权限。

非标准状态切换、分享和领域动作使用显式 Service 方法。分享前必须用对应写操作的完整默认范围安全加载父记录，再写关系；读关系不能被用来获得分享、更新或删除能力。

### 声明引用与关系

固定引用：

```java
@CrudReference(targetResource = "system.user")
private Long reviewerId;
```

多态引用：

```java
private String sourceEntity;

@CrudReference(resourceProperty = "sourceEntity")
private Long sourceId;
```

纯 M2M 关联：

```java
@CrudAssociation(
        sourceEntity = Invoice.class,
        key = "participants",
        kind = AssociationKind.MANY_TO_MANY_JOIN,
        sourceProperty = "invoiceId",
        targetProperty = "userId",
        targetResource = "system.user",
        syncMode = RelationDefinition.SyncMode.REPLACE,
        maxCardinality = 100)
@Entity
@IdClass(InvoiceParticipant.Id.class)
public class InvoiceParticipant {
    @Id private Long invoiceId;
    @Id private Long userId;
}
```

附加 `ReferencePolicy` 只能在目标资源基线上收窄，不能放宽。关系输入使用 `Patch<List<Long>>`；未传保持，空列表清空，非空集合同步。

### 声明输出视图

默认：

- `detail` 包含 VO 标量与声明式关系输出；
- `list/picker/export` 默认只包含标量；
- 需要在其他字段集展开关系时，用 `withFieldSet(...)` 差量覆盖。

普通引用、M2M 和只读 O2M 使用 `DefaultCrudViewMapper`。只有计算字段、跨聚合输出或通用 Loader 无法表达的情况才实现自定义 Mapper。框架会先按字段策略裁剪依赖，再批量加载，最后再次裁剪输出。

### 编写 EntityDef seed

EntityDef 使用 `kind: "code"` 和已发布 `ResourceKey`，只描述 UI：

```json
{
  "type": "relationship",
  "name": "assignee",
  "relationTo": "system.user",
  "writeKey": "assigneeId"
}
```

`relationTo` 填 `ResourceKey`，不填 slug 或 API 路径。不要在 seed 中持久化由 Catalog 派生的路由，也不要声明 Catalog 不存在的字段、筛选、排序、操作或关系。

## 验证接入结果

只做文档或设计更新时，不要声称已经运行测试。进入代码实现与交付流程后，按项目门禁执行验证。

接入检查项：

- Provider、资源键、Controller 类型和 API 路径一致；
- Repository 只继承 `CrudEntityRepository`；
- Service 实现四个必需扩展点；
- 标准 Controller 保持仅认证；
- L1/L4 由 `CrudEnforcementService` 委托 `AuthorizationService`；
- tenant/record/personal 进入 Repository SQL；
- 字段策略覆盖输入、查询和输出；
- 列表没有被描述或实现为自动包含 L2 关系记录；
- relation requirement 只用于显式单对象 GET；
- UPDATE/DELETE 不走关系替代；
- EntityDef 只收窄 Catalog 能力；
- 普通引用和关系没有注解与显式 Definition 两份声明。

## 故障排除

### 已登录但 CRUD 仍返回拒绝

检查资源操作能力、L1 权限码是否注册、L4 策略、tenant context、记录规则和字段策略。`isAuthenticated()` 只通过认证，不代表资源授权通过。

### 协作者能打开详情但列表没有该记录

这是当前标准语义。关系兜底只用于 Service 显式声明的单对象 GET，不会把 L2 记录并入普通列表。

### 协作者无法更新或删除记录

读关系只提供显式 GET 替代路径。UPDATE/DELETE 必须满足各自 L1/L4 和完整 L3 范围。

### 为什么不能直接查询 Repository？

直连会绕过 L1/L4、tenant/record/personal SQL、字段策略、QueryToken、引用检查和输出裁剪。使用 `BaseCrudService` 的标准或受控入口。

## 完成条件

- 资源由唯一 `CrudResourceDefinitionProvider` 发布；
- 启动期可由 `CrudResourceCompiler` 完整校验；
- 标准 CRUD 都进入 `BaseCrudService` 与 `CrudEnforcementService`；
- 授权只委托 `AuthorizationService`；
- L3 范围由 SQL 执行；
- 单对象关系读取仅按显式 GET requirement 生效；
- 列表、UPDATE 和 DELETE 未被关系路径扩权；
- 输出视图、关系和 EntityDef 都没有平行真理源。
