/**
 * 计划任务 API 客户端
 * @author AaronZZH & Kiro
 */

import { buildApiUrl } from "./base"
import { ApiError } from "./client"

/** 计划任务状态 */
export type ScheduledTaskStatus = "active" | "paused" | "failed"

/** 计划任务 */
export interface ScheduledTaskVO {
  id: number
  name: string
  type: string
  cron: string
  status: ScheduledTaskStatus
  lastRun: string | null
  nextRun: string | null
  failCount: number
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

export const scheduledTaskApi = {
  /** 查询计划任务列表 */
  list: () => req<ScheduledTaskVO[]>("/admin/scheduled-tasks"),

  /** 暂停任务 */
  pause: (id: number) => req<void>(`/admin/scheduled-tasks/${id}/pause`, { method: "PUT" }),

  /** 恢复任务 */
  resume: (id: number) => req<void>(`/admin/scheduled-tasks/${id}/resume`, { method: "PUT" }),

  /** 手动触发执行 */
  run: (id: number) => req<void>(`/admin/scheduled-tasks/${id}/run`, { method: "POST" })
}
