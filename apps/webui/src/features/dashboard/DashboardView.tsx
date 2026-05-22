/**
 * DashboardView——仪表盘主视图（react-grid-layout 拖拽布局）
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useMemo, useState } from "react"
import { Responsive, WidthProvider, type Layout } from "react-grid-layout"
import { GripVertical, Pencil, Save, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import type {
  DashboardWidgetVO,
  WidgetType
} from "@/lib/api/dashboard"
import { useDashboard, useSaveDashboardLayout } from "@/lib/queries/use-dashboard"
import { AddWidgetDialog } from "./AddWidgetDialog"
import { ChartWidget, CounterWidget, ListWidget, ProgressWidget, ShortcutWidget } from "./widgets"

import "react-grid-layout/css/styles.css"

const ResponsiveGridLayout = WidthProvider(Responsive)

/** 根据 Widget 类型渲染对应组件 */
function renderWidget(widget: DashboardWidgetVO, refreshInterval?: number) {
  const { id, title, config } = widget
  switch (config.type) {
    case "counter":
      return <CounterWidget widgetId={id} title={title} config={config} refreshInterval={refreshInterval} />
    case "chart":
      return <ChartWidget widgetId={id} title={title} config={config} refreshInterval={refreshInterval} />
    case "list":
      return <ListWidget widgetId={id} title={title} config={config} refreshInterval={refreshInterval} />
    case "progress":
      return <ProgressWidget widgetId={id} title={title} config={config} refreshInterval={refreshInterval} />
    case "shortcut":
      return <ShortcutWidget title={title} config={config} />
    default:
      return <div className="flex h-full items-center justify-center text-muted-foreground text-sm">未知组件</div>
  }
}

/** 生成新 Widget 的默认配置 */
function createDefaultWidget(type: WidgetType): DashboardWidgetVO {
  const id = `widget-${Date.now()}`
  const base = { id, type, title: "", position: { x: 0, y: Infinity, w: 4, h: 3 } }

  switch (type) {
    case "counter":
      return { ...base, title: "统计", config: { type: "counter", entity: "", aggregation: "count" } }
    case "chart":
      return { ...base, title: "图表", config: { type: "chart", entity: "", chartType: "bar", xField: "", yField: "" } }
    case "list":
      return { ...base, title: "列表", config: { type: "list", entity: "", columns: [], limit: 5 } }
    case "progress":
      return { ...base, title: "进度", config: { type: "progress", label: "目标", current: 0, target: 100 } }
    case "shortcut":
      return { ...base, title: "快捷入口", config: { type: "shortcut", items: [] } }
    default:
      return { ...base, title: "自定义", config: { type: "custom", component: "" } }
  }
}

export function DashboardView() {
  const { data: dashboard, isLoading } = useDashboard()
  const saveMutation = useSaveDashboardLayout()
  const [editing, setEditing] = useState(false)
  const [localWidgets, setLocalWidgets] = useState<DashboardWidgetVO[] | null>(null)

  /** 当前展示的 widgets（编辑模式用本地状态，否则用服务端数据） */
  const widgets = localWidgets ?? dashboard?.layout ?? []

  /** react-grid-layout 的 layouts 数据 */
  const layouts = useMemo(() => {
    const lg: Layout[] = widgets.map((w) => ({
      i: w.id,
      x: w.position.x,
      y: w.position.y,
      w: w.position.w,
      h: w.position.h
    }))
    return { lg }
  }, [widgets])

  /** 进入编辑模式 */
  const startEditing = useCallback(() => {
    setLocalWidgets(dashboard?.layout ?? [])
    setEditing(true)
  }, [dashboard])

  /** 取消编辑 */
  const cancelEditing = useCallback(() => {
    setLocalWidgets(null)
    setEditing(false)
  }, [])

  /** 保存布局 */
  const saveLayout = useCallback(() => {
    if (!dashboard || !localWidgets) return
    saveMutation.mutate(
      { id: dashboard.id, layout: localWidgets },
      { onSuccess: () => { setLocalWidgets(null); setEditing(false) } }
    )
  }, [dashboard, localWidgets, saveMutation])

  /** 布局变更回调 */
  const handleLayoutChange = useCallback(
    (layout: Layout[]) => {
      if (!editing || !localWidgets) return
      const updated = localWidgets.map((w) => {
        const l = layout.find((item) => item.i === w.id)
        if (!l) return w
        return { ...w, position: { x: l.x, y: l.y, w: l.w, h: l.h } }
      })
      setLocalWidgets(updated)
    },
    [editing, localWidgets]
  )

  /** 添加 Widget */
  const handleAddWidget = useCallback(
    (type: WidgetType) => {
      const newWidget = createDefaultWidget(type)
      setLocalWidgets((prev) => [...(prev ?? []), newWidget])
    },
    []
  )

  /** 删除 Widget */
  const handleRemoveWidget = useCallback(
    (widgetId: string) => {
      setLocalWidgets((prev) => (prev ?? []).filter((w) => w.id !== widgetId))
    },
    []
  )

  if (isLoading) {
    return (
      <div className="space-y-4 p-6">
        <Skeleton className="h-8 w-48" />
        <div className="grid grid-cols-3 gap-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={`dash-sk-${i}`} className="h-40" />
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-1 flex-col overflow-auto p-6">
      {/* 顶部工具栏 */}
      <div className="mb-4 flex items-center justify-between">
        <h1 className="font-bold text-xl">📊 工作台</h1>
        <div className="flex items-center gap-2">
          {editing ? (
            <>
              <AddWidgetDialog onAdd={handleAddWidget} />
              <Button variant="ghost" size="sm" onClick={cancelEditing}>
                <X className="mr-1 h-4 w-4" />
                取消
              </Button>
              <Button size="sm" onClick={saveLayout} disabled={saveMutation.isPending}>
                <Save className="mr-1 h-4 w-4" />
                保存
              </Button>
            </>
          ) : (
            <Button variant="outline" size="sm" onClick={startEditing}>
              <Pencil className="mr-1 h-4 w-4" />
              编辑布局
            </Button>
          )}
        </div>
      </div>

      {/* 网格布局 */}
      {widgets.length === 0 ? (
        <div className="flex flex-1 items-center justify-center text-muted-foreground">
          {editing ? "点击「+ 添加 Widget」开始构建仪表盘" : "暂无 Widget，点击「编辑布局」添加"}
        </div>
      ) : (
        <ResponsiveGridLayout
          className="layout"
          layouts={layouts}
          breakpoints={{ lg: 1200, md: 996, sm: 768, xs: 480 }}
          cols={{ lg: 12, md: 9, sm: 6, xs: 3 }}
          rowHeight={80}
          isDraggable={editing}
          isResizable={editing}
          onLayoutChange={handleLayoutChange}
          draggableHandle=".drag-handle"
        >
          {widgets.map((widget) => (
            <div key={widget.id} className="relative">
              {editing && (
                <div className="absolute top-1 right-1 left-1 z-10 flex items-center justify-between">
                  <span className="drag-handle cursor-grab">
                    <GripVertical className="h-4 w-4 text-muted-foreground" />
                  </span>
                  <button
                    type="button"
                    className="rounded p-0.5 hover:bg-destructive/10"
                    onClick={() => handleRemoveWidget(widget.id)}
                  >
                    <X className="h-3 w-3 text-destructive" />
                  </button>
                </div>
              )}
              {renderWidget(widget, dashboard?.refreshInterval)}
            </div>
          ))}
        </ResponsiveGridLayout>
      )}
    </div>
  )
}
