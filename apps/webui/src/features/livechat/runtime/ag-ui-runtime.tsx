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
import { useQuery, useQueryClient } from "@tanstack/react-query"
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
  type ChatMessageVO,
  chatApi,
  delegatedTaskKeys,
  getGuestMessages,
  resolveGuestSession,
  useArchiveSession,
  useChatSessions,
  useCreateSession,
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
const GUEST_SESSION_QUERY_KEY = ["guest-customer-service-session"] as const

/** 判断未知值是否为普通对象。 */
function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}

/** 匿名客服只发送纯文本消息，剔除附件、工具结果与客户端上下文。 */
function sanitizeGuestMessages(value: unknown): Record<string, unknown>[] {
  if (!Array.isArray(value)) throw new Error("匿名客服消息格式无效")
  return value.map((message) => {
    if (!isObject(message) || typeof message.role !== "string") {
      throw new Error("匿名客服消息格式无效")
    }

    const content = message.content
    let textContent: string | { type: "text"; text: string }[]
    if (typeof content === "string") {
      textContent = content
    } else if (Array.isArray(content)) {
      textContent = content.map((part) => {
        if (!isObject(part) || part.type !== "text" || typeof part.text !== "string") {
          throw new Error("匿名客服仅支持文本消息")
        }
        return { type: "text" as const, text: part.text }
      })
    } else {
      throw new Error("匿名客服仅支持文本消息")
    }

    return {
      ...(typeof message.id === "string" ? { id: message.id } : {}),
      role: message.role,
      content: textContent
    }
  })
}

/** 匿名 run 仅携带 HttpOnly cookie，并在网络边界移除登录态执行参数。 */
function fetchGuestRun(
  requestUrl: RequestInfo | URL,
  requestInit: RequestInit = {},
  sessionThreadId: string | undefined
): Promise<Response> {
  if (typeof requestInit.body !== "string") {
    return Promise.reject(new Error("匿名客服请求格式无效"))
  }

  let source: unknown
  try {
    source = JSON.parse(requestInit.body)
  } catch {
    return Promise.reject(new Error("匿名客服请求格式无效"))
  }
  if (!isObject(source)) {
    return Promise.reject(new Error("匿名客服请求格式无效"))
  }
  if (!sessionThreadId) {
    return Promise.reject(new Error("匿名客服会话尚未就绪"))
  }

  const body = {
    // assistant-ui 可能保留内部默认 threadId；匿名客服只信任 /session 返回的服务端线程。
    threadId: sessionThreadId,
    runId: source.runId,
    forwardedProps: {},
    messages: sanitizeGuestMessages(source.messages)
  }
  return fetch(requestUrl, {
    ...requestInit,
    body: JSON.stringify(body),
    credentials: "include"
  })
}

/** 将后端历史消息转换为 assistant-ui 线程消息。 */
function toThreadMessages(messages: ChatMessageVO[]): ThreadMessage[] {
  return messages.map((message) => ({
    id: String(message.id),
    role: message.role === "user" ? ("user" as const) : ("assistant" as const),
    content: [{ type: "text" as const, text: message.content }],
    createdAt: new Date(message.createdAt),
    ...(message.role !== "user" && {
      status: { type: "complete" as const, reason: "stop" as const }
    }),
    ...(message.role === "user" && { attachments: [] }),
    metadata: { custom: {} }
  })) as ThreadMessage[]
}

interface AgUiChatProviderProps {
  children: ReactNode
  /** 是否使用匿名客服专用 session、history 与 AG-UI 链路。 */
  guestMode?: boolean
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
  /** 非 guest 模式的初始线程 ID；guest 始终以服务端 session 为准。 */
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
  guestMode = false,
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
  const queryClient = useQueryClient()
  const guestThreadIdRef = useRef<string | undefined>(undefined)

  // biome-ignore lint/correctness/useExhaustiveDependencies: initialState/forwardedProps 通过序列化 key 跟踪
  const agent = useMemo(() => {
    return new ForwardedPropsHttpAgent(
      {
        url: url ?? DEFAULT_AGENT_URL,
        initialState: guestMode ? {} : (initialState ?? {}),
        headers: !guestMode && accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
        ...(guestMode
          ? {
              fetch: (requestUrl: RequestInfo | URL, requestInit?: RequestInit) =>
                fetchGuestRun(requestUrl, requestInit, guestThreadIdRef.current)
            }
          : {})
      },
      guestMode
        ? {}
        : {
            ...forwardedProps,
            mode: "CHAT",
            anonymousId: getOrCreateAnonymousId()
          }
    )
  }, [url, initialStateKey, forwardedPropsKey, accessToken, guestMode])

  // 当前 threadId——登录模式由会话列表校验，guest 模式仅信任公开 session 接口。
  const THREAD_KEY = "aaf:chatter-thread-id"
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const guestSessionQuery = useQuery({
    queryKey: GUEST_SESSION_QUERY_KEY,
    queryFn: resolveGuestSession,
    enabled: guestMode,
    refetchOnMount: "always"
  })

  const [currentThreadId, setCurrentThreadId] = useState<string | undefined>(() => {
    if (guestMode) return undefined
    return (
      initialThreadId ??
      (typeof window !== "undefined"
        ? (sessionStorage.getItem(THREAD_KEY) ?? undefined)
        : undefined)
    )
  })

  // guest/auth Provider 由上层 mode key 强制重建，挂载时清空跨 runtime 的瞬时 UI 状态。
  useEffect(() => {
    useAgentRunStore.setState({
      phase: "idle",
      activeTool: null,
      entries: [],
      suggestions: [],
      aigcTasks: [],
      subTaskActivities: {},
      selectedRole: null,
      currentThreadId: undefined,
      diagnostic: {}
    })
  }, [])

  useEffect(() => {
    const sessionThreadId =
      guestMode && guestSessionQuery.isFetchedAfterMount
        ? guestSessionQuery.data?.threadId
        : undefined
    guestThreadIdRef.current = sessionThreadId
  }, [guestMode, guestSessionQuery.data?.threadId, guestSessionQuery.isFetchedAfterMount])

  // guest 不读写登录 sessionStorage，也不把服务端客服线程复制到 Zustand。
  useEffect(() => {
    if (guestMode) {
      useAgentRunStore.getState().setCurrentThreadId(undefined)
      return
    }
    if (currentThreadId) {
      sessionStorage.setItem(THREAD_KEY, currentThreadId)
    }
    useAgentRunStore.getState().setCurrentThreadId(currentThreadId)
  }, [currentThreadId, guestMode])

  // feedbackAdapter.submit 是同步回调，用 ref 读取避免闭包捕获过期的 currentThreadId
  const currentThreadIdRef = useRef(currentThreadId)
  useEffect(() => {
    currentThreadIdRef.current = currentThreadId
  }, [currentThreadId])

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
        if (event.name.startsWith("aaf.task.")) {
          void queryClient.invalidateQueries({
            queryKey: [...delegatedTaskKeys.all, "list"]
          })
          return
        }
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
  }, [agent, queryClient])

  const onError = useCallback((error: Error) => {
    toast.error(classifyError(error))
  }, [])

  // AAF-114 官方模式改造：threadList 从"仅 threadId+切换回调"扩展为完整
  // ExternalStoreThreadListAdapter 契约（threads/archivedThreads + rename/archive/unarchive/delete），
  // 交由 ThreadListPrimitive 消费；列表数据源统一由 TanStack Query 管理（useChatSessions），
  // 不再由 SessionPopover 自行维护并行状态。
  const { data: chatSessions, isFetched: chatSessionsFetched } = useChatSessions({
    enabled: !guestMode && isAuthenticated
  })
  const { mutate: createSession, mutateAsync: createSessionAsync } = useCreateSession()
  const renameSessionMutation = useRenameSession()
  const archiveSessionMutation = useArchiveSession()
  const unarchiveSessionMutation = useUnarchiveSession()
  const deleteSessionMutation = useDeleteSession()
  const autoCreateThreadPendingRef = useRef(false)

  // sessionStorage 只保存候选 threadId；当前用户会话列表是有效性的唯一依据。
  useEffect(() => {
    if (guestMode || !isAuthenticated || !chatSessionsFetched) return
    const currentThreadExists = Boolean(
      currentThreadId &&
        chatSessions?.some(
          (session) => session.threadId === currentThreadId && session.type === "ai"
        )
    )
    if (currentThreadExists) {
      autoCreateThreadPendingRef.current = false
      return
    }
    if (autoCreateThreadPendingRef.current) return

    autoCreateThreadPendingRef.current = true
    sessionStorage.removeItem(THREAD_KEY)
    setCurrentThreadId(undefined)
    createSession(
      { type: "ai" },
      {
        onSuccess: (session) => setCurrentThreadId(session.threadId),
        onError: () => {
          autoCreateThreadPendingRef.current = false
          toast.error("创建 AI 会话失败，请重试")
        }
      }
    )
  }, [
    guestMode,
    isAuthenticated,
    chatSessionsFetched,
    chatSessions,
    currentThreadId,
    createSession
  ])

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

  const authenticatedThreadList: UseAgUiThreadListAdapter = useMemo(
    () => ({
      threadId: currentThreadId,
      threads: regularThreads,
      archivedThreads,
      onSwitchToNewThread: async () => {
        autoCreateThreadPendingRef.current = true
        try {
          const session = await createSessionAsync({ type: "ai" })
          setCurrentThreadId(session.threadId)
          await onNewThread?.()
        } catch (error) {
          autoCreateThreadPendingRef.current = false
          throw error
        }
      },
      onSwitchToThread: async (threadId: string) => {
        // 历史加载成功后再切换，失败时保留原线程。
        const history = await chatApi.getMessages(threadId)
        setCurrentThreadId(threadId)
        return { messages: toThreadMessages(history) }
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
        if (threadId === currentThreadId) {
          sessionStorage.removeItem(THREAD_KEY)
          setCurrentThreadId(undefined)
        }
      }
    }),
    [
      onNewThread,
      createSessionAsync,
      currentThreadId,
      regularThreads,
      archivedThreads,
      renameSessionMutation,
      archiveSessionMutation,
      unarchiveSessionMutation,
      deleteSessionMutation
    ]
  )

  const guestSession = guestSessionQuery.isFetchedAfterMount ? guestSessionQuery.data : undefined
  const guestThreadId = guestSession?.threadId
  const guestThreads = useMemo(
    () =>
      guestThreadId ? [{ status: "regular" as const, id: guestThreadId, title: "AI 客服" }] : [],
    [guestThreadId]
  )
  const guestThreadList: UseAgUiThreadListAdapter = useMemo(
    () => ({
      threadId: currentThreadId,
      threads: guestThreads,
      archivedThreads: [],
      onSwitchToNewThread: async () => {
        if (guestThreadId) {
          setCurrentThreadId(guestThreadId)
        }
      },
      onSwitchToThread: async (threadId: string) => {
        if (!guestSession || threadId !== guestSession.threadId) {
          throw new Error("匿名客服会话尚未就绪")
        }
        // assistant-ui 要求先更新选中线程，再异步加载该线程历史。
        setCurrentThreadId(threadId)
        const history = await getGuestMessages(guestSession)
        return { messages: toThreadMessages(history) }
      },
      onRename: async () => {},
      onArchive: async () => {},
      onUnarchive: async () => {},
      onDelete: async () => {}
    }),
    [currentThreadId, guestSession, guestThreadId, guestThreads]
  )

  const threadList = guestMode ? guestThreadList : authenticatedThreadList

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

  // guest 仅暴露公开客服 threadList，避免触发要求登录的反馈、语音和上传链路。
  const adapters = guestMode
    ? { threadList }
    : {
        threadList,
        voice: voiceAdapter,
        attachments: attachmentAdapter,
        feedback: feedbackAdapter
      }

  const runtime = useAgUiRuntime({
    agent,
    onError,
    showThinking,
    adapters
  })

  const aui = useAui(
    guestMode ? {} : { tools: Tools({ toolkit: { ...aigcToolkit, ...renderUiBlockToolkit } }) }
  )

  // 初始线程恢复：guest 等待 session；登录线程先经当前用户会话列表验证所有权。
  const switchedRef = useRef(false)
  const [initialThreadReady, setInitialThreadReady] = useState(!guestMode)
  const [initialThreadError, setInitialThreadError] = useState(false)
  const initialThreadIdToSwitch = guestMode ? guestThreadId : currentThreadId
  const initialThreadOwned = guestMode
    ? Boolean(guestSession && guestThreadId === guestSession.threadId)
    : Boolean(
        isAuthenticated &&
          chatSessionsFetched &&
          currentThreadId &&
          chatSessions?.some(
            (session) => session.threadId === currentThreadId && session.type === "ai"
          )
      )
  useEffect(() => {
    if (!initialThreadIdToSwitch || !initialThreadOwned || switchedRef.current) return
    switchedRef.current = true
    setInitialThreadError(false)
    void runtime.threads
      .switchToThread(initialThreadIdToSwitch)
      .then(() => setInitialThreadReady(true))
      .catch((error: unknown) => {
        switchedRef.current = false
        setInitialThreadError(true)
        onError(error instanceof Error ? error : new Error("客服历史加载失败"))
      })
  }, [initialThreadIdToSwitch, initialThreadOwned, onError, runtime.threads])

  return (
    <AssistantRuntimeProvider runtime={runtime} aui={aui}>
      {!guestMode && <VoiceCleanup />}
      {guestMode && (!guestThreadId || !initialThreadReady) ? (
        <div className="flex h-full items-center justify-center text-muted-foreground text-sm">
          {guestSessionQuery.isError || initialThreadError
            ? "连接客服失败，请稍后重试"
            : "正在连接客服…"}
        </div>
      ) : (
        children
      )}
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
  if (msg.includes("401") || msg.includes("unauthorized") || msg.includes("未授权")) {
    return "客服会话已失效，请刷新后重试"
  }
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
