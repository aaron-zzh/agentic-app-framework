/**
 * 运营统计 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import { statsApi, type TrendParams } from "@/lib/api/stats"

const KEYS = {
  all: ["stats"] as const,
  trend: (params: TrendParams) => ["stats", "trend", params] as const,
  funnel: () => ["stats", "funnel"] as const,
  retention: () => ["stats", "retention"] as const,
  overview: () => ["stats", "overview"] as const
}

/** 获取趋势数据 */
export function useStatsTrend(params: TrendParams, refreshInterval?: number) {
  return useQuery({
    queryKey: KEYS.trend(params),
    queryFn: () => statsApi.getTrend(params),
    refetchInterval: refreshInterval ? refreshInterval * 1000 : undefined
  })
}

/** 获取漏斗数据 */
export function useStatsFunnel(refreshInterval?: number) {
  return useQuery({
    queryKey: KEYS.funnel(),
    queryFn: () => statsApi.getFunnel(),
    refetchInterval: refreshInterval ? refreshInterval * 1000 : undefined
  })
}

/** 获取留存率 */
export function useStatsRetention(refreshInterval?: number) {
  return useQuery({
    queryKey: KEYS.retention(),
    queryFn: () => statsApi.getRetention(),
    refetchInterval: refreshInterval ? refreshInterval * 1000 : undefined
  })
}

/** 获取概览指标 */
export function useStatsOverview(refreshInterval?: number) {
  return useQuery({
    queryKey: KEYS.overview(),
    queryFn: () => statsApi.getOverview(),
    refetchInterval: refreshInterval ? refreshInterval * 1000 : undefined
  })
}
