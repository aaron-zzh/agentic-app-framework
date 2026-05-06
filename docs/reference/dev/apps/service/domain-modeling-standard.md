---
level: Practice
layer: Model
purpose: AAF 领域建模实践指南，提取 DDD 精华融入 Spring Boot 4 技术栈
status: draft
version: 0.3.0
date: 2026-03-30
author: AaronZZH
scope:
  includes:
    - 模块内分层结构
    - 实体、值对象、领域事件规范
    - 充血模型实践
    - Spring Data JPA Repository 规范
gains:
  - 能按 AAF 规范组织领域代码
  - 理解何时使用 DDD 模式、何时用简单 CRUD
---

# AAF 领域建模实践指南

## 核心理念

**不是所有模块都需要 DDD**。根据业务复杂度选择合适的模式：

| 业务复杂度 | 推荐模式 | 典型场景 |
| ----------- | --------- | --------- |
| 简单 CRUD | Controller → Service → Repository | 字典管理、配置管理 |
| 中等业务逻辑 | 充血模型 + Service | 用户管理、内容管理 |
| 复杂领域逻辑 | 聚合根 + 领域事件 + Service | 工作流、智能体编排 |

## 1. 模块包结构

所有业务代码在 `aaf-api/module/` 下按包开发，包名遵循 `com.xuejiai.aaf.module.{name}`。

### 简单模块（CRUD 为主）

```text
module.{name}
├── controller/     # REST API 控制器
├── domain/         # 实体 + 值对象
├── repository/     # Spring Data JPA 仓储
├── service/        # 业务逻辑
├── vo/             # DTO / VO
├── mapper/         # MapStruct 对象映射（按需）
└── enums/          # 枚举定义（按需）
```

### 复杂模块（有明确聚合边界）

domain/ 下按聚合划分子包，每个子包就是一个聚合，聚合根是包内的核心实体。

```text
module.{name}
├── controller/
├── domain/
│   ├── process/              # 聚合1：流程聚合
│   │   ├── Process.java      #   聚合根
│   │   ├── ProcessNode.java  #   子实体
│   │   └── NodeType.java     #   值对象/枚举
│   ├── task/                 # 聚合2：任务聚合
│   │   ├── Task.java         #   聚合根
│   │   └── TaskResult.java   #   值对象
│   └── shared/               # 跨聚合共享的值对象（按需）
│       └── Priority.java
├── repository/
├── service/
├── vo/
├── mapper/                       # MapStruct 对象映射（按需）
└── event/                        # 领域事件（按需）
```

**原则**：简单模块只需 controller/domain/service/repository 四个包；当 domain/ 内实体超过 5 个或存在明确聚合边界时，按聚合拆分子包。

## 2. 实体规范

### 2.1 基础实体

所有实体继承 BaseEntity，使用 Lombok 简化代码。

> 代码示例见 [领域建模代码片段](../../snippets/domain-snippets.md#基础实体)

### 2.2 充血模型

实体可包含业务方法，通过方法名表达业务含义，避免贫血模型。

> 代码示例见 [领域建模代码片段](../../snippets/domain-snippets.md#充血模型)

**何时使用充血模型**：
- ✅ 状态变更有业务规则约束
- ✅ 实体内部数据的一致性校验
- ✅ 简单的计算逻辑

**何时放在 Service**：
- ✅ 需要调用其他服务或外部依赖
- ✅ 跨实体的业务编排
- ✅ 需要事务控制

### 2.3 聚合根

当多个实体需要保持一致性时，通过聚合根统一管理。

> 代码示例见 [领域建模代码片段](../../snippets/domain-snippets.md#聚合根)

**聚合设计原则**：
- 保持聚合尽可能小
- 跨聚合通过 ID 引用，不直接持有对象
- 跨聚合的一致性通过领域事件实现

## 3. 值对象

值对象是不可变的，用于描述实体的属性组合。推荐使用 Java record 或 @Embeddable。

> 代码示例见 [领域建模代码片段](../../snippets/domain-snippets.md#值对象)

## 4. Repository 规范

使用 Spring Data JPA Repository，继承 JpaRepository 即可获得标准 CRUD。

> 代码示例见 [领域建模代码片段](../../snippets/domain-snippets.md#repository)

**Repository 原则**：
- 每个聚合根一个 Repository，子实体不单独建 Repository
- 优先用方法名派生查询，复杂场景用 @Query
- 分页查询返回 Page&lt;T&gt;，传入 Pageable 参数

## 5. 领域事件

两种方式发布领域事件：

- **方式一：JPA @DomainEvents**（推荐，聚合根内发布）
- **方式二：Service 中手动发布**（简单场景）

> 代码示例见 [领域建模代码片段](../../snippets/domain-snippets.md#领域事件)

## 6. 异常处理

使用 AAF 统一异常体系，不引入额外异常框架。

> 代码示例见 [领域建模代码片段](../../snippets/domain-snippets.md#异常处理)

## 7. 对象映射

使用 MapStruct 进行实体与 VO/DTO 的转换。

> 代码示例见 [领域建模代码片段](../../snippets/domain-snippets.md#对象映射mapstruct)

## 8. Service 层

Service 负责业务编排、事务控制和跨实体协调。

> 代码示例见 [领域建模代码片段](../../snippets/domain-snippets.md#service-层)

## 9. 设计原则补充

### 关于 @Data 与 setter

AAF 使用 @Data 简化代码，但聚合根的关键状态字段应通过业务方法修改，不应直接调用 setter：

```java
// ✅ 正确：通过业务方法修改状态
user.activate();
process.publish();

// ❌ 避免：直接 set 绕过业务规则
user.setStatus(UserStatus.ACTIVE);
```

> Lombok @Data 保留是为了 JPA 框架和 MapStruct 映射需要，开发者应自觉通过业务方法操作聚合根。

### 值对象优先

当多个字段总是一起出现时，优先提取为值对象，而非散落在实体中：

```java
// ✅ 值对象
@Embedded private Address address;

// ❌ 散落字段
private String province;
private String city;
private String detail;
```

### Repository 纪律

- Repository 只做数据访问，**不包含业务逻辑**
- 复杂查询场景（报表、统计）可单独建 QueryService，与领域 Repository 分离
- 异常信息应对用户友好，Controller 层统一处理 HTTP 状态码映射

## 10. 最佳实践总结

| 实践 | 说明 |
|------|------|
| 实体用 Lombok | @Data @Builder @EqualsAndHashCode，不手写 getter/setter |
| 继承 BaseEntity | 统一 id、createTime、updateTime、deleted 等公共字段 |
| 软删除 | @SQLDelete + @SQLRestriction("deleted = false") |
| Repository | 每个聚合根一个，继承 JpaRepository，子实体不单独建 |
| 充血模型适度使用 | 状态变更、内部校验放实体，编排逻辑放 Service |
| 跨聚合用 ID 引用 | 不直接持有其他聚合的对象引用 |
| 领域事件 | 优先用 @DomainEvents，简单场景用 ApplicationEventPublisher |
| 异步事件 | @TransactionalEventListener(AFTER_COMMIT) 保证事务安全 |
| 异常用 BusinessException | 不自定义异常枚举体系 |
| 值对象用 record 或 @Embeddable | 保持不可变性 |
| 只读查询 | @Transactional(readOnly = true) 提升性能 |
| setter 纪律 | @Data 保留但关键状态通过业务方法修改，不直接 set |
| 值对象优先 | 多字段总是一起出现时提取为 @Embeddable |
| Repository 无业务逻辑 | 复杂查询用 QueryService 分离 |
| 按需分包 | 简单模块不需要 event/enums 等包 |

## 11. Polymorphic Actor 约定

> AAF 的"AI 是一等公民"理念要求 Agent 和 Human 在数据模型层共享统一抽象，而非两套独立体系。

### 核心规则

所有需要记录"谁做了什么"的业务实体，统一使用 `actor_type + actor_id` 二元组，不单独建 `user_id` 或 `agent_id` 字段：

```java
// ✅ 统一 Actor 引用
@Column(nullable = false, length = 16)
private String actorType;  // HUMAN / AI

@Column(nullable = false)
private Long actorId;      // 指向 User 或 Agent 表的 ID
```

### 适用场景

| 场景 | 字段 | 说明 |
|------|------|------|
| 任务创建/完成 | `creator_type + creator_id` | 谁创建/完成了这个任务 |
| 评论/消息 | `author_type + author_id` | 谁发的 |
| 审计日志 | `actor_type + actor_id` | 谁触发了操作 |
| 文档变更 | `modifier_type + modifier_id` | 谁改的 |
| 订阅/通知 | `subscriber_type + subscriber_id` | 谁订阅的 |

### 禁止模式

```java
// ❌ 两套独立字段
private Long userId;
private Long agentId;  // 二选一填，另一个 null

// ❌ 只有 userId，Agent 操作无法记录
private Long userId;
```

### BaseEntity 扩展

`BaseEntity` 的 `createBy` / `updateBy` 字段应为 Actor 格式（`AI/architect` 或 `Human/AaronZZH`），与产出物模板中的 `actor_type/actor_id` 标记一致。

## 参考

- [AAF 架构设计](../../../../design/architecture.md)
- [AAF 开发规范](../../development-standard.md)
- [AAF 设计原则](../../../../explanation/design-principles.md)
