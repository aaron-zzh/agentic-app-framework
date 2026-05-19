/**
 * 通知偏好 API 客户端
 * @author AaronZZH & Kiro
 */

import { ApiError } from "./client"

/** 通知通道类型 */
export type NotificationChannel = "inApp" | "email"

/** 单个类别的通道配置 */
export interface ChannelConfig {
  inApp: boolean
  email: boolean
}

/** 通知偏好（前端模型） */
export interface NotificationPreference {
  /** 各类别的通道配置 */
  preferences: Record<string, ChannelConfig>
  /** 免打扰开始时间，格式 "HH:mm" */
  quietStart?: string
  /** 免打扰结束时间，格式 "HH:mm" */
  quietEnd?: string
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

export const notificationPreferenceApi = {
  /** 获取当前用户通知偏好 */
  get: () => req<NotificationPreference>("/notification-preferences"),
  /** 更新通知偏好 */
  update: (data: NotificationPreference) =>
    req<void>("/notification-preferences", { method: "PUT", body: JSON.stringify(data) })
}
