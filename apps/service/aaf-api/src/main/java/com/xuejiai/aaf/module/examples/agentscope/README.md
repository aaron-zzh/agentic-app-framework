# AgentScope 示例说明

> 位置：`com.xuejiai.aaf.module.examples.agentscope`
> 启用条件：`aaf.examples.agentscope.enabled=true`（默认关闭，避免无 API Key 时启动失败）

## 示例覆盖范围

参考 `tmp/agent/agentscope-samples`（Python）和 `tmp/agent/agentscope-java`（Java）的核心示例。

| # | 特性 | Python 参考 | REST 接口 | AG-UI |
|---|------|------------|-----------|-------|
| ① | 基础聊天（ReActAgent） | `chatbot/main.py` | `POST /basic-chat` | `POST /agui/run/basic` |
| ② | 工具调用（@Tool/@ToolParam） | `ToolCallingExample` | `POST /tool-calling` | `POST /agui/run/tool` |
| ③ | Supervisor 多智能体（subAgent 委托） | `SupervisorConfig` | `POST /supervisor` | — |
| ④ | Pipeline 顺序管道（多 Agent 串联） | `multiagent_conversation` | `POST /pipeline` | — |
| ⑤ | MsgHub 辩论（广播消息，多 Agent 协作） | `multiagent_debate/main.py` | `POST /debate` | — |
| ⑥ | Session 持久化（JsonSession save/load） | `SessionExample.java` | `POST /session-chat` | — |
| ⑦ | AG-UI 流式（SSE 事件流） | `agui/AgentConfiguration.java` | — | `POST /agui/run/{agentId}` |

所有 REST 接口前缀：`/api/examples/agentscope`

## REST Controller vs AG-UI 协议

### REST Controller（当前示例）

```
POST /api/examples/agentscope/basic-chat
{ "input": "你好" }

→ 阻塞等待 Agent 完成
→ 返回 { "code": 0, "data": "你好！有什么可以帮你的？" }
```

**适用场景**：后端服务间调用、批处理、不需要流式展示的场景。

### AG-UI 协议（流式 SSE）

```
POST /agui/run/basic
Content-Type: application/json

→ SSE 事件流（实时推送）：
data: {"type":"RUN_STARTED","runId":"xxx"}
data: {"type":"TEXT_MESSAGE_START","runId":"xxx","messageId":"yyy"}
data: {"type":"TEXT_MESSAGE_CONTENT","runId":"xxx","messageId":"yyy","delta":"你"}
data: {"type":"TEXT_MESSAGE_END","runId":"xxx","messageId":"yyy"}
data: {"type":"RUN_FINISHED","runId":"xxx"}
```

**适用场景**：前端实时流式渲染，与 `@assistant-ui/react` 直接集成。

## AG-UI 在 AAF 中的实现层次

```
前端 @assistant-ui/react
        ↓ POST /agui/run/{agentId}
agentscope-agui-spring-boot-starter（自动配置，已引入）
        ↓ AguiAgentRegistry（由 exampleAguiAgentRegistryCustomizer 注册）
AgentScope ReActAgent（basic / tool）
```

### 已有代码

| 文件 | 位置 | 说明 |
|------|------|------|
| `AgUiEvent` | `module.system.chat.agui` | AG-UI 事件类型定义（AAF 自研，基于 Spring AI） |
| `AgUiStreamHandler` | `module.system.chat.agui` | Spring AI `Flux<ChatResponse>` → SSE 转换 |
| `AgentScopeAguiAdapter` | `framework.intelligent.agent.agentscope` | AgentScope → AG-UI 适配骨架（TODO） |

两套实现**不冲突**：路径不同（`/api/chat/agent/run` vs `/agui/run`），底层模型不同（Spring AI vs AgentScope）。

## 接口示例

### ⑤ MsgHub 辩论

```json
POST /api/examples/agentscope/debate
{
  "topic": "AI 会取代程序员吗",
  "rounds": 2
}
```

返回：
```json
{
  "topic": "AI 会取代程序员吗",
  "transcript": [
    { "round": "1", "正方": "...", "反方": "..." },
    { "round": "2", "正方": "...", "反方": "..." }
  ],
  "conclusion": "主持人总结..."
}
```

### ⑥ Session 持久化

```json
POST /api/examples/agentscope/session-chat
{ "sessionId": "user-123", "input": "我叫小明" }

// 第二次调用，Agent 记得上次对话
POST /api/examples/agentscope/session-chat
{ "sessionId": "user-123", "input": "我叫什么名字？" }
```

## 启用示例

在 `application-dev.yaml` 或 `.env` 中添加：

```yaml
aaf:
  examples:
    agentscope:
      enabled: true

spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
```
