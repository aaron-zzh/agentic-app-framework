---
level: Practice
layer: Principle
purpose: 后端验收测试框架选型决策
status: accepted
version: 1.0.0
date: 2026-05-05
author: AaronZZH
deciders: [AaronZZH, 协调者]
related-tasks: [AAF-023 #7]
---

# ADR-003: 后端验收测试 — 移除 Cucumber，统一 JUnit 5

## Context and Problem Statement

AAF 的规范文档（6 处）宣称"验收测试用 Cucumber"：

- `Readme.md`（技术栈段）
- `docs/explanation/design-principles.md`
- `docs/reference/dev/requirement-standard.md`
- `docs/reference/dev/development-standard.md`
- `docs/reference/dev/apps/service/coding-style-standard.md`（3 处）
- `docs/reference/dev/snippets/testing-snippets.md`（2 处）

但实际状况：

- `apps/service/pom.xml` 中 **0 个 Cucumber 依赖**
- 测试代码中 **0 个 `.feature` 文件**
- 规范与代码不一致（规范驱动项目里反向的"规范漂在前，代码空在后"）

用户问"后端测试是否建议采用 JUnit 5、Mockito、Cucumber 组合"，触发重新评估。

需决策：按规范补齐 Cucumber，还是修正规范移除 Cucumber。

## Decision Drivers

- AAF 自身"文档是唯一真理"原则不能自相矛盾
- AI 协作场景下 AC 变更同步的摩擦成本
- Cucumber 的 BDD 前提是否在 AAF 场景成立
- Gherkin 格式作为 AC 表达工具的价值（与 Cucumber 作为测试框架解耦）

## Considered Options

- 补齐 Cucumber 落地（pom 装依赖 + 规范保留 + 新增 `.feature` 目录）
- 修正规范移除 Cucumber，走 JUnit 5 + Mockito + AssertJ + Spring Boot Test
- 保留规范说"Cucumber 可选"，实际不装

## Decision Outcome

**Chosen option**: "修正规范移除 Cucumber，走 JUnit 5 + Mockito + AssertJ + Spring Boot Test + 本地真实数据库（见 ADR-002）"。**Gherkin 作为 AC 表达格式保留**在需求文档、测试 `@DisplayName`、注释中，不落地为 `.feature` 文件。

核心理由：Cucumber 的 BDD 哲学"`.feature` 文件本身是 living documentation（即真理源）"与 AAF 的"`docs/prd/**/*.md` 的 AC 区是唯一真理源"直接冲突。引入 Cucumber 等于在项目里开第二个真理源。

### Positive Consequences

- 真理源归一：AC 只在 `docs/prd/**/*.md` 存在，测试 `@DisplayName` 引用其编号
- AI 从需求文档生成测试时一处改一处用，避免 `.feature` / step definition / 需求文档三地同步
- 测试栈层数更浅：直接 JUnit 5 栈而非 Cucumber runner → JUnit → JVM 三层
- `@MockBean ChatClient` 在同一测试类内注入，不需在 step 间传 Mock 状态
- `test-report.md` 覆盖矩阵用 JUnit XML 直接填，不需额外解析 Cucumber JSON report

### Negative Consequences

- 失去 Cucumber"非程序员读 `.feature`"的潜在价值——但 AAF 业务方就是 AaronZZH 自己，程序员直接读测试类的 `@DisplayName` 即可
- 与部分 Java 企业项目的"Cucumber 是验收测试标配"习惯相悖——但 AAF 不在企业合规项目靶心

### Reversal Triggers（反向选择触发条件）

仅当出现以下之一时考虑回引 Cucumber：

1. 业务方非程序员角色加入，且确实需要直接读/写 `.feature` 文件
2. 出现强 DSL 领域（合规、金融规则引擎等）需要业务人员审读验收测试
3. 已有 100+ `.feature` 存量资产需要兼容

以上在 v0.1-v0.3 均不成立。

## Pros and Cons of the Options

### 补齐 Cucumber 落地

- Good: 保留规范描述，不用改文档
- Good: 若未来有业务方介入可直接上手
- Bad: **与 AAF"文档是唯一真理"原则冲突**（最硬伤）
- Bad: AC 变更三地同步（需求 MD / `.feature` / step definition）
- Bad: AI 协作摩擦显著增加
- Bad: `@MockBean` 注入到 step 麻烦

### 修正规范移除 Cucumber（选择）

- Good: 真理源归一，符合 AAF 核心原则
- Good: AI 协作一处改一处用
- Good: 测试栈层数浅，失败定位快
- Good: pom 无 Cucumber 依赖、0 存量文件——移除零成本
- Bad: 需改 6 处规范文档（短期一次性成本）

### 规范说"Cucumber 可选"实际不装

- Good: 文档不改，快速
- Bad: 规范与实际仍不一致（"可选"掩盖了"从不使用"的事实）
- Bad: 后续 AI 生成验收测试时可能因看到规范又引入 Cucumber，造成回归

## More Information

### Gherkin 的正确位置（保留）

Gherkin 作为 AC 表达格式依然有价值，但**不落地为 `.feature` 文件**。正确用法：

- **需求文档 AC 区**：`docs/prd/**/*.md` 用 Gherkin 格式写 AC
  ```
  Given: 用户已登录
  When: 点击导出按钮
  Then: 收到 200 且响应包含文档列表
  ```
- **测试方法 `@DisplayName`**：`@DisplayName("AC-001: Given 登录 When 导出 Then 返回 200")`
- **测试方法注释块**：详细 Given / When / Then 给读者
- **`test-report.md` 覆盖矩阵**：AC 编号 → 测试方法映射

### Cucumber 适用前提对照

| Cucumber 适用前提 | AAF 实际 |
|-----------------|---------|
| 非程序员业务方直接读/写 `.feature` | 一人公司，业务方就是 AaronZZH 自己 |
| BDD 文化成熟 | AAF 是规范驱动，不是 BDD |
| 已有 `.feature` 存量 | 0 个 |
| 强 DSL 领域（合规/金融） | 通用 AI 框架，无强 DSL |

四个前提全部不成立。

### AAF 最终后端测试栈

- **单测**：JUnit 5 + Mockito + AssertJ
- **集成 / 验收**：JUnit 5 + Spring Boot Test + `@DisplayName` 表达 AC（Gherkin 格式）+ 本地真实 DB（见 ADR-002）
- **LLM 调用 mock**：`@MockBean ChatClient`
- **pom 显式依赖**：`spring-boot-starter-test`（兜底，不再依赖其他 `starter-*-test` 的传递）

### 历史讨论

- 原始决策记录：[AAF-023 dev-log #4](../../task/v0.1.0/AAF-023/dev-log.md#4-cucumber-移除决策记录)
- 改进意见条目：`docs/prd/improvements.md`"Cucumber 移除决策"（已采纳）

### 后续动作（AAF-023 #7 落地清单）

1. 去除 6 处规范文档的 Cucumber 引用并替换为 JUnit 5 `@DisplayName` 示例
2. `testing-snippets.md` 的 Cucumber 片段重写为 `@DisplayName + Given/When/Then` 示例
3. `pom.xml` 显式加 `spring-boot-starter-test`
4. `acceptance-test-standard.md` 顶部加"起因：ADR-003"标注
5. 在 `requirement-standard.md` 中明确"AC 用 Gherkin 格式表达，但不落地 `.feature` 文件"

### 与其他 ADR 的关系

- **ADR-001（Vitest）**：同样基于"真理源归一 / AI 协作友好"原则，作用于前端
- **ADR-002（本地真实 DB）**：后端测试技术栈的另一半，二者组成完整后端测试方案
