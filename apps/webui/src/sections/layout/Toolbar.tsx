/**
 * Toolbar——视图工具栏（参考禅道/Ones 搜索视图设计）
 * @author AaronZZH & Kiro
 *
 * 布局：
 * 行1：[更多操作]          视图切换 | 分组 | 过滤 | 共N个 | 设置
 * 行2：[常驻筛选器...] 搜索框(⌘K) [+ 添加条件]
 * 行3（条件存在时）：[高级查询] [另存为视图] [清除条件]
 */

"use client"

import { useTabs } from "@aaf/hooks"
import Link from "next/link"
import { usePathname, useSearchParams } from "next/navigation"
import { useCallback } from "react"
import { FilterFavorites } from "@/features/entity-engine/components/FilterFavorites"
import { ListTabs } from "@/features/entity-engine/components/ListTabs"
import { SearchBar } from "@/features/entity-engine/components/SearchBar"
import type { EntityDef } from "@/features/entity-engine/types"
import { useFilterParams } from "@/lib/queries/use-filter-params"
import { cn } from "@/lib/utils/cn"

interface ToolbarProps {
  entity: EntityDef
}

/** 视图工具栏 */
export function Toolbar({ entity }: ToolbarProps) {
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const currentView = searchParams.get("view") ?? "list"
  const [filters, setFilters] = useFilterParams()
  const tabs = useTabs("")

  // 动态视图列表（透视视图按需显示）
  const availableViews = [
    { key: "list", label: "列表", icon: "☰" },
    { key: "kanban", label: "看板", icon: "▦" },
    ...(entity.pivotView?.enabled ? [{ key: "pivot", label: "透视", icon: "⊞" }] : [])
  ]

  // Tab 切换时添加/替换对应字段筛选
  const handleTabChange = useCallback(
    (value: string) => {
      tabs.setValue(value)
      const tabField = entity.listView.tabs?.field
      if (!tabField) return
      const without = filters.filter((f) => f.field !== tabField)
      if (value) {
        setFilters([...without, { field: tabField, operator: "eq", value }])
      } else {
        setFilters(without)
      }
    },
    [entity, filters, setFilters, tabs]
  )

  return (
    <div className="border-b">
      {/* 行1：操作按钮 + 视图控制 */}
      <div className="flex items-center justify-between px-4 py-2">
        <div className="flex items-center gap-2">
          {/* TODO: #02915 Server Actions 批量操作菜单 */}
          <button
            type="button"
            className="h-8 rounded-md border px-3 text-muted-foreground text-sm"
            disabled
          >
            更多操作 ▾
          </button>
        </div>

        <div className="flex items-center gap-3 text-muted-foreground text-sm">
          <div className="flex items-center gap-0.5 rounded-md border p-0.5">
            {availableViews.map((v) => (
              <Link
                key={v.key}
                href={`${pathname}?view=${v.key}`}
                title={v.label}
                className={cn(
                  "rounded px-2 py-1 text-xs transition-colors",
                  currentView === v.key
                    ? "bg-accent font-medium text-accent-foreground"
                    : "text-muted-foreground hover:text-foreground"
                )}
              >
                {v.icon}
              </Link>
            ))}
          </div>
          <span className="text-xs">分组: 无</span>
          <span>共 {searchParams.get("total") ?? "—"} 个</span>
          <button type="button" className="text-xs hover:text-foreground" disabled>
            ⚙ 设置
          </button>
        </div>
      </div>

      {/* 行2：状态 Tab（配置了 tabs 时显示） */}
      <ListTabs entity={entity} activeValue={tabs.value} onChange={handleTabChange} />

      {/* 行3：统一搜索栏 */}
      <div className="flex items-center gap-2 px-4 py-2">
        <SearchBar entity={entity} filters={filters} onChange={setFilters} />
        <FilterFavorites entitySlug={entity.slug} currentFilters={filters} onApply={setFilters} />
      </div>
    </div>
  )
}
