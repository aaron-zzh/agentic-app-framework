/**
 * Studio 首页积分面板（右栏，可折叠）：环形图中心显示可用积分，下方近 30 天消耗与近 14 天消耗趋势。
 *
 * 分类名称只在悬停环形分段时显示。可用积分与积分中心口径一致（各分组 remain 之和）；
 * 分类与趋势来自 useCreditSpendCategories / useCreditSpendTrend，与积分中心共用缓存。
 * 三份数据各自降级：某一份失败只把对应数字显示为"—"或只影响趋势图。
 * 折叠后标题行显示"可用积分 · 近 30 天消耗"。
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronRight } from "lucide-react"
import Link from "next/link"
import { useMemo } from "react"
import { Skeleton } from "@/components/ui/skeleton"
import { CreditSpendTrendChart } from "@/features/billing/components/CreditSpendTrendChart"
import {
  CATEGORY_COLORS,
  useBillingCategoryMeta
} from "@/features/dashboard/widgets/ExpensesCategoryWidget"
import { BaseChart, type EChartsOption } from "@/features/stats/charts/BaseChart"
import {
  type CreditSpendCategoryRow,
  useCreditGroups,
  useCreditSpendCategories,
  useCreditSpendTrend
} from "@/lib/api/rest/billing"
import { HomeWidgetCard } from "./HomeWidgetCard"
import { useHomeWidgetCollapse } from "./use-home-widget-collapse"

const CATEGORY_DAYS = 30
const TREND_DAYS = 14

/** 没有消耗时的底环颜色 */
const EMPTY_RING_COLOR = "rgba(148, 163, 184, 0.18)"

function buildRingOption(
  rows: CreditSpendCategoryRow[],
  labelOf: (bizType: string) => string
): EChartsOption {
  const hasSpend = rows.length > 0
  return {
    backgroundColor: "transparent",
    tooltip: hasSpend
      ? { trigger: "item", confine: true, formatter: "{b}<br/>{c} 积分（{d}%）" }
      : { show: false },
    series: [
      {
        type: "pie",
        radius: ["72%", "86%"],
        center: ["50%", "50%"],
        padAngle: hasSpend && rows.length > 1 ? 2 : 0,
        itemStyle: { borderRadius: 4 },
        label: { show: false },
        emphasis: { scale: true, scaleSize: 4 },
        silent: !hasSpend,
        data: hasSpend
          ? rows.map((row, index) => ({
              name: labelOf(row.biz_type),
              value: row.total,
              itemStyle: { color: CATEGORY_COLORS[index % CATEGORY_COLORS.length] }
            }))
          : [{ name: "", value: 1, itemStyle: { color: EMPTY_RING_COLOR } }]
      }
    ]
  }
}

/** 数字格式化，undefined 显示"—" */
function formatAmount(value: number | undefined): string {
  return value === undefined ? "—" : value.toLocaleString()
}

export function HomeCreditsPanel() {
  const groups = useCreditGroups()
  const categories = useCreditSpendCategories(CATEGORY_DAYS)
  const trend = useCreditSpendTrend(TREND_DAYS)
  const resolveMeta = useBillingCategoryMeta()
  const { collapsed, toggle } = useHomeWidgetCollapse("credits")

  const option = useMemo(
    () => buildRingOption(categories.data ?? [], (bizType) => resolveMeta(bizType).label),
    [categories.data, resolveMeta]
  )
  const balance = groups.data?.reduce((sum, group) => sum + group.remain, 0)
  const spent = categories.data?.reduce((sum, row) => sum + row.total, 0)
  const trendPoints = trend.data ?? []
  const hasTrendSpend = trendPoints.some((point) => point.amount > 0)

  return (
    <HomeWidgetCard
      title="积分"
      collapsible
      collapsed={collapsed}
      onToggle={toggle}
      summary={
        <span className="tabular-nums">
          可用 <span className="text-amber-300">{formatAmount(balance)}</span> · 近 {CATEGORY_DAYS}{" "}
          天消耗 {formatAmount(spent)}
        </span>
      }
      action={
        <Link
          href="/studio/me/credits"
          className="flex items-center gap-0.5 text-muted-foreground text-xs hover:text-foreground"
        >
          积分中心
          <ChevronRight className="size-3.5" />
        </Link>
      }
    >
      {groups.isLoading || categories.isLoading ? (
        <div className="flex flex-col items-center gap-3 py-2">
          <Skeleton className="size-32 rounded-full" />
          <Skeleton className="h-4 w-32" />
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          <div className="relative h-36">
            <BaseChart option={option} className="absolute inset-0" />
            {/* 环心：可用积分，点击进入积分中心；只占环心区域，不挡住环形分段的悬停 */}
            <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
              <Link
                href="/studio/me/credits"
                className="pointer-events-auto flex flex-col items-center gap-1 rounded-full px-3 py-2 outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                <span className="text-[11px] text-muted-foreground tracking-wide">可用积分</span>
                <span className="bg-gradient-to-b from-amber-200 to-amber-400 bg-clip-text font-semibold text-2xl text-transparent tabular-nums leading-none">
                  {formatAmount(balance)}
                </span>
              </Link>
            </div>
          </div>
          <p className="text-center text-muted-foreground text-xs">
            近 {CATEGORY_DAYS} 天消耗
            <span className="mx-1 font-semibold text-foreground text-sm tabular-nums">
              {formatAmount(spent)}
            </span>
            积分
          </p>
        </div>
      )}
      <div className="mt-3 flex flex-col gap-1">
        <p className="text-[11px] text-muted-foreground tracking-wide">
          近 {TREND_DAYS} 天消耗趋势
        </p>
        {trend.isLoading ? (
          <Skeleton className="h-28 w-full rounded-lg" />
        ) : trend.isError ? (
          <p className="flex h-28 items-center justify-center text-muted-foreground text-xs">
            趋势加载失败
          </p>
        ) : hasTrendSpend ? (
          <CreditSpendTrendChart points={trendPoints} className="h-28 w-full" />
        ) : (
          <p className="flex h-28 items-center justify-center text-muted-foreground text-xs">
            近 {TREND_DAYS} 天没有积分消耗
          </p>
        )}
      </div>
    </HomeWidgetCard>
  )
}
