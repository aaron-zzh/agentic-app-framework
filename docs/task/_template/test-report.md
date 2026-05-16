# 测试报告：{任务名称}

> tester 产出文档。验证对象是**需求 AC**，不是代码本身。code 层面的问题（编译/单测/lint）归 developer 的 `check` 负责，本报告不记录。

## 元信息

| 字段 | 值 |
|------|-----|
| 任务 | AAF-XXX / #N |
| 需求文档 | `docs/prd/{module}/{feature}.md` |
| tester | — |
| 测试日期 | YYYY-MM-DD |
| developer check 状态 | ✅ 全绿 / ❌ 未通过（未通过不应开启 tester） |
| 执行命令 | `pnpm acceptance:affected` 或 `pnpm nx run {project}:acceptance` |

## 一、测试文件清单

| 文件 | 类型 | 新增/修改 | 说明 |
|------|------|----------|------|
| `.../XxxAcceptanceTest.java` | 验收 | 新增 | ... |
| `.../XxxIT.java` | 集成 | 修改 | ... |
| `.../xxx.accept.test.tsx` | E2E | 新增 | ... |

> 命名必须符合 [验收测试规范 #命名约定](../../reference/dev/test/acceptance-test-standard.md#命名约定硬约束)。

## 二、AC 覆盖矩阵

**每条需求 AC 必须有对应测试**，未覆盖的 AC 即为 blocker。

| AC 编号 | AC 摘要（Gherkin） | 对应测试方法 | 结果 | 备注 |
|---------|-------------------|-------------|------|------|
| AC-001 | Given 游客 When 提交注册 Then 返回 201 | `UserAcceptanceTest#should_return_201_when_valid_registration` | ✅ 通过 | — |
| AC-002 | Given 邮箱已存在 When 注册 Then 返回 409 | `UserAcceptanceTest#should_return_409_when_email_exists` | ✅ 通过 | — |
| AC-003 | Given 密码弱 When 注册 Then 返回 400 | — | ❌ 未覆盖 | blocker：tester 未补充 |

**覆盖统计**：

- 总 AC 数：N
- 已覆盖：M（M/N = X%）
- 未覆盖：K（K/N = Y%）
- 覆盖不完全 → blocker

## 三、发现的问题

### blocker（阻塞发布）

| 编号 | 问题描述 | 涉及 AC | 退回对象 |
|------|---------|--------|---------|
| B-1 | ... | AC-003 | developer |

### major（严重问题，应修复）

| 编号 | 问题描述 | 涉及 AC | 退回对象 |
|------|---------|--------|---------|
| M-1 | ... | AC-001 | developer |

### minor（建议改进，可延后）

| 编号 | 问题描述 | 备注 |
|------|---------|------|
| m-1 | ... | 下个迭代处理 |

## 四、缺陷退回记录

tester 发现问题后**不自行修改业务代码**，按以下流程处理：

| 问题编号 | 退回对象 | 退回原因 | developer 修复后重跑时间 | 最终状态 |
|---------|---------|---------|----------------------|---------|
| B-1 | developer-service | 实现未覆盖密码强度校验 | YYYY-MM-DD HH:MM | ✅ 已通过 |

## 五、测试环境

- Java 版本：...
- 数据库：本地 PostgreSQL 17 + Neo4j 5（开发机）/ GitHub Actions service container（CI）
- Mock：LLM ChatClient、外部 HTTP
- 浏览器（E2E）：Chromium / Firefox / WebKit

## 六、结论

- [ ] 全部 AC 覆盖（M/N = 100%）
- [ ] blocker = 0
- [ ] major ≤ 2
- [ ] 需求覆盖矩阵完整
- [ ] 退回修复的问题全部闭环

**验收结论**：通过 / 不通过（不通过原因：...）
