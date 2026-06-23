/**
 * useEstimateAigcCredits——调用 POST /api/aigc/tasks/estimate 查询预估积分
 *
 * 参数变化后 500ms 防抖再请求，避免频繁调用。
 * 返回 { credits, sufficient, isLoading }
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useQuery } from "@tanstack/react-query"
import { useEffect, useState } from "react"
import { request } from "@/lib/api/rest/crud/client"
import { useCreditBalance } from "@/lib/queries/use-credits"

export interface EstimateAigcCreditsParams {
  type: string
  model: string | null
  params?: Record<string, unknown>
  /** 有提示词才触发请求 */
  prompt?: string
  /** 是否启用，false 时不发请求（默认 true） */
  enabled?: boolean
}

export function useEstimateAigcCredits({
  type,
  model,
  params,
  prompt: _prompt,
  enabled = true
}: EstimateAigcCreditsParams) {
  // 防抖：500ms 后才更新 key 触发请求
  const [debouncedKey, setDebouncedKey] = useState(() => JSON.stringify({ type, model, params }))

  useEffect(() => {
    const t = setTimeout(() => {
      setDebouncedKey(JSON.stringify({ type, model, params }))
    }, 500)
    return () => clearTimeout(t)
  }, [type, model, params])

  const { data: balance } = useCreditBalance()

  const { data: credits, isLoading } = useQuery({
    queryKey: ["aigc", "estimate", debouncedKey],
    queryFn: () =>
      request<number>("/aigc/tasks/estimate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ type, model, params: params ?? {} })
      }),
    enabled: enabled && !!model,
    staleTime: 30_000,
    retry: false
  })

  const sufficient = credits == null ? true : (balance?.balance ?? 0) >= credits

  return { credits: credits ?? null, sufficient, isLoading }
}
