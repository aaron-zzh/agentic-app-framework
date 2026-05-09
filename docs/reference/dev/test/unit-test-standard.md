---
level: Practice
layer: Model
purpose: 定义单元测试的编写规范、命名约定和覆盖率要求
status: published
version: 1.0.0
date: 2026-05-06
author: AaronZZH
changelog:
  - 2026-05-06 | 补充 Front Matter
---

# 单元测试规范

> 单元测试是 **developer 的自验证手段**，归入 `check` target。覆盖核心业务逻辑的行为正确性，不关心需求满足度（那是 tester 的 acceptance 职责）。
>
> **相关决策**：
> - 前端单测框架走 Vitest（不走 Jest）— 起因：[ADR-001](../../../design/adr/ADR-001-vitest-vs-jest.md)
> - 后端单测走 JUnit 5 + Mockito + AssertJ，AC 用 `@DisplayName` 表达而非 Cucumber — 起因：[ADR-003](../../../design/adr/ADR-003-remove-cucumber.md)

## 基本原则

- developer 必须为核心业务逻辑编写单元测试
- 测试是 **developer 的产出**，不是 tester 的职责
- 提交前必须通过 `pnpm check:affected`（详见 [AI 自验证循环](../../team/process-standard.md#331-ai-自验证循环developer-强制内循环)）

## 命名约定（硬约束）

| 层 | 命名 | 执行器 | 归属 |
|----|------|--------|------|
| Java | `XxxTest.java` | Maven Surefire | developer 单测 |
| TS | `xxx.test.ts(x)` / `xxx.spec.ts(x)` | Vitest | developer 单测 |

**禁止**：
- 在 `*Test.java` 文件里写验收测试 / 集成测试（那应命名 `*IT.java` / `*AcceptanceTest.java`）
- 在 `*.test.ts(x)` 里写 E2E 或验收测试（应命名 `*.accept.test.ts(x)`）

> 命名决定哪个 target 执行，不可混淆。[Surefire 配置](../../../../apps/service/pom.xml) 已设置 `includes: **/*Test.java, excludes: **/*IT.java, **/*AcceptanceTest.java`。

## 技术栈

- **Java**：JUnit 5 + Mockito + AssertJ
- **TS**：Vitest + Testing Library（jsdom 环境）
- **架构测试**：ArchUnit（归入 developer 单测，命名 `*Test.java`，在 Surefire 阶段执行）

## 目录位置

- Java：测试类与被测类同包，放在 `src/test/java/` 下
- TS：紧邻被测模块，或集中到 `src/**/__tests__/`

## 方法命名

```text
should_{预期行为}_when_{条件}
```

示例：
- `should_return_empty_when_no_records_found`
- `should_throw_exception_when_user_not_authenticated`

## @DisplayName 约定

每个测试方法必须加 `@DisplayName`，用中文 Gherkin 格式（Given/When/Then）表达验收条件：

```java
@Test
@DisplayName("Given 用户名不存在 When 创建用户 Then 返回新用户信息")
void should_create_user_when_username_not_exists() { ... }

@Test
@DisplayName("Given 用户不存在 When 按 ID 查询 Then 抛出 NOT_FOUND 异常")
void should_throw_exception_when_id_not_exists() { ... }
```

**规则**：
- 方法名用英文（`should_xxx_when_xxx`），保证代码可读性和 IDE 兼容
- `@DisplayName` 用中文 Gherkin，保证测试报告对业务人员可读
- Gherkin 作为 AC 表达格式保留在 `@DisplayName` 中，不落地为 `.feature` 文件

## 覆盖策略

- 核心业务逻辑 100% 覆盖主路径
- 分支条件（if/else、try/catch、switch）全覆盖
- 边界值（null、空集合、最大/最小值）必须有对应测试
- 外部依赖全部 Mock，不访问真实数据库 / 网络

## 不写测试的场景

- 无业务逻辑的 getter/setter、DTO、纯配置类
- 框架自动生成的代码（MapStruct 生成的 Mapper 实现）
- 一次性脚本（如数据迁移工具），在 `dev-log.md` 说明原因

## 测试基类

AAF 提供测试基类，减少样板代码：

| 基类 | 用途 | 位置 |
|------|------|------|
| `BaseMockitoUnitTest` | 纯 Mockito 单测（Service 层） | `com.xuejiai.aaf.test` |

继承基类后无需手动添加 `@ExtendWith(MockitoExtension.class)`：

```java
class UserServiceTest extends BaseMockitoUnitTest {
    @Mock private UserRepository userRepository;
    @InjectMocks private UserService userService;
    // ...
}
```

## 测试方法内部结构

每个测试方法内部用注释分段，保持统一结构：

```java
@Test
@DisplayName("Given 用户名不存在 When 创建用户 Then 返回新用户信息")
void should_create_user_when_username_not_exists() {
    // 准备参数
    var request = new UserCreateReqVO("newuser", "123456", "新用户");

    // mock 方法
    when(userRepository.existsByUsernameAndDeletedFalse("newuser")).thenReturn(false);
    when(passwordEncoder.encode("123456")).thenReturn("encoded");
    when(userRepository.save(any())).thenAnswer(inv -> {
        User e = inv.getArgument(0);
        e.setId(1L);
        return e;
    });

    // 调用
    UserRespVO response = userService.create(request);

    // 断言
    assertThat(response.username()).isEqualTo("newuser");
    assertThat(response.nickname()).isEqualTo("新用户");
    verify(passwordEncoder).encode("123456");
}
```

**分段规则**：
- `// 准备参数` — 构造入参和测试数据
- `// mock 方法` — 设置 Mock 行为
- `// 调用` — 执行被测方法
- `// 断言` — 验证结果
- 简单场景可合并为 `// 调用 + 断言`

## 完整示例

### Service 层单元测试

```java
class UserServiceTest extends BaseMockitoUnitTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private UserService userService;

    @Test
    @DisplayName("Given 用户不存在 When 按 ID 查询 Then 抛出 NOT_FOUND 异常")
    void should_throw_exception_when_id_not_exists() {
        // mock 方法
        when(userRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        // 调用 + 断言
        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }
}
```

### Controller 层单元测试

```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserService userService;

    @Test
    @DisplayName("Given 用户存在 When GET /users/{id} Then 返回用户详情")
    @WithMockUser
    void should_return_user_when_get_by_id() throws Exception {
        // mock 方法
        when(userService.getById(1L)).thenReturn(sampleUser);

        // 调用 + 断言
        mockMvc.perform(get("/api/system/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }
}
```

## 与验收测试的区别

| 维度 | 单元测试（developer） | 验收测试（tester） |
|------|---------------------|--------------------|
| 对标物 | 代码的行为契约 | 需求文件的 Gherkin AC |
| 粒度 | 方法 / 类 | 用户故事 / 接口 / 端到端流程 |
| Mock | 全部依赖 Mock | 少 Mock，尽量真实（本地真实数据库 / CI service container） |
| 失败后 | developer 自己修复 | 退回 developer 走完整 check |

> 验收测试规范见 [acceptance-test-standard.md](acceptance-test-standard.md)。
