/**
 * 待办 API 客户端
 * @author AaronZZH & Kiro
 */

import { ApiError, type PageResult } from "./client"

/** 待办来源类型 */
export type TodoSourceType = "mention" | "schedule" | "manual"

/** 待办状态 */
export type TodoStatus = "pending" | "done" | "dismissed"

/** 待办项 */
export interface TodoItem {
  id: string
  title: string
  sourceType: TodoSourceType
  sourceEntity?: string
  sourceId?: string
  assigneeId: string
  status: TodoStatus
  dueDate?: string
  createdAt: string
}

export interface TodoListParams {
  page?: number
  pageSize?: number
  status?: TodoStatus
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

export const todoApi = {
  /** 待办列表 */
  list: (params: TodoListParams = {}) => {
    const qs = new URLSearchParams()
    if (params.page) qs.set("page", String(params.page))
    if (params.pageSize) qs.set("pageSize", String(params.pageSize))
    if (params.status) qs.set("status", params.status)
    const q = qs.toString()
    return req<PageResult<TodoItem>>(`/todos${q ? `?${q}` : ""}`)
  },

  /** 标记完成 */
  complete: (id: string) => req<void>(`/todos/${id}/complete`, { method: "PUT" }),

  /** 标记忽略 */
  dismiss: (id: string) => req<void>(`/todos/${id}/dismiss`, { method: "PUT" })
}
