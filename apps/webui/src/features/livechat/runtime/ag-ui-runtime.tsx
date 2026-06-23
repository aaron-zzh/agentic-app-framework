/**
 * AG-UI Runtime Provider——AI 助理对话的 runtime 配置
 * 使用 @ag-ui/client HttpAgent 对接后端 AG-UI SSE 端点，
 * 通过 useAgUiRuntime 将事件流转为 assistant-ui 可消费的 runtime
 *
 * 支持自定义端点 url 和 initialState，供 ChatterRuntime 复用
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { HttpAgent } from "@ag-ui/client"
import type { AttachmentAdapter, CompleteAttachment, PendingAttachment } from "@assistant-ui/react"
import {
  AssistantRuntimeProvider,
  type ThreadMessage,
  Tools,
  useAui,
  useVoiceControls
} from "@assistant-ui/react"
import { type UseAgUiThreadListAdapter, useAgUiRuntime } from "@assistant-ui/react-ag-ui"
import { backendApi } from "@/lib/api/rest/backend-client"

/** 文件上传返回结构（com.xuejiai.aaf.framework.storage.FileVO） */
interface FileVO {
  url: string
  key: string
}

/** 上传图片到 OSS，以 URL 形式发送给 LLM */
class OssImageAttachmentAdapter implements AttachmentAdapter {
  accept = "image/*"

  async add({ file }: { file: File }): Promise<PendingAttachment> {
    return {
      id: crypto.randomUUID(),
      type: "image",
      name: file.name,
      contentType: file.type,
      file,
      status: { type: "requires-action", reason: "composer-send" }
    }
  }

  async send(attachment: PendingAttachment): Promise<CompleteAttachment> {
    const form = new FormData()
    form.append("file", attachment.file)
    const vo = await backendApi.post<FileVO>("/system/files/upload", form, {
      headers: { "Content-Type": undefined as unknown as string }
    })
    return {
      ...attachment,
      status: { type: "complete" },
      content: [{ type: "image", image: vo.url }]
    }
  }

  async remove() {}
}

import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from "react"
import { toast } from "sonner"
import { buildApiUrl } from "@/lib/api/config"
import { chatApi } from "@/lib/api/rest/ai/chat"
import { useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"
import { useAuthStore } from "@/lib/store/auth-store"
import { getOrCreateAnonymousId } from "@/lib/utils/anonymous-id"
import { OmniVoiceAdapter } from "@/lib/voice/omni-voice-adapter"
import { aigcToolkit } from "../enhance/AigcGenerateToolUI"
import { useAgentRunStore } from "./agent-run-store"

const DEFAULT_AGENT_URL = buildApiUrl("/agui/run")

interface AgUiChatProviderProps {
  children: ReactNode
  /** 自定义端点 URL，默认 /agui/runs */
  url?: string
  /** Agent 初始状态（用于传递页面感知上下文） */
  initialState?: Record<string, unknown>
  /** 初始线程 ID，传入时自动切换到该线程（用于匿名访客恢复历史） */
  initialThreadId?: string
  /** 新建会话回调（默认调用 chatApi.createSession） */
  onNewThread?: () => Promise<void>
}

/**
 * AG-UI 对话 Provider
 * 包裹子组件，提供 AI 助理对话 runtime（SSE 流式通信）
 */
export function AgUiChatProvider({
  children,
  url,
  initialState,
  initialThreadId,
  onNewThread
}: AgUiChatProviderProps) {
  // 将 initialState 序列化为稳定字符串，避免每次渲染对象引用不同导致 agent 重建
  const initialStateKey = JSON.stringify(initialState)

  // biome-ignore lint/correctness/useExhaustiveDependencies: initialState 通过 initialStateKey 跟踪
  const agent = useMemo(() => {
    return new HttpAgent({
      url: url ?? DEFAULT_AGENT_URL,
      initialState: { ...initialState, anonymousId: getOrCreateAnonymousId() }
    })
  }, [url, initialStateKey])

  // 当前 threadId——由后端创建会话时生成，通过此状态传给 threadList 适配器
  const THREAD_KEY = "aaf:chatter-thread-id"
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)

  const [currentThreadId, setCurrentThreadId] = useState<string | undefined>(() => {
    // 优先用外部传入的 initialThreadId，其次从 sessionStorage 恢复
    return (
      initialThreadId ??
      (typeof window !== "undefined"
        ? (sessionStorage.getItem(THREAD_KEY) ?? undefined)
        : undefined)
    )
  })

  // threadId 变化时写入 sessionStorage
  useEffect(() => {
    if (currentThreadId) {
      sessionStorage.setItem(THREAD_KEY, currentThreadId)
    }
  }, [currentThreadId])

  // 已登录且无 threadId 时，自动创建一个新 session
  // biome-ignore lint/correctness/useExhaustiveDependencies: 仅响应登录态变化，避免 threadId 变化后循环创建
  useEffect(() => {
    if (isAuthenticated && !currentThreadId) {
      chatApi
        .createSession({ type: "ai" })
        .then((session) => setCurrentThreadId(session.threadId))
        .catch(() => {
          /* 静默失败 */
        })
    }
  }, [isAuthenticated])

  // 订阅 AG-UI 事件流，把运行状态/工具调用/AAF 专有 CUSTOM 事件写入运行状态 store
  useEffect(() => {
    const run = useAgentRunStore.getState()
    const sub = agent.subscribe({
      onRunStartedEvent: () => run.startRun(),
      onRunFinishedEvent: () => run.finishRun(),
      onRunErrorEvent: ({ event }) => run.errorRun(event.message),
      onToolCallStartEvent: ({ event }) => run.startTool(event.toolCallName),
      onToolCallEndEvent: () => run.endTool(),
      onCustomEvent: ({ event }) => {
        if (event.name === "suggestions") {
          run.setSuggestions(event.value as { prompt: string; label?: string }[])
          return
        }
        if (event.name === "ui_block") {
          const value = event.value as Record<string, unknown> | undefined
          if (value?.uiType === "aigc_task") {
            run.pushAigcTask({
              taskId: value.taskId as number,
              mediaType: value.mediaType as "image" | "video" | "music",
              status: (value.status as "PENDING") ?? "PENDING",
              prompt: (value.prompt as string) ?? "",
              message: (value.message as string) ?? "生成中…"
            })
          }
          return
        }
        if (event.name !== "agent-run") return
        const value = event.value as { type?: string; title?: string; message?: string } | undefined
        run.pushEntry({
          type: value?.type ?? "CUSTOM",
          title: value?.title,
          message: value?.message,
          timestamp: Date.now()
        })
      }
    })
    return () => sub.unsubscribe()
  }, [agent])

  const onError = useCallback((error: Error) => {
    toast.error(classifyError(error))
  }, [])

  const threadList: UseAgUiThreadListAdapter = useMemo(
    () => ({
      threadId: currentThreadId,
      onSwitchToNewThread: async () => {
        const session = await chatApi.createSession({ type: "ai" })
        setCurrentThreadId(session.threadId)
        await onNewThread?.()
      },
      onSwitchToThread: async (threadId: string) => {
        setCurrentThreadId(threadId)
        try {
          const history = await chatApi.getMessages(threadId)
          const messages = history.map((msg) => ({
            id: msg.id,
            role: msg.role === "user" ? ("user" as const) : ("assistant" as const),
            content: [{ type: "text" as const, text: msg.content }],
            createdAt: new Date(msg.createdAt),
            ...(msg.role !== "user" && {
              status: { type: "complete" as const, reason: "stop" as const }
            }),
            ...(msg.role === "user" && { attachments: [] }),
            metadata: { custom: {} }
          })) as ThreadMessage[]
          return { messages }
        } catch {
          return { messages: [] }
        }
      }
    }),
    [onNewThread, currentThreadId]
  )

  const voiceAdapter = useMemo(
    () => new OmniVoiceAdapter({ getToken: () => useAuthStore.getState().accessToken }),
    []
  )

  // HttpAgent@0.0.53 缺少 pendingInterrupts，已在上方通过 Object.defineProperty 补全
  const runtime = useAgUiRuntime({
    agent,
    onError,
    adapters: { threadList, voice: voiceAdapter, attachments: new OssImageAttachmentAdapter() }
  })

  const aui = useAui({ tools: Tools({ toolkit: aigcToolkit }) })

  // 初始线程恢复：currentThreadId 就绪后切换（含从 sessionStorage 恢复 + 新建 session）
  const switchedRef = useRef(false)
  useEffect(() => {
    if (currentThreadId && !switchedRef.current) {
      switchedRef.current = true
      runtime.threads.switchToThread(currentThreadId)
    }
  }, [currentThreadId, runtime.threads])

  return (
    <AssistantRuntimeProvider runtime={runtime} aui={aui}>
      <VoiceCleanup />
      <AigcTaskListener />
      {children}
    </AssistantRuntimeProvider>
  )
}

/** 卸载时断开语音，防止切换页面后 WebSocket 残留 */
function VoiceCleanup() {
  const { disconnect } = useVoiceControls()
  useEffect(
    () => () => {
      disconnect()
    },
    [disconnect]
  )
  return null
}

/**
 * 监听 AIGC 任务 SSE 完成事件，更新 agent-run-store 里的任务卡片状态。
 * 只有 aigcTasks 里存在对应 taskId 时才处理（避免误更新其他页面的卡片）。
 */
function AigcTaskListener() {
  const updateAigcTask = useAgentRunStore((s) => s.updateAigcTask)
  const aigcTasks = useAgentRunStore((s) => s.aigcTasks)

  useAigcTaskStream({
    enabled: aigcTasks.length > 0,
    onCompleted: useCallback(
      (task) => {
        updateAigcTask(task.id, {
          status: "SUCCESS",
          url: task.ossUrl ?? task.resultUrl,
          message: "生成完成"
        })
      },
      [updateAigcTask]
    ),
    onFailed: useCallback(
      (task) => {
        updateAigcTask(task.id, {
          status: "FAIL",
          message: task.errorMsg ?? "生成失败"
        })
      },
      [updateAigcTask]
    )
  })
  return null
}

/** 根据错误信息分类，返回用户友好的提示 */
export function classifyError(error: Error): string {
  const msg = error.message.toLowerCase()
  if (msg.includes("network") || msg.includes("fetch") || msg.includes("failed to fetch")) {
    return "网络连接异常，请检查网络后重试"
  }
  if (msg.includes("quota") || msg.includes("429") || msg.includes("rate limit")) {
    return "请求配额超限，请稍后再试"
  }
  if (msg.includes("model") || msg.includes("500") || msg.includes("internal")) {
    return "模型服务异常，请稍后再试"
  }
  return "对话出现错误，请重试"
}
