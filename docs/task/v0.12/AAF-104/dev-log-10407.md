
执行者：AI/developer-service

## 实现文件

| 文件 | 说明 |
|------|------|
| `.../intelligent/shared/event/NodeIdentity.java` | 新增：编排节点身份（subTaskId / kind / roleKey / skillKey），含安全键校验与 source 路径合成 |
| `.../intelligent/agent/model/InvocationContext.java` | 新增 `nodeIdentity` 字段 + 无节点身份场景的次级构造器 |

## 实现决策

- ⏳ #10407 载体已落地 — `NodeIdentity` + `InvocationContext.nodeIdentity`；**待做**：穿透 `AssistantCommand` 填真值、落 `ExecutionEvent`、透出公共事件。（2026-09-01）

> **放 `shared/event` 而非 `assistant/model`**：首版写在 `agent/model` 并 import `TaskBoard.Kind`，这让 L2 反向依赖 L3，违反"上层可调下层、禁止下层调上层"。改放 `shared/event`（`ExecutionEvent` 所在处）并自带 `NodeKind` 枚举，由编排层负责从 `TaskBoard.Kind` 映射。ArchUnit 的 `LayeringTest` 当前 0 条真实规则，抓不到这类问题，只能靠人工守。

> **`subTaskId` 是模型生成的自由字符串，必须当展示数据而非安全键**。`CoordinationPlan.ExecutorAssignment.subTaskId` 来自协调者输出的 plan JSON，`decodeAndValidatePlan` 只校验格式。`NodeIdentity` 因此加了 `SAFE_NODE_KEY` 正则（首字符字母数字，后续仅 `A-Za-z0-9._-`，≤128）——它会进入 AG-UI 的 source 路径，不能带斜杠或控制字符。授权、租户隔离、幂等一律仍用 `executionId`/`tenantId`。

> **两种模式的命名权不同**：动态分解时协调者自由命名；静态 Team 模式下 `subTaskId`/`roleKey`/`skillKey` 必须逐项匹配预定义 worker（`TaskBoard.java:124`）。实现填充时要区分。

> **次级构造器不是兼容层**：`nodeIdentity == null` 是有意义的取值——DIRECT 直答与 Assistant 自身发起的调用本就不在编排板上。次级构造器让这类场景显式表达"无节点"，而编排层派发执行者时必须用完整构造器。这避免了 9 处构造点的无意义 churn，同时保留"漏填即语义错误"的可见性。

> **发现：`AssistantCommand` 是必经的穿透点，无捷径**。原以为可在 `DelegatedTaskCoordinator.context(...)` 就地填充，但执行者的 `InvocationContext` 实际是在 `AssistantApplicationService.agentCommand(...)` 内由 child `AssistantCommand` 构建的（`commands.execute(childCommand)` → 应用服务），而 `AssistantCommand.forSubTask` 只消费 `SubTask` 派生 idempotency/runId、**不保留其身份**。因此必须给 `AssistantCommand` 加 `NodeIdentity` 字段并在 `forSubTask` 填入。这是下一步的主要工作量。

> **DB 无需改动**：`ExecutionEventEntity` 把整个 `ExecutionEvent` 存为 JSONB `event_payload`（表 `ai_task_event`），只提升 7 个字段为索引列。新增字段自动进 JSONB，存量行缺该键反序列化为 null——此前请人类确认的"存量数据兼容"问题自行消解。若将来需按 `roleKey` 做 SQL 聚合，可加 JSONB 表达式索引，属优化非阻塞。

## 验证

- `pnpm nx compile service`：BUILD SUCCESS。
- `pnpm nx test service`：framework 415 / auto-dev 2 / api 241 全绿，无回归。
