/**
 * 仪表盘 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
  type DashboardPresetMutateData,
  type DashboardWidgetVO,
  dashboardApi,
  type WidgetConfig
} from "@/lib/api/rest/dashboard/dashboard"

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
