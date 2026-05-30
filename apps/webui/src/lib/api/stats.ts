/**
 * 运营统计 API 客户端
 * @author AaronZZH & Kiro
 */

import { request } from "./client"

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
