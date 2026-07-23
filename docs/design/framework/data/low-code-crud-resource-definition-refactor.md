---
level: Practice
layer: Model
purpose: 定义 AAF 低代码 CRUD 的资源契约、唯一资源目录、安全执行管线及关系输出视图重构方案
status: draft
version: 2.1.0
date: 2026-07-23
author: AaronZZH & Kiro
tags:
  - CRUD
  - 低代码
  - 资源契约
  - 数据权限
  - 关系输出视图
dependencies:
  - ./filter-capability-matrix.md
  - ./list-detail-query-optimization-tech-design.md
  - ../../../reference/dev/architecture-constraints.md
scope:
  includes:
    - 代码资源唯一身份与启动期资源目录
    - 查询、变更、字段、租户、关系和输出视图契约
    - BaseCrud 不可绕过的安全执行管线
    - Todo 基准资源与全量切换策略
  excludes:
    - EntityDef 驱动的运行时 DDL
    - 任意字段动态查询和动态业务 API
    - ReBAC、审批、结算等领域模型本身
    - 具体实现任务和数据库迁移脚本
gains:
  - 能区分 Definition、Catalog、EntityDef、JPA Entity 和 DTO 的职责
  - 能按统一安全管线实现 CRUD 查询与变更入口
  - 能为关系同步和批量输出视图提供符合模块边界的执行 SPI
  - 能使用可执行验收矩阵判断重构是否完成
changelog:
  - 2026-07-20 | v2 实现：47 个显式资源条目完成 Catalog 原子切换，删除旧 Registry、`@EntityView` 与 Definition fallback
  - 2026-07-20 | v2 实现：接入统一安全 enforcement、字段能力、HMAC QueryToken、关系/输出视图/Mutation SPI 与 Todo 读写闭环
  - 2026-07-20 | Phase 3（过渡实现）：Definition 纳入实体/DTO/VO/Page 类型契约，Compiler 校验完整 Controller 泛型
  - 2026-07-20 | Phase 2（过渡实现）：加入 Provider、启动期 Compiler 与原子 Catalog；Todo 已显式注册，旧 Registry 尚未切换
  - 2026-07-20 | Phase 1（过渡实现）：引入 `ResourceKey`、可选 `CrudResourceDefinition` 与 Todo 单资源参考接入；尚未满足 Catalog 切换门槛
  - 2026-07-20 | Phase 0：日期变量改为 `DateTimeFilterVariable` 字段级声明，前端元数据与服务端解析共享白名单
  - 2026-07-20 | 2.0.0：按架构评审重写为唯一资源目录、可执行契约和安全管线方案，补齐租户、并发、SPI 与切换门槛
  - 2026-07-20 | 1.0.0：基于 Todo/BaseCrud 的筛选、引用、输出视图与字段权限演进讨论形成初稿
---

# 低代码 CRUD 资源定义重构方案

> **结论**：AAF 应采用“代码声明、启动期编译、服务端强制执行、EntityDef 仅做 UI 覆盖”的类型化低代码模式。重构重点不是把多个 Hook 收进一个 Builder，而是形成从资源注册、请求授权、查询或变更、关系处理到输出视图生成的单一闭环。

## 文档目标

本方案在不把业务规则降级为运行时动态 CRUD 的前提下，建立可验证的代码资源契约，使标准资源复用查询、字段访问、租户隔离、关系同步和批量输出视图，同时保留复杂领域行为的显式实现。

重构完成后应满足：

- 每个代码资源只有一个稳定身份和一个服务端能力上限；
- 所有公开和内部入口经过同一安全执行管线；
- EntityDef 只能配置交互和收窄展示，不能扩大服务端能力；
- 关系和输出视图通过框架 SPI 执行，不破坏 Maven 模块与业务包边界；
- 部分更新、并发冲突、事务回滚和事件时机具有确定语义；
- 已迁移能力不存在旧 Hook、兼容分支或双重真理源。

## 第一性原理

低代码 CRUD 的基本单元不是数据库表，也不是页面字段，而是一个受约束的业务资源。资源至少由以下事实构成：

```text
稳定身份
+ API 输入与输出契约
+ 可用操作集合
+ 查询和变更语义
+ 租户、记录和字段授权
+ 关系生命周期
+ 一致性与并发模型
+ 可观测的运行时注册信息
```

声明只有经过校验和执行才有价值。AAF 的目标闭环是：

```text
业务模块声明 Definition 与执行端口
              ↓
启动期 CrudResourceCompiler 全量编译和校验
              ↓
不可变 CrudResourceRegistry 原子发布
              ↓
BaseCrud 安全模板执行查询或变更
              ↓
CrudViewPlan 按允许字段批量装配
              ↓
EntityDef 叠加 UI 布局并下发有效能力
```

由此得到以下硬性原则：

- **代码定义能力上限**：客户端、EntityDef 和数据库配置都不能生成或扩大服务端能力；
- **安全默认拒绝**：缺少资源、操作、租户、字段或引用授权时拒绝执行；
- **单一真理源**：同一资源和同一能力只允许一种声明路径；
- **声明与执行分离**：Definition 描述契约，执行器和业务 Handler 实现行为；
- **业务不变量显式保留**：状态机、跨字段规则、领域权限和副作用不进入通用元数据解释器；
- **尽早失败**：生产环境只允许启动期完整编译，不允许首次请求时延迟发现配置错误。

## 现状与重构动因

AAF 当前已经具备以下基础：

- `CrudFilterSchema` / `CrudFilterField` 同时提供筛选元数据与服务端 `Specification`；
- `ResourceReference` / `ResourceReferencePatch` 表达资源引用及未传、清空、设置三态；
- `BaseCrudService` 提供分页、排序、字段集、记录规则、个人视角和工作区条件；
- `CodeEntityResourceRegistry` 通过 `@EntityView` Controller 发现资源；
- `UserRelationService.findRefs(...)` 等接口支持跨业务包的批量轻量引用；
- EntityDef 描述字段组件、布局和前端交互。

现状不能直接作为新架构执行内核，主要原因是：

- 资源身份同时分散在 `@EntityView`、Controller 路径、EntityDef slug、Service slug 和权限码中；
- `sortableFields()`、`filterSchema()`、`defaultSort()`、`ownerFieldName()` 等静态事实散落在多个 Hook；
- 列表、单条、批量、更新、删除和引用入口组合的访问条件不完全一致；
- 字段权限主要在输出末端裁剪，尚未覆盖筛选、排序、导出、写入和关系加载；
- 日期变量已由 `DateTimeFilterVariable` 进行字段级声明，前端元数据与服务端解析共享同一白名单；
- 关系同步缺少统一的 Patch、并发、删除、租户和执行端口契约；
- `version` 字段尚未形成真正的乐观锁机制；
- 手写输出视图虽然避免 N+1，但缺少统一输出契约、Loader 依赖和查询预算。

## 当前实施状态

本轮已完成从过渡骨架到 v2 运行时闭环的代码切换；文档保持 `draft`，因为按用户要求未执行测试、构建或验收，以下状态仅表示实现已落盘：

- 日期筛选使用请求级 `FilterEvaluationContext`，同一请求共享 Clock、ZoneId 和 now；字段变量白名单继续同时驱动元数据与服务端解析；
- `CrudResourceRegistry` 已接管资源发现，46 个 BaseCrud Controller 与 1 个 User options 端点形成 47 个显式条目；Compiler 校验端点与 Provider 双射后原子发布带 fingerprint 的快照；
- `CodeEntityResourceRegistry`、`@EntityView` 发现、可选 Definition、静态 Hook fallback 和 Controller Bean 反射引用已删除；EntityDef bootstrap、引用目录、CrudMeta 与 AI Adapter 统一消费 Catalog；
- Definition 已包含类型、descriptor、capability、tenant、exposure、字段能力、query、mutation、view 和 relation 子契约；
- BaseCrud 已接入 TenantScope、AccessMode、RecordRule、PersonalScope、CompiledFieldPolicy 和统一 enforcement；字段能力覆盖 read、write、filter、sort、aggregate、export、reference；
- QueryToken 使用服务端 HMAC，并绑定 subject、org、workspace、resource、fieldSet、query hash、权限版本、签发与过期时间；
- Framework 已提供 `Patch<T>`、ReferencePolicy、Relation Handler/Loader、CrudViewPlan/CrudViewMapper 和 Mutation/CrudViewDefinition SPI；业务 Repository 未进入 Framework；
- Todo 已显式声明 fieldSet、字段能力、source 引用、participants REPLACE 与 users/participants Loader；更新使用 expectedVersion 和聚合锁，关系更新推进父版本；
- Todo 的系统来源不可编辑、默认执行人、跨用户指派和 ReBAC 规则继续保留；特殊入口通过明确 AccessMode 进入统一管线；
- `ResourceReferencePatch`、Todo 旧手写参与人/输出视图双路径已删除；Comment 使用 `NESTED_CRUD` 端点类型，未使用的 Rating BaseCrud 已移除。

尚未执行：编译、单元测试、集成测试、静态验收和运行时迁移验证。因此本文验收标准不能标记为已通过，发布与质量门控仍需后续独立完成。

## 总体架构

### 唯一资源身份

每个资源必须使用带命名空间的稳定 `ResourceKey`：

```java
public final class TodoResource {
    public static final ResourceKey KEY = ResourceKey.of("system.todo");

    private TodoResource() {}
}
```

规则如下：

- `ResourceKey` 是关系引用、Catalog、EntityDef 绑定、审计和 AI Action 的唯一主键；
- `slug` 仅用于显示或局部路由，可由 `ResourceKey` 最后一段派生，不能反向充当主键；
- API path 和权限命名空间是 ResourceKey 的显式绑定信息，启动期校验唯一性；
- Controller、Definition 和关系声明复用同一个常量，禁止复制不同字符串；
- 现有 `@EntityView` 不再作为资源发现权威，切换后由 Catalog 向 EntityDef bootstrap 提供可信资源目录。

### 唯一资源目录

`CrudResourceRegistry` 是进程内唯一的代码资源目录。它由 `CrudResourceCompiler` 在启动期收集 `CrudResourceDefinitionProvider` 和端点绑定后一次性构建：

```text
DefinitionProvider beans
+ Controller endpoint bindings
+ RelationHandler / RelationLoader beans
+ 权限与租户策略引用
              ↓
CrudResourceCompiler
              ↓ 校验全部通过
Immutable CrudResourceRegistry snapshot
```

Catalog 至少保存：

- ResourceKey、slug、label、API path、权限命名空间；
- Entity、CreateDTO、UpdateDTO、PageDTO、输出模型；
- 操作能力、查询字段、字段集和默认排序；
- 租户、个人视角、记录和字段策略引用；
- 关系、引用、Handler、Loader 和视图计划；
- schema version、definition fingerprint 和构建时间。

以下情况必须阻止应用启动：

- ResourceKey、slug 或 API path 重复；
- Controller 没有 Definition，或 Definition 没有端点绑定；
- 查询、排序、输入或输出字段不存在于声明的类型；
- 字段集引用未知输出字段；
- 关系目标、Handler、Loader 或权限策略不存在；
- 同一资源同时使用旧 Hook 和新 Definition；
- Definition schema version 不受当前运行时支持。

Catalog 构建失败时不得发布部分目录；生产请求路径不得扫描 classpath 或重新解释注解。

## 资源契约模型

`CrudResourceDefinition` 是多个子契约的聚合，不直接执行数据库或授权逻辑：

```java
CrudResourceDefinition<
                Todo,
                TodoCreateRequest,
                TodoUpdateRequest,
                TodoVO,
                TodoPageRequest>
        definition =
                CrudResourceDefinition
                        .builder(
                                TodoResource.KEY,
                                Todo.class,
                                TodoCreateRequest.class,
                                TodoUpdateRequest.class,
                                TodoVO.class,
                                TodoPageRequest.class)
                        .descriptor(
                                ResourceDescriptor.of("待办", "/system/todos", "system:todo"))
                        .capabilities(TodoCrudCapabilities.STANDARD)
                        .tenantScope(TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL)
                        .personalScope(PersonalScope.byProperty("assigneeId"))
                        .query(todoQueryDefinition)
                        .mutation(todoMutationDefinition)
                        .view(todoViewDefinition)
                        .relations(todoRelationDefinitions)
                        .build();
```

顶层 Definition 聚合以下职责：

| 子契约 | 职责 | 不负责 |
|---|---|---|
| `ResourceDescriptor` | 身份、标签、API 和权限命名空间绑定 | UI 布局、授权结果 |
| `CapabilityDefinition` | read/create/update/delete/export/options 等能力上限 | 当前用户是否获准 |
| `QueryDefinition` | 查询字段、操作符、排序、默认排序、字段集 | 任意客户端字段解释 |
| `MutationDefinition` | 输入字段、Patch、版本和变更步骤 | 领域状态机实现 |
| `CrudViewDefinition` | 输出 schema、字段集、CrudViewMapper 和 Loader DAG | 循环查库、实体导航 |
| `RelationDefinition` | 目标、基数、Patch、租户、权限和同步策略 | 直接访问业务 Repository |
| `TenantScopeDefinition` | 资源租户类型和缺失上下文语义 | 由 UI Header 决定放行 |
| `PolicyReferences` | 操作、记录和字段策略引用 | 把角色写死为服务端规则 |

第一阶段允许属性名使用字符串，但必须包装成带所属类型的 `PropertyRef` 并在启动期校验。文档与代码不得把这种模式描述为完整编译期类型安全。

```java
PropertyRef<Todo, String> title = PropertyRef.of(Todo.class, "title");
```

只有在多个资源迁移后证明注解或生成代码能稳定减少重复，才引入 Annotation Processor。生成路径一旦启用，必须替换对应资源的手写 Provider，不能采用“注解默认值 + 显式 Definition 覆盖”的双来源合并模型。

## 模型职责边界

| 模型 | 权威内容 | 禁止承担 |
|---|---|---|
| JPA Entity | 表结构映射、数据库约束、持久化状态、乐观锁 | UI 字段、API 字段权限、跨资源展示导航 |
| Create/Update DTO | 外部输入、Bean Validation、字段存在性和 Patch 语义 | 直接授权、任意实体属性绑定 |
| QueryDefinition | 公共查询名到受控属性和规则的映射 | 从 EntityDef 或请求动态生成字段 |
| CrudViewDefinition | 输出字段、字段集、批量 Loader 和组装 | 在循环中查询 Repository |
| CrudResourceDefinition | 服务端静态能力上限及策略引用 | 当前用户动态授权结果、业务状态机 |
| CrudResourceRegistry | 已编译且可执行的运行时资源快照 | UI 布局和租户自定义展示 |
| EntityDef | 标签、组件、布局、列宽、视图和交互配置 | 扩大操作、字段、关系或查询能力 |
| 权限引擎 | actor/action/resource/record/field/context 的允许、拒绝或掩码结果 | 由 EntityDef 或前端角色决定安全结果 |

有效 UI 元数据遵循：

```text
有效能力 = Catalog 代码上限 ∩ 当前用户动态权限 ∩ EntityDef UI 收窄配置
```

EntityDef 可以调整标签和布局，可以隐藏能力，但不能新增 Catalog 未声明的字段、筛选、排序、Action 或关系。Bootstrap 发现未知绑定时应拒绝该配置并记录明确诊断，不能静默扩大或降级。

`visibleRoles` 属于 EntityDef 的交互提示，不进入服务端字段授权注解。角色通过 RBAC 获得权限，服务端只消费权限和策略结果。

## 不可绕过的安全执行管线

BaseCrud 的公开方法采用不可覆写的模板方法。业务模块只能在命名明确的扩展点追加业务条件或变更，不能替换安全条件。

### 读取管线

```text
建立 Actor / Org / Workspace 上下文
→ 校验资源操作能力与 action permission
→ 构建 TenantScope 条件
→ 构建 RecordRule 条件
→ 构建 PersonalScope 条件
→ 校验 filter/sort/group/export 字段能力
→ 执行受控查询
→ 计算当前记录允许的输出字段
→ 只激活允许字段需要的 RelationLoader
→ 批量加载并组装声明的输出类型
→ 记录审计与查询指标
```

### 写入管线

```text
建立 Actor / Org / Workspace 上下文
→ 校验资源操作能力与 action permission
→ 解析字段存在性和 Patch 三态
→ 校验 write/reference/link 字段能力
→ 在 TenantScope + RecordRule + PersonalScope 中加载带版本聚合
→ 应用标量变更和业务前置不变量
→ 校验目标引用和关系
→ 同步关系并触发后置不变量
→ 提交事务
→ after-commit 发布事件或写入事务 Outbox
→ 按读取管线返回输出视图
```

必须覆盖以下入口：

```text
page / query / get / batch / options / export / group / import
create / update / delete / reference
AI Action / internal application action
```

内部调用和 AI Action 不得绕开 Service enforcement API。管理员维护和系统任务使用命名明确的 `AccessMode`，并同时要求专用权限和审计：

```java
AccessMode.DEFAULT;
AccessMode.ADMIN_MAINTENANCE;
AccessMode.SYSTEM_JOB;
```

协作者单对象读取不使用特殊 `AccessMode`。资源 Service 只有在确实支持协作者读取时，才可为 `CrudOperation.GET` 显式返回与当前 ID 绑定的 `AuthorizationPlan.RelationRequirement`。框架先按完整 L3 范围查询；未命中后执行 L1+L2+L4，允许时仅以 tenant scope 重查。列表不会自动合并 L2 记录，UPDATE/DELETE 也不走关系替代路径。

Repository 直连不是合法的安全逃生口。Hibernate 组织过滤器可作为纵深防御，但不能替代资源管线的显式 TenantScope。

## 租户、记录与引用策略

每个资源必须声明一种租户范围：

```java
public enum TenantScope {
    GLOBAL,
    ORG_REQUIRED,
    WORKSPACE_REQUIRED,
    ORG_SHARED_WORKSPACE_OPTIONAL
}
```

语义要求：

- `GLOBAL`：仅允许显式声明的全局配置资源，不隐式继承 org/workspace；
- `ORG_REQUIRED`：缺少组织上下文时 fail-closed；
- `WORKSPACE_REQUIRED`：组织和工作空间均必需，缺失任一上下文时 fail-closed；
- `ORG_SHARED_WORKSPACE_OPTIONAL`：同组织内允许 `workspaceId = null` 的共享记录，携带 workspace 时可见共享记录和当前 workspace 记录；
- 系统任务必须显式使用 `SYSTEM_JOB`，声明租户范围并记录审计，禁止依赖“无 Header 即放行”。

`PersonalScope` 与数据 `ownerId` 分离。Todo 的 `assigneeId` 表达个人执行视角，不应再命名为 `ownerField`：

```java
PersonalScope.byProperty("assigneeId")
```

资源引用使用独立 `ReferencePolicy`：

```text
canRead(target) 不等于 canReference(target)
```

ReferencePolicy 至少校验：

- 目标 ResourceKey 和 ID 类型；
- 目标记录存在且处于可引用状态；
- 当前 actor 具有 link/reference 权限；
- 父记录与目标记录的 org/workspace 组合合法；
- global 与 tenant 资源之间是否允许单向引用；
- 删除或归档后的引用处理策略。

不可见或不可引用的目标统一按资源不存在处理，避免记录存在性探测。

## 查询、筛选与日期变量

查询字段必须显式声明公共名称、受控属性、操作符和值解析规则。不得根据 JPA Entity、EntityDef 或客户端字段名自动开放查询。

```java
QueryDefinition<Todo> query =
        QueryDefinition.<Todo>builder()
                .field(QueryField.text("title", PropertyRef.of(Todo.class, "title")))
                .field(
                        QueryField.enumValues(
                                "status",
                                PropertyRef.of(Todo.class, "status"),
                                TodoStatusEnum.ARRAYS))
                .field(
                        QueryField.localDateTime(
                                "dueDate",
                                PropertyRef.of(Todo.class, "dueDate"),
                                Set.of(
                                        DateTimeFilterVariable.NOW,
                                        DateTimeFilterVariable.TODAY_START,
                                        DateTimeFilterVariable.TOMORROW_START,
                                        DateTimeFilterVariable.NOW_PLUS_3_DAYS)))
                .sortable("id", "title", "status", "category", "dueDate", "createTime")
                .defaultSort(Sort.by("id").descending())
                .build();
```

同一 QueryField 必须同时生成：

- 前端可消费的字段、操作符和变量元数据；
- 服务端操作符、参数数量、枚举和值类型校验；
- 受控 `Specification` 构建规则。

日期解析使用请求级 `FilterEvaluationContext`：

```java
public record FilterEvaluationContext(
        Clock clock,
        ZoneId zoneId,
        LocalDateTime now) {}
```

规则如下：

- 每个请求只创建一个 `now`，所有条件和 `BETWEEN` 两端共享该基准；
- 字段未声明变量时只接受 ISO `LocalDateTime` 和 `LocalDate`；
- `$variable` 必须属于该字段允许集合，否则返回 `BAD_REQUEST`；
- 时区来源必须明确，禁止直接使用进程默认时区；
- 当前阶段只支持固定枚举，不引入 `$nowPlusNDays` 等参数化表达式。

查询窗口 token 若用于详情导航，只能证明窗口上下文，不能替代实时授权。Token 应使用服务端 HMAC 或等价签名，绑定 subject、org、workspace、ResourceKey、fieldSet、query hash、权限版本、签发时间和过期时间。

## 字段能力与访问策略

持久化字段、输入字段、查询字段和输出字段不是同一个概念，应分别声明后按明确映射关联。字段能力至少区分：

```text
read / write / filter / sort / aggregate / export / reference
```

静态 Definition 给出最大能力，动态权限只能收窄：

```text
有效字段能力 = Definition 字段上限 ∩ CompiledFieldPolicy ∩ 记录上下文策略
```

规则如下：

- 未授权写字段出现在请求中时返回 403，不静默忽略；
- 未传字段表示不修改，不能与无权限混为同一语义；
- 无读取权限的字段默认同时禁止 filter、sort、aggregate 和 export，除非 Definition 明确声明安全例外；
- 权限计算必须先于 RelationLoader，避免先加载敏感数据再删除；
- HTTP、AI Action 和内部应用 Action 使用同一 `CompiledFieldPolicy`；
- 现有权限表的 `fieldAccess` JSON 只能编译为动态收窄规则，不能扩大 Definition 上限。

CrudViewPlan 必须返回其声明的真实类型。禁止把 `V` 反射转换为 `Map` 后再强制转换回 `V`。

- 固定类型 API 使用明确 DTO/record，由 CrudViewMapper 对拒绝字段填充安全空值并按序列化契约隐藏；
- 需要动态字段形状的通用端点从接口层开始声明返回 `CrudRecord` 或 `ObjectNode`；
- 两种输出模式都必须在 Definition 中显式声明，不能在运行时偷偷改变返回类型。

## 关系与部分更新

### 统一 Patch 语义

标量、引用和集合关系使用统一存在性模型：

```java
sealed interface Patch<T> {
    record Absent<T>() implements Patch<T> {}
    record NullValue<T>() implements Patch<T> {}
    record Value<T>(T value) implements Patch<T> {}
}
```

默认语义：

| 输入 | 标量 | 可空引用 | 集合关系 |
|---|---|---|---|
| 属性未传 | `UNCHANGED` | `UNCHANGED` | `UNCHANGED` |
| JSON `null` | 按字段 nullable 决定清空或拒绝 | `CLEAR` 或拒绝 | 默认拒绝，资源可显式允许 `CLEAR` |
| `[]` | 不适用 | 不适用 | `CLEAR` |
| 非空值 | `SET` | `SET` | `SET/REPLACE/DIFF` |

重复 ID、非法 ID、已删除目标和超过最大基数必须具有确定错误，不允许静默忽略。

### 关系契约

标准关系声明至少包含：

```java
RelationDefinition<Todo, Long> participants =
        RelationDefinition.<Todo, Long>builder("participants")
                .target(ResourceKey.of("system.user"))
                .cardinality(Cardinality.MANY)
                .required(false)
                .syncMode(RelationSyncMode.REPLACE)
                .linkPermission("system:todo:participant:write")
                .tenantPolicy(RelationTenantPolicy.SAME_ORG)
                .maxCardinality(100)
                .onTargetDelete(OnTargetDelete.UNLINK)
                .handler("todo.participants")
                .build();
```

`REPLACE` 和 `DIFF` 是静态业务语义，必须由资源显式选择。框架不得根据集合大小自动切换；阈值只能用于拒绝、告警或引导设计者改用 DIFF。

高基数、带关联属性、需要独立审计或外部副作用的关系不进入标准同步器，使用显式业务 Handler。

### 执行 SPI

Framework 定义执行协议，业务模块实现 Repository 访问：

```java
public interface RelationHandler<E, P> {
    void validate(RelationContext context, E parent, P patch);

    void synchronize(RelationContext context, E parent, P patch);
}
```

```java
public interface RelationLoader<E, R> {
    Map<Long, R> load(RelationLoadContext context, Collection<E> parents);
}
```

边界要求：

- `aaf-framework` 只依赖 SPI，不依赖 Todo、User 等业务实体或 Repository；
- Handler 位于拥有关系写入语义的业务包；
- 跨业务包读取通过目标包暴露的 Service 接口，例如 `UserRelationService`；
- Handler 和 Loader 以稳定 key 注册，Catalog 在启动期验证一一对应；
- 视图映射循环中禁止访问 LAZY 关系或逐条查询 Repository。

## 事务、并发与事件

标准更新事务按以下顺序执行：

```text
授权与输入预校验
→ 按安全条件加载带版本聚合
→ 应用标量 Patch
→ flush 主实体
→ 校验并同步关系
→ 执行业务后置不变量
→ 提交事务
→ after-commit 事件或 Outbox 派发
→ 返回安全输出视图
```

一致性要求：

- 聚合根使用真正的 JPA `@Version` 或等价并发控制；
- Update 请求必须携带期望版本，版本冲突返回 409；
- 仅修改关系时也必须推进或锁定父聚合版本，避免关系更新相互覆盖；
- 关系表至少具有防重复唯一约束，并在查询、删除和同步时约束 parent 与租户字段；
- 主实体、关系同步和事务内业务状态必须全部回滚；
- 外部副作用不得假装属于数据库本地事务，应使用 after-commit 或事务 Outbox；
- 带审计属性的关联不得被普通 delete-and-insert REPLACE 覆盖。

输出视图装配包含多次查询时必须声明一致性等级。普通列表允许在同一只读事务的 `READ_COMMITTED` 语义下读取；要求稳定快照的详情或导出应显式使用更强隔离或版本快照。Loader 失败时整体失败，不返回不完整输出视图。

## 输出视图与性能预算

`CrudViewDefinition` 显式描述：

- fieldSet 对应的输出字段；
- 每个输出字段需要的实体标量属性；
- 每个字段依赖的 RelationLoader；
- Loader 之间的 DAG 和批量 key；
- CrudViewMapper 与声明的响应类型；
- 当前字段集允许的最大记录数。

执行顺序：

```text
FieldSet
∩ 当前字段访问策略
→ Required Scalars
→ Active Loader DAG
→ 每个 Loader 批量查询
→ CrudViewMapper
→ 声明的响应类型
```

性能约束：

- 列表基础预算默认不超过 count + data 两次查询；
- 关系查询预算不超过 `activeLoaderCount`，总预算建议为 `2 + activeLoaderCount`；
- 查询次数不得随页面记录数线性增长；
- IN 查询必须声明分块上限，Loader 可使用请求级缓存复用相同引用；
- 高基数关系必须声明最大基数并采用专用分页或独立端点；
- export 使用 cursor、stream 或 chunk，禁止默认把全部记录和关系装入内存；
- Definition、策略引用和 Loader 图只在启动期编译，请求路径不得扫描注解或发现字段。

## Framework 与业务 Service 的边界

### Framework 负责

- ResourceKey、Definition、Compiler 和 Catalog；
- BaseCrud 不可绕过的查询与变更模板；
- 操作、租户、记录、字段和引用策略的执行顺序；
- QueryField、Patch、RelationHandler、RelationLoader 和 CrudViewPlan SPI；
- 标准错误语义、审计上下文、查询计数和启动诊断；
- EntityDef bootstrap 所需的可信能力快照。

### 业务模块负责

- 具体 ResourceDefinitionProvider；
- Entity、DTO、VO、Controller 和 Repository；
- 状态机、跨字段不变量和领域权限；
- RelationHandler、CrudViewMapper（Todo 实现为 TodoViewMapper）及跨模块 Service 适配；
- ReBAC 分享、审批、结算、内部事件创建和管理维护 Action；
- 复杂聚合、报表和高基数关系端点。

Framework 不允许硬编码 `users(...)`、Todo 参与人或 Billing 语义。是否能抽入 Framework 由“至少多个资源具有相同契约和执行顺序”证明，而不是以预计复用率判断。

## 已确定的设计决策

| 决策 | 结论 | 理由 |
|---|---|---|
| Definition 来源 | 第一阶段使用显式 Provider | 简单、可审查，不提前引入注解复杂度 |
| 编译时机 | 应用启动期全量编译并 fail-fast | 避免首次流量触发配置错误 |
| Annotation Processor | 多资源验证重复模式后再决定 | 防止为未稳定模型生成代码 |
| 资源注册 | `CrudResourceRegistry` 是唯一目录 | 消除 Controller 扫描与 Definition 双权威 |
| EntityDef 合并 | 只做 UI overlay 和能力收窄 | 防止数据库配置扩权 |
| 字段权限 | 静态上限与动态策略取交集 | 动态配置不能突破代码安全边界 |
| 关系同步 | 每个关系显式选择 REPLACE 或 DIFF | 两者具有不同并发和审计语义 |
| 日期变量 | 固定字段级枚举 | 降低解析、授权和测试复杂度 |
| JPA 关系 | ID 为主，批量 Loader 输出视图 | 保持模块边界并避免 N+1 |
| 迁移兼容 | 不保留同能力双路径 | AAF 未到 v1.0，直接切换并删除旧入口 |

## 实施与切换计划

本方案属于高风险架构调整，涉及 BaseCrud、资源注册、权限、关系和可能的数据迁移。进入实现前必须经过人类评审，并按完整 product → architect → developer → tester → qa 流程执行。

各阶段是同一重构分支上的工作包，不代表允许把双路径中间态发布到主分支。

### 基础加固阶段

完成：

- 盘点全部 BaseCrud 资源、静态 Hook、字段集、关系和权限模式；
- 建立 page/get/batch/options/export/create/update/delete/reference 的入口矩阵；
- 统一所有入口的 TenantScope、RecordRule、PersonalScope 组合；
- 验证日期变量服务端字段级白名单；
- 明确 query token 签名和时效；
- 为 Todo 建立 REST、EntityDef、权限、关系和 SQL 查询数黄金回归测试。

退出门槛：安全入口矩阵全绿，现有 Todo 行为有可重复基线。

### 资源目录切换阶段

完成：

- 引入 ResourceKey、DefinitionProvider、Compiler 和 Catalog；
- 为全部现有 BaseCrud 资源提供最小显式 Definition；
- Controller、EntityDef bootstrap 和引用目录统一消费 Catalog；
- 删除 `@EntityView` 资源发现权威和 BaseCrud 静态 Hook；
- 禁止“Definition 不存在则回退旧 Hook”。

Todo 作为首个参考实现，但切换变更必须覆盖全部现有 BaseCrud 子类后才能合并，不能长期保留新旧资源执行模式。

退出门槛：启动校验覆盖全部资源；旧资源发现和静态 Hook 搜索结果为 0。

### Todo 读取闭环阶段

完成：

- Todo QueryDefinition、字段能力、字段集和 PersonalScope；
- 读取安全模板和显式 AccessMode；
- assignee、participants、createBy、updateBy 的 CrudViewPlan 与批量 Loader；
- 权限先于 Loader 的执行顺序；
- EntityDef 有效能力由 Catalog 与动态权限生成。

退出门槛：所有读取入口权限一致；100 和 1000 条基准数据下查询次数不随记录数线性增长。

### Todo 写入闭环阶段

完成：

- 标量、来源引用和参与人的统一 Patch 三态；
- ReferencePolicy 和 `canReference`；
- RelationHandler、REPLACE/DIFF 静态策略；
- 聚合乐观锁、关系唯一约束和事务失败注入；
- 跨用户指派等领域权限保留在 Todo 显式策略中。

退出门槛：并发、跨租户、非法引用和事务故障测试全绿；旧参与人和来源同步代码已删除。

### 多资源验证阶段

完成：

- 选择 Billing 等至少两个不同复杂度资源迁移查询和输出视图能力；
- 统计删除的样板、Definition 规模、Hook 数和测试复杂度；
- 识别真正稳定的关系与字段模式；
- 未满足复用证据的能力保留在业务模块，不继续抽象。

退出门槛：至少三类资源证明契约可复用，且没有新增业务耦合或跨模块反向依赖。

### 生成能力评估阶段

仅在显式 Definition 出现稳定重复后评估 Annotation Processor。若采用生成：

- 生成物实现同一个 DefinitionProvider 接口；
- 一个资源只能选择手写或生成之一；
- 编译错误直接定位未知属性、DTO 字段和关系目标；
- 不引入运行时注解扫描和显式覆盖优先级。

## 验收标准

### Catalog 与定义一致性

- 重复 ResourceKey、slug、API path 必须启动失败；
- 未知实体属性、DTO 字段、输出字段、fieldSet、目标资源、Handler 和 Loader 必须启动失败；
- Catalog 构建原子完成，失败时不提供部分资源；
- 生产请求路径不扫描 classpath，不发现或解释新字段；
- Definition fingerprint 可在日志、诊断端点和审计记录中关联。

### 安全入口矩阵

- page/query/get/batch/options/export/group/import/create/update/delete/reference/AI/internal 入口均经过统一策略；
- 未授权写字段显式提交返回 403，数据库和关系表无变化；
- 无读取权限字段不能通过 filter、sort、aggregate 或 export 推断；
- 业务 Hook 只能追加业务条件，不能删除 TenantScope 或 RecordRule；
- 管理和系统模式要求专用权限并产生审计记录。

### 租户与引用攻击矩阵

- 无 org、错误 org、跨 org、无必需 workspace、跨 workspace 均按资源策略拒绝；
- global → tenant、tenant → global 和跨 workspace 引用分别有明确结果；
- `canRead` 通过但 `canReference` 不通过时不得建立关系；
- 关系表不存在跨租户脏数据，查询和删除均包含父记录及租户约束。

### Patch、事务与并发

- 未传、null、空集合、重复 ID、非法 ID、已删除目标均有确定结果；
- 两个客户端基于同一版本修改标量或关系时恰有一个成功，另一个返回 409；
- 在主表保存后、关系删除后、关系插入后分别注入异常，全部数据库变更回滚；
- 事务失败不发布 after-commit 事件，Outbox 与业务事务保持一致；
- REPLACE 和 DIFF 的行为由 Definition 固定，不因数据量改变。

### 输出视图与性能

- 权限裁剪发生在 Loader 激活前；
- 列表关系查询数不随记录数线性增长；
- 指定数据规模与 activeLoaderCount 下 SQL 数量满足预算；
- Loader 对大集合执行分块，export 按 chunk/stream 处理并有内存上限；
- Loader 失败时不返回不完整输出视图或缺失安全标识的响应。

### 切换完整性

- Todo 的 list/detail/picker/export、参与人、来源引用、ReBAC 和个人视角保持黄金回归；
- 已切换资源不再覆写旧静态 Hook；
- 不存在 Definition 缺失回退、旧 Registry fallback、双写或兼容 Adapter；
- EntityDef 不能新增 Catalog 未声明的字段、操作、筛选、Action 或关系；
- 多资源验证后以真实迁移比例和样板减少量报告收益，不使用未经盘点的“80%–90%”估算。

## 风险与控制

| 风险 | 控制措施 |
|---|---|
| 资源目录切换涉及多个模块和大量文件 | 先建立黄金测试，在单一高风险变更中全量切换，经人类审核后合并 |
| 通用 Definition 演化为 God Object | 使用子契约和 SPI；没有多资源证据的能力留在业务模块 |
| 动态字段权限产生侧信道 | 权限覆盖 read/write/filter/sort/aggregate/export/reference，并先于查询和 Loader |
| 关系 REPLACE 造成并发丢失 | `@Version`、父聚合版本推进、唯一约束和 409 冲突语义 |
| 多查询输出视图出现快照漂移 | 声明只读事务与一致性等级，关键详情或导出使用稳定快照 |
| 启动校验增加启动时间 | 只在启动期执行并输出耗时；不以延迟到首次请求作为优化手段 |

## 非目标

本方案不实现或不允许：

- EntityDef 自动建表、自动加列或运行时 DDL；
- 根据客户端或 EntityDef 字段名生成任意 JPA 查询和写入；
- 以 JPA `@ManyToOne` 实体导航替代 ResourceKey、ReferencePolicy 和批量输出视图；
- 将 ReBAC、审批、结算、清理和复杂报表隐式塞入 BaseCrud；
- 以 UI 隐藏、角色名称或缓存的前端元数据替代服务端授权；
- 为迁移保留 fallback、dual-write、legacy adapter 或同能力双路径；
- 在缺少多资源证据时预先实现 Annotation Processor 或参数化日期表达式。
