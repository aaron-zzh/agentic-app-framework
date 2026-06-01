/**
 * 通知偏好 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"

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

export const notificationPreferenceApi = {
  /** 获取当前用户通知偏好 */
  get: () => backendApi.get<NotificationPreference>("/notification-preferences"),
  /** 更新通知偏好 */
  update: (data: NotificationPreference) =>
    backendApi.put<void>("/notification-preferences", data)
}
