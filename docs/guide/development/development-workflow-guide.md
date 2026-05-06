# 开发流程指南

## 目标

按 AAF 的迭代流程完成一个功能从需求到交付的全过程，知道每一步该做什么、查什么文档、产出什么。

## 前置条件

- 了解 [项目结构](../../project-structure.md)
- 了解 [协作规范](../../reference/team/collaboration-standard.md) 中的角色分工
- 有可用的开发环境（见 [开发环境搭建](../../reference/dev/dev-environment.md)）

## 流程全景

```text
准备 → 执行 → 发布 → 总结
          ↑      |
          └─回退─┘（质量门控不通过）
```

周期 2-4 周。完整流程定义见 [迭代过程规范](../../reference/team/process-standard.md)，各环节协作方式见 [协作规范](../../reference/team/collaboration-standard.md)。

---

## 1. 准备：确定迭代范围

```text
版本规划 → 需求调研 → 用户故事拆分 → [技术预研] → 选取迭代范围
```

1. **版本规划**：对照 [路线图](../../prd/roadmap.md) 确认版本目标和交付范围
2. **需求调研**：从三个来源收集待评估需求：
   - [需求收集](../../prd/requirements.md) — 业务需求和点子
   - [用户反馈](../../prd/user-feedback.md) — 产品体验反馈
   - [改进意见](../../prd/improvements.md) — 内部改进建议
3. **用户故事拆分**：
   - product 将业务需求拆分为一级用户故事，为每个故事创建需求文件 `docs/prd/{module}/{feature}.md`，标注优先级（P0/P1/P2），定义初步 AC
   - 协调者审核（格式完整性 + AC 可测性 + 架构约束一致性），将审核意见写入需求文件底部「审核记录」区块
   - 审核通过后录入 [backlog](../../task/backlog.md)，分配 `AAF-{三位序号}`
   - 🔴 高风险（架构调整、权限变更、跨模块影响）必须人类审核后才能录入 backlog
4. **技术预研**（可选）：新模块/重大技术选型时，architect 评估可行性，输出方案和风险清单；🔴 高风险方案必须人类审核
5. **选取迭代范围**：协调者按优先级（P0 > P1 > P2）从 backlog 选取 3-7 个用户故事，创建迭代任务文件 `docs/task/aaf-{version}.md`

> 拆分规则见 [需求管理规范](../../reference/dev/requirement-standard.md)，文件格式见 [任务管理规范](../../task/Readme.md)。

**产出**：需求文件、backlog 更新、迭代任务文件

---

## 2. 执行：按流水线交付

每个用户故事按以下流水线流转：

```text
需求细化 + 技术设计
  → [UI/交互设计]          ← 涉及前端时
    → 编码 + 单元测试       ← developer 内循环
      → 代码审查
        → [代码重构]        ← 审查发现结构性问题时
          → 验收测试
            → 过程审计
              → 质量门控
```

产出文档按用户故事组织在 `docs/task/{版本}/{AAF-XXX}/` 下。

### 2.1 需求细化与技术设计（product + architect）

- product 将 Epic 拆分为可独立交付的 Story，为每个 Story 补充 Spec（数据模型、业务规则、约束边界、精确 AC、测试要点）；功能流程复杂时编写产品设计文档（用户旅程、功能流程、交互逻辑）
- architect 完成技术设计文档（接口定义、类结构、模块交互、ADR），将 Story 拆分为技术任务（`#N`，全局递增），标注依赖，记录在迭代文件中
- 🔴 高风险设计（架构调整、接口删除、权限变更）必须人类审核后再进入开发

**产出**：需求文件补充 Level 2 + `docs/design/{module}/{doc}.md`

> 规范：[需求管理规范](../../reference/dev/requirement-standard.md)、[架构设计方法论](../../design/README.md)、[领域建模规范](../../reference/dev/apps/service/domain-modeling-standard.md)、[架构约束](../../reference/dev/architecture-constraints.md)

### 2.2 UI/交互设计（designer）

> 涉及前端界面时触发，纯后端跳过。

界面布局、交互流程、组件规范、响应式和多端适配方案。

> 规范：[UI 设计规范](../../design/ui/Readme.md)

### 2.3 编码实现（developer-*）

按技术设计和 UI 设计编码，遵循编码规范和架构约束。新建模块参考 [如何创建新业务包](how-to-create-module.md)。

**产出**：源码 + `docs/task/{版本}/{AAF-XXX}/dev-log.md`（[模板](../../task/_template/dev-log.md)）

提交脚注关联技术任务：`Task: #N`

> 规范：[编码风格规范](../../reference/dev/apps/service/coding-style-standard.md)、[架构约束](../../reference/dev/architecture-constraints.md)

### 2.4 单元测试与自验证（developer-*）

编写单元测试（覆盖核心逻辑），自测冒烟（主流程跑通）。

**developer 强制内循环**——完工汇报前必须全绿：

```text
编写代码
  ↓
pnpm check:affected   ← lint + typecheck + 单测 + build
  ↓
全绿？
  ├─ 否 → 读错误 → 修复 → 重跑
  └─ 是 → 汇报协调者，进入代码审查
```

**硬规则**：`check` 失败状态下汇报视为未完工，协调者有权直接驳回。不得用 `@Disabled` / `it.skip` / 吞异常绕过失败。

> 规范：[单元测试规范](../../reference/dev/test/unit-test-standard.md)

### 2.5 代码审查（architect）

规范合规检查（命名、结构、异常处理）、设计符合性检查、安全审查。

**产出**：`docs/task/{版本}/{AAF-XXX}/review.md`（[模板](../../task/_template/review.md)）

> 规范：[代码审查规范](../../reference/dev/code-review-standard.md)

### 2.6 代码重构（developer-*）

> 审查发现结构性问题时触发，非每次必须。

消除重复代码和坏味道，优化模块结构和依赖关系，确保重构后测试全部通过。

### 2.7 验收测试（tester）

对照 AC 逐条编写验收测试，覆盖集成、边界、异常路径，输出需求覆盖验证。

**前置条件**：`pnpm check:affected` 必须全绿，否则立即退回 developer。

**产出**：`docs/task/{版本}/{AAF-XXX}/test-report.md`（[模板](../../task/_template/test-report.md)）

**Bug 处理**：

```text
tester 发现 Bug → 登记 bugs.md → developer 修复 → check:affected 确认 → tester 验证关闭
```

Bug 分级：P0（阻塞主流程）= blocker；P1（功能不可用）计入 major；P2（体验问题）计入 minor。

> 规范：[验收测试规范](../../reference/dev/test/acceptance-test-standard.md)、[任务管理规范 #Bug 管理](../../task/Readme.md#bug-管理)

### 2.8 过程审计（qa）

检查流程合规（是否按流水线执行）、规范遵守、文档完整性。

> 规范：[过程审计规范](../../reference/team/process-audit-standard.md)

### 2.9 质量门控

**通过条件**：blocker = 0 且 major ≤ 2

不通过则按问题类型回退：需求→product，设计→architect，实现→developer，测试→tester，过程→qa。

> 问题分级定义见 [协作规范](../../reference/team/collaboration-standard.md)

---

## 3. 发布：部署验证

1. 按 [部署规范](../../reference/dev/deployment-standard.md) 部署到测试环境，执行冒烟测试
2. 有 schema 变更时按 [数据库迁移规范](../../reference/dev/apps/service/database-migration-standard.md) 编写 Flyway 迁移脚本（🔴 必须人类审核）
3. 演示已完成功能，收集反馈记录到 [用户反馈](../../prd/user-feedback.md)
4. 编写发布说明 `docs/task/{版本}/release-notes.md`，按 [发布规范](../../reference/dev/git/release-standard.md) 发布
5. 打 Git Tag，分支管理见 [分支管理规范](../../reference/dev/git/branch-manage-standard.md)

> 🔴 正式发布需人类确认。

---

## 4. 总结：回顾改进

1. 整理做得好的 / 待改进的 / 行动项
2. 按 [度量标准](../../reference/team/measurement-standard.md) 分析完成率、缺陷密度、周期时间、回退次数
3. 规范缺失或不合理时更新对应规范文档，改进建议写入 [改进意见](../../prd/improvements.md)
4. 已完成和已取消的任务移入 `docs/task/archive/`，更新 [backlog](../../task/backlog.md)

---

## 验证检查清单

- [ ] 所有 P0/P1 任务已完成
- [ ] 质量门控通过（blocker=0，major≤2）
- [ ] `pnpm check` 和 `pnpm acceptance` 全绿
- [ ] 接口变更有破坏性变更说明
- [ ] 数据库迁移脚本就绪（如有）
- [ ] 文档已更新
- [ ] 发布说明已编写
- [ ] backlog 已归档

---

## 文档速查

| 阶段 | 规范文档 | 产出物 |
|------|---------|--------|
| 准备 | [路线图](../../prd/roadmap.md)、[需求管理规范](../../reference/dev/requirement-standard.md) | 需求文件、backlog、迭代任务文件 |
| 需求细化 | [需求管理规范](../../reference/dev/requirement-standard.md) | `docs/prd/{module}/{feature}.md`（Level 2） |
| 技术设计 | [架构设计方法论](../../design/README.md)、[领域建模规范](../../reference/dev/apps/service/domain-modeling-standard.md)、[架构约束](../../reference/dev/architecture-constraints.md) | `docs/design/{module}/{doc}.md` |
| UI 设计 | [UI 设计规范](../../design/ui/Readme.md) | 设计稿、交互说明 |
| 编码 | [编码风格规范](../../reference/dev/apps/service/coding-style-standard.md)、[架构约束](../../reference/dev/architecture-constraints.md) | 源码、dev-log.md |
| 测试 | [单元测试](../../reference/dev/test/unit-test-standard.md)、[验收测试](../../reference/dev/test/acceptance-test-standard.md)、[集成测试](../../reference/dev/test/integration-test-standard.md) | 测试代码、test-report.md |
| Bug | [任务管理规范 #Bug 管理](../../task/Readme.md#bug-管理) | bugs.md |
| 审查 | [代码审查规范](../../reference/dev/code-review-standard.md) | review.md |
| 发布 | [发布规范](../../reference/dev/git/release-standard.md)、[部署规范](../../reference/dev/deployment-standard.md)、[提交规范](../../reference/dev/git/commit-standard.md) | release-notes.md、Git Tag |
| 总结 | [度量标准](../../reference/team/measurement-standard.md) | 回顾记录、规范变更 |
