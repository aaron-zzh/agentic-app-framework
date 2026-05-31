/**
 * 字段变更订阅 API 客户端
 * @author AaronZZH & Kiro
 */

import { buildApiUrl } from "./base"
import { ApiError } from "./client"

/** 通知通道 */
export type SubscriptionChannel = "inApp" | "email"

/** 订阅记录 */
export interface Subscription {
  id: string
  entityType: string
  entityId: string
  /** null 表示订阅所有字段 */
  fields: string[] | null
  channels: SubscriptionChannel[]
}

/** 创建/更新订阅请求 */
export interface UpsertSubscriptionReq {
  entityType: string
  entityId: string
  fields: string[] | null
  channels: SubscriptionChannel[]
}

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(buildApiUrl(path), {
    headers: { "Content-Type": "application/json", ...init?.headers },
    ...init
  })
  if (!res.ok) throw new ApiError(res.status, `请求失败: ${res.statusText}`)
  const json = await res.json()
  if (json.code !== 0) throw new ApiError(json.code, json.message ?? "未知错误")
  return json.data as T
}

export const subscriptionApi = {
  /** 获取当前用户对某条记录的订阅 */
  get: (entityType: string, entityId: string) =>
    req<Subscription | null>(`/subscriptions?entityType=${entityType}&entityId=${entityId}`),

  /** 获取当前用户在某实体下所有已订阅的记录 ID 列表 */
  listIds: (entityType: string) => req<string[]>(`/subscriptions/ids?entityType=${entityType}`),

  /** 创建或更新订阅 */
  upsert: (data: UpsertSubscriptionReq) =>
    req<Subscription>("/subscriptions", { method: "PUT", body: JSON.stringify(data) }),

  /** 取消订阅 */
  remove: (entityType: string, entityId: string) =>
    req<void>(`/subscriptions?entityType=${entityType}&entityId=${entityId}`, { method: "DELETE" })
}
