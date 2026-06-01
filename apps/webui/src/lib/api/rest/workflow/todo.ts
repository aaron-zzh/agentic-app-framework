/**
 * 待办 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"
import type { PageResult } from "../entity/crud"

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

export const todoApi = {
  /** 待办列表 */
  list: (params: TodoListParams = {}) =>
    backendApi.get<PageResult<TodoItem>>("/todos", { params }),

  /** 标记完成 */
  complete: (id: string) => backendApi.put<void>(`/todos/${id}/complete`),

  /** 标记忽略 */
  dismiss: (id: string) => backendApi.put<void>(`/todos/${id}/dismiss`)
}
