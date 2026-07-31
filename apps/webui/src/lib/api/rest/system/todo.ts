/**
 * 系统待办 API 与查询 Hooks——对齐后端 TodoController 真实契约（BaseCrudController 标准 CRUD + /status 快捷端点）。
 *
 * 待办资源属于 system 领域；Studio 待办页面可直接使用本模块的 Todo API、类型和 Query Hooks。
 *
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PageResult } from "../../types"
import { backendApi } from "../backend-client"

/** 待办分类，对应后端 TodoCategoryEnum */
export type TodoCategory = "todo" | "call" | "email" | "meeting"

/** 待办状态，对应后端 TodoStatusEnum */
export type TodoStatus = "pending" | "done" | "ignored"

/** 待办响应，字段与 TodoVO 严格对齐 */
export interface UserResourceRef {
  id: number
  label: string
  imageUrl?: string
}

export interface TodoSourceReference {
  resource: string
  id: number
}

export interface TodoVO {
  id: number
  assigneeId: number
  title: string
  category: TodoCategory
  sourceType?: "manual" | "comment" | "task"
  source?: TodoSourceReference
  status: TodoStatus
  dueDate?: string
  createTime: string
  assignee?: UserResourceRef
  participants?: UserResourceRef[]
  createBy?: UserResourceRef
  updateBy?: UserResourceRef
}

export interface TodoPageParams {
  pageNo?: number
  pageSize?: number
  status?: TodoStatus
  category?: TodoCategory
}

export interface TodoCreateInput {
  title: string
  category?: TodoCategory
  dueDate?: string
  source?: TodoSourceReference
  participantIds?: number[]
}

export interface TodoUpdateInput {
  title?: string
  category?: TodoCategory
  status?: TodoStatus
  dueDate?: string
  source?: TodoSourceReference | null
  participantIds?: number[]
}

// ==================== 用户端 / Studio 待办操作 ====================

/** Studio 待办工具固定声明个人视角，与页面所在路由树的默认场景无关 */
const OWN_SCOPE_HEADERS = { "X-Scope": "own" } as const

export const todoApi = {
  /** 分页查询（不传 assigneeId，个人视角由 X-Scope: own 在后端强制收窄） */
  page: (params: TodoPageParams = {}) =>
    backendApi.get<PageResult<TodoVO>>("/todos", {
      params: { pageNo: 1, pageSize: 200, ...params },
      headers: OWN_SCOPE_HEADERS
    }),

  /** 创建（不传 assigneeId，后端自动指派给当前用户） */
  create: (data: TodoCreateInput) =>
    backendApi.post<TodoVO>("/todos", data, { headers: OWN_SCOPE_HEADERS }),

  /** 更新（标题编辑等） */
  update: (id: number, data: TodoUpdateInput) =>
    backendApi.put<TodoVO>(`/todos/${id}`, data, { headers: OWN_SCOPE_HEADERS }),

  /** 快捷更新状态 */
  updateStatus: (id: number, status: TodoStatus) =>
    backendApi.put<TodoVO>(`/todos/${id}/status`, { status }, { headers: OWN_SCOPE_HEADERS }),

  /** 删除 */
  remove: (id: number) => backendApi.delete<void>(`/todos/${id}`, { headers: OWN_SCOPE_HEADERS }),

  /** 批量删除（用于"清除已完成"，传入当前用户自己已完成项的 ID 列表） */
  removeBatch: (ids: number[]) =>
    backendApi.post<void>("/todos/_batch-delete", { ids }, { headers: OWN_SCOPE_HEADERS })
}

const KEYS = {
  all: ["studio-todos"] as const,
  page: (params: TodoPageParams) => ["studio-todos", "page", params] as const
}

/** 待办分页列表（Studio 场景默认取足量单页，前端本地过滤 all/active/completed） */
export function useStudioTodos(params: TodoPageParams = {}) {
  return useQuery({
    queryKey: KEYS.page(params),
    queryFn: () => todoApi.page(params)
  })
}

/** 创建待办 */
export function useStudioTodoCreate() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: TodoCreateInput) => todoApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.all })
  })
}

/** 更新待办（标题编辑等） */
export function useStudioTodoUpdate() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: TodoUpdateInput }) => todoApi.update(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.all })
  })
}

/** 更新待办状态（切换完成/取消完成） */
export function useStudioTodoUpdateStatus() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: TodoStatus }) =>
      todoApi.updateStatus(id, status),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.all })
  })
}

/** 删除待办 */
export function useStudioTodoRemove() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => todoApi.remove(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.all })
  })
}

/** 批量清除已完成 */
export function useStudioTodoClearDone() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ids: number[]) => todoApi.removeBatch(ids),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.all })
  })
}

// ==================== 管理端维护接口 ====================

/**
 * 管理端待办维护 API。
 *
 * Todo 是管理端和用户端共用的资源；本段仅提供跨用户的系统维护操作。
 * 普通创建、编辑、状态更新和删除仍使用上方 todoApi。
 */
export const todoAdminApi = {
  /** 清理当前租户内全部用户的已完成待办 */
  clearDone: () => backendApi.put<number>("/todos/_clear-done"),

  /** 异步清理当前租户内全部用户的已完成待办 */
  clearDoneAsync: () => backendApi.post<string>("/todos/_clear-done/async")
}

/** 管理端清理全部用户的已完成待办 */
export function useAdminTodoClearDone() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => todoAdminApi.clearDone(),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.all })
  })
}

/** 管理端异步清理全部用户的已完成待办 */
export function useAdminTodoClearDoneAsync() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => todoAdminApi.clearDoneAsync(),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.all })
  })
}
