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

## 特殊测试场景

### 业务异常断言

验证方法在特定条件下抛出正确的业务异常和错误码：

```java
@Test
@DisplayName("Given 存在子部门 When 删除父部门 Then 抛出 DEPT_EXITS_CHILDREN 异常")
void should_throw_when_delete_dept_with_children() {
    // 准备参数
    when(deptRepository.existsByParentId(1L)).thenReturn(true);

    // 调用 + 断言
    assertThatThrownBy(() -> deptService.delete(1L))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(DeptErrorCode.EXITS_CHILDREN.code());
}
```

### 参数化测试（多组输入验证同一逻辑）

```java
@ParameterizedTest
@DisplayName("Given 各种非法状态值 When 更新状态 Then 抛出参数异常")
@ValueSource(shorts = {-1, 2, 99})
void should_throw_when_invalid_status(short invalidStatus) {
    assertThatThrownBy(() -> userService.update(1L, new UserUpdateReqVO(null, invalidStatus)))
            .isInstanceOf(BusinessException.class);
}
```

### AOP / 拦截器测试

测试注解驱动的横切逻辑（数据权限、日志、限流等）：

```java
class DataPermissionInterceptorTest extends BaseMockitoUnitTest {

    @InjectMocks private DataPermissionInterceptor interceptor;
    @Mock private MethodInvocation invocation;

    @Test
    @DisplayName("Given 方法无 @DataPermission When 拦截 Then 默认启用数据权限")
    void should_enable_permission_when_no_annotation() throws Throwable {
        // mock 方法
        when(invocation.getMethod()).thenReturn(getMethod("noAnnotation"));
        when(invocation.proceed()).thenReturn("result");

        // 调用
        interceptor.invoke(invocation);

        // 断言
        assertTrue(interceptor.getCache().values().iterator().next().enable());
    }
}
```

### 集合 diff 测试（批量操作场景）

验证批量新增/更新/删除的正确性：

```java
@Test
@DisplayName("Given 新旧列表 When diff Then 正确区分新增、更新、删除")
void should_diff_list_correctly() {
    // 准备参数
    var oldList = List.of(new Item(1L, "A"), new Item(2L, "B"));
    var newList = List.of(new Item(1L, "A2"), new Item(null, "C"));

    // 调用
    var result = CollectionUtils.diffList(oldList, newList, (o, n) -> o.id().equals(n.id()));

    // 断言
    assertThat(result.creates()).hasSize(1);   // C 是新增
    assertThat(result.updates()).hasSize(1);   // A→A2 是更新
    assertThat(result.deletes()).hasSize(1);   // B 是删除
}
```

### 时间相关测试

避免 `LocalDateTime.now()` 导致测试不稳定，使用固定时间或 Clock：

```java
@Test
@DisplayName("Given Token 已过期 When 校验 Then 返回 false")
void should_return_false_when_token_expired() {
    // 准备参数（固定时间）
    var expiredToken = createToken(LocalDateTime.of(2020, 1, 1, 0, 0));

    // 调用 + 断言
    assertThat(tokenService.isValid(expiredToken)).isFalse();
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
