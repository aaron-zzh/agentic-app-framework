
执行者：AI/developer-service

## 实现文件

| 文件 | 说明 |
|------|------|
| `.../infrastructure/agentscope/mapping/AgentScopeEventMapper.java` | 31 项 AgentEventType 穷举处置，删除静默 `default` |
| `.../module/ai/agui/AafAguiStreamContext.java` | 新增：per-run 配对状态与全部 AG-UI 事件发射入口 |
| `.../module/ai/agui/AafAguiEventConverter.java` | 新增：单一事件族投影规则的接口 |
| `.../module/ai/agui/AafAguiConverterRegistry.java` | 新增：枚举分派表 + 重复注册拒绝 |
| `.../module/ai/agui/converter/RunLifecycleEventConverter.java` | 新增：run 生命周期与终态（含错误码收敛） |
| `.../module/ai/agui/converter/TextMessageEventConverter.java` | 新增：助手文本三段式 |
| `.../module/ai/agui/converter/PublicEventFallbackConverter.java` | 新增：经公共事件脱敏后按业务事件名二次分派（工具三段式 + CUSTOM） |
| `.../module/ai/agui/AgUiProjector.java` | 瘦身为 facade，248 → 107 行 |

## 实现决策

- ⏳ #10401 部分完成 — 已建立编译器强制的穷举处置矩阵；**待做**：8 项"待映射"类型的实际映射，与 #10403 的 AG-UI 配对契约一并落地。（2026-09-01）
- ✅ #10402 converter registry 结构 — 纯结构重构行为不变，`AgUiProjector` 从 248 行降到 107 行。（2026-09-01）
- ✅ #10401 8 项待映射类型 + #10403 第二增量 — `AgentScopeEventMapper` 补齐全部 8 项，31 项 AgentEventType 无任何 empty 处置。（2026-09-01）

> **配对不变量集中到 context 是这次重构的核心**：`AafAguiStreamContext` 是唯一能构造 message/toolCall/run 终态事件的地方，converter 只能调它的方法。此前这些 `new AguiEvent.Xxx(...)` 散在 projector 的私有方法里，新加一族事件很容易绕过 started/ended 去重与"finish 恰好一次"。现在绕过需要显式改 context，改动可见。

> **registry 拒绝重复注册而非静默覆盖**：AAF 公共事件是安全合同不是插件优先级。静默覆盖意味着某事件的脱敏规则可能被无声替换，而这类问题在生产上表现为信息泄漏或前端信息缺失，很难回溯到注册顺序。构造期直接抛，附冲突双方类名。

> **发现并记录的脱敏缺口**：标准分支（生命周期 / 文本消息）直接读内部 `ExecutionEvent` 的 payload，只有兜底分支经过 `ExecutionEventPublicMapper`。也就是说 `MESSAGE_DELTA` 的正文没走脱敏路径。本次不改——把全部 converter 输入统一为公共事件需要公共事件先暴露 messageId/delta 字段，属配对契约改造的一部分，已在 `AgUiProjector` javadoc 标注并交给 #10403。

> **新增跨 run 复用检测**：`Session` 的 context 懒建，后续调用传入不同 threadId/runId 直接失败。配对状态一旦跨 run 混用，前端会收到属于另一次运行的 START/END 且事后无法区分。原实现的 threadId/runId 每次从事件重取，跨 run 复用不会报错。

> **converter 独立单测推迟**：#10403 会改 converter 输入类型与 messageId 派生规则，现在写单测届时要重写。本次靠既有 `AgUiProjectorTest` 的 8 个用例原地验证行为不变。

> **穷举而非 default 是关键手法**：switch 表达式覆盖枚举全部常量且不写 `default` 时，上游新增常量会让本类**编译失败**。这比"加测试断言枚举数量"更硬——测试可能被跳过或滞后，编译不能。历史上 AAF 只映射 13 项、其余 18 项走 `default -> Optional.empty()`，前端可见信息因此无声缺失；现在每一项都必须有显式决定。运行时若 jar 比编译期新，未匹配常量抛 `MatchException`，被执行适配器收敛为 RUN_FAILED，属 fail-closed，优于静默降级。

> **三类处置的边界**：
> - 安全忽略—思考内容：`THINKING_BLOCK_*` 在本层拦截而非 AG-UI 投影层过滤。投影层过滤只是约定，任何人新加 converter 即可绕过；mapper 层拦截是结构性保证。与 AAF-106 #10603 是同一约束，只实现一次。
> - 安全忽略—二进制块：`DATA_BLOCK_*` 与 `TOOL_RESULT_DATA_DELTA` 不进事件账本，避免把 base64 写进事件表；内容经工具证据与产物引用传递。
> - 不应出现：`SUBAGENT_EXPOSED` 记 WARN。AAF 不启用官方 subagent，收到该事件说明工具面或 builder 配置被改动，是配置漂移信号而非正常流量。

> **8 项刻意不在本任务映射**：`TEXT_BLOCK_END`、`TOOL_CALL_DELTA/END`、`TOOL_RESULT_START/TEXT_DELTA`、`USER_CONFIRM_RESULT`、`EXTERNAL_EXECUTION_RESULT`、`REQUIRE_EXTERNAL_EXECUTION`。原因是它们的落点取决于尚未确定的配对契约——例如 `TEXT_BLOCK_END` 与 `AGENT_RESULT` 都表示"文本结束"，各发一次 `MESSAGE_COMPLETED` 会破坏 AG-UI 的 start/end 配对，必须先把 messageId 改为 `replyId:blockId` 派生才能表达"单执行多文本块"。先映射再改契约等于返工，因此与 #10403 合并实施。当前显式返回 empty 并在注释说明，不是遗漏。

## 验证

- `pnpm nx compile service`：BUILD SUCCESS——**这本身就是 31 项全覆盖的证明**，缺任一常量都会编译失败。
- `pnpm nx test service`：`AgUiProjectorTest` 8 个用例全绿（原地验证重构后配对与 finish 不变量未变）；framework 415 + auto-dev 2 + api 241 全绿，无回归。

---

## #10401 / #10403 第二增量：8 项待映射类型落地（2026-09-01）

执行者：AI/developer-service

### 实现文件

## #10403 剩余范围收尾（2026-09-03）

- ✅ #10403 Step/State/Activity/messageId 补齐 — StepStarted/Finished、StateSnapshot/Delta、ActivitySnapshot/Delta 后端投影落地（复用官方 AguiStateConverter 做 RFC6902 diff），messageId 改 replyId:blockId 派生；顺带修复 nodeIdentity 传播缺口（10 处构造点）与清理 5 个死代码枚举值；webui 配套接入 Activity 卡片订阅与渲染。MessagesSnapshot 判定不做，#10403b 结论落定不复用官方 AguiStreamContext。（2026-09-03）


| 文件 | 说明 |
|------|------|
| `.../infrastructure/agentscope/mapping/AgentScopeEventMapper.java` | 补齐 8 项映射方法；`TEXT_BLOCK_START/DELTA` 补 `replyId`/`blockId` |
| `.../intelligent/shared/event/ExecutionEventType.java` | 新增 7 项：`MESSAGE_BLOCK_COMPLETED`、`TOOL_CALL_ARGS_DELTA/COMPLETED`、`TOOL_RESULT_STARTED/DELTA`、`EXTERNAL_EXECUTION_REQUESTED/SUPPLIED` |
| `.../intelligent/shared/event/publication/AafAiTaskEventRegistry.java` | 同步补齐 7 项 public type 映射（穷举 switch，无 default，编译强制） |

### 实现决策

- **`USER_CONFIRM_RESULT` 不新增类型，复用 `APPROVAL_RESOLVED`** — `replyId` 与最初暂停的 `REQUIRE_USER_CONFIRM`（映射为 `APPROVAL_REQUESTED`）相同，是同一对配对的另一半，新增类型属并行抽象。
- **`MESSAGE_BLOCK_COMPLETED` 与 `MESSAGE_COMPLETED` 严格分离** — 按 harness 落地计划的契约修正：`TEXT_BLOCK_END`（回复内某一文本块结束）与 `AGENT_RESULT`（整次回复结束）各自一次配对，合并会让 AG-UI 收到两次"消息完成"从而破坏 start/end 计数。
- **`TOOL_CALL_ARGS_DELTA/COMPLETED` 与 `TOOL_CALL_COMPLETED/FAILED` 分离** — 前者是"入参已拼齐"（`TOOL_CALL_END`），后者是"工具执行结果成功/失败"（`TOOL_RESULT_END`），是完全不同的时间点，AgentScope 官方事件时序图也是分离的两段（推理阶段 vs 执行阶段）。
- **`TOOL_CALL_DELTA`/`TOOL_RESULT_TEXT_DELTA` 只落长度不落正文** — 前者是工具入参的流式片段（可能含模型从上下文摘取的业务敏感字段值），后者是工具执行的流式文本输出（业务产出）。两者都需要 schema/证据规则脱敏才能安全外发，而脱敏规则属于 AG-UI 投影层职责（#10403 剩余部分），mapper 层没有 schema 可用，因此只保证身份（`toolCallId`/`toolName`/`replyId`）与长度可追踪，仿照 `PromptEnvelopeCaptureMiddleware` 对敏感正文"只落 hash/长度"的既有处置模式。
- **`AGENT_RESULT` 没有加 `replyId`** — 核实 `AgentResultEvent` 源码（`io.agentscope.core.event.AgentResultEvent`）确认它只携带 `Msg result`，没有独立的 `replyId` 字段（这是 31 个事件类型里唯一的例外）。原计划"把 replyId/blockId 写入事件 payload"对该类型不适用，不能凭空捏造字段，`result.getId()` 已经承担消息标识职责。
- **`USER_CONFIRM_RESULT`/`EXTERNAL_EXECUTION_RESULT` 只暴露标识** — 确认结果只给工具名列表与确认计数，`ConfirmResult.toolCall`（用户可能修改过的入参）不出边界；外部执行结果只给 `toolCallId` 列表与结果数量，`ToolResultBlock` 正文不出边界。与 `mapToolResult` 对工具执行证据的既有处置一致，不是新规则。

> **`AafAiTaskEventRegistry.descriptor` 与 mapper 同为无 default 穷举**：新增 `ExecutionEventType` 常量后，若不同批补齐该 switch 的对应分支，整个模块编译失败——这是继续沿用 #10401"穷举强制"手法的自然结果，本次踩到但未额外设计，只是补全。`ExecutionEventPublicMapper.safeData` 有 `default` 分支，新类型暂不设计安全字段，留给 #10403 决定投影契约，避免越权设计对外协议（任务边界要求"纯 aaf-framework 侧，不碰对外协议"）。

> **范围收敛为纯 `AgentScopeEventMapper` 改动**：原计划文档提到"messageId 改用 replyId:blockId 派生"，但派生逻辑属于 AG-UI 投影层（`AafAguiStreamContext`/converter），不在本增量。本增量只完成其共同前置——把 `replyId`/`blockId` 写入 `ExecutionEvent.payload()`，供投影层后续读取，未触碰 `apps/service/aaf-api` 下任何文件。

### 验证

- 按人类要求本次不执行 `pnpm nx compile service` / `pnpm nx test service`（人类手动验证）。
- 人工复核：`AgentScopeEventMapper.map` 与 `AafAiTaskEventRegistry.descriptor` 两处穷举 switch 逐项核对覆盖全部 31 项 / 全部新增 7 项常量，未发现遗漏或重复 case 标签。
- 人工复核：`ExecutionEventPayload` 的 key 校验规则（敏感字段名黑名单）对新增 key 名（`deltaLength`、`toolCallIds`、`resultCount`、`confirmedCount`、`totalCount`、`blockId`）逐一比对，均不匹配 `password/secret/credential/token/authorization/cookie/apikey/systemprompt` 等后缀规则。
- 待 AAF-108 #10801 统一补齐：`compile` + `test` 验证、`AgentScopeEventMapperFailureTest` 之外的显式断言（当前 8 项新映射无专门单测，阶段约束下不新增测试文件，待门禁恢复阶段视需要补进既有测试）。


## #10404 持久 interrupt / resume 闭环

- ✅ 2026-09-03 — developer-service
- 核实纠正：interrupt 触发点从误判的 `APPROVAL_REQUESTED`（core 权限系统，AAF 从未配置，死代码）改为真实生产路径 `AUTHORIZATION_REQUESTED`（`DefaultToolGateway`）
- `RunRequest` 新增 `resume[]`，`AssistantAguiController.run` 按非空拆分 `startRun`/`resumeRun`
- `resumeRun` 拒绝分支不调用 `AssistantApprovalEventService.stream`（要求 `APPROVED`，拒绝后无新事件可续读），直接闭合 run
- webui 同步迁移：删除从未被正确触发过的 `onPaused`/`onApprovalRequired` 死回调与两步式旧调用，改用单次 `resumeAssistantAgUi`
- `HumanApprovalController` 临时端点未删除，留待后续任务确认无其它消费方
