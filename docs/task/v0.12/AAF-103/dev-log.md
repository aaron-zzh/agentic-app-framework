
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
- ✅ #10303 中断与生命周期核实 — 执行管线语义未变（三类 timeout / CAS 终态 / doFinally 均为 AAF 侧逻辑）；更正 4 处失真注释。（2026-09-01）
- ✅ #10304 依赖降为 core — 删 harness + 两个零使用扩展，victools exclusion 迁移，5 处 pom 注释与 shadow 类注释更正。（2026-09-01）
- ✅ #10305 关闭 RQ-11/12/13 — 拒重异常类型化、interrupt 加释放边界门、`KEYS` 改 `SCAN`；RQ-13 原子性受上游限制未闭环。（2026-09-01）

> **RQ-11 的正确做法不是"事件化"**：审计建议失败方产出 `RUN_FAILED`。但失败方与胜出方共享同一 `executionId`，写入终态会被 RQ-05 的重放守卫读到并判定"此执行已结算"，导致仍在正常运行的胜出方在下一次重投时被跳过——比裸异常更糟。最终实现是抛类型化的 `DuplicateExecutionSubscriptionException`（归类 `INVALID_REQUEST`，不可重试），让调用方能靠类型而非文本区分"重复订阅"与真实失败，同时不污染共享事件账本。

> **RQ-13 只能关一半**：`KEYS` → `SCAN` 已修（`KEYS` 在 Redis 单线程上全遍历 keyspace，键数上万即阻塞所有命令，含本进程状态读写）。但"值 + 注册表"的非原子登记在上游 `RedisAgentStateStore:218-220,259-261` 分两步调用 `set` 与 `addToSet`，而 `RedisClientAdapter` 接口只有单命令原语、没有组合入口，本适配器无法合并为一次 Lua/事务提交。影响边界已评估：AAF 按含 executionId 的显式键删除状态槽，不依赖注册表扫描清理，孤儿键不会被其他执行读到。要闭环必须改上游或 fork，已在类 javadoc 记录。

> **依赖 diff 核验（替代无法用编译发现的传递依赖丢失）**：改动前后各采集 `dependency:list`。framework 与 api 各恰好移除 3 个坐标、新增 0——`agentscope-harness`、`agentscope-extensions-oss`、`agentscope-extensions-skill-postgresql-repository`，与预期完全一致。`aaf-auto-dev` 另少 24 个，是 `extensions-oss` 的阿里云 SDK 传递链（aliyun-sdk-oss / httpclient 4.x / jaxb / jdom2 / jettison / bouncycastle / opentracing），已逐项 grep 确认该模块 import 数为 0；其中 5 个 jaxb 相关坐标只是换路径后 scope 由 `compile` 变 `runtime`，并未消失。关键点：`OssStorageService` 用的 `aliyun-sdk-oss` 在 framework 是 `<optional>true</optional>` 的自有声明（此前同时也由 `extensions-oss` 非可选传递而来），改动后仍在 framework 与 api 清单中，不受影响。

> **审计结论需更正（喂给 #10305）**：02-runtime-quality.md 的 RQ-12 写"`ReActAgent.close` 是 no-op，所以现状后果仅是不必要调用"。2.0.2 源码不是这样：`ReActAgent.close()` 执行 `shutdownManager.unbindStateSaver(this)` + `clearStateCache()`（`ReActAgent.java:4133-4137`），而 `interrupt(ctx)` 是通过 `getAgentState(uid, sid).interruptControl().trigger(...)` 定位在飞调用的（`ReActAgent.java:733-750`）。因此"先 close 再 interrupt"会拿到新建的 AgentState，**中断信号静默丢失**。该缺口与本次类型切换无关（`HarnessAgent.close()` 一直在内部调 `delegate.close()`），是 RQ-12 的严重性被低估，而非新引入。当前 close 只发生在订阅终止后的 `doFinally`，循环已在收尾，故实际后果有限。

> **关键发现**：`ReActAgent.Builder.dynamicSkillsEnabled` 默认为 `true`（`ReActAgent.java:4206`），与 HarnessAgent 需显式 `disableDynamicSkills()` 的语义相反。若只做类型替换而不显式 `dynamicSkillsEnabled(false)`，切换后会向模型暴露 AAF 未授权的技能加载工具——这正是 `agentscope-usage-guide.md`「builder 默认值必须验证」规则要防的情况。`enableMetaTool` / `enablePendingToolRecovery` / `taskListEnabled` 默认已是 `false`，仍显式声明以锁定意图并让上游改默认值时能被断言发现。

> **不重复校验**：`ExecutionPolicy` 记录不变量已保证 `maxIterations >= 1` 与 `maxModelRetries >= 0`，因此 `buildAgent` 只补 core 确实不做的两件事（模型非空 + 工具面冻结），不在此处复制上游已有的校验。

> **fail-closed 断言的静态验证**：`requireFrozenToolSurface` 在生产路径抛异常，若 core 默认注册任何工具则每次编译都会失败。已逐条核实 `ReActAgent.build()`（`ReActAgent.java:4902-4952`）只在六个条件下向 toolkit 注册——`registerToolsFromHooks`（AAF 无 hook）、`enableMetaTool`（已关）、`longTermMemory != null`（未设）、`knowledgeBases` 非空（未设）、`taskListEnabled`（默认关且未启）、`skillRepositories` 非空 && `dynamicSkillsEnabled`（未设且已关）。AAF 用法下 `agentToolkit` 恰为 AAF toolkit 的深拷贝，断言恒成立。

> `HarnessAgentExecutionAdapter` 类名保留未改。计划文档建议重命名为 `AgentScopeExecutionAdapter`，但那会牵动 AutoConfiguration 与测试类名，属独立改动，不与本次类型切换混合提交。

## 验证

- `pnpm nx compile service`：6 个模块全部 BUILD SUCCESS，含 test 源码（1589 + 65 个文件）。
- `pnpm nx fix service`：spotless 重新格式化 3 个文件，未触及本次改动之外的任何文件。
- 按 v0.12 阶段约束，本次未执行 `pnpm nx test service` 与 `check`；欠下的验证记入 AAF-108 #10801。
