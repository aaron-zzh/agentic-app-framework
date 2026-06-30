/**
 * 积分消耗统计 TanStack Query Hooks（管理员仪表盘）
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import { creditsAnalyticsApi } from "@/lib/api/rest/dashboard/credits-analytics"

const KEYS = {
  overview: () => ["credits-analytics", "overview"] as const,
  byCategory: () => ["credits-analytics", "by-category"] as const,
  records: (pageNo: number, pageSize: number, type?: string) =>
    ["credits-analytics", "records", pageNo, pageSize, type] as const,
  /** 趋势图复用现有 stats trend 接口，此处只定义 key 前缀 */
  trend: (period: string) => ["credits-analytics", "trend", period] as const
}

/** 积分消耗概览（顶部三张统计卡） */
export function useCreditsOverview() {
  return useQuery({
    queryKey: KEYS.overview(),
    queryFn: creditsAnalyticsApi.getOverview,
    staleTime: 5 * 60 * 1000
  })
}

/** 积分消耗分类分布（饼图） */
export function useCreditsByCategory() {
  return useQuery({
    queryKey: KEYS.byCategory(),
    queryFn: creditsAnalyticsApi.getByCategory,
    staleTime: 5 * 60 * 1000
  })
}

/** 积分流水分页（管理员视角） */
export function useCreditsRecords(pageNo = 1, pageSize = 20, type?: string) {
  return useQuery({
    queryKey: KEYS.records(pageNo, pageSize, type),
    queryFn: () => creditsAnalyticsApi.getRecords(pageNo, pageSize, type),
    staleTime: 60 * 1000
  })
}

/** 积分消耗趋势图（复用 /api/stats/trend，返回 TrendSeriesVO） */
export function useCreditsTrend(period: "day" | "month") {
  return useQuery({
    queryKey: KEYS.trend(period),
    queryFn: () => creditsAnalyticsApi.getTrend(period),
    staleTime: 5 * 60 * 1000
  })
}
