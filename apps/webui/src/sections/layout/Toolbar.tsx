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
import { RefreshCw } from "lucide-react"
import Link from "next/link"
import { usePathname, useSearchParams } from "next/navigation"
import { useCallback } from "react"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import type { ViewSettings } from "@/features/entity-engine/components/list"
import {
  FilterFavorites,
  ListTabs,
  QuickFilterBar,
  SearchBar,
  ViewSettingsSheet
} from "@/features/entity-engine/components/list"
import type { DataFieldDef, EntityDef } from "@/lib/types/entity"
import { useFilterParams } from "@/lib/queries/use-filter-params"
import { cn } from "@/lib/utils/cn"

interface ToolbarProps {
  entity: EntityDef
  /** 由父层管理的视图设置（提升状态后由 EntityListView 传入） */
  viewSettings: ViewSettings
  onViewSettingsChange: (settings: ViewSettings) => void
}

/** 判断实体是否有快速筛选配置 */
function getHasQuickFilters(entity: EntityDef, viewSettings?: ViewSettings): boolean {
  if (viewSettings?.quickFilterFields?.length) return true
  if (entity.listView.quickFilters?.length) return true
  const filterableFields = entity.listView.filterableFields ?? []
  if (!filterableFields.length) return false
  return entity.fields.some(
    (f) => "name" in f && "type" in f && filterableFields.includes((f as DataFieldDef).name)
  )
}

/** 视图工具栏 */
export function Toolbar({
  entity,
  viewSettings,
  onViewSettingsChange: setViewSettings
}: ToolbarProps) {
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const currentView = searchParams.get("view") ?? "list"
  const [filters, setFilters] = useFilterParams()
  const tabs = useTabs("")

  // 有效的 Tab 字段：
  //   viewSettings.tabField === undefined → 未配置，回退到 EntityDef
  //   viewSettings.tabField === ""        → 用户明确关闭 Tab
  //   viewSettings.tabField === "xxx"     → 用户选择了字段
  const effectiveTabField =
    viewSettings.tabField !== undefined
      ? viewSettings.tabField || null
      : (entity.listView.tabs?.field ?? null)

  // 构造有效的 entity（覆盖 tabs 配置）
  const effectiveEntity = effectiveTabField
    ? {
        ...entity,
        listView: {
          ...entity.listView,
          tabs: { ...entity.listView.tabs, field: effectiveTabField }
        }
      }
    : { ...entity, listView: { ...entity.listView, tabs: undefined } }

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

  const hasTabs = !!effectiveTabField

  return (
    <div className="relative border-b">
      {/* 视图切换 + 设置——有 Tab 时固定到右上角，无 Tab 时在行1右侧 */}
      {hasTabs && (
        <div className="absolute top-1.5 right-3 z-10 flex items-center gap-1">
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
          <ViewSettingsSheet entity={entity} onSettingsChange={setViewSettings} />
        </div>
      )}

      {/* 行1：Tab + 快速筛选（无 Tab 时含右侧视图切换） */}
      <div className="flex items-start justify-between px-4 py-1">
        <div className="flex flex-1 flex-col gap-1">
          <ListTabs entity={effectiveEntity} activeValue={tabs.value} onChange={handleTabChange} />
          {getHasQuickFilters(entity, viewSettings) && (
            <QuickFilterBar
              entity={effectiveEntity}
              filters={filters}
              onChange={setFilters}
              viewSettings={viewSettings}
            />
          )}
        </div>

        {/* 无 Tab 时右侧显示视图切换 + 设置 */}
        {!hasTabs && (
          <div className="flex shrink-0 items-center gap-1 pt-0.5">
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
            <ViewSettingsSheet entity={entity} onSettingsChange={setViewSettings} />
          </div>
        )}
      </div>

      {/* 行2：搜索框（含已选条件 chips）+ 收藏 + 刷新 */}
      <div className="flex items-center gap-2 px-4 pt-2 pb-1.5">
        <SearchBar entity={entity} filters={filters} onChange={setFilters} />
        <FilterFavorites entitySlug={entity.slug} currentFilters={filters} onApply={setFilters} />
        <Tooltip>
          <TooltipTrigger
            render={
              <button
                type="button"
                className="flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground"
              />
            }
          >
            <RefreshCw className="size-4" />
          </TooltipTrigger>
          <TooltipContent>刷新</TooltipContent>
        </Tooltip>
      </div>
    </div>
  )
}
