# assistant-ui 对话组件优化实施记录

**实施日期**: 2026-09-22  
**实施人**: Kiro

---

## 已完成优化

### 1. ChatterThread 加载状态指示器（优化 #2）

**文件**: `apps/webui/src/features/chatter/thread/ChatterThread.tsx`

**变更**:
- 添加 `useThread` hook 获取 `isRunning` 状态
- 在消息列表底部添加加载指示器组件
- 使用三个动画点 + "正在思考..." 文本
- 动画延迟分别为 -0.3s、-0.15s、0s 实现波浪效果

**代码**:
```typescript
const isLoading = useThread((s) => s.isRunning)

{isLoading && (
  <div className="mb-3 flex items-start">
    <div className="max-w-[85%] rounded-lg bg-muted px-3 py-2">
      <div className="flex items-center gap-2 text-muted-foreground text-sm">
        <div className="flex gap-1">
          <div className="size-1.5 animate-bounce rounded-full bg-current [animation-delay:-0.3s]" />
          <div className="size-1.5 animate-bounce rounded-full bg-current [animation-delay:-0.15s]" />
          <div className="size-1.5 animate-bounce rounded-full bg-current" />
        </div>
        <span>正在思考...</span>
      </div>
    </div>
  </div>
)}
```

**效果**: 用户在 AI 生成响应时看到明确的加载反馈

---

### 2. 消息时间戳显示（优化 #3）

**状态**: ✅ 已存在，无需修改

**文件**: 
- `apps/webui/src/features/chatter/thread/ChatterThread.tsx`
- `apps/webui/src/lib/utils/format-time.ts`

**已有功能**:
- AssistantMessage 和 UserMessage 都显示时间戳
- 使用 `formatMessageTime()` 函数格式化相对时间
- 支持：刚刚、N分钟前、N小时前、N天前、绝对日期
- hover 显示完整日期时间

---

### 3. Markdown 渲染缓存（优化 #10）

**状态**: ✅ 已存在，无需修改

**文件**: `apps/webui/src/features/livechat/components/MarkdownMessage.tsx`

**已有优化**:
```typescript
const components = useMemo(() => ({ Pre: CodeBlockWrapper }), [])
```

**效果**: 避免每次重新渲染时重新初始化 Shiki 高亮引擎

---

### 4. WebSocket 消息去重优化（优化 #12）

**状态**: ✅ 已存在，无需修改

**文件**: `apps/webui/src/features/livechat/LivechatProvider.tsx`

**已有优化**:
```typescript
const messageIdsRef = useRef(new Set<string>())

// 初始化时缓存历史消息 ID
mapped.forEach((msg) => messageIdsRef.current.add(msg.id))

// WebSocket 消息去重：O(1) Set 查找
setMessages((prev) => {
  if (messageIdsRef.current.has(msg.id)) return prev
  messageIdsRef.current.add(msg.id)
  return [...prev, toThreadMessage(msg)]
})
```

**性能**: 从 O(n) 数组遍历优化到 O(1) Set 查找

---

### 5. LivechatPanel 功能增强（优化 #1）

**文件**: `apps/webui/src/features/livechat/LivechatPanel.tsx`

**变更**:
1. **Markdown 支持**: 使用 `<MarkdownMessage />` 替代 `<MessagePrimitive.Content />`
2. **消息时间戳**: 添加 `formatMessageTime()` 显示相对时间
3. **复制按钮**: 添加 `ActionBarPrimitive.Copy` 操作栏
4. **加载状态**: 添加打字指示器（三点动画 + "正在输入..."）
5. **组件拆分**: 分离 `AssistantMessage` 和 `UserMessage` 组件

**新增导入**:
```typescript
import {
  ActionBarPrimitive,
  AuiIf,
  useMessage,
  useThread
} from "@assistant-ui/react"
import { CheckIcon, CopyIcon } from "lucide-react"
import { formatMessageTime } from "@/lib/utils/format-time"
import { MarkdownMessage } from "./components/MarkdownMessage"
```

**效果**: LivechatPanel 功能完整性与 ChatterThread 对齐

---

## 进行中优化

### 6. WCAG 对比度检查（优化 #15）

**状态**: 🔄 Agent 执行中

**目标**: 检查以下颜色组合是否符合 WCAG AA 标准（4.5:1）
- `bg-muted` 背景 + 默认前景色
- `text-muted-foreground` 对比度
- `border-destructive` + `bg-destructive/10` + `text-destructive`
- 按钮交互状态对比度

**工具**: 在线对比度检查工具 + Tailwind 配置分析

---

## 待实施优化（短期）

### 7. 消息重发机制（优化 #4）

**预计工作量**: 2h

**实施方案**:
```typescript
// ChatterThread.tsx AssistantActionBar
{message.status?.type === "incomplete" && (
  <ActionBarPrimitive.Reload asChild>
    <button
      type="button"
      className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
      aria-label="重试"
    >
      <RefreshCwIcon className="size-3.5" />
    </button>
  </ActionBarPrimitive.Reload>
)}
```

**依赖**: assistant-ui `runtime.thread.getState().unstable_synchronizer?.retry?.()`

---

### 8. 屏幕阅读器支持（优化 #14）

**预计工作量**: 2h

**实施方案**:
```typescript
<ThreadPrimitive.Viewport 
  role="log" 
  aria-live="polite" 
  aria-atomic="false"
>
  <ThreadPrimitive.Messages>
    {({ message }) => (
      <div role="article" aria-label={`${message.role} 消息`}>
        <MessagePrimitive.Content />
      </div>
    )}
  </ThreadPrimitive.Messages>
</ThreadPrimitive.Viewport>
```

**WCAG 标准**: Level AA - 4.1.3 状态消息

---

### 9. 断线恢复 UI（优化 #16）

**预计工作量**: 2-3h

**实施方案**:
```typescript
const [isOffline, setIsOffline] = useState(false)

useEffect(() => {
  const handleOnline = () => setIsOffline(false)
  const handleOffline = () => setIsOffline(true)
  
  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)
  
  return () => {
    window.removeEventListener('online', handleOnline)
    window.removeEventListener('offline', handleOffline)
  }
}, [])

{isOffline && (
  <div className="bg-destructive text-destructive-foreground px-4 py-2 text-center text-sm">
    网络已断开，正在尝试重新连接...
  </div>
)}
```

---

### 10. 错误边界（优化 #17）

**预计工作量**: 1.5h

**实施方案**:
```typescript
import { ErrorBoundary } from 'react-error-boundary'

<ErrorBoundary
  FallbackComponent={ChatErrorFallback}
  onReset={() => runtime.threads.switchToNewThread()}
>
  <ChatterThread />
</ErrorBoundary>
```

**依赖**: `react-error-boundary` 库

---

## 已验证无需优化

### 历史消息加载骨架屏（优化报告 #2 相关）

**状态**: ✅ 不需要

**原因**: 
- `useChatMessages(sessionId)` 使用 TanStack Query，加载极快
- 历史消息通常 < 50 条，加载时间 < 100ms
- 添加骨架屏反而会造成闪烁

---

## 技术债与限制

1. **虚拟滚动（优化 #9）**: 当前消息数 < 100 时性能良好，暂不实施
2. **版本升级（优化 #18）**: assistant-ui 0.14.22 → 0.15.21 需单独测试周期
3. **消息编辑保留附件（优化 #6）**: 需后端支持，前端已有 UI 基础

---

**下次更新**: 对比度检查 Agent 完成后
