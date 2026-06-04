/**
 * 标准 REST CRUD 客户端。
 *
 * 对齐后端 BaseCrudController：基础分页、查询窗口、详情、创建、更新、删除和批量删除。
 */

import type { AxiosRequestConfig } from "axios"
import { ApiError } from "@/lib/api/errors"
import type { ApiResult, ListParams, PageResult } from "@/lib/api/types"
import { backendApi } from "@/lib/api/rest/backend-client"

export type CrudId = string | number
export type CrudRecord = Record<string, unknown>
export type CrudData = Record<string, unknown>
export type { ApiResult, ListParams, PageResult }
export { ApiError }

export interface CrudResource<TRecord = CrudRecord> {
  apiPath: string
  __record?: TRecord
}

export interface CrudDetailParams {
  [key: string]: string | number | boolean | string[] | undefined
  queryToken?: string
  fieldSet?: string
}

export interface CrudQueryWindowParams extends ListParams {
  fieldSet?: string
}

export function buildQuery(
  params: Record<string, string | number | boolean | string[] | undefined>
): string {
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

// TODO 先把现有业务文件里的 request(...) 逐步替换成 backendApi.get/post/put/delete
export function request<T>(path: string, init?: RequestInit): Promise<T> {
  return backendApi.request<T>({ url: path, ...toAxiosConfig(init) })
}

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

// TRecord 是为了让调用方声明“列表里每一行是什么类型”
export function fetchList<TRecord extends CrudRecord = CrudRecord>(
  resource: CrudResource<TRecord>,
  params: ListParams = {}
): Promise<PageResult<TRecord>> {
  return backendApi.get<PageResult<TRecord>>(`${resource.apiPath}${buildQuery(params)}`)
}

export function fetchQueryWindow<TRecord extends CrudRecord = CrudRecord>(
  resource: CrudResource<TRecord>,
  params: CrudQueryWindowParams = {}
): Promise<PageResult<TRecord>> {
  return backendApi.get<PageResult<TRecord>>(`${resource.apiPath}/_query${buildQuery(params)}`)
}

export function fetchRecord<TRecord extends CrudRecord = CrudRecord>(
  resource: CrudResource<TRecord>,
  id: CrudId,
  params: CrudDetailParams = {}
): Promise<TRecord> {
  return backendApi.get<TRecord>(`${resource.apiPath}/${id}${buildQuery(params)}`)
}

export function batchReadRecords<TRecord extends CrudRecord = CrudRecord>(
  resource: CrudResource<TRecord>,
  ids: CrudId[],
  fieldSet = "detail"
): Promise<TRecord[]> {
  return backendApi.post<TRecord[]>(`${resource.apiPath}/_batch-read`, { ids, fieldSet })
}

export function createRecord<TRecord extends CrudRecord = CrudRecord, TCreate extends CrudData = CrudData>(
  resource: CrudResource<TRecord>,
  data: TCreate
): Promise<TRecord> {
  return backendApi.post<TRecord>(resource.apiPath, data)
}

export function updateRecord<TRecord extends CrudRecord = CrudRecord, TUpdate extends CrudData = CrudData>(
  resource: CrudResource<TRecord>,
  id: CrudId,
  data: TUpdate
): Promise<TRecord> {
  return backendApi.put<TRecord>(`${resource.apiPath}/${id}`, data)
}

export function deleteRecord(resource: CrudResource, id: CrudId): Promise<void> {
  return backendApi.delete<void>(`${resource.apiPath}/${id}`)
}

export function deleteRecords(resource: CrudResource, ids: CrudId[]): Promise<void> {
  return backendApi.post<void>(`${resource.apiPath}/_batch-delete`, { ids })
}

export function fetchCrudMeta<TMeta = CrudRecord>(resource: CrudResource): Promise<TMeta> {
  return backendApi.get<TMeta>(`${resource.apiPath}/_meta`)
}
