
执行者：AI/developer-service

## 实现文件

| 文件 | 说明 |
|------|------|
| `.../infrastructure/agentscope/mapping/AgentScopeEventMapper.java` | 31 项 AgentEventType 穷举处置，删除静默 `default` |

## 实现决策

- ⏳ #10401 部分完成 — 已建立编译器强制的穷举处置矩阵；**待做**：8 项"待映射"类型的实际映射，与 #10403 的 AG-UI 配对契约一并落地。（2026-09-01）

> **穷举而非 default 是关键手法**：switch 表达式覆盖枚举全部常量且不写 `default` 时，上游新增常量会让本类**编译失败**。这比"加测试断言枚举数量"更硬——测试可能被跳过或滞后，编译不能。历史上 AAF 只映射 13 项、其余 18 项走 `default -> Optional.empty()`，前端可见信息因此无声缺失；现在每一项都必须有显式决定。运行时若 jar 比编译期新，未匹配常量抛 `MatchException`，被执行适配器收敛为 RUN_FAILED，属 fail-closed，优于静默降级。

> **三类处置的边界**：
> - 安全忽略—思考内容：`THINKING_BLOCK_*` 在本层拦截而非 AG-UI 投影层过滤。投影层过滤只是约定，任何人新加 converter 即可绕过；mapper 层拦截是结构性保证。与 AAF-106 #10603 是同一约束，只实现一次。
> - 安全忽略—二进制块：`DATA_BLOCK_*` 与 `TOOL_RESULT_DATA_DELTA` 不进事件账本，避免把 base64 写进事件表；内容经工具证据与产物引用传递。
> - 不应出现：`SUBAGENT_EXPOSED` 记 WARN。AAF 不启用官方 subagent，收到该事件说明工具面或 builder 配置被改动，是配置漂移信号而非正常流量。

> **8 项刻意不在本任务映射**：`TEXT_BLOCK_END`、`TOOL_CALL_DELTA/END`、`TOOL_RESULT_START/TEXT_DELTA`、`USER_CONFIRM_RESULT`、`EXTERNAL_EXECUTION_RESULT`、`REQUIRE_EXTERNAL_EXECUTION`。原因是它们的落点取决于尚未确定的配对契约——例如 `TEXT_BLOCK_END` 与 `AGENT_RESULT` 都表示"文本结束"，各发一次 `MESSAGE_COMPLETED` 会破坏 AG-UI 的 start/end 配对，必须先把 messageId 改为 `replyId:blockId` 派生才能表达"单执行多文本块"。先映射再改契约等于返工，因此与 #10403 合并实施。当前显式返回 empty 并在注释说明，不是遗漏。

## 验证

- `pnpm nx compile service`：BUILD SUCCESS——**这本身就是 31 项全覆盖的证明**，缺任一常量都会编译失败。
- `pnpm nx test service`：framework 415 + auto-dev 2 + api 241 全绿，无回归。
