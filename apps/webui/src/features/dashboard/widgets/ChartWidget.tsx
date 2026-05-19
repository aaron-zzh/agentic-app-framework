/**
 * ChartWidget——图表卡片（占位，后续引入 recharts）
 * @author AaronZZH & Kiro
 */

"use client"

import { BarChart3 } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import type { ChartWidgetConfig } from "@/lib/api/dashboard"
import { useWidgetData } from "@/lib/queries/use-dashboard"

interface ChartWidgetProps {
  widgetId: string
  title: string
  config: ChartWidgetConfig
  refreshInterval?: number
}

export function ChartWidget({ widgetId, title, config, refreshInterval }: ChartWidgetProps) {
  const { data, isLoading } = useWidgetData(widgetId, config, refreshInterval)

  if (isLoading) {
    return (
      <Card className="h-full">
        <CardHeader>
          <Skeleton className="h-4 w-24" />
        </CardHeader>
        <CardContent>
          <Skeleton className="h-32 w-full" />
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm font-medium">
          <BarChart3 className="h-4 w-4 text-muted-foreground" />
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-1 items-center justify-center">
        {/* 图表占位——后续引入 recharts 替换 */}
        <div className="flex h-32 w-full items-center justify-center rounded-md border border-dashed text-muted-foreground text-sm">
          {config.chartType} 图表（{data?.chartData?.length ?? 0} 条数据）
        </div>
      </CardContent>
    </Card>
  )
}
