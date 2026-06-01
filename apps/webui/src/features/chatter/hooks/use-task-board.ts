/**
 * useTaskBoard——通过 SSE 订阅 TaskBoard 实时状态
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useEffect, useState } from "react"
import { getTaskBoardSSEUrl, type SubTask } from "@/lib/api/rest/ai/task-board"

interface TaskBoardProgress {
  total: number
  done: number
  failed: number
  running: number
}

interface UseTaskBoardReturn {
  tasks: SubTask[]
  progress: TaskBoardProgress
  isLoading: boolean
  /** 是否收到过会话恢复事件 */
  recovered: { taskCount: number } | null
  /** 清除恢复通知 */
  dismissRecovery: () => void
}

/** 从任务列表计算进度 */
function calcProgress(tasks: SubTask[]): TaskBoardProgress {
  return {
    total: tasks.length,
    done: tasks.filter((t) => t.status === "DONE").length,
    failed: tasks.filter((t) => t.status === "FAILED").length,
    running: tasks.filter((t) => t.status === "RUNNING").length
  }
}

export function useTaskBoard(sessionId: string | undefined): UseTaskBoardReturn {
  const [tasks, setTasks] = useState<SubTask[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [recovered, setRecovered] = useState<{ taskCount: number } | null>(null)

  useEffect(() => {
    if (!sessionId) {
      setIsLoading(false)
      return
    }

    const url = getTaskBoardSSEUrl(sessionId)
    const source = new EventSource(url)

    source.addEventListener("BOARD_SNAPSHOT", (e: MessageEvent) => {
      const snapshot: SubTask[] = JSON.parse(e.data)
      setTasks(snapshot)
      setIsLoading(false)
    })

    source.addEventListener("TASK_ADDED", (e: MessageEvent) => {
      const task: SubTask = JSON.parse(e.data)
      setTasks((prev) => [...prev, task])
    })

    source.addEventListener("TASK_STATUS_CHANGED", (e: MessageEvent) => {
      const updated: SubTask = JSON.parse(e.data)
      setTasks((prev) => prev.map((t) => (t.id === updated.id ? updated : t)))
    })

    source.addEventListener("TASK_COMPLETED", (e: MessageEvent) => {
      const completed: SubTask = JSON.parse(e.data)
      setTasks((prev) => prev.map((t) => (t.id === completed.id ? completed : t)))
    })

    source.addEventListener("SESSION_RECOVERED", (e: MessageEvent) => {
      const data: { taskCount: number } = JSON.parse(e.data)
      setRecovered(data)
    })

    source.onerror = () => {
      setIsLoading(false)
    }

    return () => {
      source.close()
    }
  }, [sessionId])

  const dismissRecovery = useCallback(() => {
    setRecovered(null)
  }, [])

  return {
    tasks,
    progress: calcProgress(tasks),
    isLoading,
    recovered,
    dismissRecovery
  }
}
