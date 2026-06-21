/**
 * BillingWidget——个人积分仪表盘 widget 路由（数据源为后端 billing-* 接口）。
 *
 * <p>根据 config.component 分发到对应子组件，统一从 useWidgetData 拉真实数据并下发。
 *
 * @author AaronZZH &amp; Kiro
 */

"use client"

import dynamic from "next/dynamic"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import type { BillingWidgetConfig } from "@/lib/api/rest/dashboard/dashboard"
import { useWidgetData } from "@/lib/queries/use-dashboard"
import { cn } from "@/lib/utils/cn"
import { WIDGET_CARD_CLASS } from "./_shared/styles"
import type { BillingOverviewData } from "./FinanceOverviewWidget"
import type { BillingTransactionRow } from "./TransactionListWidget"

const FinanceOverviewWidget = dynamic(() =>
  import("./FinanceOverviewWidget").then((m) => m.FinanceOverviewWidget)
)
const ExpensesCategoryWidget = dynamic(() =>
  import("./ExpensesCategoryWidget").then((m) => m.ExpensesCategoryWidget)
)
const TransactionListWidget = dynamic(() =>
  import("./TransactionListWidget").then((m) => m.TransactionListWidget)
)
const MultiSeriesChartWidget = dynamic(() =>
  import("./MultiSeriesChartWidget").then((m) => m.MultiSeriesChartWidget)
)

interface BillingWidgetProps {
  widgetId: string
  title: string
  config: BillingWidgetConfig
  refreshInterval?: number
}

export function BillingWidget({ widgetId, title, config, refreshInterval }: BillingWidgetProps) {
  const { data, isLoading } = useWidgetData(widgetId, config, refreshInterval)

  if (isLoading) {
    return (
      <Card className={cn("h-full", WIDGET_CARD_CLASS)}>
        <CardContent className="space-y-3 p-6">
          <Skeleton className="h-6 w-32" />
          <Skeleton className="h-10 w-48" />
          <Skeleton className="h-40 w-full" />
        </CardContent>
      </Card>
    )
  }

  // useWidgetData 返回 { widgetId, type, data }，data 是后端 Map（任意结构，由 widget component 解释）
  const payload = (data?.data ?? null) as Record<string, unknown> | null

  switch (config.component) {
    case "overview":
      return <FinanceOverviewWidget data={payload as unknown as BillingOverviewData | undefined} />
    case "expenses-category":
      return (
        <ExpensesCategoryWidget
          title={title}
          categories={
            (payload?.categories as { biz_type: string; total: number }[] | undefined) ?? []
          }
        />
      )
    case "transaction-list":
      return (
        <TransactionListWidget
          title={title}
          items={(payload?.items as BillingTransactionRow[] | undefined) ?? []}
        />
      )
    case "multi-series-chart":
      return (
        <MultiSeriesChartWidget
          title={title}
          subheader="近 30 天获取与消耗"
          billing={
            payload as
              | {
                  earn: { time: string; value: number }[]
                  spend: { time: string; value: number }[]
                }
              | undefined
          }
        />
      )
    default:
      return (
        <div className="flex h-full items-center justify-center text-muted-foreground text-sm">
          未知积分组件
        </div>
      )
  }
}
