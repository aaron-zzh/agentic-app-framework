/**
 * useFilterParams——筛选条件与 URL 参数双向同步
 * @author AaronZZH & Kiro
 */

"use client"

import { usePathname, useRouter, useSearchParams } from "next/navigation"
import { useCallback, useMemo } from "react"

import type { FilterCondition } from "@/lib/types/entity/filter"

const FILTER_PREFIX = "f_"

/** 将筛选条件编码为 URL 参数 */
function encodeFilters(filters: FilterCondition[]): Record<string, string> {
  const params: Record<string, string> = {}
  for (const f of filters) {
    params[`${FILTER_PREFIX}${f.field}`] = `${f.operator}:${f.value}`
  }
  return params
}

/** 从 URL 参数解码筛选条件 */
function decodeFilters(searchParams: URLSearchParams): FilterCondition[] {
  const filters: FilterCondition[] = []
  for (const [key, value] of searchParams.entries()) {
    if (!key.startsWith(FILTER_PREFIX)) continue
    const field = key.slice(FILTER_PREFIX.length)
    const colonIdx = value.indexOf(":")
    if (colonIdx === -1) continue
    filters.push({
      field,
      operator: value.slice(0, colonIdx),
      value: value.slice(colonIdx + 1)
    })
  }
  return filters
}

/** 筛选条件 URL 同步 Hook */
export function useFilterParams() {
  const searchParams = useSearchParams()
  const router = useRouter()
  const pathname = usePathname()

  const filters = useMemo(() => decodeFilters(searchParams), [searchParams])

  const setFilters = useCallback(
    (next: FilterCondition[]) => {
      const params = new URLSearchParams(searchParams.toString())
      // 清除旧筛选参数
      for (const key of [...params.keys()]) {
        if (key.startsWith(FILTER_PREFIX)) params.delete(key)
      }
      // 写入新筛选参数
      const encoded = encodeFilters(next.filter((f) => f.value || f.operator === "isEmpty"))
      for (const [k, v] of Object.entries(encoded)) {
        params.set(k, v)
      }
      // 重置页码
      params.set("page", "1")
      router.push(`${pathname}?${params.toString()}`)
    },
    [searchParams, router, pathname]
  )

  return [filters, setFilters] as const
}
