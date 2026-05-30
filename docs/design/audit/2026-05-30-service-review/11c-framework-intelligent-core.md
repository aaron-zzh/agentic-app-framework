# 11c framework 智能核心：工具权限 · HITL · 置信度门控（优先级 2）

> 覆盖：`engine/tool/ToolCallDispatcher`、`intelligent/agent/ToolPermissionGuard`、`intelligent/assistant/{AssistantPermissionEvaluator,HumanApprovalService,PermissionScope}`、`intelligent/core/{confidence,token}`。
> 承接 [11 执行计划](11-followup-review-plan.md) 优先级 2 的 **B10 关键项**。审查人 AI/architect · 2026-05-30。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B15 | 🔴 | `engine/tool/ToolCallDispatcher#dispatch` ← `module/tool/ToolService#invoke:63` | 派发器暴露 **public 无鉴权 `dispatch()`**（注释"无权限检查，Agent 内部调用"），但 `ToolService.invoke` 直接调用它，仅做禁用/注册检查→REST 工具调用**绕过全部权限/风险门控**（不经 ToolPermissionGuard / permissionChecker / HITL），可执行任意 HIGH/CRITICAL 工具。B10 框架层根因，调用链已确认 | `dispatch()` 收敛为包级私有或并入 `dispatchWithPermission`；所有外部入口强制走带鉴权重载 + 注入 OperatorContext 主体 |
| M34 | 🟠 | `assistant/AssistantPermissionEvaluator#evaluateToolCall/evaluateOperation` | 未找到 AssistantDefinition 时 `return EvalResult.granted()`（fail-open，注释"降级为不限制"）→未知/伪造 assistantId 获**不受限权限**（同 M12 fail-open 模式） | 未找到定义应 fail-closed（拒绝或转 HITL），不得默认放行 |
| M35 | 🟠 | `assistant/HumanApprovalService#resolve/request` | `resolve(requestId,…)` **无授权校验**，任何持 requestId 者可 APPROVED/REJECTED；`requestId = sessionId + ":" + System.nanoTime()` 低熵可预测→**伪造/越权审批**绕过 HITL | resolve 校验当前用户=请求 owner；requestId 用安全随机（UUID/SecureRandom） |
| M36 | 🟠 | `assistant/HumanApprovalService`（`pending`/`results` 为内存 Map） | HITL 仅内存态 + `// TODO: WebSocket/SSE 推送`未实现→用户**从不被通知**、多实例/重启即失效；导致 `ConfidenceGate` 的 `PAUSE_FOR_HUMAN` 与 `OverLimitAction.ASK` 端到端不可达，人工门控形同虚设 | 审批落库 + 实现推送通道；跨实例共享（DB/Redis）；补端到端测试覆盖 PAUSE_FOR_HUMAN |
| m24 | 🟡 | `assistant/PermissionScope#defaults` | 默认 `allowedTools=null`（`isToolAllowed` 恒 true）→默认 scope **放行全部工具**，偏宽松 | 默认收紧为空白名单或最小集，按需放开 |

## 良好实践

- `ToolPermissionGuard` 用装饰器包装 ToolCallback，Agent 内部调用统一过权限；先委托模型（assistantPermEval）再会话级 permissionChecker，分层清晰。
- `DefaultConfidenceGate` 四象限逻辑正确（置信度×可验证性），纯函数可测；`ConfidenceGate` 集成点文档完整。
- `TokenMeteringService.isQuotaExceeded` 按月用量对配额，`quota<=0` 视为无限制；Hook 异步记录、低优先级不阻塞主流程。

## 对称性 / 一致性提示

- 认证 vs 鉴权（清单#8）：`dispatch()` 无鉴权旁路（B15）+ AssistantPermissionEvaluator fail-open（M34）+ ToolPermissionGuard `meta==null` 默认放行（复用 [04 区 M12](04-ai-engines-and-tools.md)）——工具调用链存在三处独立放行口。
- 状态变更 vs 通知（清单#7）：HITL request 后无通知（M36），审批闭环断裂。
- 成功路径 vs 错误路径：低置信 `PAUSE_FOR_HUMAN` 分支因 HITL 不可用而无人服务（M36 联动）。

## 待确认

- `ToolController`（08/B10）实际是否经 `ToolService.invoke`→确认 B15 暴露面（结合 [10 鉴权矩阵](10-authorization-matrix.md)）。
- 各 ConfidenceGate 集成点（AgentDispatcher/CognitiveCycleExecutor/ResultAggregator/ExecutionDispatcher）是否真正消费 `PAUSE_FOR_HUMAN` 并阻断——优先级 3/5 复核。
