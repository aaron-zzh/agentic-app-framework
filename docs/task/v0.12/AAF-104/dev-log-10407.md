
执行者：AI/developer-service

## 实现文件

| 文件 | 说明 |
|------|------|
| `.../intelligent/shared/event/NodeIdentity.java` | 新增：编排节点身份（subTaskId / kind / roleKey / skillKey），含安全键校验与 source 路径合成 |
| `.../intelligent/agent/model/InvocationContext.java` | 新增 `nodeIdentity` 字段 + 无节点身份场景的次级构造器 |
| `.../intelligent/assistant/application/AssistantCommand.java` | 新增 `nodeIdentity` 字段；`forSubTask` 填真值；`copy` 保留身份；次级构造器兼容非子任务操作 |
| `.../intelligent/assistant/application/AssistantApplicationService.java` | `agentCommand` 把 `command.nodeIdentity()` 传入 InvocationContext |
| `.../intelligent/assistant/application/DelegatedTaskCoordinator.java` | `context(command)` 同上 |
| `.../intelligent/shared/event/ExecutionEvent.java` | 新增 `nodeIdentity` 字段 + 无节点场景次级构造器 |
| `.../infrastructure/agentscope/mapping/AgentScopeEventMapper.java` | 从 `context.nodeIdentity()` 落入事件 |
| `.../shared/event/publication/AafAiTaskEvent.java` | 新增 `parentExecutionId` 与 `nodeIdentity` 两字段 |
| `.../shared/event/publication/ExecutionEventPublicMapper.java` | 两处构造点透出上述字段 |

## 实现决策

- ✅ #10407 完成 — 链路端到端贯通：`SubTask` → `AssistantCommand.forSubTask` → `InvocationContext` → `ExecutionEvent` → `AafAiTaskEvent`。（2026-09-01）

> **暴露形式选择：只出稳定/展示标签，不出原始 `agentId`**。此前登记的待确认项是"暴露 `agentId` 还是稳定节点标签"。实现选了后者——公共事件透出的是 `subTaskId`（展示标签）、`kind`、`roleKey`、`skillKey`（配置定义的稳定聚合维度），`agentId` 仍只留在内部 `ExecutionEvent`。四项都有下游必要用途：`kind` 决定标准/CUSTOM 分流、`subTaskId` 进 source 路径与拓扑展示、`roleKey`/`skillKey` 供监控聚合。

> **`ExecutionEvent` 与 `AafAiTaskEvent` 的构造策略不同**：前者 18 处构造点用次级构造器避免 churn（含 5 处测试），后者只 2 处构造点直接改。判据是 churn 规模，不是"能不能加便利构造器"——2 处时加便利构造器反而多留一条可漏填的路径。

> **投影兜底事件显式传 null**：`ExecutionEventPublicMapper` 第一处构造是无对应内部事件的合成投影（`projection:` 前缀），它不属于任何执行或板上节点，`parentExecutionId` 与 `nodeIdentity` 都是 null 且加了注释说明——不是漏填。

> **纠正一处此前的错误判断**：我曾据 `AggregationContract` 是"契约而非节点"断言"`TaskBoard.Kind` 只有 COORDINATOR 与 EXECUTOR、没有 AGGREGATOR"。**错的**——`Kind` 实际有四个值：`COORDINATOR` / `EXECUTOR` / `EVALUATOR` / `AGGREGATOR`。是穷举 switch 的编译错误逮出来的，不是我复查发现的。`NodeKind` 已补齐四项并按"交付类（COORDINATOR/AGGREGATOR）vs 内部类（EXECUTOR/EVALUATOR）"分组。

> **由此引出一条 #10403 的未决不变量**：若 AGGREGATOR 与 COORDINATOR 可同时出现在一块板上，就有两个交付类节点，各发一次 `TEXT_MESSAGE` 会让客户端收到两条"最终回复"。实施标准/CUSTOM 分流前必须先确认「每板至多一个交付类节点」；不成立则需按聚合契约（PASS_THROUGH / ORDERED_CONCAT）决定唯一应答者。已写入 `NodeIdentity.userFacing()` 的 javadoc。

> **`copy(...)` 必须保留 nodeIdentity**。`asResume` / `withLease` / `newExecution` / `withInput` 四个派生方法共用 `copy`，它走的是不带 nodeIdentity 的构造器。若不显式传递，恢复或换租约后的执行者会退化为"无节点"，AG-UI 将把它误判为面向用户的应答者——这是一条静默的语义降级，编译不会报错。已在该处加注释说明。

> **改错过一次位置**：`forSubTask` 的 `return new AssistantCommand(...)` 在第 278 行，而我按"最后一个 return"定位改到了第 317 行的 `copy(...)`。编译器以"找不到符号 subTask"报出。教训是定位构造点不能用"最后一个匹配"这类位置启发式，必须先确认它属于哪个方法。

> **放 `shared/event` 而非 `assistant/model`**：首版写在 `agent/model` 并 import `TaskBoard.Kind`，这让 L2 反向依赖 L3，违反"上层可调下层、禁止下层调上层"。改放 `shared/event`（`ExecutionEvent` 所在处）并自带 `NodeKind` 枚举，由编排层负责从 `TaskBoard.Kind` 映射。ArchUnit 的 `LayeringTest` 当前 0 条真实规则，抓不到这类问题，只能靠人工守。

> **`subTaskId` 是模型生成的自由字符串，必须当展示数据而非安全键**。`CoordinationPlan.ExecutorAssignment.subTaskId` 来自协调者输出的 plan JSON，`decodeAndValidatePlan` 只校验格式。`NodeIdentity` 因此加了 `SAFE_NODE_KEY` 正则（首字符字母数字，后续仅 `A-Za-z0-9._-`，≤128）——它会进入 AG-UI 的 source 路径，不能带斜杠或控制字符。授权、租户隔离、幂等一律仍用 `executionId`/`tenantId`。

> **两种模式的命名权不同**：动态分解时协调者自由命名；静态 Team 模式下 `subTaskId`/`roleKey`/`skillKey` 必须逐项匹配预定义 worker（`TaskBoard.java:124`）。实现填充时要区分。

> **次级构造器不是兼容层**：`nodeIdentity == null` 是有意义的取值——DIRECT 直答与 Assistant 自身发起的调用本就不在编排板上。次级构造器让这类场景显式表达"无节点"，而编排层派发执行者时必须用完整构造器。这避免了 9 处构造点的无意义 churn，同时保留"漏填即语义错误"的可见性。

> **发现：`AssistantCommand` 是必经的穿透点，无捷径**。原以为可在 `DelegatedTaskCoordinator.context(...)` 就地填充，但执行者的 `InvocationContext` 实际是在 `AssistantApplicationService.agentCommand(...)` 内由 child `AssistantCommand` 构建的（`commands.execute(childCommand)` → 应用服务），而 `AssistantCommand.forSubTask` 只消费 `SubTask` 派生 idempotency/runId、**不保留其身份**。因此必须给 `AssistantCommand` 加 `NodeIdentity` 字段并在 `forSubTask` 填入。这是下一步的主要工作量。

> **DB 无需改动**：`ExecutionEventEntity` 把整个 `ExecutionEvent` 存为 JSONB `event_payload`（表 `ai_task_event`），只提升 7 个字段为索引列。新增字段自动进 JSONB，存量行缺该键反序列化为 null——此前请人类确认的"存量数据兼容"问题自行消解。若将来需按 `roleKey` 做 SQL 聚合，可加 JSONB 表达式索引，属优化非阻塞。

## 验证

- `pnpm nx compile service`：BUILD SUCCESS（六模块，含 test 源码）。
- 本轮按人类指示「尽量不执行测试」，未跑 `pnpm nx test service`。曾尝试定向跑 `DelegatedTaskCoordinator*Test` 等三个类，因 `-pl aaf-framework` 单模块构建不解析 `aaf-common` 符号而失败——是构建配置问题、与本次改动无关，全量 `compile` 为绿。欠下的测试验证记入 AAF-108 #10801。
- 值得单独测的点（留给 #10801）：`copy(...)` 保留 nodeIdentity 的行为（`asResume`/`withLease` 后节点身份不丢），以及 `NodeIdentity` 对非法 `subTaskId` 的 fail-fast。
