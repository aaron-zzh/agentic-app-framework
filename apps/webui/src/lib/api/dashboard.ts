/**
 * 仪表盘 API 客户端
 * @author AaronZZH & Kiro
 */

import { ApiError } from "./client"

/** Widget 类型 */
export type WidgetType =
  | "counter"
  | "chart"
  | "echarts"
  | "list"
  | "progress"
  | "shortcut"
  | "custom"

/** Widget 位置（react-grid-layout 格式） */
export interface WidgetPosition {
  x: number
  y: number
  w: number
  h: number
}

/** Counter Widget 配置 */
export interface CounterWidgetConfig {
  type: "counter"
  entity: string
  filter?: Record<string, unknown>
  aggregation: "count" | "sum"
  field?: string
  icon?: string
  color?: string
}

/** Chart Widget 配置 */
export interface ChartWidgetConfig {
  type: "chart"
  entity: string
  chartType: "line" | "bar" | "pie" | "area"
  xField: string
  yField: string
  filter?: Record<string, unknown>
}

/** List Widget 配置 */
export interface ListWidgetConfig {
  type: "list"
  entity: string
  columns: string[]
  filter?: Record<string, unknown>
  limit?: number
  linkTo?: string
}

/** Progress Widget 配置 */
export interface ProgressWidgetConfig {
  type: "progress"
  label: string
  current: number | string
  target: number | string
}

/** Shortcut Widget 配置 */
export interface ShortcutWidgetConfig {
  type: "shortcut"
  items: { label: string; icon: string; href: string }[]
}

/** Custom Widget 配置 */
export interface CustomWidgetConfig {
  type: "custom"
  component: string
}

/** ECharts Widget 配置——绑定 /api/stats 数据源 */
export interface EChartsWidgetConfig {
  type: "echarts"
  /** 图表子类型 */
  statsType: "trend" | "funnel" | "retention" | "pie"
  /** 趋势图表渲染类型 */
  chartType?: "line" | "bar"
  /** 指标名称（trend 类型使用） */
  metric?: string
  /** 时间粒度（trend 类型使用） */
  period?: "hour" | "day" | "week" | "month"
}

export type WidgetConfig =
  | CounterWidgetConfig
  | ChartWidgetConfig
  | ListWidgetConfig
  | ProgressWidgetConfig
  | ShortcutWidgetConfig
  | CustomWidgetConfig
  | EChartsWidgetConfig

/** 仪表盘 Widget */
export interface DashboardWidgetVO {
  id: string
  type: WidgetType
  title: string
  position: WidgetPosition
  config: WidgetConfig
}

/** 仪表盘定义 */
export interface DashboardVO {
  id: string
  name: string
  layout: DashboardWidgetVO[]
  refreshInterval?: number
  shared?: boolean
}

/** Widget 数据响应 */
export interface WidgetDataVO {
  value?: number
  items?: Record<string, unknown>[]
  progress?: { current: number; target: number }
  chartData?: Record<string, unknown>[]
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

export const dashboardApi = {
  /** 获取仪表盘列表 */
  list: () => req<DashboardVO[]>("/dashboards"),

  /** 获取单个仪表盘 */
  get: (id: string) => req<DashboardVO>(`/dashboards/${id}`),

  /** 获取默认仪表盘 */
  getDefault: () => req<DashboardVO>("/dashboards/default"),

  /** 保存仪表盘布局 */
  saveLayout: (id: string, layout: DashboardWidgetVO[]) =>
    req<void>(`/dashboards/${id}/layout`, {
      method: "PUT",
      body: JSON.stringify({ layout })
    }),

  /** 创建仪表盘 */
  create: (data: { name: string; shared?: boolean }) =>
    req<DashboardVO>("/dashboards", { method: "POST", body: JSON.stringify(data) }),

  /** 获取 Widget 数据 */
  getWidgetData: (widgetId: string, config: WidgetConfig) =>
    req<WidgetDataVO>(`/dashboards/widgets/${widgetId}/data`, {
      method: "POST",
      body: JSON.stringify(config)
    })
}
