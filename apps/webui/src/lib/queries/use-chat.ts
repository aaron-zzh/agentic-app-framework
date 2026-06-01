/**
 * 聊天相关 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { type CreateSessionParams, chatApi, type SendMessageParams } from "@/lib/api/rest/ai/chat"
const KEYS = {
  all: ["chat"] as const,
  sessions: ["chat", "sessions"] as const,
  messages: (sessionId: string) => ["chat", "messages", sessionId] as const
}

/** 会话列表 */
export function useChatSessions() {
  return useQuery({
    queryKey: KEYS.sessions,
    queryFn: () => chatApi.listSessions()
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
