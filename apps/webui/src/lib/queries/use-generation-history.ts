/**
 * 生成历史 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import { generationHistoryApi } from "@/lib/api/rest/ai/generation-history"

const KEYS = {
  all: ["generation-history"] as const,
  list: (page: number, type?: "image" | "video") =>
    ["generation-history", page, type] as const
}

/** 生成历史列表 */
export function useGenerationHistory(page = 0, size = 20, type?: "image" | "video") {
  return useQuery({
    queryKey: KEYS.list(page, type),
    queryFn: () => generationHistoryApi.list({ page, size, type })
  })
}
