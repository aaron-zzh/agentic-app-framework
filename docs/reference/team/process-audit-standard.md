---
level: Practice
layer: Model
purpose: 定义 qa 角色执行过程审计的检查项、流程和产出要求
status: published
version: 1.0.0
date: 2026-05-06
author: AaronZZH
changelog:
  - 2026-05-06 | 补充 Front Matter
  - 2026-05-05 | 明确 qa 只查过程和文档，代码质量由 architect 判定
---

# 过程审计规范

> 定义 qa 角色执行过程审计的检查项、流程和产出要求。
>
> **职责边界**：只查"过程对了吗"和"文档齐了吗"，不查代码内容——代码/设计规范合规由 architect 在 [代码审查](../dev/code-review-standard.md) 阶段判定，qa 直接采纳其结论。

## 审计触发

qa 过程审计在 **tester acceptance 通过 + architect code-review 完成**之后执行，作为质量门控的最后一步。未满足前置条件不启动审计。

## 审计范围

### 流程合规

- 任务是否按流水线顺序执行（需求→设计→编码→测试→审查）
- 跳过的步骤是否有合理理由并记录在 dev-log
- 派发决策是否符合 [Agent 派发触发条件](collaboration-standard.md#agent-派发触发条件)（🟢/🟡/🔴 分级是否正确）

### 文档完整性

按派发链检查各角色产出是否齐全：

| 角色 | 应产出 | 模板路径 |
|------|-------|---------|
| product | `requirement.md` | `docs/task/_template/requirement.md` |
| architect | `design.md`（如有设计环节）+ `review.md`（代码审查结论） | `docs/task/_template/design.md`、`review.md` |
| developer | `dev-log.md` | `docs/task/_template/dev-log.md` |
| tester | `test-report.md` | `docs/task/_template/test-report.md` |
| qa | `process-audit.md`（本文档） | `docs/task/_template/process-audit.md` |

另需检查：文档内容是否与实际产出一致（例：dev-log 声称的文件改动与实际 diff 是否匹配）。

### 文档格式合规

- Front Matter 完整性（level / purpose / status / version / date / author / changelog）
- 存放路径与文件命名是否符合 [内容体系规范](../content-system/Readme.md)
- 五度空间约束（任何目录 ≤ 5 个内容项）

### 需求结构完整性

- 需求文档是否满足 [需求管理规范](../dev/requirement-standard.md) 的三级结构
- AC（验收标准）章节是否存在且非空
- **只查结构存在性，不判断业务内容对错**

### 提交与任务管理

- 提交信息是否符合 [提交规范](../dev/git/commit-standard.md)（含 `Task: #N` 脚注）
- 任务状态标记是否按规则更新（`[ ]` / ⏳ / ✅ / ❌ / 🚫）
- 任务编号是否合法（`AAF-{三位}` / `#{递增}`）

### 不审计项（明确排除）

以下内容由 architect 的 [代码审查](../dev/code-review-standard.md) 负责，qa 不重复检查：

- 编码风格（命名、分层、异常处理等）
- 架构约束符合性
- 设计符合性（接口签名、类结构、数据模型）
- 对称性 / 安全 / 性能 / 可测试性
- 单元测试覆盖与测试代码质量

架构上的根因性问题由 architect 的 [架构审计](../dev/code-review-standard.md#与其他审查活动的区别)（`audit.md`）负责，qa 也不触及。

## 审计流程

```text
tester acceptance 通过 + architect review 完成
  ↓
qa 读 review.md + test-report.md（不重读代码）
  ↓
按"审计范围"逐项检查流程和文档
  ↓
汇总 review.md / test-report.md 的 blocker/major/minor 数
  + 本次新增的流程/文档级问题
  ↓
输出 process-audit.md：质量门控判定 + 问题清单 + 整改建议
  ├─ blocker=0 且 major≤2 → 通过
  └─ 超阈值 → 退回对应责任 agent
```

## 质量门控判定规则

| 来源 | 计数方式 |
|------|---------|
| architect `review.md` | 直接采纳其 blocker/major/minor 结论，不重评 |
| tester `test-report.md` | 直接采纳其失败用例与缺陷分级 |
| qa 本次新增 | 流程合规 / 文档完整性 / 格式合规级问题 |

**通过条件**：三方合计 `blocker = 0 且 major ≤ 2`。

## 产出

审计报告写入 `docs/task/{版本}/{AAF-XXX}/process-audit.md`，使用 [`_template/process-audit.md`](../../../docs/task/_template/process-audit.md) 模板。

必填内容：

- 元信息（审计人、日期、关联任务、风险等级）
- 流程合规检查表
- 文档完整性检查表
- 质量门控判定（三方 blocker/major/minor 汇总）
- 问题清单（本次新增）
- 度量数据（需求完成率、回退次数、周期时间）

## 与 code-review 的关系

| 维度 | code-review（代码审查） | process-audit（过程审计） |
|------|------------------------|--------------------------|
| 执行者 | architect | qa |
| 查什么 | 代码/设计内容是否符合规范 | 流程和文档是否齐全合规 |
| 触发时机 | 每次 developer 提交 | tester acceptance 通过 + architect review 完成后 |
| 读代码？ | **是** | **否** |
| 输出 | `review.md` | `process-audit.md` |

qa 发现 architect 的 review 有明显遗漏（例如未覆盖对称性检查清单某项）时，作为**流程问题**记录在 `process-audit.md`，要求 architect 补审；不自己下代码规范结论。
