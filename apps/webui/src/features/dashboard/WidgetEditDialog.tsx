/**
 * WidgetEditDialog——Widget 编辑弹窗
 * 根据 Widget 类型显示不同配置项，编辑确认后通过 onSave 回调更新
 * @author AaronZZH & Kiro
 */

"use client"

import { Minus, Plus } from "lucide-react"
import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import type {
  CounterWidgetConfig,
  DashboardWidgetVO,
  EChartsWidgetConfig,
  ProgressWidgetConfig,
  ShortcutWidgetConfig
} from "@/lib/api/rest/dashboard/dashboard"
import { useMetrics } from "@/lib/queries/use-dashboard"

/** 支持的颜色选项 */
const COLOR_OPTIONS: { value: string; label: string; bg: string }[] = [
  { value: "blue", label: "蓝", bg: "bg-blue-500" },
  { value: "green", label: "绿", bg: "bg-green-500" },
  { value: "yellow", label: "黄", bg: "bg-yellow-500" },
  { value: "purple", label: "紫", bg: "bg-purple-500" },
  { value: "red", label: "红", bg: "bg-red-500" },
  { value: "orange", label: "橙", bg: "bg-orange-500" }
]

interface WidgetEditDialogProps {
  widget: DashboardWidgetVO | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSave: (updated: DashboardWidgetVO) => void
}

export function WidgetEditDialog({ widget, open, onOpenChange, onSave }: WidgetEditDialogProps) {
  const { data: metrics = [] } = useMetrics()

  // 通用字段
  const [title, setTitle] = useState("")
  // counter 专属
  const [counterMetric, setCounterMetric] = useState("")
  const [counterColor, setCounterColor] = useState("")
  const [counterIcon, setCounterIcon] = useState("")
  // echarts 专属
  const [statsType, setStatsType] = useState<EChartsWidgetConfig["statsType"]>("trend")
  const [echartsMetric, setEchartsMetric] = useState("")
  const [period, setPeriod] = useState<EChartsWidgetConfig["period"]>("day")
  const [chartType, setChartType] = useState<EChartsWidgetConfig["chartType"]>("line")
  // progress 专属
  const [progressLabel, setProgressLabel] = useState("")
  const [progressCurrent, setProgressCurrent] = useState("")
  const [progressTarget, setProgressTarget] = useState("")
  // shortcut 专属
  const [shortcuts, setShortcuts] = useState<{ label: string; path: string }[]>([])

  // 打开弹窗时初始化表单值
  useEffect(() => {
    if (!widget || !open) return
    setTitle(widget.title)

    switch (widget.config.type) {
      case "counter": {
        const c = widget.config as CounterWidgetConfig
        setCounterMetric(c.entity ?? "")
        setCounterColor(c.color ?? "")
        setCounterIcon(c.icon ?? "")
        break
      }
      case "echarts": {
        const c = widget.config as EChartsWidgetConfig
        setStatsType(c.statsType ?? "trend")
        setEchartsMetric(c.metric ?? "")
        setPeriod(c.period ?? "day")
        setChartType(c.chartType ?? "line")
        break
      }
      case "progress": {
        const c = widget.config as ProgressWidgetConfig
        setProgressLabel(c.label ?? "")
        setProgressCurrent(String(c.current ?? ""))
        setProgressTarget(String(c.target ?? ""))
        break
      }
      case "shortcut": {
        const c = widget.config as ShortcutWidgetConfig
        setShortcuts(c.items.map((item) => ({ label: item.label, path: item.href })))
        break
      }
    }
  }, [widget, open])

  function handleSave() {
    if (!widget) return

    let updatedConfig = widget.config

    switch (widget.config.type) {
      case "counter": {
        const metric = metrics.find((m) => m.key === counterMetric)
        updatedConfig = {
          ...widget.config,
          entity: counterMetric,
          aggregation: metric?.aggregation ?? (widget.config as CounterWidgetConfig).aggregation,
          color: counterColor || undefined,
          icon: counterIcon || undefined
        } as CounterWidgetConfig
        break
      }
      case "echarts":
        updatedConfig = {
          ...widget.config,
          statsType,
          metric: echartsMetric || undefined,
          period,
          chartType
        } as EChartsWidgetConfig
        break
      case "progress":
        updatedConfig = {
          ...widget.config,
          label: progressLabel,
          current: Number(progressCurrent) || 0,
          target: Number(progressTarget) || 0
        } as ProgressWidgetConfig
        break
      case "shortcut":
        updatedConfig = {
          ...widget.config,
          items: shortcuts.map((s) => ({ label: s.label, icon: "", href: s.path }))
        } as ShortcutWidgetConfig
        break
    }

    onSave({ ...widget, title, config: updatedConfig })
    onOpenChange(false)
  }

  if (!widget) return null

  // 按 group 分组 metrics
  const grouped = metrics.reduce<Record<string, typeof metrics>>((acc, m) => {
    if (!acc[m.group]) acc[m.group] = []
    acc[m.group].push(m)
    return acc
  }, {})

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>编辑 Widget</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {/* 所有类型：标题 */}
          <div className="space-y-1.5">
            <Label htmlFor="widget-title">标题</Label>
            <Input
              id="widget-title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Widget 标题"
            />
          </div>

          {/* counter 类型配置 */}
          {widget.config.type === "counter" && (
            <>
              <div className="space-y-1.5">
                <Label>指标</Label>
                <Select value={counterMetric} onValueChange={(v) => setCounterMetric(v ?? "")}>
                  <SelectTrigger>
                    <SelectValue placeholder="选择统计指标..." />
                  </SelectTrigger>
                  <SelectContent>
                    {Object.entries(grouped).map(([group, items]) => (
                      <div key={group}>
                        <div className="px-2 py-1 font-medium text-muted-foreground text-xs">
                          {group}
                        </div>
                        {items.map((m) => (
                          <SelectItem key={m.key} value={m.key}>
                            {m.label}
                          </SelectItem>
                        ))}
                      </div>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label>颜色</Label>
                <div className="flex gap-2">
                  {COLOR_OPTIONS.map((c) => (
                    <button
                      key={c.value}
                      type="button"
                      title={c.label}
                      className={`size-6 rounded-full ${c.bg} ring-offset-background transition-all ${
                        counterColor === c.value ? "ring-2 ring-primary ring-offset-2" : ""
                      }`}
                      onClick={() => setCounterColor(counterColor === c.value ? "" : c.value)}
                    />
                  ))}
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="counter-icon">图标</Label>
                <Input
                  id="counter-icon"
                  value={counterIcon}
                  onChange={(e) => setCounterIcon(e.target.value)}
                  placeholder="图标名称（如 users）"
                />
              </div>
            </>
          )}

          {/* echarts 类型配置 */}
          {widget.config.type === "echarts" && (
            <>
              <div className="space-y-1.5">
                <Label>图表类型</Label>
                <Select
                  value={statsType}
                  onValueChange={(v) => setStatsType(v as EChartsWidgetConfig["statsType"])}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="trend">趋势图</SelectItem>
                    <SelectItem value="funnel">漏斗图</SelectItem>
                    <SelectItem value="retention">留存图</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="echarts-metric">指标</Label>
                <Input
                  id="echarts-metric"
                  value={echartsMetric}
                  onChange={(e) => setEchartsMetric(e.target.value)}
                  placeholder="dau / mau / revenue / aigc_task / credit_cost"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <Label>时间粒度</Label>
                  <Select
                    value={period}
                    onValueChange={(v) => setPeriod(v as EChartsWidgetConfig["period"])}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="hour">小时</SelectItem>
                      <SelectItem value="day">天</SelectItem>
                      <SelectItem value="week">周</SelectItem>
                      <SelectItem value="month">月</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-1.5">
                  <Label>渲染类型</Label>
                  <Select
                    value={chartType}
                    onValueChange={(v) => setChartType(v as EChartsWidgetConfig["chartType"])}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="line">折线图</SelectItem>
                      <SelectItem value="bar">柱状图</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </>
          )}

          {/* progress 类型配置 */}
          {widget.config.type === "progress" && (
            <>
              <div className="space-y-1.5">
                <Label htmlFor="progress-label">标签</Label>
                <Input
                  id="progress-label"
                  value={progressLabel}
                  onChange={(e) => setProgressLabel(e.target.value)}
                  placeholder="目标描述"
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <Label htmlFor="progress-current">当前值</Label>
                  <Input
                    id="progress-current"
                    type="number"
                    value={progressCurrent}
                    onChange={(e) => setProgressCurrent(e.target.value)}
                    placeholder="0"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="progress-target">目标值</Label>
                  <Input
                    id="progress-target"
                    type="number"
                    value={progressTarget}
                    onChange={(e) => setProgressTarget(e.target.value)}
                    placeholder="100"
                  />
                </div>
              </div>
            </>
          )}

          {/* shortcut 类型配置 */}
          {widget.config.type === "shortcut" && (
            <div className="space-y-2">
              <Label>快捷入口</Label>
              {shortcuts.map((item, idx) => (
                <div key={idx} className="flex gap-2">
                  <Input
                    value={item.label}
                    onChange={(e) => {
                      const updated = [...shortcuts]
                      updated[idx] = { ...updated[idx], label: e.target.value }
                      setShortcuts(updated)
                    }}
                    placeholder="名称"
                    className="flex-1"
                  />
                  <Input
                    value={item.path}
                    onChange={(e) => {
                      const updated = [...shortcuts]
                      updated[idx] = { ...updated[idx], path: e.target.value }
                      setShortcuts(updated)
                    }}
                    placeholder="/workspace/..."
                    className="flex-1"
                  />
                  <button
                    type="button"
                    className="rounded p-1 hover:bg-destructive/10"
                    onClick={() => setShortcuts(shortcuts.filter((_, i) => i !== idx))}
                  >
                    <Minus className="size-4 text-destructive" />
                  </button>
                </div>
              ))}
              <button
                type="button"
                className="flex items-center gap-1 text-muted-foreground text-sm hover:text-foreground"
                onClick={() => setShortcuts([...shortcuts, { label: "", path: "" }])}
              >
                <Plus className="size-3" />
                添加入口
              </button>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={handleSave}>保存</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
