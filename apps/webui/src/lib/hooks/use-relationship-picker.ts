/**
 * useRelationshipPicker——关联字段数据逻辑
 * @author AaronZZH & Kiro
 */

import { useCallback, useEffect, useRef, useState } from "react"

export interface RelationOption {
  id: string
  label: string
}

const RECENT_KEY = (endpoint: string) => `aaf_recent_${endpoint}`
const MAX_RECENT = 5

export function useRelationshipPicker(searchEndpoint?: string, displayField = "name") {
  const [query, setQuery] = useState("")
  const [options, setOptions] = useState<RelationOption[]>([])
  const [loading, setLoading] = useState(false)
  const [recent, setRecent] = useState<RelationOption[]>(() => {
    if (!searchEndpoint || typeof window === "undefined") return []
    try {
      return JSON.parse(localStorage.getItem(RECENT_KEY(searchEndpoint)) ?? "[]")
    } catch {
      return []
    }
  })
  const debounceRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  // 异步搜索（debounce 300ms）
  useEffect(() => {
    if (!query.trim() || !searchEndpoint) {
      setOptions([])
      return
    }
    clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(async () => {
      setLoading(true)
      try {
        const res = await fetch(`${searchEndpoint}?search=${encodeURIComponent(query)}&limit=10`)
        const json = await res.json()
        const list = (json.data?.list ?? json.data ?? []) as Record<string, unknown>[]
        setOptions(
          list.map((r) => ({
            id: r.id as string,
            label: (r[displayField] as string) ?? String(r.id)
          }))
        )
      } catch {
        setOptions([])
      } finally {
        setLoading(false)
      }
    }, 300)
  }, [query, searchEndpoint, displayField])

  // 记录最近选择
  const recordRecent = useCallback(
    (opt: RelationOption) => {
      if (!searchEndpoint) return
      setRecent((prev) => {
        const next = [opt, ...prev.filter((r) => r.id !== opt.id)].slice(0, MAX_RECENT)
        localStorage.setItem(RECENT_KEY(searchEndpoint), JSON.stringify(next))
        return next
      })
    },
    [searchEndpoint]
  )

  // 显示的选项：有搜索词时显示搜索结果，否则显示最近选择
  const displayOptions = query.trim() ? options : recent

  return { query, setQuery, displayOptions, loading, recordRecent }
}
