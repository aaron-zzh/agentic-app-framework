import type { AxiosRequestConfig } from "axios"

/** 后端统一响应结构 */
export interface ApiResult<T> {
  code: number
  data: T
  message?: string
}

/** 分页响应结构（与后端 PageResult<T> 对齐） */
export interface PageResult<T> {
  list: T[]
  total: number
  page?: number
  pageSize?: number
  pageNo?: number
  ids?: number[]
  queryToken?: string
  fieldSet?: string
  hasMore?: boolean
}

/** 列表查询参数 */
export interface ListParams extends Record<string, string | number | boolean | string[] | undefined> {
  page?: number
  pageNo?: number
  pageSize?: number
  sort?: string
  search?: string
}

export interface GraphqlResponse<T> {
  data?: T
  errors?: { message: string; path?: string[] }[]
}

export type ApiRequestConfig = AxiosRequestConfig

