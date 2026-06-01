/**
 * 实体引擎通用 CRUD。
 *
 * 面向元数据驱动的实体引擎；普通业务资源优先直接使用 backendApi。
 */

import type { AxiosRequestConfig } from "axios"
import { ApiError } from "../../errors"
import type { ApiResult, ListParams, PageResult } from "../../types"
import { backendApi } from "../backend-client"

export { ApiError }
export type { ApiResult, ListParams, PageResult }

function headersToRecord(headers: HeadersInit | undefined): Record<string, string> | undefined {
  if (!headers) return undefined
  return Object.fromEntries(new Headers(headers).entries())
}

function toAxiosConfig(init?: RequestInit): AxiosRequestConfig {
  if (!init) return {}
  return {
    method: init.method,
    data: init.body,
    headers: headersToRecord(init.headers),
    signal: init.signal ?? undefined
  }
}

export function request<T>(path: string, init?: RequestInit): Promise<T> {
  return backendApi.request<T>({ url: path, ...toAxiosConfig(init) })
}

function buildQuery(params: Record<string, string | number | boolean | string[] | undefined>): string {
  const entries = Object.entries(params).filter(
    ([, value]) => value !== undefined && value !== null && value !== ""
  )
  if (entries.length === 0) return ""
  const pairs: [string, string][] = []
  for (const [key, value] of entries) {
    if (Array.isArray(value)) {
      for (const item of value) pairs.push([key, String(item)])
    } else {
      pairs.push([key, String(value)])
    }
  }
  return `?${new URLSearchParams(pairs).toString()}`
}

export function fetchList<T = Record<string, unknown>>(
  apiPath: string,
  params: ListParams
): Promise<PageResult<T>> {
  return request<PageResult<T>>(`${apiPath}${buildQuery(params)}`)
}

export function fetchQueryWindow<T = Record<string, unknown>>(
  apiPath: string,
  params: ListParams & { fieldSet?: string }
): Promise<PageResult<T>> {
  return request<PageResult<T>>(`${apiPath}/_query${buildQuery(params)}`)
}

export function fetchRecord<T = Record<string, unknown>>(
  apiPath: string,
  id: string,
  params: { queryToken?: string; fieldSet?: string } = {}
): Promise<T> {
  return request<T>(`${apiPath}/${id}${buildQuery(params)}`)
}

export function createRecord<T = Record<string, unknown>>(
  apiPath: string,
  data: Record<string, unknown>
): Promise<T> {
  return backendApi.post<T>(apiPath, data)
}

export function updateRecord<T = Record<string, unknown>>(
  apiPath: string,
  id: string,
  data: Record<string, unknown>
): Promise<T> {
  return backendApi.put<T>(`${apiPath}/${id}`, data)
}

export function deleteRecord(apiPath: string, ids: string[]): Promise<void> {
  return backendApi.delete<void>(apiPath, { data: { ids } })
}
