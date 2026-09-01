
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
- ✅ #10302 冻结最终工具面 — 新增 `requireFrozenToolSurface(...)` 运行期安全门（工具名集合恰好等于白名单，不等则 close + 抛）+ 解析后 Model 非空校验 + 两个负向测试用例。（2026-09-01）

> **关键发现**：`ReActAgent.Builder.dynamicSkillsEnabled` 默认为 `true`（`ReActAgent.java:4206`），与 HarnessAgent 需显式 `disableDynamicSkills()` 的语义相反。若只做类型替换而不显式 `dynamicSkillsEnabled(false)`，切换后会向模型暴露 AAF 未授权的技能加载工具——这正是 `agentscope-usage-guide.md`「builder 默认值必须验证」规则要防的情况。`enableMetaTool` / `enablePendingToolRecovery` / `taskListEnabled` 默认已是 `false`，仍显式声明以锁定意图并让上游改默认值时能被断言发现。

> **不重复校验**：`ExecutionPolicy` 记录不变量已保证 `maxIterations >= 1` 与 `maxModelRetries >= 0`，因此 `buildAgent` 只补 core 确实不做的两件事（模型非空 + 工具面冻结），不在此处复制上游已有的校验。

> **fail-closed 断言的静态验证**：`requireFrozenToolSurface` 在生产路径抛异常，若 core 默认注册任何工具则每次编译都会失败。已逐条核实 `ReActAgent.build()`（`ReActAgent.java:4902-4952`）只在六个条件下向 toolkit 注册——`registerToolsFromHooks`（AAF 无 hook）、`enableMetaTool`（已关）、`longTermMemory != null`（未设）、`knowledgeBases` 非空（未设）、`taskListEnabled`（默认关且未启）、`skillRepositories` 非空 && `dynamicSkillsEnabled`（未设且已关）。AAF 用法下 `agentToolkit` 恰为 AAF toolkit 的深拷贝，断言恒成立。

> `HarnessAgentExecutionAdapter` 类名保留未改。计划文档建议重命名为 `AgentScopeExecutionAdapter`，但那会牵动 AutoConfiguration 与测试类名，属独立改动，不与本次类型切换混合提交。

## 验证

- `pnpm nx compile service`：6 个模块全部 BUILD SUCCESS，含 test 源码（1589 + 65 个文件）。
- `pnpm nx fix service`：spotless 重新格式化 3 个文件，未触及本次改动之外的任何文件。
- 按 v0.12 阶段约束，本次未执行 `pnpm nx test service` 与 `check`；欠下的验证记入 AAF-108 #10801。
