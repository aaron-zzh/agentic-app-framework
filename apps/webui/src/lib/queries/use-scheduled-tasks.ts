/**
 * 计划任务 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { scheduledTaskApi } from "@/lib/api/scheduled-task"

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
