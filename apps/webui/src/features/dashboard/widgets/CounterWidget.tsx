/**
 * CounterWidget——数字统计卡片
 * @author AaronZZH & Kiro
 */

"use client"

import { TrendingUp } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import type { CounterWidgetConfig } from "@/lib/api/rest/dashboard/dashboard"
import { useWidgetData } from "@/lib/queries/use-dashboard"

interface CounterWidgetProps {
  widgetId: string
  title: string
  config: CounterWidgetConfig
  refreshInterval?: number
}

export function CounterWidget({ widgetId, title, config, refreshInterval }: CounterWidgetProps) {
  const { data, isLoading } = useWidgetData(widgetId, config, refreshInterval)

  if (isLoading) {
    return (
      <Card className="h-full">
        <CardHeader>
          <Skeleton className="h-4 w-24" />
        </CardHeader>
        <CardContent>
          <Skeleton className="h-8 w-16" />
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 font-medium text-sm">
          <TrendingUp className="h-4 w-4 text-muted-foreground" />
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <p className="font-bold text-2xl">{data?.value ?? 0}</p>
      </CardContent>
    </Card>
  )
}
