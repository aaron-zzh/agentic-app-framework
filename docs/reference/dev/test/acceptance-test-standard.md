---
level: Practice
layer: Model
purpose: 定义验收测试的编写规范、命名约定和执行要求
status: published
version: 1.0.0
date: 2026-05-06
author: AaronZZH
changelog:
  - 2026-05-06 | 补充 Front Matter
---

# 验收测试规范

> 验收测试是 **tester 的产出**，归入 `acceptance` target。对照需求文件的 AC 逐条验证代码是否满足需求，输出 AC 覆盖矩阵。
>
> **相关决策**：
> - 测试环境走本地真实 DB + CI service container（不用 Testcontainers）— 起因：[ADR-002](../../../design/adr/ADR-002-local-env-vs-testcontainers.md)
> - AC 用 Gherkin 格式写在需求文档，测试用 JUnit 5 `@DisplayName` 引用 AC 编号（不落地 `.feature` 文件）— 起因：[ADR-003](../../../design/adr/ADR-003-remove-cucumber.md)
> - 前端 E2E 走 Playwright（非 Vitest acceptance），单测走 Vitest — 起因：[ADR-001](../../../design/adr/ADR-001-vitest-vs-jest.md)

## 基本原则

- 每条验收标准（AC）必须有对应的测试用例
- 测试对照需求文档中的 AC，**不凭开发者理解**编写
- 覆盖正常路径、边界场景、异常路径
- 前置条件：developer 的 `pnpm check:affected` 已全绿（见 [tester 角色文档](../../team/roles/tester.md#前置门禁)）

## 命名约定（硬约束）

| 层 | 命名 | 执行器 | 归属 |
|----|------|--------|------|
| Java 验收 | `XxxAcceptanceTest.java` | Maven Failsafe | tester 验收 |
| Java 集成 | `XxxIT.java` | Maven Failsafe | tester 集成 |
| TS 验收 / E2E | `xxx.accept.test.ts(x)` | Vitest acceptance 配置 | tester |

**禁止**：
- 在 `*Test.java` 文件里写验收测试（那是 developer 单测的命名 → Surefire 执行 → 混淆职责）
- 在 `*.test.ts(x)` 里写 E2E 或验收场景

> [Failsafe 配置](../../../../apps/service/pom.xml) 已设置 `includes: **/*IT.java, **/*AcceptanceTest.java`；vitest acceptance 配置只匹配 `*.accept.test.ts(x)`。

## 技术栈

- **后端**：JUnit 5 + Spring Boot Test + **本地真实 PostgreSQL / Neo4j** + RestAssured / WebTestClient
- **前端**：Vitest（组件/集成）或 Playwright（E2E，未来引入）
- **LLM 依赖**：Mock Spring AI `ChatClient`，**不消耗**真实 API

## 测试粒度

- 一个 AC 对应**一个或多个**测试方法
- 使用 Gherkin 风格注释标注 Given / When / Then：

```java
@Test
@DisplayName("AC-001: 注册邮箱已存在时应返回 409")
void should_return_409_when_email_already_registered() {
    // Given: 数据库存在邮箱 alice@example.com
    // When: POST /api/users 使用相同邮箱
    // Then: 响应 409，错误码 EMAIL_EXISTS
}
```

## 覆盖要求

- 每条需求 AC **至少一个**验收测试
- 关键业务路径 **100% 覆盖**
- 异常路径和边界条件按风险优先级覆盖
- 集成测试覆盖模块间交互的真实行为（连本地真实 PostgreSQL / Neo4j，CI 用 GitHub Actions service container）

## 启动命令

```bash
pnpm acceptance:affected     # 日常：只跑 affected 项目
pnpm acceptance              # 版本交付前：跑全部
```

## 产出

### 测试代码

放在对应模块的 `src/test/java/` 或前端 `src/**/*.accept.test.ts(x)`。不新建独立测试工程。

### 测试报告

路径：`docs/task/{版本}/{AAF-XXX}/test-report.md`  
模板：[docs/task/_template/test-report.md](../../../task/_template/test-report.md)

必填区块：

1. **测试文件清单**（新增 / 修改）
2. **AC 覆盖矩阵**：每条 AC 对应哪个测试方法、是否通过
3. **发现的问题**：blocker / major / minor 分级
4. **缺陷退回记录**：哪些问题打回 developer，哪些 tester 自己解决

## 与单元测试的区别

见 [单元测试规范 #与验收测试的区别](unit-test-standard.md#与验收测试的区别)。核心差异：

- 单元测试对**代码行为契约**负责（developer），验收测试对**需求满足度**负责（tester）
- 单元测试 Mock 全部依赖，验收测试尽量用真实依赖（本地真实数据库 / CI service container）
- 单元测试失败 developer 自己修，验收测试失败退回 developer 走完整 check

## 失败处理流程

```text
acceptance 失败
  ↓
分析是 tester 的测试代码有误 还是 developer 的实现有误
  ├─ 测试代码问题 → tester 自己修测试
  └─ 实现问题 → 退回 developer，走完整 check → architect 审查 → tester 重跑
```

tester **不得**修改业务代码让测试通过，那是 developer 的职责。

