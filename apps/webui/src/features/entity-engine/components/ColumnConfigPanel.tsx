/**
 * ColumnConfigPanel——列配置面板（勾选显示/隐藏）
 * @author AaronZZH & Kiro
 */

"use client"

import { Settings2 } from "lucide-react"

import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import type { ColumnPreference } from "@/lib/hooks/use-column-preferences"

interface ColumnConfigPanelProps {
  preferences: ColumnPreference[]
  onToggle: (name: string) => void
  onReset: () => void
  /** 字段名 → 显示标签映射 */
  labels?: Record<string, string>
}

/** 列配置面板——Popover 触发，勾选控制列显示/隐藏 */
export function ColumnConfigPanel({
  preferences,
  onToggle,
  onReset,
  labels
}: ColumnConfigPanelProps) {
  return (
    <Popover>
      <PopoverTrigger
        className="inline-flex h-7 w-7 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground"
        title="列配置"
      >
        <Settings2 className="h-4 w-4" />
      </PopoverTrigger>
      <PopoverContent align="end" className="w-48 p-2">
        <div className="mb-1 flex items-center justify-between px-1">
          <span className="font-medium text-muted-foreground text-xs">显示列</span>
          <button
            type="button"
            onClick={onReset}
            className="text-muted-foreground text-xs hover:text-foreground"
          >
            重置
          </button>
        </div>
        <div className="space-y-0.5">
          {[...preferences]
            .sort((a, b) => a.order - b.order)
            .map((pref) => (
              <label
                key={pref.name}
                className="flex cursor-pointer items-center gap-2 rounded px-1 py-1 hover:bg-muted"
              >
                <input
                  type="checkbox"
                  checked={pref.visible}
                  onChange={() => onToggle(pref.name)}
                  className="h-3.5 w-3.5 rounded"
                />
                <span className="text-sm">{labels?.[pref.name] ?? pref.name}</span>
              </label>
            ))}
        </div>
      </PopoverContent>
    </Popover>
  )
}
