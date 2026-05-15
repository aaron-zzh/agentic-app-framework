/**
 * API 客户端基础层——统一 fetch 封装
 * @author AaronZZH & Kiro
 */

/** 分页响应结构（与后端 PageResult<T> 对齐） */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

/** 后端统一响应结构 */
export interface ApiResult<T> {
  code: number
  data: T
  message?: string
}

/** 列表查询参数 */
export interface ListParams {
  page?: number
  pageSize?: number
  sort?: string
  search?: string
  [key: string]: unknown
}

/** API 错误 */
export class ApiError extends Error {
  constructor(
    public code: number,
    message: string
  ) {
    super(message)
    this.name = "ApiError"
  }
}

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "/api"

/** 通用 fetch 封装 */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const url = `${BASE_URL}${path}`
  const res = await fetch(url, {
    headers: { "Content-Type": "application/json", ...init?.headers },
    ...init,
  })

  if (!res.ok) {
    throw new ApiError(res.status, `请求失败: ${res.statusText}`)
  }

  const json: ApiResult<T> = await res.json()
  if (json.code !== 0) {
    throw new ApiError(json.code, json.message ?? "未知错误")
  }
  return json.data
}

/** 构建查询字符串 */
function buildQuery(params: ListParams): string {
  const entries = Object.entries(params).filter(
    ([, v]) => v !== undefined && v !== null && v !== ""
  )
  if (entries.length === 0) return ""
  return `?${new URLSearchParams(entries.map(([k, v]) => [k, String(v)])).toString()}`
}

/** 获取分页列表 */
export function fetchList<T = Record<string, unknown>>(
  apiPath: string,
  params: ListParams
): Promise<PageResult<T>> {
  return request<PageResult<T>>(`${apiPath}${buildQuery(params)}`)
}

/** 获取单条记录 */
export function fetchRecord<T = Record<string, unknown>>(
  apiPath: string,
  id: string
): Promise<T> {
  return request<T>(`${apiPath}/${id}`)
}

/** 创建记录 */
export function createRecord<T = Record<string, unknown>>(
  apiPath: string,
  data: Record<string, unknown>
): Promise<T> {
  return request<T>(apiPath, { method: "POST", body: JSON.stringify(data) })
}

/** 更新记录 */
export function updateRecord<T = Record<string, unknown>>(
  apiPath: string,
  id: string,
  data: Record<string, unknown>
): Promise<T> {
  return request<T>(`${apiPath}/${id}`, { method: "PUT", body: JSON.stringify(data) })
}

/** 删除记录 */
export function deleteRecord(apiPath: string, ids: string[]): Promise<void> {
  return request<void>(apiPath, { method: "DELETE", body: JSON.stringify({ ids }) })
}
