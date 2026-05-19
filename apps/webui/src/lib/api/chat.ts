/**
 * 聊天 API 客户端——会话 CRUD + 消息历史
 * @author AaronZZH & Kiro
 */

import { ApiError, type PageResult } from "./client"

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

const BASE = process.env.NEXT_PUBLIC_API_URL ?? "/api"

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { "Content-Type": "application/json", ...init?.headers },
    ...init
  })
  if (!res.ok) throw new ApiError(res.status, `请求失败: ${res.statusText}`)
  const json = await res.json()
  if (json.code !== 0) throw new ApiError(json.code, json.message ?? "未知错误")
  return json.data as T
}

export const chatApi = {
  /** 创建会话 */
  createSession: (params: CreateSessionParams) =>
    req<ChatSession>("/system/chat/sessions", {
      method: "POST",
      body: JSON.stringify(params)
    }),

  /** 获取会话列表 */
  listSessions: () => req<PageResult<ChatSession>>("/system/chat/sessions"),

  /** 获取会话消息历史 */
  getMessages: (sessionId: string) =>
    req<ChatMessageVO[]>(`/system/chat/sessions/${sessionId}/messages`),

  /** 发送消息（REST 通道，非 WebSocket） */
  sendMessage: (params: SendMessageParams) =>
    req<ChatMessageVO>("/system/chat/messages", {
      method: "POST",
      body: JSON.stringify(params)
    })
}
