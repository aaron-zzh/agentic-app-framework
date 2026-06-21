/**
 * EChartsWidget——ECharts 图表仪表盘卡片
 * 支持 trend/funnel/retention/pie 四种图表类型，数据源绑定 /api/stats 接口
 * @author AaronZZH & Kiro
 */

"use client"

import { Activity } from "lucide-react"
import { useEffect, useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { FunnelChart, RetentionChart, TrendChart } from "@/features/stats/charts"
import type { EChartsWidgetConfig } from "@/lib/api/rest/dashboard/dashboard"
import type { FunnelStage, TrendPoint } from "@/lib/api/rest/dashboard/stats"
import { useStatsFunnel, useStatsRetention, useStatsTrend } from "@/lib/queries/use-stats"
import { cn } from "@/lib/utils/cn"
import { WIDGET_CARD_CLASS } from "./_shared/styles"

interface EChartsWidgetProps {
  widgetId: string
  title: string
  config: EChartsWidgetConfig
  refreshInterval?: number
}

/** 生成 N 天模拟趋势数据 */
function mockTrend(days = 14, base = 100, variance = 40): TrendPoint[] {
  return Array.from({ length: days }, (_, i) => {
    const d = new Date()
    d.setDate(d.getDate() - (days - 1 - i))
    return {
      time: `${d.getMonth() + 1}/${d.getDate()}`,
      value: Math.round(base + Math.random() * variance - variance / 2)
    }
  })
}

const MOCK_RETENTION = Array.from({ length: 7 }, (_, i) => {
  const d = new Date()
  d.setDate(d.getDate() - (6 - i))
  return {
    date: d.toISOString().slice(0, 10),
    day1: Math.round(60 + Math.random() * 20),
    day3: Math.round(40 + Math.random() * 15),
    day7: Math.round(25 + Math.random() * 10),
    day14: Math.round(15 + Math.random() * 8),
    day30: Math.round(8 + Math.random() * 5)
  }
})

const MOCK_FUNNEL: FunnelStage[] = [
  { name: "访问", value: 10000 },
  { name: "注册", value: 4200 },
  { name: "激活", value: 2800 },
  { name: "付费", value: 860 }
]

/** 模拟数据提示 badge（放在标题右侧） */
function MockDataBadge() {
  return (
    <span className="rounded bg-amber-50 px-1.5 py-0.5 text-amber-600 text-xs dark:bg-amber-900/20 dark:text-amber-400">
      模拟数据
    </span>
  )
}

export function EChartsWidget({
  widgetId: _widgetId,
  title,
  config,
  refreshInterval
}: EChartsWidgetProps) {
  const [isMock, setIsMock] = useState(false)

  return (
    <Card className={cn("flex h-full flex-col", WIDGET_CARD_CLASS)}>
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 font-medium text-sm">
          <Activity className="h-4 w-4 text-muted-foreground" />
          {title}
          {isMock && <MockDataBadge />}
        </CardTitle>
      </CardHeader>
      <CardContent className="flex min-h-0 flex-1 flex-col">
        <ChartRenderer config={config} refreshInterval={refreshInterval} onMockChange={setIsMock} />
      </CardContent>
    </Card>
  )
}

/** 根据 config.statsType 渲染对应图表 */
function ChartRenderer({
  config,
  refreshInterval,
  onMockChange
}: {
  config: EChartsWidgetConfig
  refreshInterval?: number
  onMockChange: (isMock: boolean) => void
}) {
  switch (config.statsType) {
    case "trend":
      return (
        <TrendRenderer
          config={config}
          refreshInterval={refreshInterval}
          onMockChange={onMockChange}
        />
      )
    case "funnel":
      return <FunnelRenderer refreshInterval={refreshInterval} onMockChange={onMockChange} />
    case "retention":
      return <RetentionRenderer refreshInterval={refreshInterval} onMockChange={onMockChange} />
    case "pie":
      return (
        <TrendRenderer
          config={{ ...config, chartType: "bar" }}
          refreshInterval={refreshInterval}
          onMockChange={onMockChange}
        />
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
  refreshInterval,
  onMockChange
}: {
  config: EChartsWidgetConfig
  refreshInterval?: number
  onMockChange: (isMock: boolean) => void
}) {
  const { data, isLoading } = useStatsTrend(
    { metric: config.metric ?? "dau", period: config.period ?? "day" },
    refreshInterval
  )

  const isMock = !isLoading && !data?.length
  useEffect(() => {
    onMockChange(isMock)
  }, [isMock, onMockChange])

  if (isLoading) return <Skeleton className="h-full w-full" />

  const chartData = isMock ? mockTrend() : (data ?? [])

  return (
    <TrendChart
      data={chartData}
      chartType={config.chartType ?? "line"}
      dataZoom={false}
      className="h-full min-h-0 w-full flex-1"
    />
  )
}

function FunnelRenderer({
  refreshInterval,
  onMockChange
}: {
  refreshInterval?: number
  onMockChange: (isMock: boolean) => void
}) {
  const { data, isLoading } = useStatsFunnel(refreshInterval)

  const isMock = !isLoading && !data?.length
  useEffect(() => {
    onMockChange(isMock)
  }, [isMock, onMockChange])

  if (isLoading) return <Skeleton className="h-full w-full" />

  return (
    <FunnelChart
      data={isMock ? MOCK_FUNNEL : (data ?? [])}
      className="h-full min-h-0 w-full flex-1"
    />
  )
}

function RetentionRenderer({
  refreshInterval,
  onMockChange
}: {
  refreshInterval?: number
  onMockChange: (isMock: boolean) => void
}) {
  const { data, isLoading } = useStatsRetention(refreshInterval)

  const isMock = !isLoading && !data?.length
  useEffect(() => {
    onMockChange(isMock)
  }, [isMock, onMockChange])

  if (isLoading) return <Skeleton className="h-full w-full" />

  return (
    <RetentionChart
      data={isMock ? MOCK_RETENTION : (data ?? [])}
      className="h-full min-h-0 w-full flex-1"
    />
  )
}
