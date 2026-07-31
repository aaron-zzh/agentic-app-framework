/**
 * useTaskBoard——按对话查询并轮询委托任务状态。
 * @author AaronZZH & Kiro
 */

"use client"

import { useQuery } from "@tanstack/react-query"
import {
  DELEGATED_TASK_STATUSES,
  type DelegatedTaskStatus,
  type DelegatedTaskVO,
  delegatedTaskApi,
  delegatedTaskKeys
} from "@/lib/api/rest/ai"

export interface TaskBoardProgress {
  total: number
  byStatus: Record<DelegatedTaskStatus, number>
}

interface UseTaskBoardReturn {
  tasks: DelegatedTaskVO[]
  progress: TaskBoardProgress
  isLoading: boolean
}

function calcProgress(tasks: DelegatedTaskVO[]): TaskBoardProgress {
  const byStatus = Object.fromEntries(
    DELEGATED_TASK_STATUSES.map((status) => [status, 0])
  ) as Record<DelegatedTaskStatus, number>

  for (const task of tasks) {
    byStatus[task.status] += 1
  }

  return { total: tasks.length, byStatus }
}

/** 当前用户任务接口为全量列表，前端按后端规范 conversationId 收窄当前对话。 */
export function useTaskBoard(conversationId: string | undefined): UseTaskBoardReturn {
  const query = useQuery({
    queryKey: delegatedTaskKeys.list(conversationId ?? ""),
    queryFn: () => delegatedTaskApi.list(),
    enabled: Boolean(conversationId),
    refetchInterval: 3000,
    select: (tasks) => tasks.filter((task) => task.conversationId === conversationId)
  })

  const tasks = query.data ?? []
  return {
    tasks,
    progress: calcProgress(tasks),
    isLoading: query.isLoading
  }
}
