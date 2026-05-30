/**
 * EntityActions——从 entity.actions 配置渲染操作按钮并触发后端
 * @author AaronZZH & Kiro
 *
 * 按 position 渲染到不同位置：formHeader / listToolbar / rowAction / contextMenu
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
import { useCallback, useState } from "react"
import { toast } from "sonner"

import type { EntityAction, EntityDef } from "@/lib/types/entity"
import { ApiError } from "@/lib/api/client"

interface EntityActionsProps {
  entity: EntityDef
  position: EntityAction["position"]
  /** 当前记录（单条操作时） */
  record?: Record<string, unknown>
  /** 选中的 ids（批量操作时） */
  selectedIds?: string[]
}

/** 操作按钮组 */
export function EntityActions({ entity, position, record, selectedIds }: EntityActionsProps) {
  const actions = (entity.actions ?? []).filter((a) => a.position === position)
  if (actions.length === 0) return null

  return (
    <div className="flex items-center gap-1">
      {actions.map((action) => (
        <ActionButton
          key={action.key}
          action={action}
          entity={entity}
          record={record}
          selectedIds={selectedIds}
        />
      ))}
    </div>
  )
}

/** 单个操作按钮 */
function ActionButton({
  action,
  entity,
  record,
  selectedIds
}: {
  action: EntityAction
  entity: EntityDef
  record?: Record<string, unknown>
  selectedIds?: string[]
}) {
  const [loading, setLoading] = useState(false)
  const queryClient = useQueryClient()

  const handleClick = useCallback(async () => {
    // 确认弹窗
    if (action.confirmMessage) {
      const ok = window.confirm(action.confirmMessage)
      if (!ok) return
    }

    setLoading(true)
    try {
      const body: Record<string, unknown> = {}
      if (action.type === "single" && record) {
        body.id = record.id
      } else if (action.type === "batch" && selectedIds) {
        body.ids = selectedIds
      }

      const res = await fetch(action.endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
      })

      if (!res.ok) {
        throw new ApiError(res.status, `操作失败: ${res.statusText}`)
      }

      // 刷新列表缓存
      queryClient.invalidateQueries({ queryKey: [entity.slug, "list"] })
      if (record?.id) {
        queryClient.invalidateQueries({ queryKey: [entity.slug, "record", record.id] })
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "操作失败，请重试")
    } finally {
      setLoading(false)
    }
  }, [action, entity, record, selectedIds, queryClient])

  // 批量操作需要有选中项
  if (action.type === "batch" && (!selectedIds || selectedIds.length === 0)) {
    return null
  }

  return (
    <button
      type="button"
      className="inline-flex h-7 items-center gap-1 rounded border px-2 text-xs hover:bg-muted disabled:opacity-50"
      onClick={handleClick}
      disabled={loading}
    >
      {action.icon && <span>{action.icon}</span>}
      {action.label}
      {loading && <span className="animate-spin">⏳</span>}
    </button>
  )
}
