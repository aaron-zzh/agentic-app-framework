# 复审报告：AAF-120 #12028–#12033 Omni Realtime v1

审查者：AI/architect  
日期：2026-10-17  
方式：仅静态阅读当前工作区实现；按要求未运行编译、测试、lint、check 或部署。

本轮仅对刚提交的 `response.created` 取消竞态修复做静态复审：核对 `DefaultRealtimeConversationSession` 的音频、`input.text`、`input.image`、设备 `response.create` 和知识检索续答入口，以及 DashScope 的 `responseId` 传播和取消后的输出过滤。#9 不在本轮范围，保留上一轮的测试覆盖遗留结论。

用户明确要求本轮不添加或执行测试、编译、lint、check、部署；以下结论仅来自当前代码的静态阅读，未获得运行时、并发时序或测试执行验证。

## 问题统计

| blocker | major | minor |
|---:|---:|---:|
| 0 | 1 | 0 |

#6 已按静态阅读确认修复。#9 的 developer 单测缺失仍为 major，且本轮按用户要求未添加或执行测试，不能据此推断该修复已通过任何自动化或运行时验证。

## 复审结论

| 检查项 | 结果 | 静态核对结论 |
|---|---|---|
| Query token JWT 握手隔离 | 通过 | `QueryTokenJwtHandshakeInterceptor` 只在 `/ws/realtime-conversation` 注册，拒绝 `Authorization`、`Cookie`、空 token、重复或其他 query 参数；handler 也重复验证该约束。`/ws/asr`、IM、通知等其它端点继续使用原 `JwtHandshakeInterceptor`。 |
| device 角色三层回退与空 allowlist | 通过 | 解析顺序为设备 role、Assistant 默认 role（显式传 `null`）、场景默认 role；不再向 `LinkedHashSet` 放入 `null`。每个候选经 Assistant 解析和场景 allowlist 验证，空 allowlist 明确拒绝全部候选。 |
| response 与 knowledge generation 栅栏 | 通过（仅静态） | 取消使 `responseStartAllowed` 变为 `false`，但保留取消前的 `expectedResponseGeneration`。音频、文本、图片和设备 `response.create` 均拒绝在此等待期启动新一轮；知识检索 completion 同时校验 response generation、search generation、call ID 与活跃状态。旧 `response.created` 只能消费它发起时的 expected generation，取消已推进 generation，故被标记取消而不成为活跃响应。 |
| SerialExecutor close | 通过 | `close()` 在锁内置 closed、清空排队任务并 shutdown 执行器；后续 `execute` 直接丢弃。关闭可由 drain 中当前任务调用，不会等待自身；drain 完成当前清理后退出。 |
| DashScope responseId 传播 | 通过（仅静态） | adapter 从顶层 `response_id` 或嵌套 `response.id` 提取 ID，并写入 session 实际消费的 `response.created`、文本/音频 delta、音频完成、响应完成和 function-call 事件。session 以当前 active response ID 过滤文本、音频、完成及 function-call；取消会使旧 ID 进入取消集合并清空 active ID，因此这些迟到输出不会转发。未消费的 `response.text.done` 与 `response.audio_transcript.done` 不会发送到 v1 客户端。 |
| 前端 ready guard | 通过 | 三个消费者统一导入 `parseOmniRealtimeReadyEvent`，验证 v1 protocol、server endpointing、上下行 `pcm_s16le`、16/24 kHz 与单声道。非法 ready 时，两个页面停止采集与播放后关闭 WebSocket；voice adapter 清理资源、以 error 结束并关闭 WebSocket。 |
| `/api/device/voice/session` cascade v3 | 通过 | `DeviceVoiceWebSocketConfig` 仍只注册 `/api/device/voice/session` 并使用 `DeviceVoiceSessionHandler`；v1 生产代码未引用该 handler 或旧路由。 |

## 问题列表

| # | 原严重级别 | 文件 / 符号 | 复审结论 | 修复状态 |
|---:|---|---|---|---|
| 1 | blocker | `DefaultRealtimeConversationSession`；`Executors` import | 已导入 `java.util.concurrent.Executors`，`knowledgeExecutor` 与内部 `SerialExecutor` 的引用均有对应类型导入。静态构建缺陷已消除。 | 已修复 |
| 2 | blocker | `DashScopeRealtimeConversationEngine.openConversation` | `OmniRealtimeParam.builder()` 已使用 `apiKey(apiKey)`，不再使用错误的 `apikey(...)`。 | 已修复 |
| 3 | blocker | `DeviceRealtimeConversationContextResolver.resolveRole` | 设备 role、默认 Assistant role 与场景默认 role 按顺序直接尝试；默认候选以 `null` 传给 API，不再写入禁止 null 的集合。 | 已修复 |
| 4 | blocker | `DeviceRealtimeConversationContextResolver.allowed` | allowlist 非空且包含解析出的 role 才可通过；空集合不会放宽角色边界。 | 已修复 |
| 5 | major | `QueryTokenJwtHandshakeInterceptor`；`AigcWebSocketConfig`；`JwtRealtimeConversationWebSocketHandler` | 专用 interceptor 只解码唯一的 `token` query，拒绝 Cookie 与 Authorization。`AigcWebSocketConfig` 仅为 realtime endpoint 挂载它，其他 JWT WebSocket 端点保持原 interceptor；handler 同步拒绝设备头、Cookie、Authorization 和其它 query。 | 已修复 |
| 6 | major | `DefaultRealtimeConversationSession.handleAudio`、`handleText`、`handleImage`、`handleResponseCreate`、`completeKnowledgeSearch`、`handleEngineResponseCreated` | **本轮限定范围内已修复（仅静态）。** 取消尚未收到 provider `response.created` 时，`invalidateResponse()` 增加 `responseGeneration` 并保持旧 `expectedResponseGeneration`，同时关闭 `responseStartAllowed`。文本、图片和设备 create 经 `requireIdleResponse` 或显式 expected 检查拒绝；音频在此“取消等待”状态拒绝。服务端连续 PCM 在 `responseStartAllowed == true` 时仍可继续送入同一 VAD 输入，不属于新一轮入口。知识检索续答先校验捕获的 response/search generation、call ID 与 `RESPONDING`，通过后才 `beginResponse()` 并登记新的 expected generation。旧 `response.created` 的 expected generation 与已推进的 generation 不相等，被加入取消集合；当前轮 response.created 才能建立 active ID。 | 已修复（未运行验证） |
| 7 | major | `DefaultRealtimeConversationSession.handleKnowledgeCall`、`completeKnowledgeSearch`、`cancelKnowledgeSearch` | 异步检索捕获 response generation 和 search generation，并在 completion 校验两者、活跃 call ID 和响应状态。取消会推进 search generation、清除活跃 call ID、取消并清空 future，因此忽略中断的旧任务无法向新响应提交 function output。 | 已修复 |
| 8 | major | `apps/webui/src/lib/voice/omni-realtime-protocol.ts`；三个 ready 消费者 | 已抽取统一 parser。三个消费者均先调用它再开始采集，并在验证失败时执行关闭/资源清理，不会因错误服务端或格式合同不符而发送 raw PCM。 | 已修复 |
| 9 | major | Omni v1 新增/修改的服务端及前端逻辑 | **不在本轮复审范围。** 维持上一轮“缺少覆盖”为 major 的结论。本轮用户明确要求不添加或执行测试，因而没有测试新增、测试执行、编译、lint、check 或运行时验证结果可证明本轮修复的行为。 | 遗留 major（未验证） |
| 10 | minor | `DefaultRealtimeConversationSession.SerialExecutor` | executor 现在实现显式 close：关闭后清空队列、拒绝新任务并 shutdown 虚拟线程执行器。session 关闭路径在其它资源释放后调用该 close，满足关闭后 callback 不再排入的要求。 | 已修复 |
| 11 | minor | `RealtimeWebSocketTransport.send`；`AbstractRealtimeConversationWebSocketHandler` | 发送失败不再静默吞掉：记录脱敏的 session、消息类型和异常类，幂等关闭 transport，并通过 `setSendFailureHandler` 进入 handler 的 `closeSession`。即使 `ready` 发送失败早于 handler 注册回调，注册时也会检测 `sendFailed` 并立即执行清理。 | 已修复 |

## 对称性检查

| # | 检查项 | 状态 | 说明 |
|---:|---|---|---|
| 1 | 生产者 vs 消费者 | 通过 | 服务端 ready 合同的固定协议、endpointing、PCM 编码、采样率和通道数，已由三个前端消费者共同的 parser 校验。 |
| 2 | 创建 vs 删除 | 通过 | session 关闭时清理知识 future、tool scope、conversation、SerialExecutor 与 handler registry；发送失败也进入同一 session 清理路径。 |
| 5 | 监听器注册 vs 注销 | 通过（限定范围、仅静态） | frontend abort listener 使用 `{ once: true }` 且验证失败会清理媒体资源。响应 provider 事件在取消后的 expected generation 等待期会阻止新一轮入口，迟到 `response.created` 也会因 generation 不匹配被取消。 |
| 6 | 资源申请 vs 释放 | 通过（限定范围、仅静态） | SerialExecutor、knowledge executor 与 tool scope 有关闭路径。取消推进 response/search generation，旧知识检索 completion 与旧 provider response 均不能写入后继响应或输出。 |
| 8 | 认证 vs 鉴权 | 通过 | 浏览器只接收 token query JWT，设备入口只接收设备凭据头；设备 role 候选全部受非空场景 allowlist 约束。 |
| 9 | 成功路径 vs 错误路径 | 通过 | transport 发送失败有脱敏日志、幂等 transport close 和 session close 回调；初始化期的 ready 发送失败在回调注册后补偿清理。 |
| 13 | 已有模式 vs 新建抽象 | 通过 | 共享 session、专用 endpoint interceptor 与前端单一 ready parser 均符合本任务已批准设计；未发现 v1 对 cascade v3 的改动或引用。 |

## 结论

**本轮限定范围静态复审通过；整体仍保留 #9 的 major。**

`DefaultRealtimeConversationSession` 对取消后仍等待 provider `response.created` 的状态保持旧 `expectedResponseGeneration`，并以 `responseStartAllowed == false` 识别该等待期。`input.text`、`input.image` 与设备 `response.create` 拒绝任何 pending expected generation；音频在取消等待期同样拒绝，而仍允许同一轮服务端 VAD 的连续 PCM（`responseStartAllowed == true`）继续输入。知识检索续答在提交 function output 或启动续答前，已校验响应代际、检索代际、call ID 与 `RESPONDING` 状态。

`handleEngineResponseCreated` 只接纳与当前 `responseGeneration` 相等的 expected generation。取消已推进 generation，因此取消前迟到的 `response.created` 会进入 `cancelledResponseIds`，不会成为 active response。DashScope 从顶层 `response_id` 或嵌套 `response.id` 向会话实际消费的响应事件传播 ID；会话只转发 active response ID 的文本、音频、音频完成、响应完成与 function-call 事件。取消后，旧 ID 被标记取消并清空 active ID，迟到输出被过滤。

用户明确要求本轮不添加或执行测试、编译、lint、check 或部署，且均未执行。因此这是代码阅读结论，未验证并发调度、供应商回调顺序或运行时行为。#9 的测试覆盖 major 不在本轮范围，继续保留；后续如需交付，应由 developer 补齐对应测试并按流程完成自验证后再复审。
