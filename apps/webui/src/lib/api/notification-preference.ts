/**
 * 通知偏好 API 客户端
 * @author AaronZZH & Kiro
 */

import { ApiError } from "./client"

export type NotificationChannel = "inApp" | "email" | "wechat"

export interface NotificationPreferenceItem {
  /** 通知类别 */
  type: string
  /** 各通道是否启用 */
  channels: Record<NotificationChannel, boolean>
}

export interface NotificationPreference {
  items: NotificationPreferenceItem[]
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
  get: () => req<NotificationPreference>("/notification-preferences"),
  update: (data: NotificationPreference) =>
    req<void>("/notification-preferences", { method: "PUT", body: JSON.stringify(data) })
}
