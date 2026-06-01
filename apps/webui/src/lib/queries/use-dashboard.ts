/**
 * 仪表盘 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { type DashboardWidgetVO, dashboardApi, type WidgetConfig } from "@/lib/api/rest/dashboard/dashboard"

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
    queryFn: () => dashboardApi.getDefault()
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
