/**
 * 仪表盘预设模板——运营/技术/财务三套预设布局
 * @author AaronZZH & Kiro
 */

import type { DashboardWidgetVO } from "@/lib/api/dashboard"

export interface DashboardPreset {
  key: string
  name: string
  description: string
  widgets: DashboardWidgetVO[]
  refreshInterval: number
}

/** 运营仪表盘预设 */
const operationsPreset: DashboardPreset = {
  key: "operations",
  name: "运营仪表盘",
  description: "DAU/MAU 趋势、用户漏斗、留存率分析",
  refreshInterval: 60,
  widgets: [
    {
      id: "ops-dau-trend",
      type: "echarts",
      title: "DAU 趋势",
      position: { x: 0, y: 0, w: 6, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "line",
        metric: "dau",
        period: "day"
      }
    },
    {
      id: "ops-mau-trend",
      type: "echarts",
      title: "MAU 趋势",
      position: { x: 6, y: 0, w: 6, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "bar",
        metric: "mau",
        period: "month"
      }
    },
    {
      id: "ops-funnel",
      type: "echarts",
      title: "用户行为漏斗",
      position: { x: 0, y: 4, w: 6, h: 4 },
      config: { type: "echarts", statsType: "funnel" }
    },
    {
      id: "ops-retention",
      type: "echarts",
      title: "用户留存率",
      position: { x: 6, y: 4, w: 6, h: 4 },
      config: { type: "echarts", statsType: "retention" }
    }
  ]
}

/** 技术仪表盘预设 */
const techPreset: DashboardPreset = {
  key: "tech",
  name: "技术仪表盘",
  description: "API 调用量、错误率、响应时间监控",
  refreshInterval: 30,
  widgets: [
    {
      id: "tech-api-calls",
      type: "echarts",
      title: "API 调用量",
      position: { x: 0, y: 0, w: 8, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "line",
        metric: "api_calls",
        period: "hour"
      }
    },
    {
      id: "tech-error-rate",
      type: "echarts",
      title: "错误率",
      position: { x: 8, y: 0, w: 4, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "bar",
        metric: "error_rate",
        period: "hour"
      }
    },
    {
      id: "tech-latency",
      type: "echarts",
      title: "平均响应时间",
      position: { x: 0, y: 4, w: 6, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "line",
        metric: "avg_latency",
        period: "hour"
      }
    },
    {
      id: "tech-active-users",
      type: "counter",
      title: "在线用户",
      position: { x: 6, y: 4, w: 3, h: 2 },
      config: {
        type: "counter",
        entity: "user",
        aggregation: "count",
        icon: "users",
        color: "blue"
      }
    },
    {
      id: "tech-uptime",
      type: "progress",
      title: "系统可用率",
      position: { x: 9, y: 4, w: 3, h: 2 },
      config: { type: "progress", label: "可用率", current: 99.9, target: 100 }
    }
  ]
}

/** 财务仪表盘预设 */
const financePreset: DashboardPreset = {
  key: "finance",
  name: "财务仪表盘",
  description: "收入趋势、订阅转化、Token 消耗",
  refreshInterval: 300,
  widgets: [
    {
      id: "fin-revenue",
      type: "echarts",
      title: "收入趋势",
      position: { x: 0, y: 0, w: 8, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "bar",
        metric: "revenue",
        period: "day"
      }
    },
    {
      id: "fin-conversion",
      type: "echarts",
      title: "订阅转化漏斗",
      position: { x: 8, y: 0, w: 4, h: 4 },
      config: { type: "echarts", statsType: "funnel" }
    },
    {
      id: "fin-token-usage",
      type: "echarts",
      title: "Token 消耗趋势",
      position: { x: 0, y: 4, w: 6, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "line",
        metric: "token_usage",
        period: "day"
      }
    },
    {
      id: "fin-arpu",
      type: "echarts",
      title: "ARPU 趋势",
      position: { x: 6, y: 4, w: 6, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "line",
        metric: "arpu",
        period: "month"
      }
    }
  ]
}

/** 所有预设模板 */
export const dashboardPresets: DashboardPreset[] = [operationsPreset, techPreset, financePreset]
