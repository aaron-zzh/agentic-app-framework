/**
 * FilterFavorites——筛选收藏（保存/应用/删除/设为默认）
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useState } from "react"

import type { FilterCondition } from "./FilterBuilder"

/** 收藏条目 */
export interface FilterFavorite {
  name: string
  filters: FilterCondition[]
  isDefault?: boolean
}

const STORAGE_KEY_PREFIX = "aaf:filter-favorites:"

/** 读取收藏 */
function loadFavorites(entitySlug: string): FilterFavorite[] {
  if (typeof window === "undefined") return []
  const raw = localStorage.getItem(`${STORAGE_KEY_PREFIX}${entitySlug}`)
  if (!raw) return []
  try {
    return JSON.parse(raw)
  } catch {
    return []
  }
}

/** 保存收藏 */
function saveFavorites(entitySlug: string, favorites: FilterFavorite[]) {
  localStorage.setItem(`${STORAGE_KEY_PREFIX}${entitySlug}`, JSON.stringify(favorites))
}

interface FilterFavoritesProps {
  entitySlug: string
  currentFilters: FilterCondition[]
  onApply: (filters: FilterCondition[]) => void
}

/** 筛选收藏组件 */
export function FilterFavorites({ entitySlug, currentFilters, onApply }: FilterFavoritesProps) {
  const [favorites, setFavorites] = useState<FilterFavorite[]>(() => loadFavorites(entitySlug))
  const [open, setOpen] = useState(false)
  const [newName, setNewName] = useState("")

  const handleSave = useCallback(() => {
    if (!newName.trim() || currentFilters.length === 0) return
    const next = [...favorites, { name: newName.trim(), filters: currentFilters }]
    setFavorites(next)
    saveFavorites(entitySlug, next)
    setNewName("")
  }, [newName, currentFilters, favorites, entitySlug])

  const handleDelete = useCallback(
    (index: number) => {
      const next = favorites.filter((_, i) => i !== index)
      setFavorites(next)
      saveFavorites(entitySlug, next)
    },
    [favorites, entitySlug]
  )

  const handleSetDefault = useCallback(
    (index: number) => {
      const next = favorites.map((f, i) => ({ ...f, isDefault: i === index }))
      setFavorites(next)
      saveFavorites(entitySlug, next)
    },
    [favorites, entitySlug]
  )

  return (
    <div className="relative inline-block">
      <button
        type="button"
        className="text-muted-foreground text-sm hover:text-foreground"
        onClick={() => setOpen(!open)}
      >
        ⭐ 收藏
      </button>
      {open && (
        <div className="absolute top-8 right-0 z-20 w-56 rounded-md border bg-background p-2 shadow-md">
          {favorites.length > 0 && (
            <ul className="mb-2 space-y-1">
              {favorites.map((fav, i) => (
                <li
                  key={fav.name}
                  className="flex items-center justify-between rounded px-2 py-1 text-sm hover:bg-muted"
                >
                  <button
                    type="button"
                    className="flex-1 text-left"
                    onClick={() => {
                      onApply(fav.filters)
                      setOpen(false)
                    }}
                  >
                    {fav.isDefault && <span className="mr-1 text-xs">★</span>}
                    {fav.name}
                  </button>
                  <span className="flex gap-1">
                    <button
                      type="button"
                      className="text-muted-foreground text-xs hover:text-primary"
                      onClick={() => handleSetDefault(i)}
                      title="设为默认"
                    >
                      ★
                    </button>
                    <button
                      type="button"
                      className="text-muted-foreground text-xs hover:text-destructive"
                      onClick={() => handleDelete(i)}
                    >
                      ✕
                    </button>
                  </span>
                </li>
              ))}
            </ul>
          )}
          {currentFilters.length > 0 && (
            <div className="flex gap-1 border-t pt-2">
              <input
                className="h-7 flex-1 rounded border px-2 text-xs"
                placeholder="收藏名称"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleSave()}
              />
              <button
                type="button"
                className="rounded bg-primary px-2 text-primary-foreground text-xs"
                onClick={handleSave}
              >
                保存
              </button>
            </div>
          )}
          {favorites.length === 0 && currentFilters.length === 0 && (
            <p className="py-2 text-center text-muted-foreground text-xs">暂无收藏</p>
          )}
        </div>
      )}
    </div>
  )
}

/** 获取默认收藏（启动时自动应用） */
export function getDefaultFavorite(entitySlug: string): FilterCondition[] | null {
  const favorites = loadFavorites(entitySlug)
  const def = favorites.find((f) => f.isDefault)
  return def?.filters ?? null
}
