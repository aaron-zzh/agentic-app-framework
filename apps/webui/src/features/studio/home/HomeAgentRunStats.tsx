/**
 * Studio 首页智能体运行统计（右栏，可折叠）：近 7 天成功与失败次数堆叠柱状图（示例数据）。
 *
 * 折叠后标题行显示"近 7 天成功 N · 失败 N"。
 * @author AaronZZH & Kiro
 */

"use client"

import { useMemo } from "react"
import { NeonChip } from "@/components/studio"
import { Skeleton } from "@/components/ui/skeleton"
import { BaseChart, type EChartsOption } from "@/features/stats/charts/BaseChart"
import { HomeWidgetCard } from "./HomeWidgetCard"
import { HomeWidgetError } from "./HomeWidgetError"
import { type AgentRunStatPoint, useAgentRunStatsMock } from "./home-mock-data"
import { useHomeWidgetCollapse } from "./use-home-widget-collapse"

function buildOption(points: AgentRunStatPoint[]): EChartsOption {
  return {
    backgroundColor: "transparent",
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    legend: { top: 0, right: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
    grid: { left: 28, right: 4, top: 24, bottom: 20 },
    xAxis: {
      type: "category",
      data: points.map((point) => point.date.slice(5)),
      axisLabel: { fontSize: 10 }
    },
    yAxis: {
      type: "value",
      minInterval: 1,
      axisLabel: { fontSize: 10 },
      splitLine: { lineStyle: { opacity: 0.2 } }
    },
    series: [
      {
        name: "成功",
        type: "bar",
        stack: "runs",
        barMaxWidth: 16,
        data: points.map((point) => point.success),
        itemStyle: { color: "#22c55e" }
      },
      {
        name: "失败",
        type: "bar",
        stack: "runs",
        barMaxWidth: 16,
        data: points.map((point) => point.failed),
        itemStyle: { color: "#ef4444", borderRadius: [4, 4, 0, 0] }
      }
    ]
  }
}

export function HomeAgentRunStats() {
  const { data: points, isLoading, isError, refetch } = useAgentRunStatsMock()
  const { collapsed, toggle } = useHomeWidgetCollapse("agent-runs")
  const option = useMemo(() => buildOption(points ?? []), [points])
  const success = points?.reduce((sum, point) => sum + point.success, 0)
  const failed = points?.reduce((sum, point) => sum + point.failed, 0)
  const hasRuns = (success ?? 0) + (failed ?? 0) > 0

  return (
    <HomeWidgetCard
      title="智能体运行统计"
      collapsible
      collapsed={collapsed}
      onToggle={toggle}
      badges={
        <NeonChip tone="amber" size="sm" className="shrink-0">
          示例数据
        </NeonChip>
      }
      summary={
        <span className="tabular-nums">
          近 7 天成功 <span className="text-emerald-400">{success ?? "—"}</span> · 失败{" "}
          <span className="text-rose-400">{failed ?? "—"}</span>
        </span>
      }
    >
      {isLoading ? (
        <Skeleton className="h-40 w-full rounded-lg" />
      ) : isError || !points ? (
        <HomeWidgetError title="运行统计加载失败" onRetry={() => void refetch()} />
      ) : hasRuns ? (
        <BaseChart option={option} className="h-40 w-full" />
      ) : (
        <p className="flex h-40 items-center justify-center text-muted-foreground text-xs">
          近 7 天没有智能体运行记录
        </p>
      )}
    </HomeWidgetCard>
  )
}
