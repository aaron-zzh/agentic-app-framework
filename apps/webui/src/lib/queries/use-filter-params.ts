/**
 * useFilterParams——筛选条件与 URL 参数双向同步
 * @author AaronZZH & Kiro
 */

"use client"

import { usePathname, useRouter, useSearchParams } from "next/navigation"
import { useCallback, useMemo } from "react"

import type { FilterCondition } from "@/lib/types/entity/filter"

const FILTER_PARAM = "filter"

/** 无需输入值的操作符——即使 values 为空也应写入 URL */
const VALUELESS_OPERATORS = new Set([
  "isEmpty",
  "isNotEmpty",
  "isNull",
  "isNotNull",
  "isTrue",
  "isFalse"
])

interface FilterPayload {
  logic: "and"
  conditions: FilterCondition[]
}

function encodeFilterPayload(conditions: FilterCondition[]): Record<string, string> {
  if (!conditions.length) return {}

  const payload: FilterPayload = { logic: "and", conditions }
  return { [FILTER_PARAM]: encodeBase64Url(JSON.stringify(payload)) }
}

/** 将有效筛选条件编码为查询窗口识别的 Base64URL JSON 参数。 */
export function encodeFilterParams(filters: FilterCondition[]): Record<string, string> {
  return encodeFilterPayload(
    filters.filter((filter) => filter.values.length > 0 || VALUELESS_OPERATORS.has(filter.operator))
  )
}

/** 从 URL 参数解码筛选条件。 */
export function decodeFilterParams(searchParams: URLSearchParams): FilterCondition[] {
  const encoded = searchParams.get(FILTER_PARAM)
  if (!encoded) return []

  try {
    const payload: unknown = JSON.parse(decodeBase64Url(encoded))
    if (!isFilterPayload(payload)) return []
    return payload.conditions
  } catch {
    return []
  }
}

function isFilterPayload(value: unknown): value is FilterPayload {
  if (!value || typeof value !== "object") return false
  const payload = value as Record<string, unknown>
  return (
    payload.logic === "and" &&
    Array.isArray(payload.conditions) &&
    payload.conditions.every(isFilterCondition)
  )
}

function isFilterCondition(value: unknown): value is FilterCondition {
  if (!value || typeof value !== "object") return false
  const condition = value as Record<string, unknown>
  return (
    typeof condition.field === "string" &&
    typeof condition.operator === "string" &&
    Array.isArray(condition.values) &&
    condition.values.every((item) => typeof item === "string")
  )
}

function encodeBase64Url(value: string): string {
  const bytes = new TextEncoder().encode(value)
  const binary = Array.from(bytes, (byte) => String.fromCharCode(byte)).join("")
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "")
}

function decodeBase64Url(value: string): string {
  const base64 = value.replaceAll("-", "+").replaceAll("_", "/")
  const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=")
  const binary = atob(padded)
  const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

/** 筛选条件 URL 同步 Hook */
export function useFilterParams() {
  const searchParams = useSearchParams()
  const router = useRouter()
  const pathname = usePathname()

  const filters = useMemo(() => decodeFilterParams(searchParams), [searchParams])

  const setFilters = useCallback(
    (next: FilterCondition[]) => {
      const params = new URLSearchParams(searchParams.toString())
      params.delete(FILTER_PARAM)
      for (const key of [...params.keys()]) {
        if (key.startsWith("f_")) params.delete(key)
      }

      const encoded = encodeFilterParams(next)
      for (const [key, value] of Object.entries(encoded)) {
        params.set(key, value)
      }
      if (process.env.NODE_ENV === "development") {
        // biome-ignore lint/suspicious/noConsole: 开发环境输出与 URL 一致的可复制筛选条件。
        console.info(
          `当前筛选参数: ${JSON.stringify(decodeFilterParams(new URLSearchParams(encoded)))}`
        )
      }
      params.set("page", "1")
      router.push(`${pathname}?${params.toString()}`)
    },
    [searchParams, router, pathname]
  )

  return [filters, setFilters] as const
}
