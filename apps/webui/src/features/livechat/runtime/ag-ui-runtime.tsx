/**
 * AG-UI Runtime Provider——AI 助理对话的 runtime 配置
 * 使用 @ag-ui/client HttpAgent 对接后端 AG-UI SSE 端点，
 * 通过 useAgUiRuntime 将事件流转为 assistant-ui 可消费的 runtime
 *
 * 支持自定义端点 url、线程共享状态（state）与本次 run 调用参数（forwardedProps），供 ChatterRuntime 复用
 *
 * @author AaronZZH & Kiro
 */

"use client"

import {
  AssistantRuntimeProvider,
  type AttachmentAdapter,
  type CompleteAttachment,
  CompositeAttachmentAdapter,
  type FeedbackAdapter,
  type PendingAttachment,
  SimpleTextAttachmentAdapter,
  type ThreadMessage,
  Tools,
  useAui,
  useVoiceControls
} from "@assistant-ui/react"
import { type UseAgUiThreadListAdapter, useAgUiRuntime } from "@assistant-ui/react-ag-ui"
import type { AafAiTaskEvent } from "@/lib/api/rest/ai"
import { backendApi } from "@/lib/api/rest/backend-client"
import { ForwardedPropsHttpAgent } from "./forwarded-props-http-agent"

/** 文件上传返回结构（StoredFile）。 */
interface StoredFile {
  fileId: number
  url: string
  key: string
  originalName: string
  mimeType: string | null
  size: number
  contentHash: string | null
  uploaderId: number | null
}

const MAX_IMAGE_ATTACHMENT_BYTES = 20 * 1024 * 1024
const MAX_TEXT_ATTACHMENT_BYTES = 1024 * 1024
const MAX_DOCUMENT_ATTACHMENT_BYTES = 20 * 1024 * 1024

/** 上传图片到 OSS；URL 作为 AG-UI source，filename 承载服务端文件 key。 */
export class OssImageAttachmentAdapter implements AttachmentAdapter {
  accept = "image/jpeg,image/png,image/webp,image/gif"

  async add({ file }: { file: File }): Promise<PendingAttachment> {
    if (file.size > MAX_IMAGE_ATTACHMENT_BYTES) {
      throw new Error("图片不能超过 20MB")
    }
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
    const vo = await backendApi.post<StoredFile>("/system/files/upload", form, {
      headers: { "Content-Type": undefined as unknown as string }
    })
    return {
      ...attachment,
      status: { type: "complete" },
      content: [
        {
          type: "file",
          data: vo.url,
          mimeType: vo.mimeType ?? attachment.contentType ?? "application/octet-stream",
          filename: vo.key
        }
      ]
    }
  }

  async remove() {}
}

/**
 * 上传文档（PDF/DOCX/Markdown/HTML/TXT）到 OSS；filename 承载服务端文件 key，服务端按 mimeType 分流到
 * {@code DocumentAttachmentGuardService} 解析（AAF-114 #11410）。react-ag-ui 按 mimeType 把非图片 file
 * part 映射为 AG-UI {@code document} content part，与 {@link OssImageAttachmentAdapter} 结构一致，仅
 * accept 与大小限制不同。
 */
export class OssDocumentAttachmentAdapter implements AttachmentAdapter {
  accept =
    "application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,.pdf,.docx,.md,.markdown,.html,.htm,.txt"

  async add({ file }: { file: File }): Promise<PendingAttachment> {
    if (file.size > MAX_DOCUMENT_ATTACHMENT_BYTES) {
      throw new Error("文档不能超过 20MB")
    }
    return {
      id: crypto.randomUUID(),
      type: "document",
      name: file.name,
      contentType: file.type,
      file,
      status: { type: "requires-action", reason: "composer-send" }
    }
  }

  async send(attachment: PendingAttachment): Promise<CompleteAttachment> {
    const form = new FormData()
    form.append("file", attachment.file)
    const vo = await backendApi.post<StoredFile>("/system/files/upload", form, {
      headers: { "Content-Type": undefined as unknown as string }
    })
    return {
      ...attachment,
      status: { type: "complete" },
      content: [
        {
          type: "file",
          data: vo.url,
          mimeType: vo.mimeType ?? attachment.contentType ?? "application/octet-stream",
          filename: vo.key
        }
      ]
    }
  }

  async remove() {}
}

class LimitedTextAttachmentAdapter extends SimpleTextAttachmentAdapter {
  override accept =
    "text/plain,text/html,text/markdown,text/csv,text/xml,text/json,text/css,application/json,.txt,.md,.markdown,.csv,.json,.html,.xml,.css"

  override async add({ file }: { file: File }): Promise<PendingAttachment> {
    if (file.size > MAX_TEXT_ATTACHMENT_BYTES) {
      throw new Error("文本附件不能超过 1MB")
    }
    return super.add({ file })
  }
}

import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from "react"
import { toast } from "sonner"
import { renderUiBlockToolkit } from "@/features/chatter/runtime/ui-block/render-ui-block-toolkit"
import { buildApiUrl } from "@/lib/api/config"
import {
  chatApi,
  useArchiveSession,
  useChatSessions,
  useDeleteSession,
  useRenameSession,
  useUnarchiveSession
} from "@/lib/api/rest/ai"
import { useAuthStore } from "@/lib/store/auth-store"
import { getOrCreateAnonymousId } from "@/lib/utils/anonymous-id"
import { OmniVoiceAdapter } from "@/lib/voice/omni-voice-adapter"
import { aigcToolkit } from "../enhance/AigcGenerateToolUI"
import { useAgentRunStore } from "./agent-run-store"
import { applyJsonPatch, type JsonPatchOperation } from "./json-patch"

const DEFAULT_AGENT_URL = buildApiUrl("/agui/run")

interface AgUiChatProviderProps {
  children: ReactNode
  /** 自定义端点 URL，默认 /agui/run */
  url?: string
  /**
   * 线程级共享状态（AG-UI `state`）——只放页面感知上下文这类真正的状态。
   *
   * 调用参数不要放这里，放 {@link AgUiChatProviderProps.forwardedProps}。
   */
  initialState?: Record<string, unknown>
  /** 本次 run 的一次性调用参数（AG-UI `forwardedProps`）：assistantId、taskModelSelection 等。 */
  forwardedProps?: Record<string, unknown>
  /** 初始线程 ID，传入时自动切换到该线程（用于匿名访客恢复历史） */
  initialThreadId?: string
  /** 新建会话回调（默认调用 chatApi.createSession） */
  onNewThread?: () => Promise<void>
  /** 是否显示服务端允许公开的 reasoning 内容，默认 true。 */
  showThinking?: boolean
}

/**
 * AG-UI 对话 Provider
 * 包裹子组件，提供 AI 助理对话 runtime（SSE 流式通信）
 */
export function AgUiChatProvider({
  children,
  url,
  initialState,
  forwardedProps,
  initialThreadId,
  onNewThread,
  showThinking = true
}: AgUiChatProviderProps) {
  // 将 initialState / forwardedProps 序列化为稳定字符串，避免每次渲染对象引用不同导致 agent 重建
  const initialStateKey = JSON.stringify(initialState)
  const forwardedPropsKey = JSON.stringify(forwardedProps)
  const accessToken = useAuthStore((s) => s.accessToken)

  // biome-ignore lint/correctness/useExhaustiveDependencies: initialState/forwardedProps 通过序列化 key 跟踪
  const agent = useMemo(() => {
    return new ForwardedPropsHttpAgent(
      {
        url: url ?? DEFAULT_AGENT_URL,
        initialState: initialState ?? {},
        headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {}
      },
      {
        ...forwardedProps,
        mode: "CHAT",
        anonymousId: getOrCreateAnonymousId()
      }
    )
  }, [url, initialStateKey, forwardedPropsKey, accessToken])

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

  // threadId 变化时写入 sessionStorage，并同步到 agent-run-store 供子树组件（如反馈 ActionBar）读取
  useEffect(() => {
    if (currentThreadId) {
      sessionStorage.setItem(THREAD_KEY, currentThreadId)
    }
    useAgentRunStore.getState().setCurrentThreadId(currentThreadId)
  }, [currentThreadId])

  // feedbackAdapter.submit 是同步回调，用 ref 读取避免闭包捕获过期的 currentThreadId
  const currentThreadIdRef = useRef(currentThreadId)
  useEffect(() => {
    currentThreadIdRef.current = currentThreadId
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
      onRunStartedEvent: ({ event }) => {
        run.startRun()
        run.setRunId(event.runId)
      },
      onRunFinishedEvent: () => run.finishRun(),
      onRunErrorEvent: ({ event }) => run.errorRun(event.message),
      onToolCallStartEvent: ({ event }) => run.startTool(event.toolCallName),
      onToolCallEndEvent: () => run.endTool(),
      onActivitySnapshotEvent: ({ event }) => {
        if (event.activityType !== "SUBTASK") return
        const content = event.content as Record<string, unknown>
        run.upsertSubTaskActivity({
          subTaskId: event.messageId,
          kind: (content.kind as string) ?? "",
          roleKey: (content.roleKey as string) ?? "",
          status: (content.status as string) ?? ""
        })
      },
      onActivityDeltaEvent: ({ event }) => {
        if (event.activityType !== "SUBTASK") return
        const current = useAgentRunStore.getState().subTaskActivities[event.messageId]
        if (!current) return
        const patched = applyJsonPatch(
          current as unknown as Record<string, unknown>,
          event.patch as JsonPatchOperation[]
        )
        run.upsertSubTaskActivity({
          subTaskId: event.messageId,
          kind: (patched.kind as string) ?? current.kind,
          roleKey: (patched.roleKey as string) ?? current.roleKey,
          status: (patched.status as string) ?? current.status
        })
      },
      onCustomEvent: ({ event }) => {
        if (event.name === "aaf.role.resolved") {
          const value = event.value as AafAiTaskEvent | undefined
          const roleKey = value?.data.roleKey
          const roleName = value?.data.roleName
          const routeConstraint = value?.data.routeConstraint
          if (typeof roleKey === "string" && typeof roleName === "string") {
            run.setSelectedRole({
              roleKey,
              roleName,
              routeConstraint: typeof routeConstraint === "string" ? routeConstraint : "AUTO"
            })
          }
          return
        }
        if (event.name === "suggestions") {
          run.setSuggestions(event.value as { prompt: string; label?: string }[])
          return
        }
        if (event.name === "aaf.model.completed") {
          // AAF-114 #11412 诊断入口：CUSTOM 载荷固定包一层 AafAiTaskEvent 信封（见后端
          // PublicEventFallbackConverter.customValue），业务字段在 value.data 里，不在顶层；
          // modelId 是 AAF 内部稳定模型标识，非供应商原始模型名，已由 ExecutionEventPublicMapper 白名单放行
          const value = event.value as AafAiTaskEvent | undefined
          const data = value?.data
          run.updateDiagnostic({
            modelId: typeof data?.modelId === "string" ? data.modelId : undefined,
            inputTokens: typeof data?.inputTokens === "number" ? data.inputTokens : undefined,
            outputTokens: typeof data?.outputTokens === "number" ? data.outputTokens : undefined,
            cachedTokens: typeof data?.cachedTokens === "number" ? data.cachedTokens : undefined,
            durationSeconds:
              typeof data?.durationSeconds === "number" ? data.durationSeconds : undefined
          })
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

  // AAF-114 官方模式改造：threadList 从"仅 threadId+切换回调"扩展为完整
  // ExternalStoreThreadListAdapter 契约（threads/archivedThreads + rename/archive/unarchive/delete），
  // 交由 ThreadListPrimitive 消费；列表数据源统一由 TanStack Query 管理（useChatSessions），
  // 不再由 SessionPopover 自行维护并行状态。
  const { data: chatSessions } = useChatSessions({ enabled: isAuthenticated })
  const renameSessionMutation = useRenameSession()
  const archiveSessionMutation = useArchiveSession()
  const unarchiveSessionMutation = useUnarchiveSession()
  const deleteSessionMutation = useDeleteSession()

  const regularThreads = useMemo(
    () =>
      (chatSessions ?? [])
        .filter((s) => s.status !== "ARCHIVED")
        .map((s) => ({ status: "regular" as const, id: s.threadId, title: s.title })),
    [chatSessions]
  )
  const archivedThreads = useMemo(
    () =>
      (chatSessions ?? [])
        .filter((s) => s.status === "ARCHIVED")
        .map((s) => ({ status: "archived" as const, id: s.threadId, title: s.title })),
    [chatSessions]
  )

  const threadList: UseAgUiThreadListAdapter = useMemo(
    () => ({
      threadId: currentThreadId,
      threads: regularThreads,
      archivedThreads,
      onSwitchToNewThread: async () => {
        const session = await chatApi.createSession({ type: "ai" })
        setCurrentThreadId(session.threadId)
        await onNewThread?.()
      },
      onSwitchToThread: async (threadId: string) => {
        // AAF-114 #11411：历史加载失败不伪装成空会话——只有成功后才切换 currentThreadId，
        // 失败时保留在原线程并把异常原样抛给调用方（SessionPopover 捕获后提示用户重试）。
        const history = await chatApi.getMessages(threadId)
        const messages = history.map((msg) => ({
          id: String(msg.id),
          role: msg.role === "user" ? ("user" as const) : ("assistant" as const),
          content: [{ type: "text" as const, text: msg.content }],
          createdAt: new Date(msg.createdAt),
          ...(msg.role !== "user" && {
            status: { type: "complete" as const, reason: "stop" as const }
          }),
          ...(msg.role === "user" && { attachments: [] }),
          metadata: { custom: {} }
        })) as ThreadMessage[]
        setCurrentThreadId(threadId)
        return { messages }
      },
      onRename: async (threadId, newTitle) => {
        await renameSessionMutation.mutateAsync({ threadId, title: newTitle })
      },
      onArchive: async (threadId) => {
        await archiveSessionMutation.mutateAsync(threadId)
      },
      onUnarchive: async (threadId) => {
        await unarchiveSessionMutation.mutateAsync(threadId)
      },
      onDelete: async (threadId) => {
        await deleteSessionMutation.mutateAsync(threadId)
      }
    }),
    [
      onNewThread,
      currentThreadId,
      regularThreads,
      archivedThreads,
      renameSessionMutation,
      archiveSessionMutation,
      unarchiveSessionMutation,
      deleteSessionMutation
    ]
  )

  const voiceAdapter = useMemo(
    () => new OmniVoiceAdapter({ getToken: () => useAuthStore.getState().accessToken }),
    []
  )

  const attachmentAdapter = useMemo(
    () =>
      new CompositeAttachmentAdapter([
        new OssImageAttachmentAdapter(),
        new OssDocumentAttachmentAdapter(),
        new LimitedTextAttachmentAdapter()
      ]),
    []
  )

  const feedbackAdapter = useMemo<FeedbackAdapter>(
    () => ({
      submit: ({ message, type }) => {
        const threadId = currentThreadIdRef.current
        if (!threadId) return
        const { modelId, runId } = useAgentRunStore.getState().diagnostic
        chatApi
          .submitMessageFeedback(threadId, message.id, { type, model: modelId, runId })
          .catch(() => toast.error("反馈提交失败，请重试"))
      }
    }),
    []
  )

  // HttpAgent@0.0.53 缺少 pendingInterrupts，已在上方通过 Object.defineProperty 补全
  const runtime = useAgUiRuntime({
    agent,
    onError,
    showThinking,
    adapters: {
      threadList,
      voice: voiceAdapter,
      attachments: attachmentAdapter,
      feedback: feedbackAdapter
    }
  })

  const aui = useAui({ tools: Tools({ toolkit: { ...aigcToolkit, ...renderUiBlockToolkit } }) })

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
