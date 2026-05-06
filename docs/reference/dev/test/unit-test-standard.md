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

## 覆盖策略

- 核心业务逻辑 100% 覆盖主路径
- 分支条件（if/else、try/catch、switch）全覆盖
- 边界值（null、空集合、最大/最小值）必须有对应测试
- 外部依赖全部 Mock，不访问真实数据库 / 网络

## 不写测试的场景

- 无业务逻辑的 getter/setter、DTO、纯配置类
- 框架自动生成的代码（MapStruct 生成的 Mapper 实现）
- 一次性脚本（如数据迁移工具），在 `dev-log.md` 说明原因

## 与验收测试的区别

| 维度 | 单元测试（developer） | 验收测试（tester） |
|------|---------------------|--------------------|
| 对标物 | 代码的行为契约 | 需求文件的 Gherkin AC |
| 粒度 | 方法 / 类 | 用户故事 / 接口 / 端到端流程 |
| Mock | 全部依赖 Mock | 少 Mock，尽量真实（本地真实数据库 / CI service container） |
| 失败后 | developer 自己修复 | 退回 developer 走完整 check |

> 验收测试规范见 [acceptance-test-standard.md](acceptance-test-standard.md)。
