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
import { buildFieldContext, evaluateCondition } from "@/features/entity-engine/lib/field-context"
import type { EntityAction, EntityDef } from "@/features/entity-engine/types"
import { buildApiUrl } from "@/lib/api/config"
import { backendApi } from "@/lib/api/rest/backend-client"
import { ApiError, crudKey, crudRequestConfig } from "@/lib/api/rest/crud"
import { subscribe, waitForEvent } from "@/lib/realtime/event-registry"
import type { RealtimeEvent } from "@/lib/realtime/event-stream"
import { type AsyncTaskStatus, AsyncTaskStatusIndicator } from "./AsyncTaskStatusIndicator"

const ASYNC_TASK_EVENT_TYPE = "async-task.status"

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

function toAsyncTaskResult(event: RealtimeEvent): AsyncTaskResult | null {
  if (event.type !== ASYNC_TASK_EVENT_TYPE) return null

  const task = event.payload
  if (typeof task.taskId !== "string" || !isAsyncTaskStatus(task.status)) return null

  return {
    taskId: task.taskId,
    status: task.status,
    result: typeof task.result === "string" ? task.result : undefined,
    lastError: typeof task.lastError === "string" ? task.lastError : undefined
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

/** 通过全局事件通道跟踪异步任务：中间状态回调展示进度，终态时成功返回、失败抛错。 */
async function trackAsyncTask(
  taskId: string,
  signal: AbortSignal,
  onStatus: (task: AsyncTaskResult) => void
): Promise<AsyncTaskResult> {
  const unsubscribe = subscribe(ASYNC_TASK_EVENT_TYPE, (event) => {
    const task = toAsyncTaskResult(event)
    if (task?.taskId === taskId) onStatus(task)
  })

  try {
    const event = await waitForEvent((candidate) => {
      const task = toAsyncTaskResult(candidate)
      return task?.taskId === taskId && (task.status === "SUCCEEDED" || task.status === "FAILED")
    }, signal)
    const task = toAsyncTaskResult(event)
    if (task === null) throw new ApiError(0, "异步任务状态事件无效")
    if (task.status === "FAILED") throw new ApiError(0, task.lastError ?? "异步任务执行失败")
    return task
  } finally {
    unsubscribe()
  }
}

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === "AbortError"
}

/** 操作入口组 */
export function EntityActions({ entity, position, record, selectedIds }: EntityActionsProps) {
  const queryClient = useQueryClient()
  const abortControllerRef = useRef<AbortController | null>(null)
  const [runningActionKey, setRunningActionKey] = useState<string>()
  const [pendingAction, setPendingAction] = useState<EntityAction>()
  const [trackedTask, setTrackedTask] = useState<AsyncTaskResult & { label: string }>()
  const actions = (entity.actions ?? []).filter(
    (action) =>
      action.position === position &&
      (action.type !== "batch" || (selectedIds != null && selectedIds.length > 0)) &&
      (action.visibleWhen == null ||
        evaluateCondition(action.visibleWhen, buildFieldContext(record ?? {})))
  )

  useEffect(
    () => () => {
      const controller = abortControllerRef.current
      abortControllerRef.current = null
      controller?.abort()
    },
    []
  )

  const executeAction = useCallback(
    async (action: EntityAction) => {
      const controller = new AbortController()
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
            ...crudRequestConfig(entity),
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
          toast.success(`${action.label}任务已提交`)

          const task = await trackAsyncTask(result.taskId, controller.signal, (status) => {
            setTrackedTask({ ...status, label: action.label })
          })
          await queryClient.invalidateQueries({ queryKey: crudKey(entity) })

          const deletedCount = readDeletedCount(task.result)
          toast.success(
            deletedCount === undefined
              ? `${action.label}已完成`
              : `${action.label}已完成，已删除 ${deletedCount} 条记录`
          )
        } else {
          await queryClient.invalidateQueries({ queryKey: crudKey(entity) })
          toast.success(`${action.label}成功`)
        }
      } catch (error) {
        if (!isAbortError(error)) {
          toast.error(error instanceof Error ? error.message : `${action.label}失败`)
        }
      } finally {
        if (abortControllerRef.current === controller) {
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
            onClick={(event) => {
              event.stopPropagation()
              requestAction(action)
            }}
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
