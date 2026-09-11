/**
 * 聊天 API 客户端——会话 CRUD + 消息历史
 * @author AaronZZH & Kiro
 */

import { buildApiUrl } from "@/lib/api/config"
import { ApiError } from "@/lib/api/errors"
import type { ApiResult } from "@/lib/api/types"
import { backendApi } from "../backend-client"
import { restEndpoints } from "../endpoints"
import { fetchList } from "../entity/crud"

/** 聊天会话 */
export interface ChatSession {
  [key: string]: unknown
  id: string
  title: string
  type: "ai" | "livechat" | "im"
  status: "ACTIVE" | "ARCHIVED" | "BOT" | "WAITING" | "CLOSED"
  userId: string
  threadId: string
  agentId?: string
  createdAt: string
  updatedAt: string
}

/** 聊天消息（后端 ChatMessageVO） */
export interface ChatMessageVO {
  id: string
  sessionId: string
  role: "user" | "assistant" | "system"
  content: string
  createdAt: string
}

export interface CreateSessionParams {
  title?: string
  type: ChatSession["type"]
  agentId?: string
}

export interface SendMessageParams {
  sessionId: string
  content: string
}

/** 匿名客服会话；threadId 完全由服务端 cookie 会话决定。 */
export interface GuestSession {
  threadId: string
}

/** 匿名客服公开请求：不经过登录拦截器，只携带 HttpOnly visitor cookie。 */
async function guestRequest<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(buildApiUrl(path), {
    ...init,
    credentials: "include",
    headers: { Accept: "application/json", ...init.headers }
  })

  let result: ApiResult<T>
  try {
    result = (await response.json()) as ApiResult<T>
  } catch {
    throw new ApiError(response.status || 0, "客服响应格式无效")
  }

  if (!response.ok) {
    throw new ApiError(response.status, result.message ?? "客服请求失败", result.data)
  }
  if (result.code !== 0) {
    throw new ApiError(result.code, result.message ?? "客服请求失败", result.data)
  }
  return result.data
}

/** 解析当前匿名客服会话，服务端同时通过 Set-Cookie 维护身份。 */
export function resolveGuestSession(): Promise<GuestSession> {
  return guestRequest<GuestSession>(restEndpoints.public.customerServiceSession, {
    method: "POST"
  })
}

/** 读取服务端刚解析出的匿名客服线程消息历史。 */
export function getGuestMessages(session: GuestSession): Promise<ChatMessageVO[]> {
  return guestRequest<ChatMessageVO[]>(
    restEndpoints.public.customerServiceMessages(session.threadId),
    { method: "GET" }
  )
}

export const chatApi = {
  /** 创建会话 */
  createSession: (params: CreateSessionParams) =>
    backendApi.post<ChatSession>(restEndpoints.ai.chatSessions, params),

  /** 获取会话列表（全量，兼容旧用法） */
  listSessions: () => backendApi.get<ChatSession[]>(restEndpoints.ai.chatSessions),

  /** 分页查询会话列表（支持标题模糊搜索），走 ConversationController 标准分页接口 */
  pageListSessions: (params?: { page?: number; pageSize?: number; search?: string }) =>
    fetchList<ChatSession>({ apiPath: restEndpoints.ai.chatConversations }, params),

  /** 获取会话消息历史（参数为 threadId，非数值 id） */
  getMessages: (threadId: string) =>
    backendApi.get<ChatMessageVO[]>(restEndpoints.ai.chatSessionMessages(threadId)),

  /** 重命名会话（参数为 threadId，assistant-ui ThreadListAdapter 契约） */
  renameSession: (threadId: string, title: string) =>
    backendApi.put<void>(restEndpoints.ai.chatSessionRename(threadId), { title }),

  /** 归档会话（参数为 threadId） */
  archiveSession: (threadId: string) =>
    backendApi.post<void>(restEndpoints.ai.chatSessionArchive(threadId)),

  /** 取消归档会话（参数为 threadId） */
  unarchiveSession: (threadId: string) =>
    backendApi.post<void>(restEndpoints.ai.chatSessionUnarchive(threadId)),

  /** 删除会话（软删除，参数为 threadId） */
  deleteSession: (threadId: string) =>
    backendApi.delete<void>(restEndpoints.ai.chatSessionDelete(threadId)),

  /** 发送消息（REST 通道，非 WebSocket） */
  sendMessage: (params: SendMessageParams) =>
    backendApi.post<ChatMessageVO>(restEndpoints.ai.chatMessages, params),

  /** 获取欢迎页建议问题 */
  getSuggestions: (agentId?: string) =>
    backendApi.get<{ prompt: string; label?: string }[]>(restEndpoints.ai.chatSuggestions(agentId)),

  /**
   * 提交消息反馈（点赞/点踩，AAF-114 #11412）。
   *
   * aguiMessageId 是前端 assistant-ui ThreadMessage.id（AI 消息为服务端下发的 replyId:blockId），
   * 不是数据库自增 id；reason 仅用于负反馈时前端补充采集的原因文本。
   */
  submitMessageFeedback: (
    threadId: string,
    aguiMessageId: string,
    params: { type: "positive" | "negative"; reason?: string; model?: string; runId?: string }
  ) => backendApi.post<void>(restEndpoints.ai.chatMessageFeedback(threadId, aguiMessageId), params)
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

const KEYS = {
  all: ["chat"] as const,
  sessions: ["chat", "sessions"] as const,
  messages: (sessionId: string) => ["chat", "messages", sessionId] as const
}

/** 会话列表 */
export function useChatSessions(options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: KEYS.sessions,
    queryFn: () => chatApi.listSessions(),
    enabled: options?.enabled ?? true
  })
}

/** 会话消息历史 */
export function useChatMessages(sessionId: string | undefined) {
  return useQuery({
    queryKey: KEYS.messages(sessionId ?? ""),
    queryFn: () => chatApi.getMessages(sessionId as NonNullable<typeof sessionId>),
    enabled: !!sessionId
  })
}

/** 创建会话 */
export function useCreateSession() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (params: CreateSessionParams) => chatApi.createSession(params),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.sessions })
    }
  })
}

/** 重命名会话（参数为 threadId） */
export function useRenameSession() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ threadId, title }: { threadId: string; title: string }) =>
      chatApi.renameSession(threadId, title),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.sessions })
    }
  })
}

/** 归档会话（参数为 threadId） */
export function useArchiveSession() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (threadId: string) => chatApi.archiveSession(threadId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.sessions })
    }
  })
}

/** 取消归档会话（参数为 threadId） */
export function useUnarchiveSession() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (threadId: string) => chatApi.unarchiveSession(threadId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.sessions })
    }
  })
}

/** 删除会话（软删除，参数为 threadId） */
export function useDeleteSession() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (threadId: string) => chatApi.deleteSession(threadId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.sessions })
    }
  })
}

/** 发送消息（REST 通道） */
export function useSendMessage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (params: SendMessageParams) => chatApi.sendMessage(params),
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: KEYS.messages(variables.sessionId) })
    }
  })
}

/** 使消息缓存失效（WebSocket 收到新消息时调用） */
export function useInvalidateChatMessages() {
  const qc = useQueryClient()
  return (sessionId: string) => {
    qc.invalidateQueries({ queryKey: KEYS.messages(sessionId) })
  }
}
