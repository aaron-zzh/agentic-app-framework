---
level: Practice
layer: Product
purpose: 记录 AAF-120 #12028 至 #12033 Omni Realtime v1 新增 Gherkin 场景的静态验收核对结果与待联调项
status: draft
version: 1.0.0
date: 2026-10-17
author: tester
---

# AAF-120 Omni Realtime v1 静态验收报告

## 方法与范围

- 核对对象为 `requirement.md`「实时会话协议增量（候选技术任务 #12028）」新增的 8 个 Gherkin Scenario，以及 #12028 至 #12033 的当前工作区 diff。
- 本报告仅记录源码和 diff 可定位的实现证据。没有把源码存在等同于动态验收通过。
- 按明确要求，未创建或修改测试代码，未运行测试、编译、lint、`check`、部署或任何验收命令。
- `dev-log.md` 也明确记录本轮未运行编译、测试、lint、check 或部署。因此 developer `pnpm check:affected` 全绿这一 tester 前置门禁尚无执行证据，不能进入动态验收。

## 元信息

| 字段 | 值 |
|---|---|
| 任务 | AAF-120 / #12028–#12033 |
| 需求文档 | `docs/task/v0.13/AAF-120/requirement.md` |
| 测试人员 | tester |
| 核对日期 | 2026-10-17 |
| developer check 状态 | ⚪ 未执行，未提供全绿证据 |
| 执行命令 | 未执行，按本次限制不运行 `pnpm acceptance:affected` |
| 核对方式 | Gherkin 与当前 diff 的静态追踪 |

## 测试文件清单

| 文件 | 类型 | 新增/修改 | 说明 |
|---|---|---|---|
| 无 | 验收/集成 | 无 | 当前 diff 未新增或修改 `*AcceptanceTest.java`、`*IT.java` 或 `*.accept.test.ts(x)`。本次按要求未补测试代码。 |

## AC 覆盖矩阵

| AC | Gherkin 场景 | 静态实现证据 | 对应验收测试 | 静态结论 | 动态状态与待验证项 |
|---|---|---|---|---|---|
| AC-RT-01 | 浏览器以 JWT 和已绑定角色建立实时会话 | `AigcWebSocketConfig` 将 `/ws/realtime-conversation` 绑定到 JWT handler；`JwtRealtimeConversationWebSocketHandler` 仅从 `JwtHandshakeInterceptor.ATTR_USER_ID` 构造调用者；`WebRealtimeConversationContextResolver` 用该用户的个人默认组织/工作区及默认 Assistant 解析 `roleKey`；`DefaultAssistantConversationContextApi` 对指定角色调用 `requireRole`。 | 无 | ⚠️ 静态符合路径存在 | 用有效 JWT、已绑定且启用角色建连，确认唯一 `ready`，并确认实际引擎只取得服务端解析出的 Assistant、指令、工具和知识范围。 |
| AC-RT-02 | 浏览器不能使用默认 Assistant 未绑定的角色 | `WebRealtimeConversationContextResolver` 将 `hello.roleKey` 交给 Assistant 上下文 API；`DefaultAssistantConversationContextApi` 对不存在、未绑定或不可用角色抛出异常；handler 将初始化异常作为 `CONTEXT_REJECTED` 关闭。 | 无 | ⚠️ 静态拒绝路径存在 | 准备未绑定/禁用/不可见角色，确认建连失败，且不会创建或切换到其他 Assistant。 |
| AC-RT-03 | 两个入口只接受各自的认证凭据 | 浏览器入口注册 JWT 握手拦截器，`JwtRealtimeConversationWebSocketHandler` 拒绝 `X-Device-Key` 与 `X-Device-Credential`；设备入口位于 `DeviceSecurityConfig` 的 `/api/device/**`、`ROLE_DEVICE` 安全链下，`DeviceRealtimeConversationWebSocketHandler` 要求两项设备头且拒绝 `Authorization`、Cookie 和 query；设备握手拦截器只复制已认证 `DevicePrincipal`。 | 无 | ⚠️ 静态隔离路径存在 | 用 JWT 访问设备入口、设备凭据访问浏览器入口、缺失或单项设备头、撤销凭据分别握手，检查 HTTP/WS 拒绝语义与关闭码。 |
| AC-RT-04 | 设备主体不被提升为用户主体 | `DefaultDeviceRealtimeContextClient` 重验绑定状态、凭据撤销、场景启用和场景主体；`DeviceRealtimeConversationContextResolver` 创建 `ConversationIdentity(null, ownerUserId, orgId, workspaceId)`；`subjectId` 只保留在 `RealtimeDeviceContext` 与场景变量，未写入 operator/owner 字段；设备 handler 只接收受控上下文。 | 无 | ⚠️ 静态边界符合 | 以不同 `subjectId`、所有者和场景组合建连，审计积分、Assistant、角色及工具执行上下文，确认 `subjectId` 从不充当 userId、角色归属或计费主体。 |
| AC-RT-05 | 客户端不能注入 AI 配置或知识范围 | `AbstractRealtimeConversationWebSocketHandler#parseHello` 和 `#parseControl` 对字段白名单做精确校验；`assistantId`、`instructions`、知识库/文档 ID 等字段会成为未知字段并触发致命协议错误。`DefaultRealtimeConversationSessionFactory` 只接收已解析 caller、hello 与 transport。 | 无 | ⚠️ 静态拒绝路径存在 | 分别在 hello 和后续控制帧注入 `assistantId`、`instructions`、知识库 ID、文档 ID、业务主体/所有者字段，确认关闭且服务端未使用这些值解析上下文或检索范围。 |
| AC-RT-06 | 知识检索在服务端按受控范围执行 | `DefaultRealtimeConversationSession` 仅在服务端引擎发出固定 `knowledge_search` 工具调用时调用 `RealtimeKnowledgeSearchService`；检索服务只接收会话冻结的 `ConversationIdentity`/`ConversationContext`，以 `knowledgeBaseIds`、`PermissionExecutionService.runAsOwner` 和 `OrgContext.runIfAbsent` 调用 `KnowledgeSearchApi`。客户端不能提交 `function_output` 或工具定义。`knowledge_search.completed` 仅暴露状态、耗时和结果数。 | 无 | ⚠️ 静态服务端路径存在 | 建立含受控知识库的会话，验证检索实际只命中冻结范围；尝试客户端函数输出、工具和检索范围注入；用范围外文档验证响应不会把它作为依据。 |
| AC-RT-07 | 语音使用规定的二进制 PCM 格式 | `AbstractRealtimeConversationWebSocketHandler#handleBinaryMessage` 将二进制帧交给会话；`DefaultRealtimeConversationSession#sendReady` 声明上行 16 kHz 单声道、下行 24 kHz 单声道 `pcm_s16le`；`RealtimeWebSocketTransport#sendAudio` 使用 `BinaryMessage`；浏览器示例以 `ArrayBuffer` 发送采样为 16 kHz 的 PCM，并以 24 kHz `AudioContext` 播放。JSON 控制帧白名单没有 PCM 字段，设备入口拒绝 query，未见 MQTT 音频接入。 | 无 | ⚠️ 静态传输路径存在 | 抓取真实 WebSocket 帧，确认上/下行都是 binary 且无帧头；用 JSON/Base64/MQTT 载荷验证拒绝；以实际浏览器和引擎验证采样率、单声道、字节序及播放质量。源码无法证明浏览器最终采样率和供应商音频格式。 |
| AC-RT-08 | Omni Realtime v1 与 cascade v3 独立并存 | 新 v1 代码未包含 fallback、shim 或双协议分支。可是当前 diff 删除了原 `OmniRealtimeWebSocketHandler`、`OmniRealtimeService`、`DashScopeOmniRealtimeService` 与 `/ws/omni-realtime` 注册。仓库中没有可定位的 `cascade v3` 权威协议、入口或回归基线，无法证明这些被删路径不属于或不影响 v3。 | 无 | ❓ 无法静态确认 | 人工确认 v3 的权威入口、调用方和回归基线；随后对该入口做发布前后握手、音频、控制帧和业务调用回归。若 `/ws/omni-realtime` 属于 v3，则当前删除与 AC 冲突。 |

**覆盖统计（本次静态核对）**：

- Gherkin AC 总数：8
- 有静态实现证据：7
- 无法静态确认：1（AC-RT-08）
- 验收/集成测试代码覆盖：0/8
- 动态执行结果：0/8，均未执行

## 发现的问题

### blocker（阻塞动态验收与质量门控）

| 编号 | 问题描述 | 涉及 AC | 退回对象 |
|---|---|---|---|
| B-1 | 当前 diff 没有任何本组 Gherkin 对应的 `*AcceptanceTest.java`、`*IT.java` 或 `*.accept.test.ts(x)`；8 条 AC 均无可执行验收断言。本次限制禁止补测，故不能形成验收覆盖或通过质量门控。 | AC-RT-01～AC-RT-08 | 协调者安排 tester 补验收/集成测试；developer 先提供 `check:affected` 全绿证据 |

### major（需在验收前澄清或联调）

| 编号 | 问题描述 | 涉及 AC | 退回对象 |
|---|---|---|---|
| M-1 | `cascade v3` 没有仓库内权威入口或回归基线；同时 diff 删除旧 `/ws/omni-realtime` 路由和实现。无法静态证明删除范围与 v3 独立，存在回归风险。 | AC-RT-08 | architect / 人工确认 |

### minor（建议改进，可延后）

| 编号 | 问题描述 | 备注 |
|---|---|---|
| m-1 | `dev-log.md` 的 #12032 写“Web UI 按本次范围未改动”，而当前 diff 新增实时示例页并修改两个实时 Web UI 调用点。 | 更正开发记录，使实际变更范围可审计。 |

## 缺陷退回记录

| 问题编号 | 退回对象 | 退回原因 | developer 修复后重跑时间 | 最终状态 |
|---|---|---|---|---|
| B-1 | 协调者 / tester | 本次明确不创建测试代码；待解除限制后补齐 8 条 AC 的验收与跨入口集成测试。 | 未安排 | ⏳ 待处理 |
| M-1 | architect / 人工 | 需明确 cascade v3 的权威入口、调用方与回归基线，确认被删 `/ws/omni-realtime` 不属于 v3。 | 未安排 | ⏳ 待确认 |

## 人工与联调验证清单

- 在 developer 完成并提供 `pnpm check:affected` 全绿结果后，执行两个入口的真实 WebSocket 握手矩阵：有效、缺失、错误、交叉使用与已吊销凭据。
- 用真实 Assistant 绑定数据覆盖 roleKey 已绑定、未绑定、禁用、不可见与缺少默认 Assistant；核验失败时没有备用 Assistant 或默认角色回退。
- 用不同设备、场景、所有者和 `subjectId` 联调，审计 Assistant、工具范围和积分执行主体，排除 `subjectId` 身份提升。
- 使用受控知识库与范围外文档，验证服务端冻结检索范围、客户端无法提交工具/function output/知识筛选，且客户端事件不泄露知识库 ID、文档 ID、查询或原文。
- 抓包检查 WebSocket binary PCM 上行 16 kHz 单声道、下行 24 kHz 单声道，并验证 JSON/Base64/MQTT 不能承载 PCM。
- 人工确定 cascade v3 基线后，对其既有入口、控制帧、音频和调用方做独立回归；仅当该基线不受当前 diff 影响时，AC-RT-08 才能确认。

## 测试环境

- 未启动测试环境。
- 未调用外部 LLM、数据库、MQTT broker 或浏览器自动化。
- 后续后端验收应使用本地真实 PostgreSQL / Neo4j，并 Mock LLM；跨入口 WebSocket、浏览器 PCM 和设备凭据需在联调环境验证。

## 结论

- [ ] 全部 AC 动态通过
- [ ] blocker = 0
- [ ] major ≤ 2
- [x] 静态 AC 矩阵已建立
- [ ] 验收/集成测试覆盖完整

**静态验收结论：不通过。** 七条 AC 有实现路径的静态证据，但所有 AC 均未执行、没有验收测试覆盖，且 cascade v3 独立并存无法静态确认。未执行任何测试、编译、lint、check 或部署。
