/**
 * AddWidgetDialog——添加 Widget 弹窗
 * @author AaronZZH & Kiro
 */

"use client"

import { BarChart3, Hash, List, Target, Zap } from "lucide-react"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog"
import type { WidgetType } from "@/lib/api/dashboard"

/** 可添加的 Widget 类型列表 */
const WIDGET_OPTIONS: { type: WidgetType; label: string; description: string; icon: React.ReactNode }[] = [
  { type: "counter", label: "数字统计", description: "展示聚合计数或求和", icon: <Hash className="h-5 w-5" /> },
  { type: "chart", label: "图表", description: "折线图/柱状图/饼图", icon: <BarChart3 className="h-5 w-5" /> },
  { type: "list", label: "列表", description: "展示最近记录", icon: <List className="h-5 w-5" /> },
  { type: "progress", label: "进度", description: "目标完成进度", icon: <Target className="h-5 w-5" /> },
  { type: "shortcut", label: "快捷入口", description: "常用操作快捷方式", icon: <Zap className="h-5 w-5" /> }
]

interface AddWidgetDialogProps {
  onAdd: (type: WidgetType) => void
}

export function AddWidgetDialog({ onAdd }: AddWidgetDialogProps) {
  return (
    <Dialog>
      <DialogTrigger render={<Button variant="outline" size="sm">+ 添加 Widget</Button>} />
      <DialogContent>
        <DialogHeader>
          <DialogTitle>添加 Widget</DialogTitle>
          <DialogDescription>选择要添加到仪表盘的组件类型</DialogDescription>
        </DialogHeader>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {WIDGET_OPTIONS.map((opt) => (
            <button
              key={opt.type}
              type="button"
              className="flex items-start gap-3 rounded-lg border p-3 text-left transition-colors hover:bg-accent"
              onClick={() => onAdd(opt.type)}
            >
              <span className="mt-0.5 text-muted-foreground">{opt.icon}</span>
              <div>
                <p className="font-medium text-sm">{opt.label}</p>
                <p className="text-muted-foreground text-xs">{opt.description}</p>
              </div>
            </button>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  )
}
