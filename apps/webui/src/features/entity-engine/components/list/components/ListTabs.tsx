/**
 * ListTabs——状态 Tab 快捷筛选（表格上方）
 * @author AaronZZH & Kiro
 */

"use client"

import type { DataFieldDef, EntityDef } from "@/features/entity-engine/types"
import { cn } from "@/lib/utils/cn"

interface ListTabsProps {
  entity: EntityDef
  activeValue: string
  onChange: (value: string) => void
  counts?: Record<string, number>
}

/** 状态 Tab 快捷筛选 */
export function ListTabs({ entity, activeValue, onChange, counts }: ListTabsProps) {
  const tabsConfig = entity.listView.tabs
  if (!tabsConfig) return null

  const field = entity.fields.find(
    (f) => "name" in f && (f as DataFieldDef).name === tabsConfig.field
  ) as DataFieldDef | undefined

  // 生成 Tab 项：优先用配置，否则从字段 options 自动生成
  const items = tabsConfig.items ?? [
    { value: "", label: "全部" },
    ...(field?.type === "select" && "options" in field
      ? ((field as { options?: { value: string; label: string }[] }).options ?? [])
      : [])
  ]

  return (
    <div className="flex items-center gap-0 border-b px-4">
      {items.map((item) => (
        <button
          key={item.value}
          type="button"
          className={cn(
            "relative flex items-center gap-1.5 px-4 py-2 text-sm transition-colors",
            activeValue === item.value
              ? "font-medium text-foreground after:absolute after:bottom-0 after:left-0 after:h-0.5 after:w-full after:bg-primary"
              : "text-muted-foreground hover:text-foreground"
          )}
          onClick={() => onChange(item.value)}
        >
          {item.label}
          {tabsConfig.showCount && counts?.[item.value] !== undefined && (
            <span
              className={cn(
                "rounded-full px-1.5 py-0.5 text-xs",
                activeValue === item.value ? "bg-primary/10 text-primary" : "bg-muted"
              )}
            >
              {counts[item.value]}
            </span>
          )}
        </button>
      ))}
    </div>
  )
}
