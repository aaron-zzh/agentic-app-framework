---
level: Practice
layer: Product
purpose: 拆分 AAF-103 AgentScope core 复用收缩的编译目标切换、依赖降级与运行时加固任务
status: draft
version: 1.0.0
date: 2026-09-01
author: AaronZZH & Kiro
tags:
  - AAF-103
  - AgentScope
  - Harness
  - 技术任务
related:
  - ../../../design/audit/2026-09-01-harness-landing-plan.md
  - ../../../design/adr/ADR-005-agentscope-boundary-and-orchestration.md
gains:
  - 能按依赖顺序把执行内核从 HarnessAgent 切到 core ReActAgent
  - 能证明最终工具面无隐式工具泄漏
---

# AAF-103 AgentScope core 复用收缩任务

## 任务约束

- 技术真理源：[Harness 落地计划 · 主线：正确复用 AgentScope core](../../../design/audit/2026-09-01-harness-landing-plan.md)。
- 决策依据：[ADR-005](../../../design/adr/ADR-005-agentscope-boundary-and-orchestration.md) 议题三 + 勘误节。
- 风险等级：🔴 高（跨模块 + 依赖边界 + 安全工具面）；按 developer → architect review → tester → qa 流转。
- 禁兼容层：不保留 `HarnessAgent` 作为 fallback / optional / 实验开关；一次原子替换。
- 不 broad refactor：不借机重构缓存策略、定义模型或 TaskBoard。
- **阶段约束**：不新增测试文件，断言补进既有测试；不执行 `check` / `acceptance`；准出最低要求为 `pnpm nx compile service` 通过。详见计划文档「当前开发阶段约束」。

## 技术任务

### #10301 编译目标切为 ReActAgent

- **状态**：[x] ✅ 已完成（2026-09-01）— developer-service
- **负责人**：developer-service
- **依赖**：无
- **范围**：
  - `AgentScopeSpecCompiler` 的 `compile/compileDirect/compileDynamicNew/compileNew` 返回值、两个 `BoundedAgentCache` value、内部构建方法统一改为 `ReActAgent`。
  - 两条 builder 链收敛为唯一私有 `buildAgent(...)`，动态与预定义分支只准备冻结输入。
  - `ResolvedExecution`、`ActiveExecution` 改 core 类型；调整 `AgentScopeSpecCompilerTest` 既有断言。
- **完成标准**：无 `io.agentscope.harness` import；预定义、DIRECT、ephemeral 三条路径行为不变；`compile` 通过。
- **实际结果**：`compile` 6 模块全绿；`.agentId()` 因 core 无该 builder 参数而移除；15 个 `disableXxx()` 随之删除（见 #10302）。

### #10302 删除 Harness-only 开关并冻结最终工具面

- **状态**：[x] ✅ 已完成（2026-09-01）— developer-service
- **负责人**：developer-service
- **依赖**：#10301
- **范围**：
  - ✅ 删除 15 个 `disableXxx()`；显式 `dynamicSkillsEnabled(false)`、`enableMetaTool(false)`、`enablePendingToolRecovery(false)`，不调用 `enableTaskList()`。
  - ✅ build 前校验解析后的 `Model` 非空（core `build()` 不校验，null 会漂到首次模型调用才 NPE）；迭代与重试上限不重复校验——`ExecutionPolicy` 记录不变量已保证。
  - ✅ 新增 `requireFrozenToolSurface(...)` 运行期安全门：构建后校验 `agent.getToolkit().getToolNames()` 恰好等于白名单，不等则 close + 抛。
  - ✅ 既有测试补两个负向用例：工具面 `containsExactly` 白名单且不含 14 个内建工具名；空画像时工具面为空。
- **完成标准**：最终 Toolkit 与冻结白名单完全相等，负向断言覆盖上述工具名；`compile` 通过。
- **备注**：`disableXxx()` 删除与 #10301 物理不可分（core 无这些方法），已随 #10301 落地。`dynamicSkillsEnabled` 上游默认 `true`，必须显式关闭。

### #10303 中断与生命周期改用 core API

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10301
- **范围**：
  - `active.agent().getDelegate().interrupt(context)` 改为 `active.agent().interrupt(context)`；`streamEvents` 与 `close` 走 core 原生调用。
  - 保留既有唯一终态 CAS、三类 timeout、顺序落库与 `doFinally` 清理，不与本次类型切换混合改动状态机语义。
  - 调整 `HarnessAgentExecutionAdapterTest` 的 5 个 P0 并发用例使其在 core 类型下继续锁定行为。
- **完成标准**：cancel/complete 竞争仍只产生一个终态，close 恰好一次；`compile` 通过。

### #10304 依赖坐标降为 core 并清理僵尸扩展

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10301、#10302、#10303
- **范围**：
  - `aaf-dependencies/pom.xml` 与 `aaf-framework/pom.xml` 的 `agentscope-harness` 改为 `agentscope-core`，victools 两个 `<exclusion>` 原样迁移。
  - 删除零使用坐标 `agentscope-extensions-skill-postgresql-repository`、`agentscope-extensions-oss`；保留 redis 与三个 model 扩展。
  - 修正 pom 注释中"harness 是推荐入口""harness 线程队列/元数据后端"等已失效叙述。
  - 修正 `io/agentscope/core/util/JsonSchemaUtils.java` 的失真版本注释（保留该类，见计划文档决策六）。
- **完成标准**：依赖树无直接 harness；`compile` 通过；shadow 注释准确说明 victools 4→5 断层。

### #10305 关闭 RQ-11 / RQ-12 / RQ-13

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10303
- **范围**：
  - RQ-11：重复订阅失败方产生稳定失败事件并释放资源，不再抛裸 `IllegalStateException`。
  - RQ-12：interrupt 与 close 通过同一 lifecycle CAS 保证有序，close 后不再 interrupt。
  - RQ-13：`SpringRedisClientAdapter` 用 Lua/事务原子维护 value + registry；生产扫描改 SCAN，去掉 `KEYS`。
  - 断言补进既有 `HarnessAgentExecutionAdapterTest`，竞态用可控 Publisher/barrier，不用 sleep。
- **完成标准**：三条 minor 在 02-runtime-quality.md 中可标记关闭；`compile` 通过。

## 新增任务

> 开发过程中发现需要新增的任务，由开发者提出，协调者评估后写入

## 评审状态（🔴 高风险适用）

| 阶段 | 执行次数 | 最后执行 | 状态 | 必须 |
|------|---------|---------|------|------|
| product（需求细化） | 0 | — | ⏳ PENDING | 🔴 是 |
| architect（技术设计） | 0 | — | ⏳ PENDING | 🔴 是 |
| designer（UI 审查） | — | — | — | 不涉及前端 |
| developer（编码） | 0 | — | ⏳ PENDING | 🔴 是 |
| architect（代码审查） | 0 | — | ⏳ PENDING | 🔴 是 |
| tester（验收测试） | 0 | — | ⏳ PENDING | 🔴 是 |
| qa（过程审计） | 0 | — | ⏳ PENDING | 🔴 是 |
