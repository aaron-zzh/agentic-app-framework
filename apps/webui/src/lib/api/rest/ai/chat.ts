/**
 * 聊天 API 客户端——会话 CRUD + 消息历史
 * @author AaronZZH & Kiro
 */

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
