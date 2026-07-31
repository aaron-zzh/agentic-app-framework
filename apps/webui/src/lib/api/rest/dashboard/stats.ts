/**
 * 运营统计 API 客户端
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import { request } from "../entity/crud"

/** 趋势数据点 */
export interface TrendPoint {
  time: string
  value: number
}

/** 趋势查询参数 */
export interface TrendParams {
  metric: string
  period: "hour" | "day" | "week" | "month"
  startDate?: string
  endDate?: string
}

/** 漏斗阶段 */
export interface FunnelStage {
  name: string
  value: number
}

/** 留存率数据 */
export interface RetentionData {
  date: string
  day1: number
  day3: number
  day7: number
  day14: number
  day30: number
}

/** 概览指标 */
export interface OverviewMetrics {
  dau: number
  dauChange: number
  mau: number
  mauChange: number
  newUsers: number
  newUsersChange: number
  avgSessionDuration: number
  avgSessionDurationChange: number
}

export const statsApi = {
  /** 获取趋势数据 */
  getTrend: (params: TrendParams) => {
    const qs = new URLSearchParams({
      metric: params.metric,
      period: params.period,
      ...(params.startDate && { startDate: params.startDate }),
      ...(params.endDate && { endDate: params.endDate })
    })
    return request<TrendPoint[]>(`/stats/trend?${qs.toString()}`)
  },

  /** 获取漏斗数据 */
  getFunnel: () => request<FunnelStage[]>("/stats/funnel"),

  /** 获取留存率 */
  getRetention: () => request<RetentionData[]>("/stats/retention"),

  /** 获取概览指标 */
  getOverview: () => request<OverviewMetrics>("/stats/overview")
}

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
