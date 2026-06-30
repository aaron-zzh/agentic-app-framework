/**
 * 积分消耗统计 API 客户端（管理员仪表盘）
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"
import type { PageResult } from "../crud/client"

/** 积分消耗概览（顶部三张统计卡） */
export interface CreditsOverviewVO {
  /** 当前可用余额 */
  balance: number
  /** 本月消耗积分 */
  monthConsumed: number
  /** 本月充值积分 */
  monthRecharged: number
  /** 本月消耗环比变化率（%），正数=增长 */
  consumedChangeRate: number
  /** 本月充值环比变化率（%），正数=增长 */
  rechargedChangeRate: number
}

/** 积分消耗分类分布（饼图单项） */
export interface CreditsCategoryItem {
  /** 分类名称（如 AIGC / AI_CALL / 其他） */
  name: string
  /** 消耗积分 */
  value: number
}

/** 积分消耗分类分布（饼图整体） */
export interface CreditsCategoryVO {
  items: CreditsCategoryItem[]
  /** 合计消耗 */
  total: number
}

/** 积分流水记录（管理员视角） */
export interface CreditRecordVO {
  id: string
  /** 用户昵称 */
  userName: string
  /** 脱敏手机号，格式 138****8888 */
  phone: string | null
  /** 流水类型：EARN / SPEND / FREEZE / UNFREEZE / EXPIRE */
  type: "EARN" | "SPEND" | "FREEZE" | "UNFREEZE" | "EXPIRE"
  /** 消费分类（仅 SPEND 有值） */
  category: string | null
  /** 积分变动量（绝对值） */
  amount: number
  /** 变动后余额 */
  balanceAfter: number
  /** 来源描述 */
  source: string | null
  /** 备注 */
  remark: string | null
  /** 创建时间 */
  createTime: string
}

/** 趋势图数据（对齐后端 TrendSeriesVO） */
export interface TrendSeriesVO {
  categories: string[]
  series: { name: string; data: number[] }[]
}

export const creditsAnalyticsApi = {
  /** 获取积分消耗概览 */
  getOverview: () => backendApi.get<CreditsOverviewVO>("/stats/credits/overview"),

  /** 获取积分消耗分类分布（饼图） */
  getByCategory: () => backendApi.get<CreditsCategoryVO>("/stats/credits/by-category"),

  /** 获取积分流水分页（管理员视角） */
  getRecords: (pageNo = 1, pageSize = 20, type?: string) => {
    const params = new URLSearchParams({
      pageNo: String(pageNo),
      pageSize: String(pageSize),
      ...(type ? { type } : {})
    })
    return backendApi.get<PageResult<CreditRecordVO>>(
      `/stats/credits/records?${params.toString()}`
    )
  },

  /**
   * 获取积分消耗趋势（复用 /stats/trend）
   * 后端返回 TrendSeriesVO：{ categories, series }
   */
  getTrend: (period: "day" | "month") => {
    const params = new URLSearchParams({ metric: "credit_cost", period })
    return backendApi.get<TrendSeriesVO>(`/stats/trend?${params.toString()}`)
  }
}
