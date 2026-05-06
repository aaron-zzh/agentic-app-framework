---
level: Practice
layer: Model
purpose: 定义 Java/Spring Boot 后端编码风格规范
status: published
version: 1.0.0
date: 2026-05-06
author: AaronZZH
changelog:
  - 2026-05-06 | 补充 Front Matter
---

# AAF 开发规范

本文档定义 Agentic App Framework (AAF) 项目的开发标准和规范，包括模块命名、依赖管理、包结构、代码风格、测试、日志、异常处理等。所有开发者（包括 AI 编程助手）均需遵守。

## 基本原则

- 遵循阿里巴巴 Java 开发手册
- 遵循 [架构约束规范](../../architecture-constraints.md)（依赖方向、分层纪律、模块边界）
- 使用 Lombok 简化样板代码（`@Data`、`@Builder`、`@Slf4j` 等）
- 代码即文档，保持可读性，必要处添加注释
- **注释语言统一中文**，禁止中英混用（与 `docs/` 真理源一致）
- 编写单元测试（JUnit 5 + Mockito），保证功能正确性
- 使用 Cucumber 编写验收测试，自然语言描述业务场景
- 遵守 Git 提交规范

## 模块命名规范

### 基本规则

- **全小写**：所有模块名使用小写字母
- **连字符分隔**：多个单词使用连字符（-）分隔
- **语义明确**：模块名应清晰表达功能

### 命名模式

| 模块类型   | 命名模式            | 示例                          |
| ---------- | ------------------- | ----------------------------- |
| 核心模块   | `aaf-{name}`        | `aaf-framework`、`aaf-common` |
| 业务模块   | `aaf-module-{name}` | `aaf-module-system`           |
| 自动化模块 | `aaf-auto-{name}`   | `aaf-auto-dev`                |

### 示例

✅ **正确**：

- `aaf-auto-dev`
- `aaf-module-workflow`
- `aaf-framework`

❌ **错误**：

- `aaf-autodev`（应使用连字符）
- `aaf-AutoDev`（不应使用大写）
- `aaf_framework`（不应使用下划线）

## 依赖管理规范

### 核心原则

**所有依赖必须先在 `aaf-dependencies` 注册版本，各模块引入时不写版本号。**

### 工作流程

1. 在 `aaf-dependencies/pom.xml` 中注册版本
2. 在具体模块中引入（不写版本号）
3. 升级时只修改 `aaf-dependencies` 中的版本号

> 代码示例见 [模块代码片段](../../snippets/module-snippets.md)

### 依赖范围

| 依赖类型     | 在哪里声明           | 说明                    |
| ------------ | -------------------- | ----------------------- |
| 框架公共依赖 | `aaf-api/pom.xml`    | 业务层统一引入          |
| 运行时依赖   | `aaf-api/pom.xml`    | 如数据库驱动            |
| 可选模块     | `aaf-api/pom.xml`    | 如 aaf-auto-dev（optional）|

> 代码示例见 [模块代码片段](../../snippets/module-snippets.md)

## 目录结构规范

### 模块职责划分

| 目录                | 职责                   | 是否可修改 |
| ------------------- | ---------------------- | ---------- |
| `aaf-dependencies/` | 依赖版本管理（BOM）    | 框架维护者 |
| `aaf-common/`       | 公共工具类、常量、异常 | 框架维护者 |
| `aaf-framework/`    | 核心框架能力（引擎层） | 框架维护者 |
| `aaf-auto-dev/`     | AI 自动开发能力        | 框架维护者 |
| `aaf-api/`          | ⭐ 业务代码开发区 + 启动入口 | 用户开发 |

### 业务模块开发

**所有业务代码必须在 `aaf-api/module/` 目录下开发，按包隔离。**

> 创建步骤见 [模块代码片段](../../snippets/module-snippets.md#创建新业务模块)

## 包命名规范

### 基本规则

```java
com.xuejiai.aaf.{module}.{feature}.{layer}
```

### 示例

```java
com.xuejiai.aaf.common.util               # 公共工具类
com.xuejiai.aaf.framework.agent           # 框架-智能体引擎
com.xuejiai.aaf.framework.workflow        # 框架-工作流引擎
com.xuejiai.aaf.module.system.controller  # 业务-系统-控制器
com.xuejiai.aaf.module.agent.service      # 业务-智能体-服务
```

### 分层结构

```text
aaf-api/src/main/java/com/xuejiai/aaf/module/{name}/
    ├── controller/    # REST API 控制器
    ├── service/       # 业务逻辑层
    ├── domain/        # 实体 + 值对象
    ├── repository/    # Spring Data JPA 仓储
    ├── vo/            # DTO / VO
    ├── mapper/        # MapStruct 对象映射（按需）
    ├── enums/         # 枚举定义（按需）
    └── event/         # 领域事件（按需）
```

## 代码规范

### 领域建模规范

以下场景的详细规范见 [领域建模规范](domain-modeling-standard.md)：

- **创建实体类**：实体模板、@SQLDelete 软删除、BaseEntity 继承规范
- **业务逻辑归属**：充血模型 vs Service 的选择标准
- **聚合拆分**：实体超过 5 个时按聚合划分 `domain/` 子包、聚合根设计原则
- **Repository**：每聚合根一个 Repository、查询规范、QueryService 分离
- **值对象**：@Embeddable / record 的提取时机
- **领域事件**：@DomainEvents / ApplicationEventPublisher 跨模块一致性
- **对象转换**：MapStruct Convert 接口模板
- **实体内异常**：实体内抛 BusinessException 的时机与写法

### 类命名

| 类型       | 命名规则                              | 示例             |
| ---------- | ------------------------------------- | ---------------- |
| 控制器     | `{Name}Controller`                    | `UserController` |
| 服务类     | `{Name}Service`                       | `UserService`    |
| Repository | `{Name}Repository`                    | `UserRepository` |
| 实体类     | `{Name}`                              | `User`           |
| 对象映射   | `{Name}Convert`                       | `UserConvert`    |
| DTO        | `{Name}CreateDTO` / `{Name}UpdateDTO` | `UserCreateDTO`  |
| VO         | `{Name}VO`                            | `UserVO`         |

### 注解使用

> 代码示例见 [编码代码片段](../../snippets/coding-snippets.md#注解使用)

### 工具类使用

优先使用框架提供的工具类：

- `cn.hutool.core.util.*` - Hutool 工具类
- `com.xuejiai.aaf.common.util.*` - AAF 公共工具类

## 配置文件规范

### 配置文件位置

```
aaf-api/src/main/resources/
├── application.yml           # 主配置文件
├── application-dev.yml       # 开发环境
├── application-test.yml      # 测试环境
└── application-prod.yml      # 生产环境
```

### 配置命名

使用 kebab-case（短横线命名）：

```yaml
aaf:
  auto-dev-enabled: false
  agent:
    default-model: gpt-4
    max-retry: 3
```

## Git 提交规范

### 提交消息格式

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type 类型

| 类型       | 说明                   |
| ---------- | ---------------------- |
| `feat`     | 新功能                 |
| `fix`      | 修复 Bug               |
| `docs`     | 文档更新               |
| `style`    | 代码格式（不影响功能） |
| `refactor` | 重构                   |
| `test`     | 测试相关               |
| `chore`    | 构建/工具链            |

### 示例

```
feat(agent): 添加多智能体协作功能

- 实现智能体注册机制
- 添加智能体通信协议
- 完善智能体生命周期管理

Closes #123
```

## 文档规范

### 文档命名

遵循 `file-name-standard.md` 规范：

- 全小写
- 连字符分隔
- 使用标准后缀（`-standard.md`、`-guide.md`、`-design.md`）

### 文档结构

```
docs/
├── development-standard.md    # 开发规范
├── architecture.md            # 架构设计
├── project-structure.md       # 项目结构
├── api/                       # API 文档
├── guides/                    # 操作指南
└── design/                    # 设计文档
```

## 测试规范

### 测试分层

| 层级     | 框架                        | 用途                    |
| -------- | --------------------------- | ----------------------- |
| 单元测试 | JUnit 5 + Mockito           | Service/Mapper 逻辑验证 |
| 集成测试 | JUnit 5 + `@SpringBootTest` | Controller/API 测试     |
| 验收测试 | Cucumber                    | 端到端业务场景验证      |

### 测试文件命名

```
{ClassName}Test.java
```

### 单元测试示例

> 代码示例见 [测试代码片段](../../snippets/testing-snippets.md#单元测试)

### 验收测试示例

> 代码示例见 [测试代码片段](../../snippets/testing-snippets.md#验收测试cucumber)

## 日志规范

### 日志级别

| 级别    | 使用场景               |
| ------- | ---------------------- |
| `ERROR` | 系统错误，需要立即处理 |
| `WARN`  | 警告信息，可能影响功能 |
| `INFO`  | 关键业务流程           |
| `DEBUG` | 调试信息               |
| `TRACE` | 详细跟踪信息           |

### 日志示例

> 代码示例见 [编码代码片段](../../snippets/coding-snippets.md#日志使用)

## 异常处理规范

### 异常分类

| 类型                | 用途     |
| ------------------- | -------- |
| `BusinessException` | 业务异常 |
| `SystemException`   | 系统异常 |
| `ParamException`    | 参数异常 |

> 代码示例见 [编码代码片段](../../snippets/coding-snippets.md#异常定义)

## 性能规范

### 数据库查询

- 避免 `SELECT *`，明确指定字段
- 合理使用索引
- 分页查询必须限制每页数量
- 避免 N+1 查询问题

### 缓存使用

- 热点数据使用缓存
- 设置合理的过期时间
- 注意缓存一致性

## 安全规范

### 输入验证

- 使用 `@Valid` 注解自动校验请求参数

> 代码示例见 [编码代码片段](../../snippets/coding-snippets.md#输入验证)

### 敏感信息

- 不在日志中输出密码、Token 等敏感信息
- 敏感配置使用环境变量或配置中心
- 数据库密码等信息不提交到代码仓库

## 检查清单

创建新模块前，确认：

- [ ] 包名遵循 `com.xuejiai.aaf.module.{name}` 格式
- [ ] 在 `aaf-api/module/` 下创建包目录
- [ ] 如有新依赖，先在 `aaf-dependencies` 注册版本
- [ ] 编写了对应的业务说明或规范文档

---

## 参考资源

- [领域建模规范](domain-modeling-standard.md) — 实体模板、充血模型、聚合根、值对象、Repository、领域事件、Service 层规范
- [领域建模代码片段](../../snippets/domain-snippets.md) — 上述规范的可复用代码模板
- [Maven 最佳实践](https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html)
- [Spring Boot 开发规范](https://spring.io/guides)
- [阿里巴巴 Java 开发手册](https://github.com/alibaba/p3c)
