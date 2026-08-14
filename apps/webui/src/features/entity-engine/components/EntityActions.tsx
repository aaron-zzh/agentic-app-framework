/**
 * EntityActions——从 entity.actions 配置渲染操作入口并触发后端
 * @author AaronZZH & Kiro
 *
 * 按 position 渲染到不同位置：formHeader / listToolbar / rowAction / contextMenu
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
import { ChevronDownIcon, LoaderCircleIcon } from "lucide-react"
import { useCallback, useEffect, useRef, useState } from "react"
import { toast } from "sonner"

import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import type { EntityAction, EntityDef } from "@/features/entity-engine/types"
import { buildApiUrl, buildSseUrl } from "@/lib/api/config"
import { backendApi } from "@/lib/api/rest/backend-client"
import { crudKey, fromEntityDef } from "@/lib/api/rest/crud"
import { ApiError } from "@/lib/api/rest/entity"
import { type AsyncTaskStatus, AsyncTaskStatusIndicator } from "./AsyncTaskStatusIndicator"

const ASYNC_TASK_EVENT_NAME = "async-task.status"
const ASYNC_TASK_EVENTS_URL = buildSseUrl("/async-tasks/events")

interface ActionSubmitResult {
  taskId?: string
  status?: AsyncTaskStatus
}

interface AsyncTaskResult {
  taskId: string
  status: AsyncTaskStatus
  result?: string
  lastError?: string
}

interface EntityActionsProps {
  entity: EntityDef
  position: EntityAction["position"]
  /** 当前记录（单条操作时） */
  record?: Record<string, unknown>
  /** 选中的 ids（批量操作时） */
  selectedIds?: string[]
}

function isAsyncTaskStatus(value: unknown): value is AsyncTaskStatus {
  return (
    value === "PENDING" ||
    value === "QUEUED" ||
    value === "RUNNING" ||
    value === "RETRY_WAIT" ||
    value === "SUCCEEDED" ||
    value === "FAILED"
  )
}

function parseAsyncTaskResult(data: string): AsyncTaskResult | null {
  try {
    const value = JSON.parse(data) as unknown
    if (typeof value !== "object" || value === null) return null

    const task = value as Record<string, unknown>
    if (typeof task.taskId !== "string" || !isAsyncTaskStatus(task.status)) return null

    return {
      taskId: task.taskId,
      status: task.status,
      result: typeof task.result === "string" ? task.result : undefined,
      lastError: typeof task.lastError === "string" ? task.lastError : undefined
    }
  } catch {
    return null
  }
}

function readDeletedCount(result?: string): number | undefined {
  if (!result) return undefined

  try {
    const value = JSON.parse(result) as unknown
    if (typeof value !== "object" || value === null) return undefined

    const deletedCount = (value as Record<string, unknown>).deletedCount
    return typeof deletedCount === "number" && Number.isInteger(deletedCount) && deletedCount >= 0
      ? deletedCount
      : undefined
  } catch {
    return undefined
  }
}

function subscribeToAsyncTask(
  taskId: string,
  signal: AbortSignal,
  onStatus: (task: AsyncTaskResult) => void
): { source: EventSource; completion: Promise<AsyncTaskResult> } {
  const source = new EventSource(ASYNC_TASK_EVENTS_URL, { withCredentials: true })
  const completion = new Promise<AsyncTaskResult>((resolve, reject) => {
    let settled = false

    const cleanup = () => {
      source.removeEventListener(ASYNC_TASK_EVENT_NAME, handleStatus)
      source.removeEventListener("error", handleError)
      signal.removeEventListener("abort", handleAbort)
      source.close()
    }
    const settle = (callback: () => void) => {
      if (settled) return
      settled = true
      cleanup()
      callback()
    }
    const handleStatus: EventListener = (event) => {
      if (!(event instanceof MessageEvent)) return

      const task = parseAsyncTaskResult(String(event.data))
      if (!task || task.taskId !== taskId) return

      onStatus(task)
      if (task.status === "SUCCEEDED") {
        settle(() => resolve(task))
      } else if (task.status === "FAILED") {
        settle(() => reject(new ApiError(0, task.lastError ?? "异步任务执行失败")))
      }
    }
    const handleError = () => {
      settle(() => reject(new ApiError(0, "异步任务状态连接失败")))
    }
    const handleAbort = () => {
      settle(() => reject(new DOMException("操作已取消", "AbortError")))
    }

    source.addEventListener(ASYNC_TASK_EVENT_NAME, handleStatus)
    source.addEventListener("error", handleError)
    signal.addEventListener("abort", handleAbort, { once: true })
    if (signal.aborted) handleAbort()
  })

  return { source, completion }
}

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === "AbortError"
}

/** 操作入口组 */
export function EntityActions({ entity, position, record, selectedIds }: EntityActionsProps) {
  const queryClient = useQueryClient()
  const abortControllerRef = useRef<AbortController | null>(null)
  const eventSourceRef = useRef<EventSource | null>(null)
  const [runningActionKey, setRunningActionKey] = useState<string>()
  const [pendingAction, setPendingAction] = useState<EntityAction>()
  const [trackedTask, setTrackedTask] = useState<AsyncTaskResult & { label: string }>()
  const actions = (entity.actions ?? []).filter(
    (action) =>
      action.position === position &&
      (action.type !== "batch" || (selectedIds != null && selectedIds.length > 0))
  )

  useEffect(
    () => () => {
      const controller = abortControllerRef.current
      eventSourceRef.current?.close()
      eventSourceRef.current = null
      abortControllerRef.current = null
      controller?.abort()
    },
    []
  )

  const executeAction = useCallback(
    async (action: EntityAction) => {
      const controller = new AbortController()
      eventSourceRef.current?.close()
      eventSourceRef.current = null
      abortControllerRef.current?.abort()
      abortControllerRef.current = controller
      setRunningActionKey(action.key)

      try {
        const body: Record<string, unknown> = {}
        if (action.type === "single" && record) {
          body.id = record.id
        } else if (action.type === "batch" && selectedIds) {
          body.ids = selectedIds
        }

        const result = await backendApi.post<ActionSubmitResult>(
          buildApiUrl(action.endpoint),
          body,
          {
            signal: controller.signal,
            showError: false
          }
        )

        if (action.execution === "async") {
          if (!result.taskId) {
            throw new ApiError(0, "异步操作未返回任务 ID")
          }

          setTrackedTask({
            taskId: result.taskId,
            status: result.status ?? "PENDING",
            label: action.label
          })
          const subscription = subscribeToAsyncTask(result.taskId, controller.signal, (task) => {
            setTrackedTask({ ...task, label: action.label })
          })
          eventSourceRef.current = subscription.source
          toast.success(`${action.label}任务已提交`)

          const task = await subscription.completion
          await queryClient.invalidateQueries({ queryKey: crudKey(fromEntityDef(entity)) })

          const deletedCount = readDeletedCount(task.result)
          toast.success(
            deletedCount === undefined
              ? `${action.label}已完成`
              : `${action.label}已完成，已删除 ${deletedCount} 条记录`
          )
        } else {
          await queryClient.invalidateQueries({ queryKey: crudKey(fromEntityDef(entity)) })
          toast.success(`${action.label}成功`)
        }
      } catch (error) {
        if (!isAbortError(error)) {
          toast.error(error instanceof Error ? error.message : `${action.label}失败`)
        }
      } finally {
        if (abortControllerRef.current === controller) {
          eventSourceRef.current?.close()
          eventSourceRef.current = null
          abortControllerRef.current = null
          setRunningActionKey(undefined)
        }
      }
    },
    [entity, queryClient, record, selectedIds]
  )

  const requestAction = useCallback(
    (action: EntityAction) => {
      if (action.confirmMessage) {
        setPendingAction(action)
        return
      }
      void executeAction(action)
    },
    [executeAction]
  )

  const confirmAction = useCallback(() => {
    if (pendingAction) {
      void executeAction(pendingAction)
    }
  }, [executeAction, pendingAction])

  const confirmationDialog = (
    <ConfirmDialog
      open={pendingAction != null}
      onOpenChange={(open) => {
        if (!open) setPendingAction(undefined)
      }}
      title={`确认${pendingAction?.label ?? "操作"}`}
      description={pendingAction?.confirmMessage}
      onConfirm={confirmAction}
    />
  )

  if (actions.length === 0) return null

  if (position === "listToolbar") {
    return (
      <>
        <DropdownMenu>
          <DropdownMenuTrigger
            render={<Button variant="ghost" disabled={runningActionKey != null} />}
          >
            操作
            {runningActionKey ? (
              <LoaderCircleIcon data-icon="inline-end" className="animate-spin" />
            ) : (
              <ChevronDownIcon data-icon="inline-end" />
            )}
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuGroup>
              {actions.map((action) => (
                <DropdownMenuItem
                  key={action.key}
                  disabled={runningActionKey != null}
                  onClick={() => requestAction(action)}
                >
                  {action.icon && <span>{action.icon}</span>}
                  <span>{action.label}</span>
                </DropdownMenuItem>
              ))}
            </DropdownMenuGroup>
          </DropdownMenuContent>
        </DropdownMenu>
        {trackedTask && (
          <AsyncTaskStatusIndicator {...trackedTask} onClose={() => setTrackedTask(undefined)} />
        )}
        {confirmationDialog}
      </>
    )
  }

  return (
    <>
      <div className="flex items-center gap-1">
        {actions.map((action) => (
          <button
            key={action.key}
            type="button"
            className="inline-flex h-7 items-center gap-1 rounded border px-2 text-xs hover:bg-muted disabled:opacity-50"
            onClick={() => requestAction(action)}
            disabled={runningActionKey != null}
          >
            {action.icon && <span>{action.icon}</span>}
            {action.label}
            {runningActionKey === action.key && <span className="animate-spin">⏳</span>}
          </button>
        ))}
      </div>
      {confirmationDialog}
    </>
  )
}
