/**
 * 字段变更订阅 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"

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

export const subscriptionApi = {
  /** 获取当前用户对某条记录的订阅 */
  get: (entityType: string, entityId: string) =>
    backendApi.get<Subscription | null>("/subscriptions", { params: { entityType, entityId } }),

  /** 获取当前用户在某实体下所有已订阅的记录 ID 列表 */
  listIds: (entityType: string) =>
    backendApi.get<string[]>("/subscriptions/ids", { params: { entityType } }),

  /** 创建或更新订阅 */
  upsert: (data: UpsertSubscriptionReq) =>
    backendApi.put<Subscription>("/subscriptions", data),

  /** 取消订阅 */
  remove: (entityType: string, entityId: string) =>
    backendApi.delete<void>("/subscriptions", { params: { entityType, entityId } })
}
