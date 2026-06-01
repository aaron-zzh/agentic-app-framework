/**
 * ListWidget——列表卡片
 * @author AaronZZH & Kiro
 */

"use client"

import { List } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import type { ListWidgetConfig } from "@/lib/api/rest/dashboard/dashboard"
import { useWidgetData } from "@/lib/queries/use-dashboard"

interface ListWidgetProps {
  widgetId: string
  title: string
  config: ListWidgetConfig
  refreshInterval?: number
}

export function ListWidget({ widgetId, title, config, refreshInterval }: ListWidgetProps) {
  const { data, isLoading } = useWidgetData(widgetId, config, refreshInterval)

  if (isLoading) {
    return (
      <Card className="h-full">
        <CardHeader>
          <Skeleton className="h-4 w-24" />
        </CardHeader>
        <CardContent className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={`skeleton-${i}`} className="h-5 w-full" />
          ))}
        </CardContent>
      </Card>
    )
  }

  const items = data?.items ?? []

  return (
    <Card className="h-full overflow-hidden">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 font-medium text-sm">
          <List className="h-4 w-4 text-muted-foreground" />
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="overflow-auto">
        {items.length === 0 ? (
          <p className="text-muted-foreground text-sm">暂无数据</p>
        ) : (
          <ul className="space-y-2">
            {items.map((item, idx) => (
              <li
                key={String(item.id ?? idx)}
                className="truncate rounded-md border px-3 py-2 text-sm"
              >
                {config.columns.map((col) => String(item[col] ?? "")).join(" · ")}
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
