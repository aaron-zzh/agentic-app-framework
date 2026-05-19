---
level: Practice
layer: Product
purpose: AAF 在线客服与聊天模块设计——基于 assistant-ui 统一架构 + AI 感知驱动
status: draft
version: 1.0.0
date: 2026-05-12
author: AaronZZH
---

# 在线客服与聊天模块（Livechat & Chatbot）

> 基于 assistant-ui 统一架构实现。客服/机器人/AI 助理共享同一套 UI 组件（Thread/ThreadList/Composer），区别仅在 runtime 层。融合 [AI 感知能力](./interaction-mode-structured-view.md#三十五ai-感知能力ai-context-awareness)，实现"AI 全程在线"的智能客服体验。
> 所属体系：[结构化交互模式](./interaction-mode-structured-view.md) | 实时方案：[realtime-data-strategy.md](./tech-design/realtime-data-strategy.md)
> DSL 集成（对话中 DSL 片段渲染与执行）：[DSL 运行时](../../framework/dsl/dsl-runtime.md)

## 一、设计理念

```text
传统客服：独立聊天系统 → iframe 嵌入 → 数据割裂 → AI 后加
AAF 客服：assistant-ui 统一架构 → 共享 runtime → AI 原生 → 感知驱动
```

核心原则：
- **统一架构**：客服对话、AI 助理对话、内部 IM 共用 assistant-ui 组件，不引入独立聊天框架（禁止并行抽象）
- **runtime 分离**：同一套 Thread/Composer UI，通过不同 runtime 对接不同后端
- **AI 感知融合**：AI 不仅回答问题，还感知客服工作台上下文（当前会话、访客信息、历史记录），主动辅助客服人员
- **机器人前置**：Chatbot 脚本自动应答，AI Agent 兜底，最后转人工
- **对话为辅，结构化为主**：LiveChatter 是意图表达和快速指令的辅助通道，AI 输出优先"焊"进结构化视图（工单表单预填充、知识库卡片内联、审批按钮卡片），而非在对话中输出需要用户复制粘贴的长文本。对话中确认的决策自动固化为结构化数据（工单/配置/实体记录），避免上下文腐烂
- **决策日志**：AI 自主推进的操作（自动回复、工单分类、路由决策）留下决策记录，支持异步人工审查而非实时阻塞

## 二、统一架构

```text
┌─────────────────────────────────────────────────────────────┐
│              assistant-ui 统一 UI 层                          │
│  Thread / ThreadList / Composer / Message / ToolUI           │
├─────────────────────────────────────────────────────────────┤
│              Runtime 适配层                                   │
│  ┌──────────────┐  ┌───────────────┐  ┌──────────────────┐ │
│  │ AgUiRuntime  │  │LivechatRuntime│  │  IMRuntime       │ │
│  │（AI 助理）    │  │（客服/机器人） │  │（内部聊天）       │ │
│  └──────┬───────┘  └──────┬────────┘  └────────┬─────────┘ │
├─────────┼─────────────────┼─────────────────────┼───────────┤
│         │ SSE(AG-UI)      │ WebSocket           │ WebSocket │
│         ▼                 ▼                     ▼           │
│  ┌──────────────┐  ┌───────────────┐  ┌──────────────────┐ │
│  │ Agent 服务    │  │ Livechat 服务 │  │  IM 服务         │ │
│  │ (Spring AI)  │  │ (WebFlux)     │  │  (WebFlux)       │ │
│  └──────────────┘  └───────────────┘  └──────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 三种场景的 Runtime 配置

```typescript
// 场景 1：AI 助理对话（AG-UI runtime）
const aiRuntime = useAgUiRuntime({ api: "/api/agent/run" });

// 场景 2：客服/机器人对话（ExternalStoreRuntime + WebSocket）
const livechatRuntime = useExternalStoreRuntime({
  messages: livechatMessages,
  onNew: (msg) => ws.send(JSON.stringify({ type: 'message', body: msg.content })),
  convertMessage: convertLivechatMessage,
});

// 场景 3：内部 IM（同 ExternalStoreRuntime）
const imRuntime = useExternalStoreRuntime({
  messages: imMessages,
  onNew: (msg) => ws.send(JSON.stringify({ type: 'message', body: msg.content })),
  convertMessage: convertIMMessage,
});
```

所有场景共享同一套 `<Thread />` / `<Composer />` 组件，仅 runtime 不同。

## 三、数据模型

```typescript
// 客服频道配置
interface LivechatChannel {
  id: string
  name: string                        // "官网客服" / "售后支持"
  operators: string[]                  // 客服人员 userId[]
  chatbotScript?: string              // 关联的机器人脚本 ID
  maxConcurrent?: number              // 每个客服最大并发会话数
  autoAssign: boolean                 // 自动分配客服
  welcomeMessage?: string             // 欢迎语
  offlineMessage?: string             // 离线提示
  businessHours?: BusinessHours[]     // 工作时间
}

// 聊天会话
interface ChatSession {
  id: string
  channelId: string
  type: 'livechat' | 'internal' | 'group'
  status: 'bot' | 'waiting' | 'active' | 'closed'
  participants: Participant[]
  messages: Message[]
  chatbotState?: ChatbotState         // 机器人当前状态
  createdAt: string
  closedAt?: string
}

// 消息
interface Message {
  id: string
  sessionId: string
  authorId: string                    // userId 或 'bot' 或 guestId
  authorType: 'user' | 'bot' | 'guest'
  body: string                        // 支持 Markdown
  messageType: 'text' | 'card' | 'action' | 'system'
  attachments?: Attachment[]
  metadata?: Record<string, any>      // 机器人步骤信息等
  createdAt: string
}
```

## 四、机器人脚本引擎

```typescript
// 脚本定义（EntityDef 配置驱动）
interface ChatbotScript {
  id: string
  name: string
  steps: ChatbotStep[]
}

interface ChatbotStep {
  id: string
  type: 'text'                        // 纯文本消息
    | 'question_selection'            // 选项选择
    | 'question_text'                 // 自由输入
    | 'question_email'                // 邮箱收集
    | 'question_phone'                // 电话收集
    | 'forward_operator'              // 转人工
    | 'create_ticket'                 // 创建工单
    | 'ai_answer'                     // AI Agent 回答
  message: string                     // 机器人发送的消息
  answers?: { id: string; label: string; nextStepId?: string }[]
  nextStepId?: string                 // 默认下一步
  validation?: { pattern?: string; errorMessage?: string }
}
```

### 机器人对话流程

```text
访客打开聊天
  → 加载 ChatbotScript
  → 发送 welcomeMessage
  → 执行第一个 step（通常是 question_selection）
  → 用户选择/输入
  → 保存答案 → 跳转 nextStepId
  → ...循环...
  → step.type === 'forward_operator'
    → 分配在线客服 → 客服加入会话 → status 切换为 'active'
  → step.type === 'ai_answer'
    → 调用 AI Agent → 返回答案 → 用户确认是否解决
```

### 机器人脚本配置（无代码）

机器人脚本通过 [统一流程图编辑器](./flow-editor.md)（聊天机器人节点集）可视化配置：

```text
[欢迎语] → [选择问题类型]
              ├─ 产品咨询 → [AI 回答] → [是否解决？]
              │                            ├─ 是 → [结束]
              │                            └─ 否 → [转人工]
              ├─ 售后服务 → [收集订单号] → [转人工]
              └─ 其他 → [自由输入] → [AI 回答] → ...
```

## 五、前端实现（基于 assistant-ui）

### LivechatRuntime 实现

```typescript
// lib/chat/livechat-runtime.ts
import { ExternalStoreRuntime } from "@assistant-ui/react";

export function useLivechatRuntime(sessionId: string) {
  const ws = useLivechatWebSocket(sessionId);
  const messages = useLivechatMessages(sessionId);

  return useExternalStoreRuntime({
    messages,
    onNew: async (message) => {
      ws.send(JSON.stringify({
        type: 'chat/send_message',
        sessionId,
        body: message.content[0].text,
      }));
      if (currentStep?.type === 'question_selection') {
        ws.send(JSON.stringify({ type: 'chat/bot_answer', answerId: message.metadata?.answerId }));
      }
    },
    convertMessage: (msg) => ({
      id: msg.id,
      role: msg.authorType === 'guest' ? 'user' : 'assistant',
      content: [{ type: 'text', text: msg.body }],
      metadata: { authorType: msg.authorType, authorName: msg.authorName },
    }),
  });
}
```

### 统一聊天面板组件

```tsx
// components/chat/chat-panel.tsx
import { AssistantRuntimeProvider } from "@assistant-ui/react";
import { Thread } from "@/components/assistant-ui/thread";

export function ChatPanel({ mode, sessionId }: { mode: 'ai' | 'livechat' | 'im'; sessionId: string }) {
  const runtime = mode === 'ai'
    ? useAgUiRuntime({ api: "/api/agent/run" })
    : useLivechatRuntime(sessionId);

  return (
    <AssistantRuntimeProvider runtime={runtime}>
      <Thread />
    </AssistantRuntimeProvider>
  );
}
```

### 多会话列表（ThreadList）

```tsx
// 工作区侧边栏聊天面板：AI 助理 + 客服 + IM 统一展示
<ThreadList
  threads={[
    ...aiThreads,        // AI 助理对话
    ...livechatThreads,  // 客服会话
    ...imThreads,        // 内部聊天
  ]}
  renderThread={(thread) => <ThreadListItem thread={thread} />}
/>
```

## 六、实时通信

```text
前端 ←→ WebSocket ←→ 后端

事件类型：
  chat/new_message        → 新消息推送
  chat/typing             → 正在输入指示
  chat/session_update     → 会话状态变更（转人工/关闭）
  chat/operator_joined    → 客服加入
  chat/presence           → 在线状态变更
```

### 消息推送策略

| 场景 | 推送方式 |
|------|---------|
| 用户在线（WebSocket 连接中） | WebSocket 实时推送 |
| 用户离线（PWA 已安装） | Web Push 通知 |
| 用户离线（无 PWA） | 不推送，下次上线拉取未读 |

## 七、访客端嵌入（LivechatWidget）

基于 assistant-ui Thread 组件封装的浮窗：

```text
┌─────────────────────┐
│  💬  在线客服         │  ← 浮动按钮（右下角）
└─────────────────────┘
        ↓ 点击展开
┌─────────────────────┐
│ AssistantRuntimeProvider(livechatRuntime)
│ ┌─────────────────┐ │
│ │ <Thread />       │ │  ← assistant-ui Thread 组件
│ │ 🤖 您好！请问...  │ │  ← 机器人欢迎语（bot message）
│ │                 │ │
│ │ [产品咨询]       │ │  ← ToolUI 渲染选项按钮
│ │ [售后服务]       │ │
│ │ [转人工客服]     │ │
│ └─────────────────┘ │
│ ┌─────────────────┐ │
│ │ <Composer />     │ │  ← assistant-ui Composer 组件
│ └─────────────────┘ │
└─────────────────────┘
```

### 初始化流程

```text
页面加载
  → 检查 livechatConfig（从后端 /api/livechat/init 获取）
  → available = chatbotScript 存在 || 在线客服人数 > 0
  → 渲染浮动按钮
  → 用户点击
  → GET /api/livechat/session（获取或创建临时会话，persist=false）
  → 机器人可用 → 启动 ChatbotRenderer
  → 用户发送第一条消息 → POST 创建持久化会话记录
```

## 八、工作区内聊天（ChatPanel）

工作区用户之间的内部聊天，复用 assistant-ui ThreadList + Thread：

```text
侧边栏 [💬 消息] → 打开 ChatPanel（ThreadList 组件）
  ├── AI 助理对话（AgUiRuntime）
  ├── 客服会话（LivechatRuntime，客服人员视角）
  ├── 一对一私聊（IMRuntime）
  └── 群组聊天（IMRuntime）
点击任一会话 → Thread 组件渲染，runtime 自动切换
```

## 九、客服工作台

客服人员在工作区内管理所有客服会话：

```text
/workspace/livechat → 客服工作台视图
  ├── 待接入队列（status='waiting' 的会话）
  ├── 我的会话（当前处理中）
  ├── 会话详情（右侧面板：访客信息 + 历史记录）
  └── 快捷回复（: 触发搜索预设回复模板）
```

## 十、与实体引擎集成

客服模块作为 EntityDef 注册，享受配置驱动的全部能力：

```typescript
entityRegistry.chatSession = {
  slug: 'chat-session', label: '客服会话', apiPath: '/api/chat/sessions',
  icon: 'message-circle', group: '客服中心',
  fields: [
    { name: 'visitor', type: 'text', label: '访客' },
    { name: 'operator', type: 'relationship', relationTo: 'user', label: '客服' },
    { name: 'status', type: 'select', options: [
      { label: '机器人', value: 'bot', color: 'blue' },
      { label: '等待中', value: 'waiting', color: 'yellow' },
      { label: '进行中', value: 'active', color: 'green' },
      { label: '已关闭', value: 'closed', color: 'gray' },
    ]},
    { name: 'channel', type: 'relationship', relationTo: 'livechat-channel' },
    { name: 'rating', type: 'number', label: '满意度评分' },
    { name: 'closedAt', type: 'date' },
  ],
  listView: {
    columns: ['visitor', 'operator', 'status', 'channel', 'rating', 'closedAt'],
    defaultSort: 'createdAt:desc',
    filterableFields: ['status', 'operator', 'channel'],
  },
  kanbanView: { statusField: 'status', cardTitle: 'visitor' },
}
```

## 十一、AI Agent 集成 + AI 感知融合

> 融合 [AI 感知能力（第三十五章）](./interaction-mode-structured-view.md#三十五ai-感知能力ai-context-awareness)。AI 不仅作为机器人回答问题，还感知整个客服工作台上下文，主动辅助客服人员。

### 机器人模式：AI Agent 自动应答

```text
step.type === 'ai_answer'
  → 收集对话上下文（历史消息 + 访客信息 + 知识库）
  → 调用 AI Agent（Spring AI 后端，AG-UI 协议）
  → Agent 检索知识库 → 生成回答
  → 通过 LivechatRuntime 推送到 Thread 渲染
  → 追问："这个回答有帮助吗？" [有帮助] [转人工]
```

### DSL 片段渲染（自然语言与 DSL 混合）

AI Agent 回复中可包含 DSL 代码块，前端自动识别并渲染为可交互卡片：

```text
Agent 回复：
  "已为您创建用户管理模块，配置如下："
  ┌─ DSL 预览卡片 ──────────────────────┐
  │ entity User {                        │
  │   name: String @required             │
  │   email: Email @unique               │
  │   role: Enum["admin", "user"]        │
  │ }                                    │
  │ [应用此配置] [编辑] [取消]            │
  └──────────────────────────────────────┘
```

识别规则：消息中 ` ```dsl ` 代码块 → `<DslPreviewCard />` 组件渲染（来自 [DSL 运行时](../../framework/dsl/dsl-runtime.md) 的 `dsl-preview-card.tsx`）。用户确认后转发后端 DSL 引擎执行。

### 客服辅助模式：AI 感知工作台上下文

当客服人员处理会话时，AI 感知服务自动收集工作台上下文：

```typescript
interface LivechatAIContext extends AIPageContext {
  // 继承通用页面感知
  currentEntity: EntityDef              // chat-session 实体
  currentView: 'list' | 'form' | 'kanban'

  // 客服专属感知
  activeSession: {
    visitorInfo: { name, source, history }  // 访客信息
    conversationHistory: Message[]          // 当前对话历史
    visitorIntent?: string                  // AI 推断的访客意图
    sentiment?: 'positive' | 'neutral' | 'negative'  // 情绪分析
  }
  operatorWorkload: number                 // 当前客服并发数
  relatedKnowledge?: string[]              // 相关知识库条目
}
```

### AI 感知驱动的客服辅助能力

| 能力 | 触发条件 | 行为 | 与 AI 感知的关系 |
|------|---------|------|------------------|
| **智能回复建议** | 访客发送消息 | 推荐 2-3 条候选回复，客服一键发送 | 扩展"字段自动补全"→"回复自动补全" |
| **知识库检索** | 检测到访客问题 | 侧边栏自动展示相关知识条目 | 扩展"关联推荐" |
| **情绪预警** | 访客情绪转负面 | 顶部横幅提醒 + 建议安抚话术 | 扩展"异常检测" |
| **意图识别** | 对话进行中 | 自动标记访客意图（咨询/投诉/购买） | 扩展"流程引导" |
| **转接建议** | 问题超出当前客服能力 | 建议转接到专业客服 + 自动附带上下文 | 扩展"操作建议" |
| **工单预填** | 需要创建工单时 | 根据对话自动填充工单字段 | 扩展"智能默认值" |

### 客服工作台 AI 辅助 UI

```text
┌─────────────────────────────────────────────────────────┐
│ [待接入: 3] [我的会话: 5]              [AI 辅助: ✓ 开启] │
├──────────────┬──────────────────────────────────────────┤
│ 会话列表      │  当前对话（Thread 组件）                   │
│ (ThreadList) │  ┌────────────────────────────────────┐  │
│              │  │ 访客：我的订单一直没发货...           │  │
│ ● 张三 [急]  │  │ ─────────────────────────────────  │  │
│ ○ 李四       │  │ 💡 AI 建议回复：                    │  │
│ ○ 王五       │  │ [查询订单状态] [安抚+承诺跟进]      │  │
│              │  │ [转接物流专员]                      │  │
│              │  └────────────────────────────────────┘  │
│              │  ┌────────────────────────────────────┐  │
│              │  │ Composer（输入框）                   │  │
│              │  └────────────────────────────────────┘  │
│              ├──────────────────────────────────────────┤
│              │  AI 感知面板（右侧）                      │
│              │  ├── 访客意图：物流查询                   │
│              │  ├── 情绪：😠 负面（建议安抚）            │
│              │  ├── 相关知识：[物流延迟处理流程]         │
│              │  └── 历史：上次咨询 3 天前（同一问题）    │
└──────────────┴──────────────────────────────────────────┘
```

### 实现机制

```typescript
// AI 感知服务扩展（客服场景）
// 复用 AIAwarenessService，注册客服专属 context collector
aiAwarenessService.registerCollector('livechat', {
  collect: (baseContext) => ({
    ...baseContext,
    activeSession: getCurrentSession(),
    operatorWorkload: getOperatorWorkload(),
    relatedKnowledge: searchKnowledge(baseContext.activeSession?.conversationHistory),
  }),
  suggestions: [
    { type: 'reply', trigger: 'onVisitorMessage', handler: generateReplySuggestions },
    { type: 'knowledge', trigger: 'onVisitorMessage', handler: searchRelatedKnowledge },
    { type: 'sentiment', trigger: 'onVisitorMessage', handler: analyzeSentiment },
    { type: 'intent', trigger: 'onSessionStart', handler: classifyIntent },
  ],
});

// 建议通过 assistant-ui 的 ToolUI 渲染
makeAssistantToolUI({
  toolName: "suggest_reply",
  render: ({ args }) => (
    <ReplySuggestionCard
      suggestions={args.suggestions}
      onAccept={(text) => sendMessage(text)}
    />
  ),
});
```

## 十二、满意度评价

```text
会话关闭时 → 弹出评价卡片
  ⭐⭐⭐⭐⭐ （1-5 星）
  [可选] 文字反馈
  → POST /api/chat/sessions/{id}/rating
```

## 十三、核心特性

| 特性 | 说明 |
|------|------|
| **统一 UI 架构** | 客服/机器人/AI 助理共享 assistant-ui 组件（Thread/Composer），一套 UI 多种 runtime |
| **AI 原生** | AI Agent + 知识库检索 + AI 感知辅助，不是后加的插件 |
| **AI 辅助客服** | 感知工作台上下文 → 智能回复建议 + 情绪预警 + 意图识别 + 知识推送 |
| **无代码机器人配置** | 通过可视化流程编辑器拖拽配置聊天机器人脚本 |
| **机器人→AI→人工无缝切换** | 同一 Thread 内 runtime 不切换，后端路由切换，用户无感 |
| **原生响应式通信** | WebSocket（Spring WebFlux），实时推送消息/状态/在线指示 |
| **全端覆盖** | 网页嵌入（LivechatWidget）+ 工作区内聊天 + UniApp |
| **嵌入式 SDK** | 第三方网站一行代码接入 AI 客服浮窗，详见 [embed-sdk.md](./embed-sdk.md) |
| **配置驱动** | 客服模块作为 EntityDef 注册，享受列表/看板/筛选/权限全部能力 |
| **DSL 集成** | 对话中 Agent 返回 DSL 片段 → 前端自动渲染可交互预览卡片 |
| **多会话管理** | ThreadList 统一展示 AI 对话 + 客服会话 + 内部 IM |
| **满意度评价** | 会话关闭时自动弹出评分卡片 |

## 十四、与 AI 感知能力的关系

```text
AI 感知（第三十五章）定义了通用能力：
  - AIPageContext 数据模型
  - AIAwarenessService 服务接口
  - AISuggestion 建议机制

客服模块是 AI 感知在客服场景的具体应用：
  - 扩展 AIPageContext → LivechatAIContext（增加会话/访客/情绪）
  - 注册客服专属 collector + suggestion handler
  - 通过 assistant-ui ToolUI 渲染建议卡片
  - 客服人员 = 结构化视图用户 + AI 感知受益者
```

**AI 感知是基础设施，客服模块是场景应用。**
