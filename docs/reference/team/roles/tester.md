# 测试工程师（tester）

## 岗位定位

**对需求负责**。对照验收标准（AC）验证代码是否满足需求，产出 AC 覆盖矩阵。

> **与 developer 的边界**：developer 负责 `check`（对代码负责，含 lint/typecheck/单测/build），tester 负责 `acceptance`（对需求负责，含验收测试/集成测试）。详见 [AI 自验证循环](../process-standard.md#331-ai-自验证循环developer-强制内循环)。

**AI 协作模式**：AI 对照验收标准生成测试；人类设计策略和边界场景。

## 职责

### 执行阶段

1. **验收测试**：对照 AC 逐条编写，验证代码是否满足验收标准
2. **集成测试**：模块间交互测试，边界场景和异常路径测试
3. **需求覆盖验证**：确保每条 AC 有对应测试，输出覆盖矩阵（见 [test-report.md 模板](../../../task/_template/test-report.md)）

### 不做

| 事项 | 归属 |
|------|------|
| 编写业务实现代码 | developer |
| 修复编译错误 / 单元测试失败 / lint 警告 | developer（check 的范畴） |
| 改业务逻辑来让验收测试通过 | developer（退回 developer 修复） |

## 前置门禁

tester 启动前，协调者必须确认 developer 的 `pnpm check:affected` 已全绿。以下情况立即**退回 developer**，不在 acceptance 阶段修：

- 编译不过 / 启动失败
- 单元测试有红
- Spotless / lint / typecheck 报错
- 缺依赖、配置文件错

## 输入命令

```bash
pnpm acceptance:affected     # 日常：只跑 affected 项目
pnpm acceptance              # 版本交付前：跑全部
```

## 输出要求

### 测试代码

- **Java**：`*IT.java`（集成测试）或 `*AcceptanceTest.java`（验收测试），放在对应模块的 `src/test/java/` 下 → Failsafe 执行
- **TS**：`*.accept.test.ts(x)` → Vitest acceptance 配置执行
- **不得**使用 `*Test.java` / `*.test.ts(x)` 命名（这是 developer 单测的命名）

### 测试报告

- 输出路径：`docs/task/{版本}/{AAF-XXX}/test-report.md`
- 格式：严格按 [模板](../../../task/_template/test-report.md)
- **必须包含**：
  - 测试文件清单（新增 / 修改）
  - **AC 覆盖矩阵**（每条 AC 对应哪个测试方法，含覆盖状态）
  - 发现的问题（blocker / major / minor 分级）
  - 缺陷退回记录（哪些打回 developer）

### 测试规范

- 技术栈：JUnit 5 + Spring Boot Test + Mockito + 本地真实 PostgreSQL / Neo4j（CI 用 GitHub Actions service container）
- 前端：Vitest + Testing Library（未来可引入 Playwright）
- Mock LLM 调用，避免 CI 消耗外部 API

## 源码访问

使用 knowledge 工具搜索源码，不要依赖 `file://` 加载全部源码。读懂被测行为即可，不为了写测试去改产品代码。
