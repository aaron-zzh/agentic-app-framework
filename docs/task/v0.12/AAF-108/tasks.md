---
level: Practice
layer: Product
purpose: 拆分 AAF-108 门禁恢复与集成收口的全量验证、补测与真理源同步任务
status: draft
version: 1.0.0
date: 2026-09-01
author: AaronZZH & Kiro
tags:
  - AAF-108
  - 质量门禁
  - 技术任务
related:
  - ../../../design/audit/2026-09-01-harness-landing-plan.md
gains:
  - 能把阶段性偏离欠下的验证与测试一次性补齐
  - 能让设计与规范真理源与落地实现重新一致
---

# AAF-108 门禁恢复与集成收口任务

## 任务约束

- 技术真理源：[Harness 落地计划 · 分阶段路线](../../../design/audit/2026-09-01-harness-landing-plan.md) 末阶段。
- 本故事原定是 AAF-103～AAF-107 阶段性偏离（不跑 `check`/`acceptance`、不新增测试文件）的偿还任务。
- 🔴 **范围调整（2026-09-02，人类拍板）**：三个全量门禁任务（`#10801`/`#10802`/`#10803`）**不做**，改为在各自技术任务内随手补齐编译验证（已见效：AAF-107 #10706、AAF-108 遗留的 `AgentScopeSpecCompilerTest`/新增 `AgentScopeEventMapperTest` 已在后续会话里补齐并跑通 `pnpm nx test service` 全绿，不再等待一次性全量收口）。理由：全量门禁收口对齐真实进度价值有限——AAF-104 仍有未完成任务（`#10403b`/`#10404`），在故事尚未全部交付前跑"最终收口"没有意义；随手验证已经能及时发现回归，不需要额外集中批次。
- 风险等级：🔴 高（真理源一致性与发布结论，仍适用于将来重新评估是否需要收口）。
- 准入：AAF-103～AAF-107 全部合入且无未修 blocker。

## 技术任务

### #10801 全量门禁一次性跑通

- **状态**：🚫 不做（2026-09-02 范围调整，见上方"范围调整"说明）
- **负责人**：~~developer-service~~
- **依赖**：AAF-103～AAF-107 全部完成
- **原范围**（不再执行）：
  - 执行 `pnpm check` 与 `pnpm acceptance`，修复前五个故事累积的编译、lint、单测与验收失败。
  - 失败即视为 AAF-103～AAF-107 全部未完工，按失败归属回退对应故事修复，不在本故事内改他人范围的设计。
- **原完成标准**（不再适用）：`pnpm check` + `pnpm acceptance` 全绿；质量门控 blocker=0 且 major≤2。

### #10802 补齐欠下的测试文件

- **状态**：🚫 不做（2026-09-02 范围调整，见上方"范围调整"说明）——实质工作已随手完成（见下），不再作为独立全量批次
- **负责人**：~~developer-service（单测）+ tester（集成/验收）~~
- **依赖**：#10801
- **原范围**（不再执行为独立批次）：
  - 按 AAF-103～AAF-107 各工作包的验收判据，补齐阶段约束期间未新建的测试文件。
  - 测试分层不变：`*Test.java` → Surefire → developer；`*IT.java` / `*AcceptanceTest.java` → Failsafe → tester。
  - 重点补：工具面负向测试、AG-UI schema 与配对、跨副本 resume、计划状态机与恢复、Redis 故障注入。
- **已随手完成的部分**（非本任务批次，记录事实）：`AgentScopeSpecCompilerTest` 两处 mock 缺陷修复（`Stream` 复用/`UnnecessaryStubbing`）；新建 `AgentScopeEventMapperTest`（32 个用例，覆盖 AAF-105 遗留的映射覆盖缺口）并修正两处断言错误。`pnpm nx test service` 已验证 `BUILD SUCCESS`（241+ 测试全绿）。跨副本 resume/计划状态机恢复/Redis 故障注入等验收级测试仍未补齐，留给触发相关功能的具体故事自行决定是否需要。

### #10803 同步真理源与例外登记

- **状态**：🚫 不做（2026-09-02 范围调整，见上方"范围调整"说明）——实质工作已随手完成（见下），不再作为独立协调批次
- **负责人**：~~协调者 + architect~~
- **依赖**：#10801
- **原范围**（不再执行为独立批次，已完成情况见下）：
  - ✅ `architecture.md`：已改为"AAF Harness 包裹的 AgentScope core ReAct"表述。
  - ✅ `runtime-event.md`：已写入公共事件披露白名单说明（含思考内容不外发的拦截点说明）。
  - ✅ `core/model-router.md`：已写入模型侧思考参数（AAF-106）与 L0 唯一模型解析（AAF-105 ADR-007）说明。
  - ✅ `agentscope-usage-guide.md`：已区分"官方推荐 vs AAF 选型"，删除 `HarnessAgent` 承载执行的表述。
  - ✅ `InvocationMode.AUTONOMOUS_HARNESS` 已原子重命名为 `AUTONOMOUS_AGENT_LOOP`，未保留旧枚举别名。
  - ✅ `JsonSchemaUtils` shadow 已登记为禁兼容层显式例外（`docs/prd/improvements.md` 2026-09-02 条目），含退出路径。
  - [ ] "阶段约束的三条偏离在恢复后从计划文档标记为已关闭"——未逐一核对关闭，不阻塞，留给下次触及相关文档时顺手关闭。
- **完成标准**（原定，已达成大部分）：无文档与实现冲突残留；例外与改进项均有登记位置。

## 新增任务

> 开发过程中发现需要新增的任务，由开发者提出，协调者评估后写入

## 评审状态（🔴 高风险适用）

| 阶段 | 执行次数 | 最后执行 | 状态 | 必须 |
|------|---------|---------|------|------|
| product（需求细化） | — | — | — | 无新需求 |
| architect（技术设计） | 0 | — | ⏳ PENDING | 🔴 是 |
| designer（UI 审查） | — | — | — | 不涉及前端 |
| developer（编码） | 0 | — | ⏳ PENDING | 🔴 是 |
| architect（代码审查） | 0 | — | ⏳ PENDING | 🔴 是 |
| tester（验收测试） | 0 | — | ⏳ PENDING | 🔴 是 |
| qa（过程审计） | 0 | — | ⏳ PENDING | 🔴 是 |
