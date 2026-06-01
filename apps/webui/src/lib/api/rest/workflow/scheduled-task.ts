/**
 * 计划任务 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"

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

export const scheduledTaskApi = {
  /** 查询计划任务列表 */
  list: () => backendApi.get<ScheduledTaskVO[]>("/admin/scheduled-tasks"),

  /** 暂停任务 */
  pause: (id: number) => backendApi.put<void>(`/admin/scheduled-tasks/${id}/pause`),

  /** 恢复任务 */
  resume: (id: number) => backendApi.put<void>(`/admin/scheduled-tasks/${id}/resume`),

  /** 手动触发执行 */
  run: (id: number) => backendApi.post<void>(`/admin/scheduled-tasks/${id}/run`)
}
