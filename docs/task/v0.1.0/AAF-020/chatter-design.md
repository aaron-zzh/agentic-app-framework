---
level: Practice
layer: Model
purpose: live-chatter 重构设计——统一协议 + Chatter 组件 + 统一后端端点
status: active
version: 1.0.0
date: 2026-05-22
author: AaronZZH
---

# live-chatter 重构设计

## 功能说明

### 用户视角

`Chatter` 是 AAF 的统一对话组件，支持三种对话模式，可嵌入任意页面：

| 模式 | 说明 | 典型场景 |
|------|------|---------|
| **AI 助手**（`preset="ai"`） | 与 AI 进行多轮对话，支持持久化历史 | 工作台 AI 助手、知识问答 |
| **Kiro Agent**（`preset="kiro"`） | 向 kiro-cli 发送开发指令，实时查看执行输出 | 开发文档页面、代码生成 |
| **用户聊天**（`preset="livechat"`） | 与其他用户实时聊天 | 客服、内部 IM |

**三种布局**，适配不同场景：
- `layout="panel"`：内嵌在页面中（如开发文档页面右侧），不遮挡内容，可拖拽调整宽度
- `layout="dialog"`：非模式弹窗，背景不虚化，可同时操作页面和对话
- `layout="drawer"`：从右侧滑入的抽屉，适合临时唤起

**拖放支持**：可将文档树节点、图谱节点等 UI 元素直接拖入对话框，自动附加为消息附件。

**顶部工具栏**：
- 左侧：AI / Kiro / 用户 切换按钮（同一窗口内切换，不丢失历史）
- 中间：新建会话按钮（AI / 用户聊天模式）
- 右侧：外部注入的自定义内容（如 Kiro agent 角色选择器）

### 开发者视角

**最简用法**：

```tsx
import { Chatter } from "@/features/chatter"

// 开发文档页面内嵌 Kiro Agent
<Chatter preset="kiro" layout="panel" />

// AI 助手非模式弹窗
<Chatter preset="ai" layout="dialog" open={open} onOpenChange={setOpen} />

// 用户聊天抽屉
<Chatter preset="livechat" layout="drawer" targetUserId="user-123" />
```

**注入自定义工具栏**（如 Kiro agent 角色选择器）：

```tsx
<Chatter
  preset="kiro"
  layout="panel"
  agentRole={agentRole}
  toolbar={
    <Select value={agentRole} onValueChange={setAgentRole}>
      <SelectItem value="architect">architect</SelectItem>
      <SelectItem value="developer-service">developer-service</SelectItem>
    </Select>
  }
/>
```

**拖放：让 UI 元素可拖入对话框**：

```tsx
import { DraggableItem } from "@/features/chatter"

// 包裹任意元素使其可拖入 Chatter
<DraggableItem item={{ type: "doc", id: 42, title: "架构设计文档" }}>
  <button>架构设计文档</button>
</DraggableItem>

// 页面顶层需要 DndContext（Chatter 内部已包含，只需确保 DraggableItem 在同一 DndContext 下）
import { DndContext } from "@dnd-kit/core"
<DndContext>
  {/* 文档树 + Chatter 同层 */}
</DndContext>
```

**后端端点**：`POST /api/chat/run`，通过 `target.type`（`ai` / `kiro` / `user`）路由，`state.persist` 控制是否持久化消息。

---

## 背景与目标

当前 live-chatter 有三套独立 runtime（AG-UI SSE / WebSocket / Kiro），切换需要重新挂载 Provider，丢失会话状态，且无法在同一对话窗内切换对话对象（AI / Kiro / 用户）。

重构目标：
- **统一协议**：前后端统一走 AG-UI SSE，WebSocket 多用户聊天也通过 SSE 响应
- **统一端点**：后端 `POST /api/chat/run`，通过 `target` 字段路由到不同处理器
- **统一组件**：前端 `<Chatter>` 组件，通过 `preset` 和 `layout` 控制行为和布局
- **丝滑切换**：同一对话窗内切换对话对象，不重新挂载，不丢失历史
- **拖放支持**：UI 元素可拖入对话框作为附件

---

## 统一协议：POST /api/chat/run

### 请求格式（AG-UI 扩展）

```json
{
  "threadId": "uuid",
  "messages": [
    { "role": "user", "content": "帮我分析这个文档" }
  ],
  "target": {
    "type": "ai",
    "agentRole": null,
    "userId": null
  },
  "state": {
    "persist": true,
    "sessionId": "uuid"
  }
}
```

**target 字段**：

| type | 说明 | 路由到 |
|------|------|--------|
| `ai` | AI 助手对话 | `ResilientChatService`（Spring AI） |
| `kiro` | Kiro Agent | `KiroAgentController`（ProcessBuilder） |
| `user` | 用户间聊天 | `ChatWebSocketHandler`（推送给目标用户） |

**state 字段**：

| 字段 | 说明 |
|------|------|
| `persist` | 是否持久化到 `sys_chat_message`（默认 true） |
| `sessionId` | 会话 ID，不传则自动创建 |

### 响应格式（标准 AG-UI SSE）

```
data: {"type":"RUN_STARTED","run_id":"...","thread_id":"..."}
data: {"type":"TEXT_MESSAGE_START","message_id":"...","role":"assistant"}
data: {"type":"TEXT_MESSAGE_CONTENT","message_id":"...","delta":"你好"}
data: {"type":"TEXT_MESSAGE_END","message_id":"..."}
data: {"type":"RUN_FINISHED","run_id":"...","thread_id":"..."}
```

`type=user` 时，后端通过 WebSocket 把消息推给目标用户，同时通过 SSE 返回"已发送"确认事件。目标用户收到消息后，通过 SSE 推送给发送方（实现双向实时）。

---

## 前端 Chatter 组件 API

### 基本用法

```tsx
import { Chatter } from "@/features/chatter"

// Kiro Agent 内嵌面板（开发文档页面）
<Chatter preset="kiro" layout="panel" />

// AI 助手非模式弹窗
<Chatter preset="ai" layout="dialog" defaultOpen />

// 用户间聊天抽屉
<Chatter preset="livechat" layout="drawer" targetUserId={userId} />
```

### Props

```typescript
interface ChatterProps {
  // 场景预设（决定默认 target + 是否持久化）
  preset: "ai" | "kiro" | "livechat"

  // 布局模式
  layout: "panel"    // ResizablePanel 内嵌，无遮罩，可拖拽调整宽度
        | "dialog"   // 非模式弹窗（modal=false），不虚化背景
        | "drawer"   // Sheet 侧边抽屉

  // 对话目标（可运行时切换，不重新挂载）
  targetUserId?: string   // preset=livechat 时指定目标用户
  agentRole?: string      // preset=kiro 时指定 agent 角色

  // 持久化（默认跟随 preset：ai/livechat=true，kiro=false）
  persist?: boolean

  // 布局控制（layout=panel 时有效）
  defaultSize?: number    // 默认宽度百分比
  minSize?: number
  maxSize?: number

  // 弹窗控制（layout=dialog/drawer 时有效）
  open?: boolean
  onOpenChange?: (open: boolean) => void

  // 顶部工具栏 slot（外部注入，如 agent 选择器）
  toolbar?: ReactNode

  // 拖放回调
  onDrop?: (item: ChatterDropItem) => void

  // 会话管理
  sessionId?: string
  onSessionChange?: (sessionId: string) => void
}

// 可拖入对话框的元素类型
interface ChatterDropItem {
  type: "doc" | "file" | "image" | "text"
  id?: number
  title?: string
  content?: string
  url?: string
}
```

### 内部结构

```
Chatter
├── ChatterRuntime（统一 runtime，基于 useAgUiRuntime）
│   └── 监听 target 变化，更新 HttpAgent url，不重新挂载
├── ChatterLayout（根据 layout prop 选择容器）
│   ├── panel  → 直接渲染（由父组件放入 ResizablePanel）
│   ├── dialog → Dialog modal=false
│   └── drawer → Sheet
└── ChatterPanel（对话 UI）
    ├── ChatterToolbar（顶部：target 切换 + session 管理 + slot）
    │   ├── TargetSwitcher（AI / Kiro / 用户 切换按钮组）
    │   ├── SessionManager（新建会话 / 历史会话，仅 AI/livechat）
    │   └── {toolbar} slot
    ├── ChatterThread（消息流，基于 ThreadPrimitive）
    └── ChatterComposer（输入区，支持拖放附件）
        ├── ComposerPrimitive.Input
        ├── AttachmentList（拖入的附件预览）
        └── ComposerPrimitive.Send
```

### 切换对话对象（不重新挂载）

```tsx
// ChatterRuntime 内部：target 变化时只更新 agent url
const agent = useMemo(
  () => new HttpAgent({ url: buildUrl(target) }),
  [target]  // target 变化 → 新 agent → useAgUiRuntime 内部更新
)
// useAgUiRuntime 支持 agent 引用变化时热更新，不重新挂载 Provider
```

### 拖放实现

```tsx
// 文档树节点标记为可拖拽
<DraggableDocNode docId={id} title={title} content={content} />

// ChatterComposer 接收拖放
<DroppableComposer onDrop={(item) => appendAttachment(item)}>
  <ComposerPrimitive.Input />
</DroppableComposer>
```

使用 `@dnd-kit/core`，拖放数据通过 `ChatterDropItem` 格式传递。

---

## 后端实现

### 统一端点

```
POST /api/chat/run  →  ChatRunController（aaf-api, system/chat 模块）
```

`ChatRunController` 根据 `target.type` 路由：

```java
@PostMapping("/run")
public SseEmitter run(@RequestBody ChatRunRequest request) {
    return switch (request.target().type()) {
        case "ai"   -> aiHandler.handle(request, emitter);
        case "kiro" -> kiroHandler.handle(request, emitter);
        case "user" -> userHandler.handle(request, emitter);
    };
}
```

### 持久化

`persist=true` 时，统一在 `ChatRunController` 层保存消息到 `sys_chat_message`，各 handler 不关心持久化。

### user 类型处理

后端收到 `type=user` 消息：
1. 保存到 `sys_chat_message`
2. 通过 `ChatWebSocketHandler` 推送给目标用户
3. SSE 返回 `RUN_FINISHED`（消息已投递确认）
4. 目标用户回复时，通过 SSE 推送给发送方（需要发送方保持 SSE 连接）

---

## 迁移计划

| 现有 | 迁移后 |
|------|--------|
| `AgUiChatController` (`/api/chat/agent/run`) | 保留，`ChatRunController` 内部复用其逻辑 |
| `KiroAgentController` (`/api/autodev/kiro/run`) | 保留，`ChatRunController` 内部复用 |
| `ChatController` (`/api/system/chat/stream`) | 保留，`ChatRunController` 内部复用 |
| `AgUiChatProvider` | 废弃，改用 `<Chatter preset="ai">` |
| `LivechatProvider` | 废弃，改用 `<Chatter preset="livechat">` |
| `KiroAgentProvider` + `KiroAgentDrawer` | 废弃，改用 `<Chatter preset="kiro">` |

旧端点保留（不删除），新端点并行上线，前端逐步迁移。

---

## 文件结构

```
apps/webui/src/features/chatter/
├── index.ts                    → 公开导出
├── Chatter.tsx                 → 主组件（组合 Runtime + Layout + Panel）
├── ChatterRuntime.tsx          → 统一 runtime（useAgUiRuntime + target 热更新）
├── ChatterLayout.tsx           → 布局容器（panel/dialog/drawer）
├── ChatterPanel.tsx            → 对话 UI（Toolbar + Thread + Composer）
├── ChatterToolbar.tsx          → 顶部工具栏（target 切换 + session 管理）
├── ChatterThread.tsx           → 消息流（ThreadPrimitive）
├── ChatterComposer.tsx         → 输入区（支持拖放）
├── dnd/
│   ├── DraggableItem.tsx       → 可拖拽元素包装
│   └── DroppableComposer.tsx   → 可接收拖放的输入区
└── types.ts                    → ChatterProps / ChatterDropItem 等类型
```

```
apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/system/chat/
├── controller/
│   └── ChatRunController.java  → 统一端点（新增）
├── handler/
│   ├── AiChatHandler.java      → AI 处理器（从 AgUiChatController 提取）
│   ├── KiroChatHandler.java    → Kiro 处理器（从 KiroAgentController 提取）
│   └── UserChatHandler.java    → 用户间聊天处理器
└── vo/
    ├── ChatRunRequest.java     → 统一请求 VO
    └── ChatTarget.java         → target 字段 VO
```

---

## 相关文档

- AAF-020 需求：[requirement.md](requirement.md)
- live-chatter 现有实现：`apps/webui/src/features/livechat/`
- assistant-ui AG-UI runtime：`.kiro/skills/assistant-ui/SKILL.md`
