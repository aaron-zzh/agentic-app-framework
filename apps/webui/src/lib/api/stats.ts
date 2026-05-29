/**
 * 运营统计 API 客户端
 * @author AaronZZH & Kiro
 */

import { ApiError } from "./client"

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

const BASE = process.env.NEXT_PUBLIC_API_URL ?? "/api"

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { "Content-Type": "application/json", ...init?.headers },
    ...init
  })
  if (!res.ok) throw new ApiError(res.status, `请求失败: ${res.statusText}`)
  const json = await res.json()
  if (json.code !== 0) throw new ApiError(json.code, json.message ?? "未知错误")
  return json.data as T
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
    return req<TrendPoint[]>(`/stats/trend?${qs.toString()}`)
  },

  /** 获取漏斗数据 */
  getFunnel: () => req<FunnelStage[]>("/stats/funnel"),

  /** 获取留存率 */
  getRetention: () => req<RetentionData[]>("/stats/retention"),

  /** 获取概览指标 */
  getOverview: () => req<OverviewMetrics>("/stats/overview")
}
