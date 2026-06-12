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
- 使用 Lombok 简化 JPA 实体样板代码（`@Getter`、`@Setter`、`@NoArgsConstructor`、`@Builder`、`@Slf4j`）
- **VO / DTO / Event 使用 Record**，不再需要 Lombok `@Data`（详见"Java 25 现代特性规范"）
- 代码即文档，保持可读性，必要处添加注释
- **注释语言统一中文**，禁止中英混用（与 `docs/` 真理源一致）
- 编写单元测试（JUnit 5 + Mockito），保证功能正确性
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

### Service 层规范

**业务 Service 直接写类，不加接口**：

```java
// ✅ 正确 —— 直接写 Service 类
@Service
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentRepository repo;
    public Document create(CreateDocumentRequest req) { ... }
}

// ❌ 不必要 —— 同一 Service 只有一个实现时禁止加接口
public interface DocumentService { ... }
public class DocumentServiceImpl implements DocumentService { ... }
```

**需要定义接口的场景**：

| 场景 | 示例 |
|------|------|
| 框架扩展点（用户可自定义实现） | `FileStorage`、`TaskHandler`、`AuthenticationProvider` |
| 跨模块依赖（framework 定义，api 实现） | `AgentExecutor`、`EmbeddingService` |
| 确实有多个实现 | `FileStorage` → Local / MinIO / OSS |



| 类型       | 命名规则                              | 示例             |
| ---------- | ------------------------------------- | ---------------- |
| 控制器     | `{Name}Controller`                    | `UserController` |
| 服务类     | `{Name}Service`                       | `UserService`    |
| Repository | `{Name}Repository`                    | `UserRepository` |
| 实体类     | `{Name}`                              | `User`           |
| 对象映射   | `{Name}Convert`                       | `UserConvert`    |
| 入参 DTO   | `{Name}{动作}DTO`                     | `UserCreateDTO` / `UserPageDTO` |
| 出参 VO    | `{Name}VO` / `{Name}SimpleVO`         | `UserVO` / `UserSimpleVO` |

> 入参用 `DTO` 后缀，出参用 `VO` 后缀，统一放 `vo/` 目录。

### 注解使用

> 代码示例见 [编码代码片段](../../snippets/coding-snippets.md#注解使用)

### 工具类使用

优先级（从高到低）：

1. `java.*` / `java.time.*` / `java.nio.file.*` — JDK 标准库优先
2. `org.springframework.*` — Spring 工具类（StringUtils、CollectionUtils 等）
3. `com.xuejiai.aaf.common.util.*` — AAF 公共工具类
4. 按需引入单一职责小库（如 commons-lang3、guava 局部使用）

**禁止引入 Hutool**（hutool-all / hutool-core）：体积大、与 Spring/JDK 重复、传递依赖风险高。

## 配置文件规范

### 配置文件位置

```
aaf-api/src/main/resources/
├── application.yaml           # 主配置文件
├── application-dev.yaml       # 开发环境
├── application-test.yaml      # 测试环境
└── application-prod.yaml      # 生产环境
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
| 验收测试 | JUnit 5 + Failsafe          | 端到端业务场景验证      |

### 测试文件命名

```
{ClassName}Test.java          # 单元测试（Surefire）
{ClassName}IT.java            # 集成测试（Failsafe）
{ClassName}AcceptanceTest.java # 验收测试（Failsafe）
```

### 单元测试示例

> 代码示例见 [测试代码片段](../../snippets/testing-snippets.md#单元测试)

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

## Java 25 现代特性规范

> 起因：[tech-stack.md §1](../../../../design/apps/service/tech-stack.md#一java-25-核心特性)，Record / Sealed / Pattern Matching 是 AAF 首选。

### Record 使用规范

**原则：不可变数据载体一律用 Record，不用 Class + Lombok。**

| 场景 | 用 Record | 用 Class |
|------|-----------|----------|
| 请求/响应 VO | ✅ 首选 | 需要 Builder 模式或继承时 |
| 领域事件 | ✅ 首选 | — |
| 配置属性 | ✅ `@ConfigurationProperties` 支持 Record | 需要 `@Value` 默认值时 |
| 内部值对象 | ✅ 首选 | — |
| JPA 实体 | ❌ 不可用 | ✅ 实体必须用 Class（JPA 需要无参构造 + 可变状态） |
| 需要继承的类 | ❌ Record 是 final | ✅ 用 Class 或 Sealed Class |

**VO 命名（Record 版）：**

```java
// ✅ 入参 DTO —— Record，字段即校验
public record UserCreateDTO(
        @NotBlank String username,
        @Email String email,
        @Size(min = 6) String password
) {}

// ✅ 出参 VO —— Record，天然不可变
public record UserVO(
        Long id,
        String username,
        String email,
        LocalDateTime createTime
) {}

// ✅ 分页请求 DTO
public record UserPageDTO(
        String keyword,
        Integer status,
        int pageNo,
        int pageSize
) {}
```

**Record 与 Lombok 的关系：**

| 场景 | Lombok | Record |
|------|--------|--------|
| VO / DTO / Event | ❌ 不再需要 `@Data` | ✅ Record 自带 equals/hashCode/toString |
| JPA Entity | ✅ `@Getter` `@Setter` `@NoArgsConstructor` | ❌ 不可用 |
| Builder 模式 | ✅ `@Builder` | ❌ Record 无 Builder（用静态工厂方法替代） |

### Sealed Classes 使用规范

**原则：有限状态/类型集合用 Sealed，替代 abstract class + 散落子类。**

适用场景：
- 工作流节点类型
- Agent 状态机
- 错误码分类
- 命令/事件多态

```java
// ✅ 工作流节点 —— 有限类型集合
public sealed interface WorkflowNode
        permits StartNode, EndNode, TaskNode, GatewayNode, SubProcessNode {
    String id();
    String name();
}

public record StartNode(String id, String name) implements WorkflowNode {}
public record EndNode(String id, String name) implements WorkflowNode {}
public record TaskNode(String id, String name, String assignee) implements WorkflowNode {}
public record GatewayNode(String id, String name, GatewayType type) implements WorkflowNode {}
public record SubProcessNode(String id, String name, String processId) implements WorkflowNode {}

// ✅ Agent 执行结果 —— 有限状态
public sealed interface AgentResult
        permits AgentResult.Success, AgentResult.Failure, AgentResult.NeedConfirm {
    record Success(String output) implements AgentResult {}
    record Failure(String reason, Exception cause) implements AgentResult {}
    record NeedConfirm(String question, Runnable onConfirm) implements AgentResult {}
}
```

### Pattern Matching 使用规范

**原则：用 switch 表达式 + pattern matching 替代 if-else 链和 visitor 模式。**

```java
// ✅ 类型安全分支 —— 编译器保证穷举
public String describe(WorkflowNode node) {
    return switch (node) {
        case StartNode s -> "流程开始: " + s.name();
        case EndNode e -> "流程结束: " + e.name();
        case TaskNode t -> "任务: " + t.name() + " 指派: " + t.assignee();
        case GatewayNode g -> "网关: " + g.name() + " 类型: " + g.type();
        case SubProcessNode sp -> "子流程: " + sp.processId();
    };
}

// ✅ 守卫条件（guarded pattern）
public void handle(AgentResult result) {
    switch (result) {
        case AgentResult.Success s when s.output().length() > 1000 ->
            log.info("长输出，截断展示");
        case AgentResult.Success s ->
            log.info("成功: {}", s.output());
        case AgentResult.Failure f ->
            log.error("失败: {}", f.reason(), f.cause());
        case AgentResult.NeedConfirm c ->
            notifyUser(c.question());
    }
}

// ❌ 禁止 —— 用 instanceof 链做类型判断
if (node instanceof StartNode) { ... }
else if (node instanceof EndNode) { ... }
else if (node instanceof TaskNode) { ... }
```

**何时不用 Pattern Matching：**
- 简单 null 检查 → 用 `Objects.requireNonNull` 或 `Optional`
- 单一类型判断 → 普通 `instanceof` 即可
- 非 sealed 类型 → switch 无法保证穷举，用 if-else 或策略模式

### 现代语法替换清单

以下场景**必须**使用新语法替换传统写法，Code Review 中发现旧写法视为 minor：

| 场景 | ❌ 旧写法 | ✅ 新写法 | 说明 |
|------|----------|----------|------|
| 多行字符串 | `"line1\n" + "line2\n"` | `"""` text block `"""` | SQL、JSON、模板 |
| 类型判断后转型 | `if (obj instanceof Foo) { Foo f = (Foo) obj; }` | `if (obj instanceof Foo f)` | Pattern variable |
| 多分支类型判断 | if-else instanceof 链 | `switch` + pattern matching | sealed 类型必须用 switch |
| null + 类型混合判断 | if-null-then-else-instanceof | `switch` + `case null` | Java 21+ |
| 不可变数据载体 | Class + Lombok `@Data` | `record` | VO / DTO / Event |
| 局部变量类型明显 | `Map<String, List<UserRespVO>> map = new HashMap<>()` | `var map = new HashMap<String, List<UserRespVO>>()` | 右侧类型已明确时用 `var` |
| 资源管理 | try-finally close | try-with-resources | 所有 `AutoCloseable` |
| 集合创建 | `Arrays.asList(...)` / `new ArrayList<>(List.of(...))` | `List.of(...)` / `Set.of(...)` / `Map.of(...)` | 不可变集合优先 |
| 空集合返回 | `return new ArrayList<>()` | `return List.of()` | 不可变空集合 |
| Optional 链 | if-null 嵌套 | `Optional.map().orElse()` | 但不过度嵌套，超过 3 层改回 if |
| 字符串拼接 | `"Hello " + name + "!"` | `"Hello %s!".formatted(name)` 或 STR template | 超过 2 个变量时 |
| switch 表达式 | switch + break + 赋值 | `var x = switch(...) { case A -> ...; }` | 有返回值的 switch |
| 并行任务 | `ExecutorService` + `Future` | `StructuredTaskScope` | Java 21+ 结构化并发 |

### var 使用规范

`var` 仅在**右侧类型已明确**时使用，禁止降低可读性：

```java
// ✅ 右侧类型明确
var user = new User();
var list = userRepository.findAll();
var map = new HashMap<String, Integer>();

// ✅ 工厂方法 / 链式调用，返回类型显而易见
var encoder = BCryptPasswordEncoder();
var result = Result.success(data);

// ❌ 禁止 —— 右侧类型不明确
var data = service.process(input);  // 返回什么类型？
var x = calculate();                // 完全看不出类型
```

### Optional 使用规范

推荐用于返回值表达"可能为空"的语义，禁止滥用：

```java
// ✅ Repository 返回值
Optional<User> findByUsername(String username);

// ✅ 链式处理（≤3 层）
String city = user.getAddress()
        .map(Address::getCity)
        .orElse("未知");

// ✅ orElseThrow 替代手动 if-null-throw
User user = userRepository.findByIdAndDeletedFalse(id)
        .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "用户不存在"));

// ❌ 禁止作为方法参数
void update(Optional<String> nickname) { ... }

// ❌ 禁止作为实体字段
@Column private Optional<String> nickname;

// ❌ 禁止超过 3 层嵌套（改回 if-else）
optional.map(...).flatMap(...).map(...).filter(...).orElse(...)  // 太深，不可读
```

**规则**：
- 返回值用 `Optional` 表达可空语义 ✅
- 参数用 `@Nullable` 注解或方法重载 ✅
- 实体字段直接用 null ✅
- 超过 3 层链式调用改回 if-else ✅

### 函数式编程规范（Stream / Lambda）

推荐用于集合转换，禁止过度嵌套和副作用操作：

```java
// ✅ 集合转换（map / filter / collect）
var names = users.stream()
        .filter(u -> u.getStatus() == 1)
        .map(User::getUsername)
        .toList();

// ✅ 方法引用优先于 Lambda
.map(User::getId)       // ✅
.map(u -> u.getId())    // ❌ 有方法引用时不用 Lambda

// ✅ 简单 Lambda（1-2 行）
list.forEach(item -> log.info("处理: {}", item.getName()));

// ❌ 禁止超过 3 行的 Lambda —— 提取为私有方法
list.forEach(item -> {
    validate(item);
    transform(item);
    save(item);        // 太长，提取为 processItem(item)
});

// ❌ 禁止 forEach 内修改外部状态
var result = new ArrayList<>();
list.forEach(item -> result.add(transform(item)));  // 用 .map().toList() 替代

// ❌ 禁止 parallelStream —— 用虚拟线程 + StructuredTaskScope
list.parallelStream().map(...).toList();  // 不可控，线程池共享问题
```

**规则**：
- Stream 链式不超过 5 步，超过拆分为中间变量或方法
- Lambda 不超过 3 行，超过提取为私有方法
- 优先方法引用 `Class::method`
- 禁止 `forEach` 内修改外部可变状态（用 `map` + `collect`）
- 禁止 `parallelStream`（AAF 用虚拟线程处理并行）

### String Templates（预览特性）

Java 21+ 的字符串模板（如果启用 preview）：

```java
// ✅ 多变量拼接时优先用 formatted 或 String Template
var msg = "用户 %s（ID=%d）登录失败".formatted(username, userId);

// ❌ 禁止超过 2 个变量的 + 拼接
var msg = "用户 " + username + "（ID=" + userId + "）登录失败";
```

## 并发编程规范（Virtual Threads）

> 起因：[ADR-004](../../../../design/adr/ADR-004-virtual-threads-over-webflux.md)

AAF 全量启用虚拟线程（`spring.threads.virtual.enabled=true`），业务代码默认运行在虚拟线程上。

### 核心规则

| 规则 | 说明 |
|------|------|
| 禁止 `synchronized` | 会 pin carrier thread，统一用 `ReentrantLock` |
| 禁止自建线程池处理 I/O | 虚拟线程已覆盖，`new FixedThreadPool` 处理 I/O 任务是反模式 |
| 禁止 WebFlux 全栈响应式 | 业务代码不使用 Mono/Flux，仅 SSE 流式输出例外 |
| 连接池大小需匹配 | HikariCP `maximumPoolSize` 需匹配 DB `max_connections`，不能用默认 10 |

### 不需要特殊处理的场景

```java
// 串行调用 —— 直接写，虚拟线程自动处理阻塞
@PostMapping("/api/documents")
public Document create(@RequestBody CreateDocumentRequest req) {
    var doc = documentService.create(req);       // DB 阻塞 → 虚拟线程挂起，不占 OS 线程
    knowledgeService.index(doc);                  // 外部调用 → 同上
    notificationService.notify(doc.ownerId());    // 外部调用 → 同上
    return doc;
}
```

### 需要主动设计的场景

**1. 并行调用降低延迟**（用 StructuredTaskScope）：

```java
// 3 个接口各 1s，并行后总耗时 1s
try (var scope = StructuredTaskScope.open()) {
    var a = scope.fork(() -> serviceA.call());
    var b = scope.fork(() -> serviceB.call());
    var c = scope.fork(() -> serviceC.call());
    scope.join();
}
```

**2. 下游资源保护**（连接池/限流）：

```java
// 外部 API 限流 100 QPS —— 用 Semaphore 或 Resilience4j RateLimiter
private final Semaphore limiter = new Semaphore(100);
```

**3. CPU 密集任务**（隔离到平台线程池）：

```java
@Async("cpuIntensiveExecutor")  // 固定大小 = CPU 核心数
public void parseHugeDocument(Document doc) { ... }
```

### 锁的写法

```java
// ❌ 禁止
private synchronized void doSomething() { ... }

// ✅ 正确
private final ReentrantLock lock = new ReentrantLock();

private void doSomething() {
    lock.lock();
    try {
        // ...
    } finally {
        lock.unlock();
    }
}
```

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

## 禁止清单

| 禁止项 | 原因 | 正确做法 |
|--------|------|---------|
| `@Value` 散落在 Service 中 | 配置分散难维护 | 集中到 `@ConfigurationProperties` 类 |
| 事务内调用外部 API（LLM/S3/HTTP） | 长时间占用 DB 连接，事务超时风险 | 外部调用放事务外，或用事件异步处理 |
| 同类内部调用 `@Transactional` 方法 | AOP 代理不生效，事务不会开启 | 拆到另一个 Bean，或用 `self` 注入 |
| `throw new RuntimeException(...)` | 绕过全局异常处理 | 统一用 `BusinessException(ErrorCode.XXX)` |
| 循环调用 DB（N+1） | 性能灾难 | 批量查询 `findAllById()` 或 `@EntityGraph` |

## 代码格式化规范（Spotless + Google Java Format）

> pom.xml 已配置 `spotless-maven-plugin`，开发者无需手动格式化。

### 格式化方案

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 格式化引擎 | Google Java Format 1.22.0 | 业界标准，零争议 |
| 缩进风格 | AOSP（4 空格） | 比 Google 默认 2 空格更易读 |
| import 顺序 | `java` → `javax` → `org` → `com` → `com.xuejiai` → 静态 | 分组清晰 |
| 未使用 import | 自动移除 | — |
| 全限定类名 | 禁止在字段/方法/参数中使用全限定类名（如 `com.xxx.Foo field`），必须在文件头 import 后使用短名 | 可读性 |
| 尾部空白 | 自动清除 | — |
| 文件末尾 | 强制换行 | — |

### 开发者工作流

```bash
# 格式化所有 Java 文件
pnpm nx run service:fix

# 检查格式（CI 强制，不通过即失败）
pnpm nx run service:lint
```

### IDE 配置

**IntelliJ IDEA**：安装 `google-java-format` 插件 → Settings → google-java-format → 勾选 Enable → 选择 AOSP style。保存时自动格式化。

**VS Code**：不推荐直接编辑 Java，使用 `pnpm nx run service:fix` 命令行格式化。

### 规则

- **不手动调格式**：所有格式由 Spotless 统一处理，禁止手动对齐/换行
- **提交前自动检查**：`pnpm check:affected` 包含格式检查，格式不通过视为未完工
- **不加 `// @formatter:off`**：除非极特殊场景（如 ASCII art 注释），禁止关闭格式化
- **新文件自动覆盖**：`src/main/java/**/*.java` 和 `src/test/java/**/*.java` 全部纳入

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
