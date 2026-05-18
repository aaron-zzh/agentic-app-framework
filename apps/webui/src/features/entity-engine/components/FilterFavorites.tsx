/**
 * FilterFavorites——筛选收藏（保存/应用/删除/设为默认）
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean } from "@aaf/hooks"
import { Bookmark, Star, X } from "lucide-react"
import { useCallback, useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import type { FilterCondition } from "./FilterBuilder"

/** 收藏条目 */
export interface FilterFavorite {
  name: string
  filters: FilterCondition[]
  isDefault?: boolean
}

const STORAGE_KEY_PREFIX = "aaf:filter-favorites:"

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

function saveFavorites(entitySlug: string, favorites: FilterFavorite[]) {
  localStorage.setItem(`${STORAGE_KEY_PREFIX}${entitySlug}`, JSON.stringify(favorites))
}

interface FilterFavoritesProps {
  entitySlug: string
  currentFilters: FilterCondition[]
  onApply: (filters: FilterCondition[]) => void
}

export function FilterFavorites({ entitySlug, currentFilters, onApply }: FilterFavoritesProps) {
  const [favorites, setFavorites] = useState<FilterFavorite[]>(() => loadFavorites(entitySlug))
  const { value: open, setValue: setOpen } = useBoolean()
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
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        render={
          <button
            type="button"
            title="收藏筛选"
            className="flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground"
          />
        }
      >
        <Bookmark className="size-4" />
      </PopoverTrigger>

      <PopoverContent className="w-64 p-2" align="end">
        {favorites.length > 0 && (
          <ul className="mb-2 space-y-0.5">
            {favorites.map((fav, i) => (
              <li
                key={fav.name}
                className="flex items-center gap-1 rounded px-2 py-1.5 hover:bg-muted"
              >
                <button
                  type="button"
                  className="flex-1 truncate text-left text-sm"
                  onClick={() => {
                    onApply(fav.filters)
                    setOpen(false)
                  }}
                >
                  {fav.isDefault && (
                    <Star className="mr-1 inline size-3 fill-yellow-400 text-yellow-400" />
                  )}
                  {fav.name}
                </button>
                <button
                  type="button"
                  className="shrink-0 text-muted-foreground hover:text-yellow-500"
                  onClick={() => handleSetDefault(i)}
                  title="设为默认"
                >
                  <Star className="size-3.5" />
                </button>
                <button
                  type="button"
                  className="shrink-0 text-muted-foreground hover:text-destructive"
                  onClick={() => handleDelete(i)}
                >
                  <X className="size-3.5" />
                </button>
              </li>
            ))}
          </ul>
        )}

        {currentFilters.length > 0 && (
          <div className="flex gap-1.5 border-t pt-2">
            <Input
              className="h-7 flex-1 text-xs"
              placeholder="收藏名称"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && handleSave()}
            />
            <Button size="sm" className="h-7 px-2 text-xs" onClick={handleSave}>
              保存
            </Button>
          </div>
        )}

        {favorites.length === 0 && currentFilters.length === 0 && (
          <p className="py-3 text-center text-muted-foreground text-xs">暂无收藏筛选</p>
        )}
      </PopoverContent>
    </Popover>
  )
}

export function getDefaultFavorite(entitySlug: string): FilterCondition[] | null {
  const favorites = loadFavorites(entitySlug)
  const def = favorites.find((f) => f.isDefault)
  return def?.filters ?? null
}
