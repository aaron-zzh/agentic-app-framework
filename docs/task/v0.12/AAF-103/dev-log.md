
执行者：AI/developer-service

## 实现文件

| 文件 | 说明 |
|------|------|
| `.../infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java` | 编译目标切 `ReActAgent`，两条 builder 链收敛为唯一 `buildAgent(...)` |
| `.../infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java` | `ResolvedExecution`/`ActiveExecution` 改 core 类型，interrupt 去掉 `getDelegate()` 间接层 |
| `.../compiler/AgentScopeSpecCompilerTest.java` | 类型与断言改 `ReActAgent`，`getDelegate().getSysPrompt()` → `getSysPrompt()` |
| `.../execution/HarnessAgentExecutionAdapterTest.java` | 两个 mock（`HarnessAgent` + `ReActAgent` delegate）合并为单个 `ReActAgent` mock |
| `apps/service/project.json` | 新增 `compile` target（`mvn test-compile`），支撑阶段性编译门槛 |

## 实现决策

- ✅ #10301 编译目标切为 ReActAgent — 15 个 `disableXxx()` 随之删除（core 无这些方法，与 #10302 物理不可分），`.agentId()` 去掉（core 无该 builder 参数，业务 agentId 只存于 AgentSpec/缓存键/RuntimeContext/事件）。（2026-09-01）
- ⏳ #10302 部分完成 — `disableXxx()` 已删并显式关闭三项；**待做**：build 前显式校验（model/正数迭代重试/非空 Toolkit）+ 最终工具名负向断言。（2026-09-01）

> **关键发现**：`ReActAgent.Builder.dynamicSkillsEnabled` 默认为 `true`（`ReActAgent.java:4206`），与 HarnessAgent 需显式 `disableDynamicSkills()` 的语义相反。若只做类型替换而不显式 `dynamicSkillsEnabled(false)`，切换后会向模型暴露 AAF 未授权的技能加载工具——这正是 `agentscope-usage-guide.md`「builder 默认值必须验证」规则要防的情况。`enableMetaTool` / `enablePendingToolRecovery` / `taskListEnabled` 默认已是 `false`，仍显式声明以锁定意图并让上游改默认值时能被断言发现。

> `HarnessAgentExecutionAdapter` 类名保留未改。计划文档建议重命名为 `AgentScopeExecutionAdapter`，但那会牵动 AutoConfiguration 与测试类名，属独立改动，不与本次类型切换混合提交。

## 验证

- `pnpm nx compile service`：6 个模块全部 BUILD SUCCESS，含 test 源码（1589 + 65 个文件）。
- `pnpm nx fix service`：spotless 重新格式化 3 个文件，未触及本次改动之外的任何文件。
- 按 v0.12 阶段约束，本次未执行 `pnpm nx test service` 与 `check`；欠下的验证记入 AAF-108 #10801。
