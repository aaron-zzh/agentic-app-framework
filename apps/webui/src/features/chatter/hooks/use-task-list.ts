/**
 * useTaskList——读取当前对话的 canonical TaskDetails 投影。
 * @author AaronZZH & Kiro
 */

"use client"

import { useQuery, useQueryClient } from "@tanstack/react-query"
import {
  isTaskTerminal,
  TASK_STATUSES,
  type TaskDetails,
  type TaskStatus,
  taskApi,
  taskKeys
} from "@/lib/api/rest/ai"
import { useEventSubscription } from "@/lib/realtime/use-event-subscription"

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

/** 事件通道的兜底轮询间隔：状态变化由 `task.status.changed` 事件即时失效，轮询只覆盖连接中断。 */
const TRANSITIONING_POLL_MS = 3_000
const ACTIVE_POLL_MS = 10_000
const TASK_EVENT_PREFIX = "task."

/** DIRECT Execution 不属于 Task API；本 hook 因此只展示已 materialize/promotion 的 Task。 */
export function useTaskList(conversationId: string | undefined): UseTaskListReturn {
  const queryClient = useQueryClient()
  const selectedConversationId = conversationId ?? ""
  const query = useQuery({
    queryKey: taskKeys.list(selectedConversationId),
    queryFn: () => taskApi.list(selectedConversationId),
    enabled: selectedConversationId.length > 0,
    refetchInterval: (query) => {
      const tasks = query.state.data
      if (tasks?.some((task) => task.status === "PAUSING" || task.status === "CANCELING")) {
        return TRANSITIONING_POLL_MS
      }
      return tasks?.some((task) => !isTaskTerminal(task.status)) ? ACTIVE_POLL_MS : false
    }
  })

  // 全局事件只作失效通知：payload 的 conversationId / taskId 为字符串，按当前对话匹配
  useEventSubscription(TASK_EVENT_PREFIX, (event) => {
    if (selectedConversationId.length === 0) return
    if (event.payload.conversationId !== selectedConversationId) return
    void queryClient.invalidateQueries({ queryKey: taskKeys.list(selectedConversationId) })
    const taskId = event.payload.taskId
    if (typeof taskId === "string") {
      void queryClient.invalidateQueries({ queryKey: taskKeys.detail(taskId) })
    }
  })

  const tasks = query.data ?? []
  return { tasks, progress: calcProgress(tasks), isLoading: query.isLoading }
}
