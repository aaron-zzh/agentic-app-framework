---
level: Practice
layer: Model
purpose: 定义 AAF 不可违背的架构约束，作为代码实现和 AI 生成的硬性边界
status: published
version: 1.0.0
date: 2026-07-14
author: AaronZZH
scope:
  includes:
    - 依赖方向约束
    - 模块边界约束
    - 分层纪律约束
gains:
  - 架构约束可被人和 AI 共同遵守和验证
---

# 架构约束规范

> 本文档定义 AAF 的架构硬约束。所有代码实现（人工或 AI 生成）必须遵守，违反即为架构缺陷。

## 依赖方向

```
aaf-api → aaf-framework → aaf-common
aaf-auto-dev → aaf-framework → aaf-common
```

- ❌ 禁止反向依赖（如 aaf-common 依赖 aaf-framework）
- ❌ 禁止 aaf-api 内不同业务包直接访问彼此的 entity/repository（通过事件或 service 接口解耦）
- ❌ 禁止业务包依赖 aaf-auto-dev

## 模块限制清单

| 模块 | 允许 | 禁止 |
|------|------|------|
| **aaf-dependencies** | 仅声明依赖版本（BOM） | ❌ 任何 Java 代码、❌ 任何运行时依赖 |
| **aaf-common** | 工具类、常量、异常定义、通用注解、第三方工具封装 | ❌ 业务逻辑、❌ Spring Bean 定义、❌ 数据库访问、❌ 依赖 framework/api/auto-dev |
| **aaf-framework** | AI/Agent/工作流/知识库/记忆等框架级能力、Spring 扩展 | ❌ 具体业务逻辑、❌ 依赖 aaf-api、❌ 依赖 aaf-auto-dev、❌ 业务实体 |
| **aaf-auto-dev** | 代码生成、分析、自进化相关能力 | ❌ 具体业务逻辑、❌ 依赖 aaf-api |
| **aaf-api** | 业务模块（按包隔离）、REST 控制器、启动类 | ❌ 跨业务包直接访问 entity/repository、❌ 框架级通用能力（应放 framework） |

## 分层纪律

业务模块内部分层：`controller/ → service/ → domain/`

- ❌ controller 禁止直接访问 domain/repository
- ❌ domain 层禁止依赖 Spring 框架注解（@Service、@Autowired 等）
- ❌ service 层禁止返回 Entity 给 controller（必须转为 VO/DTO）

## 模块边界

- 所有业务代码必须在 `aaf-api/module/` 下开发
- 包命名：`com.xuejiai.aaf.module.{name}`
- 每个业务包独立，不共享数据库表（跨包通过 service 接口或事件通信）

## 决策树：一段逻辑放哪个模块？

```text
D1. 这段代码是否与具体业务无关（纯工具/通用能力）？
 ├─ 是 → D2
 └─ 否 → D4

D2. 是否依赖 Spring / 框架能力？
 ├─ 否 → 放 aaf-common（纯工具类、常量、异常定义）
 └─ 是 → D3

D3. 是否属于 AI/Agent/工作流/知识库等框架级能力？
 ├─ 是 → 放 aaf-framework
 └─ 否 → 放 aaf-common（通用 Spring 扩展，如自定义注解处理器）

D4. 是否属于 AI 自动开发（代码生成/分析/自进化）？
 ├─ 是 → 放 aaf-auto-dev
 └─ 否 → D5

D5. 是具体业务逻辑（用户、文档、聊天等）？
 └─ 是 → 放 aaf-api/module/{业务名}
```

## 决策树：一段逻辑放业务包的哪一层？

```text
L1. 这段代码是否直接处理 HTTP 请求/响应（参数校验、路由、返回格式）？
 ├─ 是 → 放 controller/
 └─ 否 → L2

L2. 是否是纯领域概念（实体、值对象、枚举、领域事件）？
 ├─ 是 → 放 domain/（禁止 Spring 注解）
 └─ 否 → L3

L3. 是否是数据访问（查询、持久化）？
 ├─ 是 → 放 repository/
 └─ 否 → L4

L4. 是否是对象转换（Entity ↔ VO/DTO）？
 ├─ 是 → 放 mapper/
 └─ 否 → 放 service/（业务编排、事务、跨实体协调）
```

## 决策树：跨业务包通信方式？

```text
C1. 调用方是否需要同步获取返回值？
 ├─ 是 → 通过目标包暴露的 Service 接口调用（注入 interface，不直接依赖实现）
 └─ 否 → C2

C2. 是否是"通知"性质（发生了某事，不关心谁处理）？
 └─ 是 → 发布领域事件（Spring ApplicationEvent），目标包监听处理
```

## 数据安全

- Entity 的 `password`、`secret` 等敏感字段必须标注 `@JsonIgnore`
- 禁止在日志中输出敏感信息
- 数据库连接信息禁止硬编码在代码中


## ArchUnit 守护规则（待激活）

以下规则在 Maven 拆分完成 + P2.3 阶段激活 `LayeringTest.java`：

```java
// 智能层不直接访问数据库
noClasses().that().resideInPackage("..intelligent..")
    .should().accessClassesThat().resideInPackage("..repository..")

// 引擎层不调用智能层
noClasses().that().resideInPackage("..engine..")
    .should().accessClassesThat().resideInPackage("..intelligent..")

// common 无 Spring Bean
noClasses().that().resideInPackage("..common..")
    .should().beAnnotatedWith(Component.class)
```
