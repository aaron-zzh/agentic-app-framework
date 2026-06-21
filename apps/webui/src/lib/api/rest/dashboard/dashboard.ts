/**
 * 仪表盘 API 客户端
 * @author AaronZZH & Kiro
 */

import { request } from "../entity/crud"

/** Widget 类型 */
export type WidgetType =
  | "counter"
  | "chart"
  | "echarts"
  | "list"
  | "progress"
  | "shortcut"
  | "custom"
  | "finance"

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

/** Finance Widget 配置——金融类专属组件 */
export interface FinanceWidgetConfig {
  type: "finance"
  component:
    | "overview"
    | "multi-series-chart"
    | "expenses-category"
    | "card-carousel"
    | "transaction-list"
}

export type WidgetConfig =
  | CounterWidgetConfig
  | ChartWidgetConfig
  | ListWidgetConfig
  | ProgressWidgetConfig
  | ShortcutWidgetConfig
  | CustomWidgetConfig
  | EChartsWidgetConfig
  | FinanceWidgetConfig

/** 仪表盘 Widget */
export interface DashboardWidgetVO {
  id: string
  type: WidgetType
  title: string
  position: WidgetPosition
  config: WidgetConfig
}

/** 可用指标元数据（counter Widget 配置用） */
export interface MetricMeta {
  key: string
  label: string
  group: string
  aggregation: "count" | "sum"
  userScopable: boolean
}

/** 仪表盘预设（后端返回） */
export interface DashboardPresetVO {
  id: string
  presetKey: string
  name: string
  description: string
  adminOnly: boolean
  refreshInterval: number
  widgets: DashboardWidgetVO[]
  sortOrder: number
}

/** 仪表盘定义 */
export interface DashboardVO {
  id: string
  name: string
  widgets: DashboardWidgetVO[]
  refreshInterval?: number
  shared?: boolean
}

/** Widget 数据响应 */
export interface WidgetDataVO {
  value?: number
  items?: Record<string, unknown>[]
  progress?: { current: number; target: number }
  chartData?: Record<string, unknown>[]
  /** 同环比百分比（counter 类用），>0 上升、<0 下降；后端缺失时前端不显示 */
  trend?: number
  /** 时序短点数组（counter 类用），用于右下角 sparkline；少于 2 点时不渲染 */
  sparkline?: number[]
}

export interface DashboardPresetMutateData {
  name?: string
  description?: string
  adminOnly?: boolean
  refreshInterval?: number
  widgets?: DashboardWidgetVO[]
}

export const dashboardApi = {
  /** 获取预设列表 */
  listPresets: () => request<DashboardPresetVO[]>("/system/dashboards/presets"),

  /** 创建预设（管理员） */
  createPreset: (data: DashboardPresetMutateData & { name: string }) =>
    request<DashboardPresetVO>("/system/dashboards/presets", {
      method: "POST",
      body: JSON.stringify(data)
    }),

  /** 更新预设（管理员） */
  updatePreset: (id: string, data: DashboardPresetMutateData) =>
    request<DashboardPresetVO>(`/system/dashboards/presets/${id}`, {
      method: "PUT",
      body: JSON.stringify(data)
    }),

  /** 删除预设（管理员，软删除） */
  deletePreset: (id: string) =>
    request<void>(`/system/dashboards/presets/${id}`, { method: "DELETE" }),

  /** 获取可用指标列表（counter Widget 配置用） */
  listMetrics: () => request<MetricMeta[]>("/system/dashboards/metrics"),

  /** 获取仪表盘列表 */
  list: () => request<DashboardVO[]>("/system/dashboards"),

  /** 获取单个仪表盘 */
  get: (id: string) => request<DashboardVO>(`/system/dashboards/${id}`),

  /** 获取默认仪表盘 */
  getDefault: () => request<DashboardVO | null>("/system/dashboards/default"),

  /** 保存仪表盘布局 */
  saveLayout: (id: string, layout: DashboardWidgetVO[]) =>
    request<void>(`/system/dashboards/${id}/layout`, {
      method: "PUT",
      body: JSON.stringify({ layout })
    }),

  /** 创建仪表盘 */
  create: (data: { name: string; shared?: boolean }) =>
    request<DashboardVO>("/system/dashboards", { method: "POST", body: JSON.stringify(data) }),

  /** 重命名仪表盘 */
  rename: (id: string, name: string) =>
    request<DashboardVO>(`/system/dashboards/${id}`, {
      method: "PUT",
      body: JSON.stringify({ name })
    }),

  /** 删除仪表盘 */
  delete: (id: string) => request<void>(`/system/dashboards/${id}`, { method: "DELETE" }),

  /** 获取 Widget 数据 */
  getWidgetData: (widgetId: string, config: WidgetConfig) =>
    request<WidgetDataVO>(`/system/dashboards/widgets/${widgetId}/data`, {
      method: "POST",
      body: JSON.stringify(config)
    })
}
