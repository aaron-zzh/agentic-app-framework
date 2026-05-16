/**
 * useColumnPreferences——列配置用户偏好（localStorage 持久化）
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useMemo, useState } from "react"

import type { ColumnDef, ListViewConfig } from "@/features/entity-engine/types"

/** 用户列偏好 */
export interface ColumnPreference {
  name: string
  visible: boolean
  order: number
}

const STORAGE_KEY_PREFIX = "aaf:columns:"

/** 从 ListViewConfig.columns 解析为标准 ColumnDef */
function normalizeColumns(columns: ListViewConfig["columns"]): ColumnDef[] {
  return columns.map((col) => (typeof col === "string" ? { name: col } : col))
}

/** 读取 localStorage 偏好 */
function loadPreferences(entitySlug: string): ColumnPreference[] | null {
  if (typeof window === "undefined") return null
  const raw = localStorage.getItem(`${STORAGE_KEY_PREFIX}${entitySlug}`)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

/** 保存偏好到 localStorage */
function savePreferences(entitySlug: string, prefs: ColumnPreference[]) {
  localStorage.setItem(`${STORAGE_KEY_PREFIX}${entitySlug}`, JSON.stringify(prefs))
}

/** 列配置管理 Hook */
export function useColumnPreferences(entitySlug: string, config: ListViewConfig) {
  const allColumns = useMemo(() => normalizeColumns(config.columns), [config.columns])

  const [preferences, setPreferences] = useState<ColumnPreference[]>(() => {
    const saved = loadPreferences(entitySlug)
    if (saved) return saved
    return allColumns.map((col, i) => ({
      name: col.name,
      visible: !col.hidden,
      order: i
    }))
  })

  /** 当前可见列（按 order 排序） */
  const visibleColumns = useMemo(
    () =>
      [...preferences]
        .filter((p) => p.visible)
        .sort((a, b) => a.order - b.order)
        .map((p) => allColumns.find((c) => c.name === p.name)!)
        .filter(Boolean),
    [preferences, allColumns]
  )

  /** 切换列可见性 */
  const toggleColumn = useCallback(
    (name: string) => {
      setPreferences((prev) => {
        const next = prev.map((p) => (p.name === name ? { ...p, visible: !p.visible } : p))
        savePreferences(entitySlug, next)
        return next
      })
    },
    [entitySlug]
  )

  /** 重排列顺序 */
  const reorderColumns = useCallback(
    (orderedNames: string[]) => {
      setPreferences((prev) => {
        const next = prev.map((p) => ({
          ...p,
          order: orderedNames.indexOf(p.name)
        }))
        savePreferences(entitySlug, next)
        return next
      })
    },
    [entitySlug]
  )

  /** 重置为默认 */
  const resetColumns = useCallback(() => {
    const defaults = allColumns.map((col, i) => ({
      name: col.name,
      visible: !col.hidden,
      order: i
    }))
    setPreferences(defaults)
    localStorage.removeItem(`${STORAGE_KEY_PREFIX}${entitySlug}`)
  }, [allColumns, entitySlug])

  return { visibleColumns, preferences, toggleColumn, reorderColumns, resetColumns }
}
