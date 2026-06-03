/**
 * 聊天 API 客户端——会话 CRUD + 消息历史
 * @author AaronZZH & Kiro
 */

import { restEndpoints } from "../endpoints"
import { request } from "../entity/crud"

/** 聊天会话 */
export interface ChatSession {
  id: string
  title: string
  type: "ai" | "livechat" | "im"
  userId: string
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
    request<ChatSession>(restEndpoints.ai.chatSessions, {
      method: "POST",
      body: JSON.stringify(params)
    }),

  /** 获取会话列表 */
  listSessions: () => request<ChatSession[]>(restEndpoints.ai.chatSessions),

  /** 获取会话消息历史 */
  getMessages: (sessionId: string) =>
    request<ChatMessageVO[]>(restEndpoints.ai.chatSessionMessages(sessionId)),

  /** 发送消息（REST 通道，非 WebSocket） */
  sendMessage: (params: SendMessageParams) =>
    request<ChatMessageVO>(restEndpoints.ai.chatMessages, {
      method: "POST",
      body: JSON.stringify(params)
    })
}
