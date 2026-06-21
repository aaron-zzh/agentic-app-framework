/**
 * AddWidgetDialog——添加 Widget 弹窗
 * @author AaronZZH & Kiro
 */

"use client"

import { Activity, BarChart3, Hash, List, Target, Zap } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog"
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
  WidgetType
} from "@/lib/api/rest/dashboard/dashboard"
import { useMetrics } from "@/lib/queries/use-dashboard"

/** 可添加的 Widget 类型列表 */
const WIDGET_OPTIONS: {
  type: WidgetType
  label: string
  description: string
  icon: React.ReactNode
}[] = [
  {
    type: "counter",
    label: "数字统计",
    description: "展示聚合计数或求和",
    icon: <Hash className="h-5 w-5" />
  },
  {
    type: "chart",
    label: "图表",
    description: "折线图/柱状图/饼图",
    icon: <BarChart3 className="h-5 w-5" />
  },
  {
    type: "echarts",
    label: "ECharts 统计",
    description: "趋势/漏斗/留存（运营统计）",
    icon: <Activity className="h-5 w-5" />
  },
  { type: "list", label: "列表", description: "展示最近记录", icon: <List className="h-5 w-5" /> },
  {
    type: "progress",
    label: "进度",
    description: "目标完成进度",
    icon: <Target className="h-5 w-5" />
  },
  {
    type: "shortcut",
    label: "快捷入口",
    description: "常用操作快捷方式",
    icon: <Zap className="h-5 w-5" />
  }
]

interface AddWidgetDialogProps {
  onAdd: (widget: Partial<DashboardWidgetVO>) => void
}

export function AddWidgetDialog({ onAdd }: AddWidgetDialogProps) {
  const [open, setOpen] = useState(false)
  const [step, setStep] = useState<"type" | "config">("type")
  const [_selectedType, setSelectedType] = useState<WidgetType | null>(null)
  const [selectedMetric, setSelectedMetric] = useState<string>("")
  const { data: metrics = [] } = useMetrics()

  function handleSelectType(type: WidgetType) {
    if (type === "counter") {
      setSelectedType(type)
      setStep("config")
    } else {
      onAdd({ type })
      setOpen(false)
      reset()
    }
  }

  function handleConfirm() {
    if (!selectedMetric) return
    const metric = metrics.find((m) => m.key === selectedMetric)
    const config: CounterWidgetConfig = {
      type: "counter",
      entity: selectedMetric,
      aggregation: metric?.aggregation ?? "count"
    }
    onAdd({ type: "counter", title: metric?.label ?? "统计", config })
    setOpen(false)
    reset()
  }

  function reset() {
    setStep("type")
    setSelectedType(null)
    setSelectedMetric("")
  }

  // 按 group 分组
  const grouped = metrics.reduce<Record<string, typeof metrics>>((acc, m) => {
    if (!acc[m.group]) acc[m.group] = []
    acc[m.group].push(m)
    return acc
  }, {})

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        setOpen(v)
        if (!v) reset()
      }}
    >
      <DialogTrigger
        render={
          <Button variant="outline" size="sm">
            + 添加 Widget
          </Button>
        }
      />
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{step === "type" ? "选择 Widget 类型" : "配置数字统计"}</DialogTitle>
          <DialogDescription>
            {step === "type" ? "选择要添加到仪表盘的组件类型" : "选择要统计的指标"}
          </DialogDescription>
        </DialogHeader>

        {step === "type" ? (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {WIDGET_OPTIONS.map((opt) => (
              <button
                key={opt.type}
                type="button"
                className="flex items-start gap-3 rounded-lg border p-3 text-left transition-colors hover:bg-accent"
                onClick={() => handleSelectType(opt.type)}
              >
                <span className="mt-0.5 text-muted-foreground">{opt.icon}</span>
                <div>
                  <p className="font-medium text-sm">{opt.label}</p>
                  <p className="text-muted-foreground text-xs">{opt.description}</p>
                </div>
              </button>
            ))}
          </div>
        ) : (
          <div className="space-y-4">
            <Select value={selectedMetric} onValueChange={(v) => setSelectedMetric(v ?? "")}>
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
            <DialogFooter>
              <Button variant="outline" onClick={() => setStep("type")}>
                返回
              </Button>
              <Button disabled={!selectedMetric} onClick={handleConfirm}>
                添加
              </Button>
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
