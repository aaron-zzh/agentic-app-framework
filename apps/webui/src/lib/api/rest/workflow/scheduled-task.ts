/**
 * 计划任务 API 客户端
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
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

const KEYS = {
  all: ["scheduled-tasks"] as const,
  list: () => ["scheduled-tasks", "list"] as const
}

/** 计划任务列表 */
export function useScheduledTasks() {
  return useQuery({
    queryKey: KEYS.list(),
    queryFn: () => scheduledTaskApi.list()
  })
}

/** 暂停任务 */
export function useScheduledTaskPause() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => scheduledTaskApi.pause(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}

/** 恢复任务 */
export function useScheduledTaskResume() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => scheduledTaskApi.resume(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}

/** 手动触发执行 */
export function useScheduledTaskRun() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => scheduledTaskApi.run(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}
