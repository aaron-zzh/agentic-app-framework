import { backendApi } from "../backend-client"

/**
 * 仪表盘 API 客户端
 * @author AaronZZH & Kiro
 */

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
  | "billing"

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

/** Billing Widget 配置——个人积分仪表盘专属组件，从后端拉真实数据。 */
export interface BillingWidgetConfig {
  type: "billing"
  component: "overview" | "multi-series-chart" | "expenses-category" | "transaction-list"
  /** transactions 列表条数（仅 transaction-list 用），默认 10，上限 50 */
  limit?: number
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
  | BillingWidgetConfig

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
  /** 复杂 widget（如 billing）专用——后端返回的扩展数据 Map，字段由 widget component 自行解释 */
  data?: Record<string, unknown>
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
  listPresets: () => backendApi.get<DashboardPresetVO[]>("/system/dashboards/presets"),

  /** 创建预设（管理员） */
  createPreset: (data: DashboardPresetMutateData & { name: string }) =>
    backendApi.post<DashboardPresetVO>("/system/dashboards/presets", data),

  /** 更新预设（管理员） */
  updatePreset: (id: string, data: DashboardPresetMutateData) =>
    backendApi.put<DashboardPresetVO>(`/system/dashboards/presets/${id}`, data),

  /** 删除预设（管理员，软删除） */
  deletePreset: (id: string) => backendApi.delete<void>(`/system/dashboards/presets/${id}`),

  /** 获取可用指标列表（counter Widget 配置用） */
  listMetrics: () => backendApi.get<MetricMeta[]>("/system/dashboards/metrics"),

  /** 获取仪表盘列表 */
  list: () => backendApi.get<DashboardVO[]>("/system/dashboards"),

  /** 获取单个仪表盘 */
  get: (id: string) => backendApi.get<DashboardVO>(`/system/dashboards/${id}`),

  /** 获取默认仪表盘 */
  getDefault: () => backendApi.get<DashboardVO | null>("/system/dashboards/default"),

  /** 保存仪表盘布局 */
  saveLayout: (id: string, layout: DashboardWidgetVO[]) =>
    backendApi.put<void>(`/system/dashboards/${id}/layout`, { layout }),

  /** 创建仪表盘 */
  create: (data: { name: string; shared?: boolean }) =>
    backendApi.post<DashboardVO>("/system/dashboards", data),

  /** 重命名仪表盘 */
  rename: (id: string, name: string) =>
    backendApi.put<DashboardVO>(`/system/dashboards/${id}`, { name }),

  /** 删除仪表盘 */
  delete: (id: string) => backendApi.delete<void>(`/system/dashboards/${id}`),

  /** 获取 Widget 数据 */
  getWidgetData: (widgetId: string, config: WidgetConfig) =>
    backendApi.post<WidgetDataVO>(`/system/dashboards/widgets/${widgetId}/data`, config)
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

const KEYS = {
  all: ["dashboard"] as const,
  default: () => ["dashboard", "default"] as const,
  detail: (id: string) => ["dashboard", id] as const,
  widgetData: (widgetId: string, config: WidgetConfig) =>
    ["dashboard", "widget", widgetId, config] as const
}

/** 获取默认仪表盘 */
export function useDashboard() {
  return useQuery({
    queryKey: KEYS.default(),
    queryFn: () => dashboardApi.getDefault(),
    staleTime: 5 * 60 * 1000, // 5 分钟内不重新请求
    retry: false // 接口出错不重试，直接降级到本地 preset
  })
}

/** 获取指定仪表盘 */
export function useDashboardById(id: string) {
  return useQuery({
    queryKey: KEYS.detail(id),
    queryFn: () => dashboardApi.get(id),
    enabled: !!id
  })
}

/** 获取 Widget 数据（支持自动刷新） */
export function useWidgetData(widgetId: string, config: WidgetConfig, refreshInterval?: number) {
  return useQuery({
    queryKey: KEYS.widgetData(widgetId, config),
    queryFn: () => dashboardApi.getWidgetData(widgetId, config),
    refetchInterval: refreshInterval ? refreshInterval * 1000 : undefined
  })
}

/** 保存仪表盘布局 */
export function useSaveDashboardLayout() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, layout }: { id: string; layout: DashboardWidgetVO[] }) =>
      dashboardApi.saveLayout(id, layout),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}

/** 创建仪表盘 */
export function useCreateDashboard() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { name: string; shared?: boolean }) => dashboardApi.create(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}

/** 获取预设列表（优先从接口，失败降级到本地硬编码） */
export function usePresets() {
  return useQuery({
    queryKey: ["dashboard", "presets"] as const,
    queryFn: () => dashboardApi.listPresets(),
    staleTime: 10 * 60 * 1000,
    retry: false
  })
}

/** 获取可用指标列表（counter Widget 配置用） */
export function useMetrics() {
  return useQuery({
    queryKey: ["dashboard", "metrics"] as const,
    queryFn: () => dashboardApi.listMetrics(),
    staleTime: 60 * 60 * 1000 // 1 小时，指标列表基本不变
  })
}

/** 列出当前用户所有仪表盘 */
export function useDashboardList() {
  return useQuery({
    queryKey: KEYS.all,
    queryFn: () => dashboardApi.list(),
    staleTime: 5 * 60 * 1000
  })
}

/** 重命名仪表盘 */
export function useRenameDashboard() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) => dashboardApi.rename(id, name),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}

/** 删除仪表盘 */
export function useDeleteDashboard() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => dashboardApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}

const PRESET_KEYS = {
  all: ["dashboard", "presets"] as const
}

/** 创建预设 */
export function useCreatePreset() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: DashboardPresetMutateData & { name: string }) =>
      dashboardApi.createPreset(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: PRESET_KEYS.all })
  })
}

/** 更新预设 */
export function useUpdatePreset() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: DashboardPresetMutateData }) =>
      dashboardApi.updatePreset(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: PRESET_KEYS.all })
  })
}

/** 删除预设 */
export function useDeletePreset() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => dashboardApi.deletePreset(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: PRESET_KEYS.all })
  })
}
