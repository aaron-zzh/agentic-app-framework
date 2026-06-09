/**
 * FinanceWidget——金融类 widget 路由，根据 component 分发到对应子组件
 */

"use client"

import type { FinanceWidgetConfig } from "@/lib/api/rest/dashboard/dashboard"
import dynamic from "next/dynamic"
import {
  mockBalanceStatistics,
  mockCreditCards,
  mockExpensesCategories,
  mockTransactions
} from "./finance-mock"

const CardCarouselWidget = dynamic(() =>
  import("./CardCarouselWidget").then((m) => m.CardCarouselWidget)
)
const ExpensesCategoryWidget = dynamic(() =>
  import("./ExpensesCategoryWidget").then((m) => m.ExpensesCategoryWidget)
)
const FinanceOverviewWidget = dynamic(() =>
  import("./FinanceOverviewWidget").then((m) => m.FinanceOverviewWidget)
)
const MultiSeriesChartWidget = dynamic(() =>
  import("./MultiSeriesChartWidget").then((m) => m.MultiSeriesChartWidget)
)
const TransactionListWidget = dynamic(() =>
  import("./TransactionListWidget").then((m) => m.TransactionListWidget)
)

interface FinanceWidgetProps {
  title: string
  config: FinanceWidgetConfig
}

export function FinanceWidget({ title, config }: FinanceWidgetProps) {
  switch (config.component) {
    case "overview":
      return <FinanceOverviewWidget />
    case "multi-series-chart":
      return <MultiSeriesChartWidget title={title} chart={mockBalanceStatistics} />
    case "expenses-category":
      return <ExpensesCategoryWidget title={title} series={mockExpensesCategories} />
    case "card-carousel":
      return <CardCarouselWidget list={mockCreditCards} />
    case "transaction-list":
      return <TransactionListWidget title={title} tableData={mockTransactions} />
    default:
      return (
        <div className="flex h-full items-center justify-center text-muted-foreground text-sm">
          未知金融组件
        </div>
      )
  }
}
