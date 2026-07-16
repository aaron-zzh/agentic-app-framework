/**
 * 待办列表 footer——剩余计数、all/active/completed 过滤、清除已完成
 * @author AaronZZH & Kiro
 */

"use client"

import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils/cn"

export type TodoFilter = "all" | "active" | "completed"

const FILTER_LABELS: Record<TodoFilter, string> = {
  all: "全部",
  active: "未完成",
  completed: "已完成"
}

export function TodoFooter({
  activeCount,
  completedCount,
  filter,
  onFilterChange,
  onClearCompleted
}: {
  activeCount: number
  completedCount: number
  filter: TodoFilter
  onFilterChange: (filter: TodoFilter) => void
  onClearCompleted: () => void
}) {
  return (
    <div className="flex items-center justify-between px-4 py-3 text-muted-foreground text-sm">
      <span>{activeCount} 项未完成</span>

      <div className="flex items-center gap-1">
        {(Object.keys(FILTER_LABELS) as TodoFilter[]).map((key) => (
          <Button
            key={key}
            type="button"
            variant={filter === key ? "outline" : "ghost"}
            size="sm"
            className={cn("h-7 px-2.5 text-xs", filter === key && "border-primary text-primary")}
            onClick={() => onFilterChange(key)}
          >
            {FILTER_LABELS[key]}
          </Button>
        ))}
      </div>

      <Button
        type="button"
        variant="ghost"
        size="sm"
        className="h-7 px-2 text-xs"
        disabled={completedCount === 0}
        onClick={onClearCompleted}
      >
        清除已完成
      </Button>
    </div>
  )
}
