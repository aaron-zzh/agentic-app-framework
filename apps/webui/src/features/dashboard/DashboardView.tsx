/**
 * DashboardView——仪表盘主视图（react-grid-layout 拖拽布局）
 * @author AaronZZH & Kiro
 */

"use client"

import { GripVertical, LayoutDashboard, Pencil, Plus, Save, Trash2, X } from "lucide-react"
import { useCallback, useMemo, useState } from "react"
import { type Layout, Responsive, WidthProvider } from "react-grid-layout"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import type { DashboardVO, DashboardWidgetVO, WidgetType } from "@/lib/api/rest/dashboard/dashboard"
import {
  useCreateDashboard,
  useDashboard,
  useDashboardList,
  useDeleteDashboard,
  usePresets,
  useRenameDashboard,
  useSaveDashboardLayout
} from "@/lib/queries/use-dashboard"
import { useAuthStore } from "@/lib/store/auth-store"
import { AddWidgetDialog } from "./AddWidgetDialog"
import { ApplyPresetDialog } from "./ApplyPresetDialog"
import { WidgetEditDialog } from "./WidgetEditDialog"
import {
  BillingWidget,
  ChartWidget,
  CounterWidget,
  EChartsWidget,
  FinanceWidget,
  ListWidget,
  ProgressWidget,
  ShortcutWidget
} from "./widgets"

import "react-grid-layout/css/styles.css"

const ResponsiveGridLayout = WidthProvider(Responsive)

/** 根据 Widget 类型渲染对应组件 */
function renderWidget(widget: DashboardWidgetVO, refreshInterval?: number) {
  const { id, title, config } = widget
  switch (config.type) {
    case "counter":
      return (
        <CounterWidget
          widgetId={id}
          title={title}
          config={config}
          refreshInterval={refreshInterval}
        />
      )
    case "chart":
      return (
        <ChartWidget
          widgetId={id}
          title={title}
          config={config}
          refreshInterval={refreshInterval}
        />
      )
    case "echarts":
      return (
        <EChartsWidget
          widgetId={id}
          title={title}
          config={config}
          refreshInterval={refreshInterval}
        />
      )
    case "list":
      return (
        <ListWidget widgetId={id} title={title} config={config} refreshInterval={refreshInterval} />
      )
    case "progress":
      return (
        <ProgressWidget
          widgetId={id}
          title={title}
          config={config}
          refreshInterval={refreshInterval}
        />
      )
    case "shortcut":
      return <ShortcutWidget title={title} config={config} />
    case "finance":
      return <FinanceWidget title={title} config={config} />
    case "billing":
      return (
        <BillingWidget
          widgetId={id}
          title={title}
          config={config}
          refreshInterval={refreshInterval}
        />
      )
    default:
      return (
        <div className="flex h-full items-center justify-center text-muted-foreground text-sm">
          未知组件
        </div>
      )
  }
}

/** 生成新 Widget 的默认配置 */
function createDefaultWidget(type: WidgetType): DashboardWidgetVO {
  const id = `widget-${Date.now()}`
  const base = { id, type, title: "", position: { x: 0, y: Infinity, w: 4, h: 3 } }

  switch (type) {
    case "counter":
      return {
        ...base,
        title: "统计",
        config: { type: "counter", entity: "", aggregation: "count" }
      }
    case "chart":
      return {
        ...base,
        title: "图表",
        config: { type: "chart", entity: "", chartType: "bar", xField: "", yField: "" }
      }
    case "echarts":
      return {
        ...base,
        title: "ECharts 图表",
        config: {
          type: "echarts",
          statsType: "trend",
          chartType: "line",
          metric: "dau",
          period: "day"
        }
      }
    case "list":
      return { ...base, title: "列表", config: { type: "list", entity: "", columns: [], limit: 5 } }
    case "progress":
      return {
        ...base,
        title: "进度",
        config: { type: "progress", label: "目标", current: 0, target: 100 }
      }
    case "shortcut":
      return { ...base, title: "快捷入口", config: { type: "shortcut", items: [] } }
    default:
      return { ...base, title: "自定义", config: { type: "custom", component: "" } }
  }
}

export function DashboardView() {
  const { data: dashboard, isLoading } = useDashboard()
  const { data: remotePresets } = usePresets()
  const { data: dashboardList = [] } = useDashboardList()
  const saveMutation = useSaveDashboardLayout()
  const createMutation = useCreateDashboard()
  const renameMutation = useRenameDashboard()
  const deleteMutation = useDeleteDashboard()

  const [editing, setEditing] = useState(false)
  const [localWidgets, setLocalWidgets] = useState<DashboardWidgetVO[] | null>(null)
  // 当前选中的仪表盘 id，null 表示默认
  const [activeDashboardId, setActiveDashboardId] = useState<string | null>(null)
  const [newName, setNewName] = useState("")
  const [renamingId, setRenamingId] = useState<string | null>(null)
  const [renameValue, setRenameValue] = useState("")

  const userRoles = useAuthStore((s) => s.user?.roles)
  const isAdmin = userRoles?.some((r) => ["admin", "super_admin", "org_admin"].includes(r)) ?? false

  // 当前激活的仪表盘
  const activeDashboard: DashboardVO | undefined = activeDashboardId
    ? dashboardList.find((d) => d.id === activeDashboardId)
    : (dashboard ?? undefined)

  /** 默认 widgets：取后端预设（personal / admin），接口未就绪时返回空数组（页面显示空白由父级处理） */
  const defaultPresetKey = isAdmin ? "admin" : "personal"
  const defaultWidgets: DashboardWidgetVO[] = (() => {
    if (!remotePresets) return []
    const preset = remotePresets.find((p) => p.presetKey === defaultPresetKey) ?? remotePresets[0]
    return preset?.widgets ?? []
  })()
  const widgets =
    localWidgets ?? (activeDashboard?.widgets?.length ? activeDashboard.widgets : defaultWidgets)

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

  const startEditing = useCallback(() => {
    setLocalWidgets(activeDashboard?.widgets ?? [])
    setEditing(true)
  }, [activeDashboard])

  const cancelEditing = useCallback(() => {
    setLocalWidgets(null)
    setEditing(false)
  }, [])

  const saveLayout = useCallback(async () => {
    if (!localWidgets) return
    let dashboardId = activeDashboard?.id
    if (!dashboardId) {
      // 用户还没有仪表盘，自动创建一个默认仪表盘
      const created = await createMutation.mutateAsync({ name: "我的仪表盘" })
      dashboardId = created.id
    }
    saveMutation.mutate(
      { id: dashboardId, layout: localWidgets },
      {
        onSuccess: () => {
          setLocalWidgets(null)
          setEditing(false)
        }
      }
    )
  }, [activeDashboard, localWidgets, saveMutation, createMutation])

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

  const handleAddWidget = useCallback((partial: Partial<DashboardWidgetVO>) => {
    const type = partial.type ?? "counter"
    const newWidget = partial.config
      ? ({ ...createDefaultWidget(type), ...partial } as DashboardWidgetVO)
      : createDefaultWidget(type)
    setLocalWidgets((prev) => [...(prev ?? []), newWidget])
  }, [])

  const handleApplyPreset = useCallback(
    (widgets: DashboardWidgetVO[]) => {
      setLocalWidgets(widgets)
      // 应用预设后自动保存
      if (activeDashboard) {
        saveMutation.mutate(
          { id: activeDashboard.id, layout: widgets },
          {
            onSuccess: () => {
              setLocalWidgets(null)
              setEditing(false)
            }
          }
        )
      }
    },
    [activeDashboard, saveMutation]
  )

  const handleRemoveWidget = useCallback((widgetId: string) => {
    setLocalWidgets((prev) => (prev ?? []).filter((w) => w.id !== widgetId))
  }, [])

  const [editingWidget, setEditingWidget] = useState<DashboardWidgetVO | null>(null)

  const handleEditWidget = useCallback((updated: DashboardWidgetVO) => {
    setLocalWidgets((prev) => (prev ?? []).map((w) => (w.id === updated.id ? updated : w)))
  }, [])

  // 新建仪表盘
  const handleCreate = useCallback(() => {
    if (!newName.trim()) return
    createMutation.mutate(
      { name: newName.trim() },
      {
        onSuccess: (created) => {
          setNewName("")
          setActiveDashboardId(created.id)
        }
      }
    )
  }, [newName, createMutation])

  // 确认重命名
  const handleRename = useCallback(
    (id: string) => {
      if (!renameValue.trim()) return
      renameMutation.mutate(
        { id, name: renameValue.trim() },
        { onSuccess: () => setRenamingId(null) }
      )
    },
    [renameValue, renameMutation]
  )

  // 删除仪表盘
  const handleDelete = useCallback(
    (id: string) => {
      deleteMutation.mutate(id, {
        onSuccess: () => {
          if (activeDashboardId === id) setActiveDashboardId(null)
        }
      })
    },
    [activeDashboardId, deleteMutation]
  )

  if (isLoading) {
    return (
      <div className="p-6">
        <div className="mb-4 flex items-center justify-between">
          <Skeleton className="h-7 w-32" />
          <Skeleton className="h-8 w-24" />
        </div>
        <div className="grid grid-cols-12 gap-4">
          <Skeleton className="col-span-8 h-56 rounded-xl" />
          <Skeleton className="col-span-4 h-56 rounded-xl" />
          <Skeleton className="col-span-6 h-48 rounded-xl" />
          <Skeleton className="col-span-3 h-48 rounded-xl" />
          <Skeleton className="col-span-3 h-48 rounded-xl" />
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-1 flex-col overflow-auto p-6 pb-10">
      {/* 顶部工具栏 */}
      <div className="mb-4 flex items-center justify-between gap-3">
        {/* 左侧：仪表盘选择器 */}
        <div className="flex items-center gap-2">
          <LayoutDashboard className="size-5 shrink-0" />
          <DropdownMenu>
            <DropdownMenuTrigger
              render={
                <Button variant="ghost" className="h-8 gap-1 px-1 font-bold text-xl">
                  {activeDashboard?.name ?? "工作台"}
                </Button>
              }
            />
            <DropdownMenuContent align="start" className="w-56">
              {dashboardList.map((d) =>
                renamingId === d.id ? (
                  <div key={d.id} className="flex items-center gap-1 px-2 py-1">
                    <Input
                      autoFocus
                      className="h-6 text-sm"
                      value={renameValue}
                      onChange={(e) => setRenameValue(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") handleRename(d.id)
                        if (e.key === "Escape") setRenamingId(null)
                      }}
                    />
                    <Button size="sm" className="h-6 px-2" onClick={() => handleRename(d.id)}>
                      确定
                    </Button>
                  </div>
                ) : (
                  <DropdownMenuItem
                    key={d.id}
                    className="group flex items-center justify-between"
                    onSelect={() => {
                      setActiveDashboardId(d.id)
                      setLocalWidgets(null)
                      setEditing(false)
                    }}
                  >
                    <span
                      className={activeDashboard?.id === d.id ? "font-medium text-primary" : ""}
                    >
                      {d.name}
                    </span>
                    {isAdmin && (
                      <span className="ml-2 hidden gap-1 group-hover:flex">
                        <button
                          type="button"
                          className="rounded p-0.5 hover:bg-accent"
                          onClick={(e) => {
                            e.stopPropagation()
                            setRenamingId(d.id)
                            setRenameValue(d.name)
                          }}
                        >
                          <Pencil className="size-3" />
                        </button>
                        <button
                          type="button"
                          className="rounded p-0.5 text-destructive hover:bg-destructive/10"
                          onClick={(e) => {
                            e.stopPropagation()
                            handleDelete(d.id)
                          }}
                        >
                          <Trash2 className="size-3" />
                        </button>
                      </span>
                    )}
                  </DropdownMenuItem>
                )
              )}
              <DropdownMenuSeparator />
              {/* 新建仪表盘（仅管理员） */}
              {isAdmin && (
                <div className="flex items-center gap-1 px-2 py-1">
                  <Input
                    placeholder="新建仪表盘..."
                    className="h-6 text-sm"
                    value={newName}
                    onChange={(e) => setNewName(e.target.value)}
                    onKeyDown={(e) => e.key === "Enter" && handleCreate()}
                  />
                  <Button
                    size="sm"
                    className="h-6 px-2"
                    disabled={!newName.trim() || createMutation.isPending}
                    onClick={handleCreate}
                  >
                    <Plus className="size-3" />
                  </Button>
                </div>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>

        {/* 右侧：编辑工具栏（仅管理员） */}
        <div className="flex items-center gap-2">
          {isAdmin &&
            (editing ? (
              <>
                <ApplyPresetDialog onApply={handleApplyPreset} />
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
            ))}
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
          rowHeight={64}
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
                  <div className="flex items-center gap-0.5">
                    <button
                      type="button"
                      className="rounded p-0.5 hover:bg-accent"
                      onClick={() => setEditingWidget(widget)}
                    >
                      <Pencil className="h-3 w-3 text-muted-foreground" />
                    </button>
                    <button
                      type="button"
                      className="rounded p-0.5 hover:bg-destructive/10"
                      onClick={() => handleRemoveWidget(widget.id)}
                    >
                      <X className="h-3 w-3 text-destructive" />
                    </button>
                  </div>
                </div>
              )}
              {renderWidget(widget, activeDashboard?.refreshInterval)}
            </div>
          ))}
        </ResponsiveGridLayout>
      )}

      <WidgetEditDialog
        widget={editingWidget}
        open={editingWidget !== null}
        onOpenChange={(v) => {
          if (!v) setEditingWidget(null)
        }}
        onSave={handleEditWidget}
      />
    </div>
  )
}
