
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
