# AgentScope 示例说明

> 位置：`com.xuejiai.aaf.module.examples.agentscope`
> 启用条件：`aaf.examples.agentscope.enabled=true`（默认关闭）

## 启用方式

```yaml
aaf:
  examples:
    agentscope:
      enabled: true
      mcp:
        server-url: ${MCP_SERVER_URL:}          # 可选，⑦ MCP 示例
      langfuse:
        public-key: ${LANGFUSE_PUBLIC_KEY:}     # 可选，② Tracing 示例
        secret-key: ${LANGFUSE_SECRET_KEY:}
        endpoint: https://cloud.langfuse.com/api/public/otel/v1/traces

spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}             # 必填
```

## 示例总览

共 10 个示例，覆盖 AgentScope Java SDK 的全部核心能力。

| # | AgentScope 能力 | REST 接口 | AG-UI 接口 | 外部依赖 |
|---|----------------|-----------|-----------|---------|
| ① | `ReActAgent` 基础对话 | `POST /basic-chat` | `/agui/run/basicChatAgent` | — |
| ② | `@Tool` 工具调用<br>**+ Hook/Tracing** | `POST /tool-calling` | `/agui/run/toolCallingAgent` | 可选 Langfuse |
| ③ | `subAgent` Supervisor 委托 | `POST /supervisor` | `/agui/run/supervisorAgent` | — |
| ④ | 多 Agent 串联 Pipeline | `POST /pipeline` | `/agui/run/sqlGeneratorAgent` | — |
| ⑤ | `MsgHub` 广播协作 | `POST /debate` | — | — |
| ⑥ | `JsonSession` 持久化记忆 | `POST /session-chat` | — | — |
| ⑦ | `McpClientBuilder` MCP 工具 | `POST /mcp-tool` | `/agui/run/mcpToolAgent` | MCP Server |
| ⑧ | `Knowledge` + `RAGMode` RAG | `POST /rag-chat` | `/agui/run/ragChatAgent` | DashScope Embedding |
| ⑨ | `PlanNotebook` 任务规划 | `POST /plan-chat` | `/agui/run/planAgent` | — |
| ⑩ | `RealtimeTTSModel` 语音合成 | `POST /tts` | — | DashScope TTS |

所有 REST 接口前缀：`/api/examples/agentscope`

---

## 各示例详解

### ① 基础聊天 — `ReActAgent`

```json
POST /api/examples/agentscope/basic-chat
{ "input": "你好，介绍一下自己" }
```

代码：`AgentScopeExampleConfig#basicChatAgent`

---

### ② 工具调用 + Hook/Tracing — `@Tool` + `Hook` + `TracerRegistry`

**融合三个能力：工具调用 + Hook 机制 + Langfuse Tracing。**

执行工具调用时，`ObservationHook` 在日志中打印完整链路（即 Tracing）：
```
[Hook:PreCall] → [Hook:PreReasoning] → [Hook:PostReasoning] LLM决定调用工具
→ [Hook:PreActing] 执行工具 → [Hook:PostActing] 工具完成 → [Hook:PostCall] Token用量
```

配置 Langfuse 后，所有链路数据自动上报（无需修改 Agent 代码）：
```yaml
aaf.examples.agentscope.langfuse.public-key: lf-xxx
aaf.examples.agentscope.langfuse.secret-key: sk-xxx
```

```json
POST /api/examples/agentscope/tool-calling
{ "input": "计算 123 + 456，并告诉我现在北京时间" }
```

Hook 事件表：

| 事件 | 时机 | 典型用途 |
|------|------|---------|
| `PreCallEvent` | Agent.call() 入口 | 输入审计、限流 |
| `PreReasoningEvent` | LLM 推理前 | 注入上下文、过滤敏感信息 |
| `PostReasoningEvent` | LLM 推理后 | HITL 工具确认（`stopAgent()`） |
| `PreActingEvent` | 工具执行前 | 参数校验、权限检查 |
| `PostActingEvent` | 工具执行后 | 结果审计 |
| `PostCallEvent` | Agent.call() 出口 | **Token 计量**（`getChatUsage()`） |
| `ErrorEvent` | 任意阶段异常 | 错误监控 |

代码：`AgentScopeExampleConfig#toolCallingAgent` + `ObservationHook` + `TokenMeteringHook` + `initTracing()`

---

### ③ Supervisor 多智能体 — `subAgent`

```json
POST /api/examples/agentscope/supervisor
{ "input": "帮我查一下明天下午有没有空，安排一个会议" }
```

代码：`AgentScopeExampleConfig#supervisorAgent` → `calendarSubAgent`

---

### ④ Pipeline 顺序管道 — 多 Agent 串联

```json
POST /api/examples/agentscope/pipeline
{ "input": "查询最近 7 天注册的用户数量" }
```

返回：`{ "input": "...", "sql": "SELECT COUNT(*) ...", "score": "0.92" }`

代码：`PipelineExampleConfig` + `AgentScopeExampleService#pipelineRun`

---

### ⑤ MsgHub 辩论 — `MsgHub` 广播

```json
POST /api/examples/agentscope/debate
{ "topic": "AI 会取代程序员吗", "rounds": 2 }
```

代码：`AgentScopeExampleService#debate`（动态创建 Agent + MsgHub）

---

### ⑥ Session 持久化 — `JsonSession`

```json
POST /api/examples/agentscope/session-chat
{ "sessionId": "user-123", "input": "我叫小明" }
```

Session 文件：`~/.aaf/examples/sessions/`

代码：`AgentScopeExampleService#sessionChat`

---

### ⑦ MCP 工具集成 — `McpClientBuilder`

**需要外部 MCP Server。** 工具由 Server 动态提供，无需修改代码即可扩展工具集。

```yaml
aaf.examples.agentscope.mcp.server-url: http://localhost:3000/sse
```

```json
POST /api/examples/agentscope/mcp-tool
{ "input": "用 MCP 工具帮我完成任务" }
```

支持传输方式：SSE（`sseTransport`）、StreamableHTTP（`streamableHttpTransport`）、Stdio（`stdioTransport`）

代码：`McpExampleConfig`

---

### ⑧ RAG 知识库聊天 — `Knowledge` + `RAGMode.GENERIC`

**需要外部服务：DashScope Embedding API（text-embedding-v3）。**

Generic RAG 模式：每次 LLM 推理前自动检索知识库，将相关文档注入 system prompt。

```json
POST /api/examples/agentscope/rag-chat
{ "input": "AAF 框架有哪些核心能力？" }
```

与基础聊天的区别：回答基于知识库内容，减少幻觉。

RAG 两种模式：
- `RAGMode.GENERIC`：自动注入，无需 Agent 主动调用（本示例）
- `RAGMode.AGENTIC`：Agent 通过 `retrieve_knowledge` 工具主动检索

代码：`AgentScopeExampleConfig#ragChatAgent` + `exampleKnowledge`（InMemoryStore + DashScope Embedding）

---

### ⑨ Plan 任务规划 — `PlanNotebook`

Agent 将复杂任务分解为子任务，逐步执行并追踪进度。适合多步骤复杂任务。

```json
POST /api/examples/agentscope/plan-chat
{ "input": "帮我设计一个用户登录功能，包括前端页面、后端接口和数据库设计" }
```

PlanNotebook 工作流：
1. Agent 调用 `create_plan` 创建计划（含子任务列表）
2. 每次推理前自动注入 `<system-hint>` 提示当前进度
3. Agent 按子任务执行，调用 `finish_subtask` 标记完成
4. 全部完成后调用 `finish_plan`

提供 10 个工具函数：`create_plan`、`finish_subtask`、`finish_plan` 等。

代码：`PlanExampleConfig#planAgent`（`PlanNotebook.builder().storage(InMemoryPlanStorage)...`）

---

### ⑩ Realtime TTS 语音合成 — `RealtimeTTSModel`

**需要外部服务：DashScope API（qwen3-tts-flash-realtime 模型）。**

WebSocket 流式 TTS，返回 WAV 音频字节。

```bash
curl -X POST /api/examples/agentscope/tts \
  -H "Content-Type: application/json" \
  -d '{"input": "你好，欢迎使用 AAF 框架"}' \
  --output output.wav
```

与普通 TTS 的区别：
- 普通 TTS：一次性输入，HTTP + SSE 返回
- Realtime TTS：WebSocket 流式输入输出，支持 `push(text)` 增量推送，适合"边生成边播放"

代码：`RealtimeExampleConfig#exampleRealtimeTts` + `AgentScopeExampleService#textToSpeech`

---

## AG-UI 流式访问

所有 `ReActAgent` Bean 自动注册到 AG-UI 端点：

```
POST /agui/run/basicChatAgent      → ① 基础聊天
POST /agui/run/toolCallingAgent    → ② 工具调用（含 Hook）
POST /agui/run/calendarSubAgent    → ③ 日历子 Agent
POST /agui/run/supervisorAgent     → ③ Supervisor（A2A 对外主 Agent）
POST /agui/run/sqlGeneratorAgent   → ④ SQL 生成
POST /agui/run/sqlRaterAgent       → ④ SQL 评分
POST /agui/run/mcpToolAgent        → ⑦ MCP 工具
POST /agui/run/ragChatAgent        → ⑧ RAG 聊天
POST /agui/run/planAgent           → ⑨ Plan 规划
```

> ⑤⑥⑩ 动态创建 Agent 或非 Agent 类型，无 AG-UI 端点。

---

## 代码结构

```
agentscope/
├── config/
│   ├── AgentScopeExampleConfig.java   # ①②③⑧ Agent Bean + Langfuse Tracing + RAG Knowledge
│   ├── PipelineExampleConfig.java     # ④ Pipeline Agent Bean
│   ├── McpExampleConfig.java          # ⑦ MCP Client + Agent Bean
│   ├── PlanExampleConfig.java         # ⑨ PlanNotebook + Agent Bean
│   └── RealtimeExampleConfig.java     # ⑩ RealtimeTTSModel Bean
├── controller/
│   └── AgentScopeExampleController.java  # REST 接口（①-⑩）
├── service/
│   └── AgentScopeExampleService.java     # 业务逻辑（⑤⑥⑦⑧⑨⑩）
├── tools/
│   ├── MathTools.java                 # ② 数学计算工具（@Tool）
│   ├── CalendarTools.java             # ②③ 日历工具（@Tool）
│   └── ObservationHook.java           # ② Hook/Tracing 观察（7种事件）
└── README.md
```
