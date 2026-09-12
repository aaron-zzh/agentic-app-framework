/**
 * useTaskList——读取当前对话的 canonical TaskDetails 投影。
 * @author AaronZZH & Kiro
 */

"use client"

import { useQuery } from "@tanstack/react-query"
import {
  isTaskTerminal,
  TASK_STATUSES,
  type TaskDetails,
  type TaskStatus,
  taskApi,
  taskKeys
} from "@/lib/api/rest/ai"

export interface TaskListProgress {
  total: number
  byStatus: Record<TaskStatus, number>
}

interface UseTaskListReturn {
  tasks: TaskDetails[]
  progress: TaskListProgress
  isLoading: boolean
}

function calcProgress(tasks: TaskDetails[]): TaskListProgress {
  const byStatus = Object.fromEntries(TASK_STATUSES.map((status) => [status, 0])) as Record<
    TaskStatus,
    number
  >
  for (const task of tasks) byStatus[task.status] += 1
  return { total: tasks.length, byStatus }
}

/** DIRECT Execution 不属于 Task API；本 hook 因此只展示已 materialize/promotion 的 Task。 */
export function useTaskList(conversationId: string | undefined): UseTaskListReturn {
  const selectedConversationId = conversationId ?? ""
  const query = useQuery({
    queryKey: taskKeys.list(selectedConversationId),
    queryFn: () => taskApi.list(selectedConversationId),
    enabled: selectedConversationId.length > 0,
    refetchInterval: (query) => {
      const tasks = query.state.data
      if (tasks?.some((task) => task.status === "PAUSING" || task.status === "CANCELING")) {
        return 1000
      }
      return tasks?.some((task) => !isTaskTerminal(task.status)) ? 3000 : false
    }
  })
  const tasks = query.data ?? []
  return { tasks, progress: calcProgress(tasks), isLoading: query.isLoading }
}
