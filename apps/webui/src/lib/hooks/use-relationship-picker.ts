/**
 * useRelationshipPicker——关联字段数据逻辑
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import { useCallback, useEffect, useState } from "react"
import { useDebounce } from "use-debounce"

import { backendApi } from "@/lib/api/rest/backend-client"

export interface RelationOption {
  id: string
  label: string
  imageUrl?: string
}

const MAX_RECENT = 5

const RECENT_KEY = (endpoint: string) => `aaf_recent_${endpoint}`

function readRecent(endpoint?: string): RelationOption[] {
  if (!endpoint || typeof window === "undefined") return []

  try {
    return JSON.parse(localStorage.getItem(RECENT_KEY(endpoint)) ?? "[]") as RelationOption[]
  } catch {
    return []
  }
}

/** 查询并管理关系字段的最近选择。 */
export function useRelationshipPicker(searchEndpoint?: string, displayField = "name") {
  const [query, setQuery] = useState("")
  const [recent, setRecent] = useState<RelationOption[]>(() => readRecent(searchEndpoint))
  const [debouncedQuery] = useDebounce(query, 300)
  const normalizedQuery = debouncedQuery.trim()

  useEffect(() => {
    setQuery("")
    setRecent(readRecent(searchEndpoint))
  }, [searchEndpoint])

  const optionsQuery = useQuery({
    queryKey: ["relationship-picker", searchEndpoint, displayField, normalizedQuery],
    queryFn: async (): Promise<RelationOption[]> => {
      if (!searchEndpoint) return []

      const params = new URLSearchParams({ limit: "20" })
      if (normalizedQuery) params.set("q", normalizedQuery)
      const records = await backendApi.get<Record<string, unknown>[]>(
        `${searchEndpoint}?${params.toString()}`
      )
      return records.map((record) => ({
        id: String(record.id),
        label: String(
          record.label ??
            record[displayField] ??
            record.displayName ??
            record.nickname ??
            record.username ??
            record.id
        ),
        ...(typeof record.imageUrl === "string" ? { imageUrl: record.imageUrl } : {})
      }))
    },
    enabled: Boolean(searchEndpoint)
  })

  const recordRecent = useCallback(
    (option: RelationOption) => {
      if (!searchEndpoint) return

      setRecent((previous) => {
        const next = [option, ...previous.filter((item) => item.id !== option.id)].slice(
          0,
          MAX_RECENT
        )
        localStorage.setItem(RECENT_KEY(searchEndpoint), JSON.stringify(next))
        return next
      })
    },
    [searchEndpoint]
  )

  return {
    query,
    setQuery,
    displayOptions: optionsQuery.data ?? recent,
    loading: optionsQuery.isFetching,
    recordRecent
  }
}
