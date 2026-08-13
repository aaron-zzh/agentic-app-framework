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
import { useIsFetching, useQueryClient } from "@tanstack/react-query"
import { RefreshCw } from "lucide-react"
import Link from "next/link"
import { usePathname, useSearchParams } from "next/navigation"
import { useCallback, useSyncExternalStore } from "react"
import { toast } from "sonner"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import type { ViewSettings } from "@/features/entity-engine/components/list"
import {
  FilterBar,
  FilterBuilder,
  FilterFavorites,
  ListTabs,
  SearchBar,
  ViewSettingsSheet
} from "@/features/entity-engine/components/list"
import type { DataFieldDef, EntityDef } from "@/features/entity-engine/types"
import { type CrudMeta, crudKey, fromEntityDef, useCrudMeta } from "@/lib/api/rest/crud"
import { useFilterParams } from "@/lib/api/rest/entity"
import { cn } from "@/lib/utils/cn"

interface ToolbarProps {
  entity: EntityDef
  /** 由父层管理的视图设置（提升状态后由 EntityListView 传入） */
  viewSettings: ViewSettings
  onViewSettingsChange: (settings: ViewSettings) => void
}

const subscribeToHydration = () => () => undefined

function useHydrated(): boolean {
  return useSyncExternalStore(
    subscribeToHydration,
    () => true,
    () => false
  )
}

/** 判断实体是否有要显示在筛选栏中的字段。 */
function getHasFilterFields(entity: EntityDef, viewSettings?: ViewSettings): boolean {
  const filterFields = viewSettings?.filterFields ?? entity.listView.filterFields ?? []
  if (!filterFields.length) return false
  return entity.fields.some(
    (field) =>
      "name" in field && "type" in field && filterFields.includes((field as DataFieldDef).name)
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
  const resource = fromEntityDef(entity)
  const { data: crudMeta } = useCrudMeta<CrudMeta>(resource, {
    enabled: currentView === "list"
  })
  const tabs = useTabs("")
  const isHydrated = useHydrated()
  const queryClient = useQueryClient()
  const entityFetching = useIsFetching({ queryKey: crudKey(resource) })

  // hydration 前不读取客户端 Query 缓存状态，避免 Base UI Trigger 的 disabled 属性不一致。
  const isRefreshing = isHydrated && entityFetching > 0
  const isRefreshDisabled = isHydrated && isRefreshing

  // 刷新：保留当前筛选/排序/分页参数，等待该实体的活动 CRUD 查询完成后反馈结果。
  const handleRefresh = useCallback(async () => {
    if (isRefreshDisabled) return

    try {
      await queryClient.invalidateQueries(
        { queryKey: crudKey(resource), refetchType: "active" },
        { throwOnError: true }
      )
      toast.success("已刷新，当前已是最新数据")
    } catch {
      toast.error("刷新失败，请稍后重试")
    }
  }, [isRefreshDisabled, queryClient, resource])

  const filterCapabilities = crudMeta?.filterFields
  const filterableFields = new Set(filterCapabilities?.map((capability) => capability.field) ?? [])
  const equalityFilterFields = new Set(
    filterCapabilities
      ?.filter((capability) => capability.operators.some((operator) => operator.value === "eq"))
      .map((capability) => capability.field) ?? []
  )
  const configuredTabField =
    viewSettings.tabField !== undefined
      ? viewSettings.tabField || null
      : (entity.listView.tabs?.field ?? null)
  const effectiveTabField =
    configuredTabField && equalityFilterFields.has(configuredTabField) ? configuredTabField : null
  const configuredFilterFields = viewSettings.filterFields ?? entity.listView.filterFields ?? []
  const effectiveFilterFields = configuredFilterFields.filter((field) =>
    filterableFields.has(field)
  )
  const effectiveViewSettings = { ...viewSettings, filterFields: effectiveFilterFields }

  // 构造由后端能力收窄后的有效 EntityDef，仅用于筛选栏和状态 Tab 渲染。
  const effectiveEntity = {
    ...entity,
    listView: {
      ...entity.listView,
      filterFields: effectiveFilterFields,
      tabs: effectiveTabField ? { ...entity.listView.tabs, field: effectiveTabField } : undefined
    }
  }

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
      if (!effectiveTabField) return
      const without = filters.filter((f) => f.field !== effectiveTabField)
      if (value) {
        setFilters([...without, { field: effectiveTabField, operator: "eq", values: [value] }])
      } else {
        setFilters(without)
      }
    },
    [effectiveTabField, filters, setFilters, tabs]
  )

  const hasTabs = !!effectiveTabField

  return (
    <div className="relative z-10 border-b">
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
          <ViewSettingsSheet
            entity={entity}
            filterCapabilities={filterCapabilities}
            onSettingsChange={setViewSettings}
          />
        </div>
      )}

      {/* 行1：Tab + 筛选栏（无 Tab 时含右侧视图切换） */}
      <div className="flex items-start justify-between px-4 py-1">
        <div className="flex flex-1 flex-col gap-1">
          <ListTabs entity={effectiveEntity} activeValue={tabs.value} onChange={handleTabChange} />
          {(getHasFilterFields(effectiveEntity, effectiveViewSettings) ||
            Boolean(filterCapabilities?.length)) && (
            <FilterBar
              entity={effectiveEntity}
              filters={filters}
              onChange={setFilters}
              viewSettings={effectiveViewSettings}
              trailingAction={
                crudMeta?.filterFields.length ? (
                  <FilterBuilder
                    entity={entity}
                    filters={filters}
                    onChange={setFilters}
                    capabilities={crudMeta.filterFields}
                  />
                ) : null
              }
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
            <ViewSettingsSheet
              entity={entity}
              filterCapabilities={filterCapabilities}
              onSettingsChange={setViewSettings}
            />
          </div>
        )}
      </div>

      {/* 行2：搜索框 + 收藏 + 刷新 */}
      <div className="flex items-center gap-2 px-4 pt-2 pb-1.5">
        <SearchBar entity={entity} filters={filters} onChange={setFilters} />
        <FilterFavorites entitySlug={entity.slug} currentFilters={filters} onApply={setFilters} />
        <Tooltip>
          <TooltipTrigger
            render={
              <button
                type="button"
                onClick={handleRefresh}
                disabled={isRefreshDisabled}
                aria-busy={isRefreshing}
                aria-label={isRefreshing ? "正在刷新" : "刷新"}
                className="flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50"
              />
            }
          >
            <RefreshCw className={cn("size-4", isRefreshing && "animate-spin")} />
          </TooltipTrigger>
          <TooltipContent>{isRefreshing ? "正在刷新" : "刷新"}</TooltipContent>
        </Tooltip>
      </div>
    </div>
  )
}
