/**
 * TaskBoard API 客户端——子任务状态订阅与查询
 * @author AaronZZH & Kiro
 */

import { buildApiUrl } from "./base"

/** 子任务 */
export interface SubTask {
  id: string
  description: string
  status: "PENDING" | "RUNNING" | "DONE" | "FAILED"
  dependsOn: string[]
  result?: string
}

/** TaskBoard 整体状态 */
export interface TaskBoardState {
  sessionId: string
  tasks: SubTask[]
  progress: { total: number; done: number; failed: number; running: number }
}

/** SSE 事件类型 */
export type TaskBoardEventType =
  | "TASK_ADDED"
  | "TASK_STATUS_CHANGED"
  | "TASK_COMPLETED"
  | "BOARD_SNAPSHOT"
  | "SESSION_RECOVERED"

/** SSE 事件载荷 */
export interface TaskBoardEvent {
  type: TaskBoardEventType
  data: SubTask | SubTask[] | { taskCount: number }
}

/** 获取 TaskBoard SSE 订阅 URL */
export function getTaskBoardSSEUrl(sessionId: string): string {
  return buildApiUrl(`/chat/sessions/${sessionId}/tasks`)
}
