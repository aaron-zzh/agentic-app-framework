/**
 * EChartsWidget——ECharts 图表仪表盘卡片
 * 支持 trend/funnel/retention/pie 四种图表类型，数据源绑定 /api/stats 接口
 * @author AaronZZH & Kiro
 */

"use client"

import { Activity } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { FunnelChart, RetentionChart, TrendChart } from "@/features/stats/charts"
import type { EChartsWidgetConfig } from "@/lib/api/dashboard"
import { useStatsFunnel, useStatsRetention, useStatsTrend } from "@/lib/queries/use-stats"

interface EChartsWidgetProps {
  widgetId: string
  title: string
  config: EChartsWidgetConfig
  refreshInterval?: number
}

export function EChartsWidget({
  widgetId: _widgetId,
  title,
  config,
  refreshInterval
}: EChartsWidgetProps) {
  return (
    <Card className="flex h-full flex-col">
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 font-medium text-sm">
          <Activity className="h-4 w-4 text-muted-foreground" />
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="flex min-h-0 flex-1">
        <ChartRenderer config={config} refreshInterval={refreshInterval} />
      </CardContent>
    </Card>
  )
}

/** 根据 config.statsType 渲染对应图表 */
function ChartRenderer({
  config,
  refreshInterval
}: {
  config: EChartsWidgetConfig
  refreshInterval?: number
}) {
  switch (config.statsType) {
    case "trend":
      return <TrendRenderer config={config} refreshInterval={refreshInterval} />
    case "funnel":
      return <FunnelRenderer refreshInterval={refreshInterval} />
    case "retention":
      return <RetentionRenderer refreshInterval={refreshInterval} />
    case "pie":
      return (
        <TrendRenderer config={{ ...config, chartType: "bar" }} refreshInterval={refreshInterval} />
      )
    default:
      return (
        <div className="flex h-full w-full items-center justify-center text-muted-foreground text-sm">
          未知图表类型
        </div>
      )
  }
}

function TrendRenderer({
  config,
  refreshInterval
}: {
  config: EChartsWidgetConfig
  refreshInterval?: number
}) {
  const { data, isLoading } = useStatsTrend(
    { metric: config.metric ?? "dau", period: config.period ?? "day" },
    refreshInterval
  )

  if (isLoading) return <Skeleton className="h-full w-full" />
  if (!data?.length) return <EmptyState />

  return <TrendChart data={data} chartType={config.chartType ?? "line"} className="h-full w-full" />
}

function FunnelRenderer({ refreshInterval }: { refreshInterval?: number }) {
  const { data, isLoading } = useStatsFunnel(refreshInterval)

  if (isLoading) return <Skeleton className="h-full w-full" />
  if (!data?.length) return <EmptyState />

  return <FunnelChart data={data} className="h-full w-full" />
}

function RetentionRenderer({ refreshInterval }: { refreshInterval?: number }) {
  const { data, isLoading } = useStatsRetention(refreshInterval)

  if (isLoading) return <Skeleton className="h-full w-full" />
  if (!data?.length) return <EmptyState />

  return <RetentionChart data={data} className="h-full w-full" />
}

function EmptyState() {
  return (
    <div className="flex h-full w-full items-center justify-center text-muted-foreground text-sm">
      暂无数据
    </div>
  )
}
