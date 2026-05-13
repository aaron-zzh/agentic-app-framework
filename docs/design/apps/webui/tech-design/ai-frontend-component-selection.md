---
level: Practice
layer: Product
purpose: AI 前端组件选型：assistant-ui + AG-UI 协议
status: draft
version: 3.0.0
date: 2026-05-10
author: AaronZZH
---

# AI 前端组件选型

## 一、选型结论

**assistant-ui** 作为 AI 对话层的核心方案，通过 AG-UI 协议对接后端。

```text
┌─────────────────────────────────────────────────────────┐
│  后端：AgentScope + Spring AI                            │
│  输出：AG-UI Protocol SSE 事件流                         │
└────────────────────────┬────────────────────────────────┘
                         │ SSE (AG-UI)
                         ▼
┌─────────────────────────────────────────────────────────┐
│  前端：assistant-ui                                      │
├─────────────────────────────────────────────────────────┤
│  协议层：@assistant-ui/react-ag-ui（AG-UI 适配）         │
│  核心层：@assistant-ui/react（runtime + primitives）     │
│  编辑器：@assistant-ui/react-lexical（Lexical 集成）     │
│  渲染层：组件源码（shadcn 模式，可自由改造）             │
└─────────────────────────────────────────────────────────┘
```

## 二、方案对比与决策依据

### 2.1 候选方案

| 方案 | 说明 | 结论 |
|------|------|------|
| **assistant-ui** | shadcn 模式组件库，内置 AG-UI 适配 + runtime + Lexical | ✅ 采用 |
| CopilotKit + Ant Design X | CopilotKit hooks 管数据，Ant Design X 管 UI | ❌ 两层拼凑，集成成本高 |
| CopilotKit + 自研 UI | CopilotKit hooks + shadcn/ui 自研 | ❌ assistant-ui 已实现同等能力 |
| AI SDK + 自研 | Vercel AI SDK useChat + 全部自研 | ❌ Data Stream Protocol 不支持多 Agent/状态同步 |
| 全部自研 | AG-UI 解析 + hooks + UI 全写 | ❌ 工作量 ~12 周，不必要 |

### 2.2 选择 assistant-ui 的理由

1. **一体化方案**：协议适配 + 状态管理 + UI 组件一站式解决，不需要拼凑多个库
2. **AG-UI 原生支持**：`@assistant-ui/react-ag-ui` 直接对接 AG-UI 后端
3. **Lexical 原生集成**：`@assistant-ui/react-lexical`，AAF 需要 Lexical 编辑器
4. **源码可控**：shadcn 模式，组件代码在项目里，可自由改造
5. **视觉风格**：类 ChatGPT/Claude/Cursor，符合 AI 原生应用审美
6. **Primitives 架构**：ThreadPrimitive、MessagePrimitive 等原语，组合灵活

### 2.3 不选其他方案的理由

**AI SDK**：
- Data Stream Protocol 与 AG-UI 互斥，不能混用
- 不支持多 Agent、状态同步、人工审批等 Agent 交互场景
- Spring AI 也不原生支持 Data Stream Protocol，同样需要适配层

**CopilotKit**：
- 与 assistant-ui 能力重叠，但 UI 是 npm 黑盒（不如 shadcn 模式灵活）
- 无 Lexical 集成
- 需要额外搭配 Ant Design X 做 UI，增加依赖

**Ant Design X**：
- 纯 UI 组件，无协议适配、无状态管理，数据层全部自己接
- 可作为 UI 改造参考（气泡样式、打字机效果、ThoughtChain），但不作为主方案

## 三、架构详情

### 3.1 依赖清单

| 包 | 用途 | 必须 |
|----|------|------|
| `@assistant-ui/react` | 核心 runtime + primitives | ✅ |
| `@assistant-ui/react-ag-ui` | AG-UI 协议适配 | ✅ |
| `@assistant-ui/react-lexical` | Lexical 编辑器集成 | ✅ |
| `@assistant-ui/react-markdown` | Markdown 渲染 | ✅ |
| 组件源码（通过 CLI 注入） | Thread、Composer、ToolFallback 等 | ✅ |
| `@ant-design/x-markdown` | 备选：流式 Markdown 高性能渲染 | ⚠️ 按需 |

### 3.2 后端适配层

Spring WebFlux Controller，将 AgentScope 事件映射为 AG-UI SSE：

| AgentScope 内部事件 | AG-UI 事件 |
|---|---|
| Agent 开始执行 | `RUN_STARTED` |
| 文本 token 输出 | `TEXT_MESSAGE_START` → `TEXT_MESSAGE_CONTENT` → `TEXT_MESSAGE_END` |
| 工具调用 | `TOOL_CALL_START` → `TOOL_CALL_ARGS` → `TOOL_CALL_END` |
| 状态变更 | `STATE_DELTA` |
| 需要人工审批 | `INTERRUPT` |
| 执行步骤切换 | `STEP_STARTED` / `STEP_FINISHED` |
| Agent 执行完成 | `RUN_FINISHED` |

### 3.3 前端使用方式

```tsx
import { AssistantRuntimeProvider } from "@assistant-ui/react";
import { useAgUiRuntime } from "@assistant-ui/react-ag-ui";
import { Thread } from "@/components/assistant-ui/thread";

export default function ChatPage() {
  const runtime = useAgUiRuntime({
    api: "/api/agent/run",
  });

  return (
    <AssistantRuntimeProvider runtime={runtime}>
      <Thread />
    </AssistantRuntimeProvider>
  );
}
```

### 3.4 UI 改造方向

assistant-ui 默认风格偏简洁（类 ChatGPT），可参考 Ant Design X 改造：

| 改造点 | 参考来源 |
|--------|---------|
| 消息气泡样式 | Ant Design X `Bubble` 组件 |
| 打字机/流式效果 | Ant Design X `TypingContent` |
| 思维链步骤条 | Ant Design X `ThoughtChain` |
| 流式 Markdown 性能优化 | `@ant-design/x-markdown`（可直接替换渲染层） |
| 发送框交互细节 | Ant Design X `Sender`（附件、快捷键） |

## 四、协议选型：AG-UI

### 4.1 为什么选 AG-UI

- AAF 后端是 AgentScope 多智能体系统，需要丰富的事件模型（状态同步、工具调用、中断、多步骤）
- AG-UI 已被 Oracle、AWS Strands、Microsoft MAF、Google ADK、LangGraph、CrewAI 等采纳
- assistant-ui 原生支持 AG-UI，零额外适配
- Spring AI 不原生支持任何前端协议（AG-UI 和 Data Stream Protocol 都需要适配层），选事件模型更丰富的

### 4.2 AG-UI 事件类型（17 种）

```text
RUN_STARTED / RUN_FINISHED / RUN_ERROR     → Agent 生命周期
TEXT_MESSAGE_START / CONTENT / END          → 文本流
TOOL_CALL_START / ARGS / END               → 工具调用
STATE_SNAPSHOT / STATE_DELTA               → 状态同步
STEP_STARTED / STEP_FINISHED               → 多步骤
INTERRUPT                                  → 人工审批/中断
CUSTOM                                     → 扩展点
```

## 五、参考项目

| 项目 | 参考价值 |
|------|---------|
| **Ant Design X** | UI 交互设计参考：气泡样式、打字机效果、ThoughtChain 步骤条、流式 Markdown（`@ant-design/x-markdown`）、Sender 发送框交互 |
| **CopilotKit** | Agent 交互模式参考：useCopilotAction（前端 Action 注册）、useCopilotReadable（上下文注入）、useHumanInTheLoop（审批流）、CoAgent 多智能体协作、A2UI Generative UI |

## 六、对话框与文档画板协作

assistant-ui 只管 AI 对话框。文档画板基于 AAF 自研语义组件实现，两者通过共享 runtime + AG-UI 事件实现状态同步。

### 6.1 架构

```text
┌─────────────────────────────────────────────────────────────┐
│  页面布局                                                     │
├──────────────────────┬──────────────────────────────────────┤
│  对话框（assistant-ui）│  文档画板（自研语义组件）               │
│  ├── Thread          │  ├── Lexical 编辑器                   │
│  ├── Composer        │  ├── 工作流画布                        │
│  └── ToolUI          │  ├── 知识图谱                          │
│                      │  └── 其他语义组件                      │
├──────────────────────┴──────────────────────────────────────┤
│  共享状态层（AssistantRuntimeProvider）                        │
│  ├── Agent → 对话框：文本流、工具结果                          │
│  ├── Agent → 画板：STATE_DELTA / Tool Call 驱动组件更新       │
│  ├── 画板 → Agent：makeAssistantVisible 暴露画板状态          │
│  └── 对话框 ↔ 画板：共享同一个 Agent 会话                     │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 交互模式

| 方向 | 机制 | 说明 |
|------|------|------|
| Agent → 对话框 | AG-UI TEXT_MESSAGE 事件 | 正常对话流式输出 |
| Agent → 画板 | Tool Call（如 `update_document`）或 STATE_DELTA | Agent 操作画板内容 |
| 画板 → Agent | `makeAssistantVisible` | 声明式暴露画板状态，Agent 推理时感知 |
| DSL → 语义组件 | Agent 输出结构化数据 → 画板解析渲染 | AAF 核心能力，独立于 assistant-ui |

### 6.3 示例

```tsx
// 1. AssistantRuntimeProvider 包裹整个页面（对话框 + 画板共享 runtime）
<AssistantRuntimeProvider runtime={runtime}>
  <div className="flex">
    <Thread />           {/* 对话框 */}
    <DocumentCanvas />   {/* 文档画板 */}
  </div>
</AssistantRuntimeProvider>

// 2. Agent 操作画板：通过 Tool Call 驱动
makeAssistantToolUI({
  toolName: "update_document",
  render: ({ args }) => {
    updateCanvas(args.content);  // 驱动画板状态更新
    return <div>文档已更新</div>;
  },
});

// 3. 画板状态暴露给 Agent
const DocumentCanvas = makeAssistantVisible(MyCanvas, {
  description: "用户当前正在编辑的文档内容",
});
```

## 七、assistant-ui 架构借鉴

### 7.1 核心分层

```text
primitives/（UI 原语层）→ 无样式的行为组件，组合出任意 UI
context/（上下文层）    → 状态存储 + Provider + React Context
core/runtime/（运行时） → 框架无关的核心接口（ThreadRuntime/ComposerRuntime）
core/adapters/（适配器）→ 语音/附件/反馈/线程历史等扩展点
react-*/（协议适配包） → AG-UI/AI SDK/LangGraph 等后端的适配器
```

### 7.2 AAF 可借鉴的设计模式

| assistant-ui 模式 | AAF 应用 |
|---|---|
| **适配器模式**：core 定义抽象接口，`react-ag-ui` / `react-ai-sdk` 是不同后端的适配器 | AAF 对接 AgentScope 时参考此模式，保持前端与后端解耦 |
| **react-lexical**：Lexical 作为聊天输入框的集成 | AAF 的 Composer 直接复用，文档画板的 Lexical 编辑器独立实现 |
| **TAP（Tool Approval Protocol）**：人工审批 Tool Call 的标准化 | 对应 AAF 的置信度门控 + 人工审批流 |
| **safe-content-frame**：AI 生成内容的安全沙箱渲染 | AAF 的 Agent 生成代码/HTML 预览场景可参考 |
| **Primitives 组合模式**：ThreadPrimitive.Root/Messages/Viewport 等无样式原语 | AAF 自定义 UI 时基于 primitives 组合，不受预制组件约束 |
| **Model Context**：`makeAssistantVisible` / `makeAssistantTool` 声明式注册 | AAF 画板状态暴露给 Agent、前端 Tool 注册 |
