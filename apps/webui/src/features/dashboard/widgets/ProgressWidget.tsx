/**
 * ProgressWidget——进度卡片
 * @author AaronZZH & Kiro
 */

"use client"

import { Target } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { Skeleton } from "@/components/ui/skeleton"
import type { ProgressWidgetConfig } from "@/lib/api/dashboard"
import { useWidgetData } from "@/lib/queries/use-dashboard"

interface ProgressWidgetProps {
  widgetId: string
  title: string
  config: ProgressWidgetConfig
  refreshInterval?: number
}

export function ProgressWidget({ widgetId, title, config, refreshInterval }: ProgressWidgetProps) {
  const { data, isLoading } = useWidgetData(widgetId, config, refreshInterval)

  if (isLoading) {
    return (
      <Card className="h-full">
        <CardHeader>
          <Skeleton className="h-4 w-24" />
        </CardHeader>
        <CardContent>
          <Skeleton className="h-4 w-full" />
        </CardContent>
      </Card>
    )
  }

  const current = data?.progress?.current ?? Number(config.current) ?? 0
  const target = data?.progress?.target ?? Number(config.target) ?? 100
  const percent = target > 0 ? Math.round((current / target) * 100) : 0

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm font-medium">
          <Target className="h-4 w-4 text-muted-foreground" />
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        <Progress value={percent} />
        <p className="text-muted-foreground text-xs">
          {current} / {target}（{percent}%）
        </p>
      </CardContent>
    </Card>
  )
}
