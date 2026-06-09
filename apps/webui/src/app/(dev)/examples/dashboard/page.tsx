/**
 * Dashboard 示例——用预设模拟数据展示仪表盘布局（只读，不走接口）
 * @author AaronZZH & Kiro
 */

"use client"

import { Target, TrendingUp, Zap } from "lucide-react"
import { useMemo, useState } from "react"
import { type Layout, Responsive, WidthProvider } from "react-grid-layout"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { dashboardPresets } from "@/features/dashboard/presets"
import { CardCarouselWidget } from "@/features/dashboard/widgets/CardCarouselWidget"
import { ExpensesCategoryWidget } from "@/features/dashboard/widgets/ExpensesCategoryWidget"
import { FinanceOverviewWidget } from "@/features/dashboard/widgets/FinanceOverviewWidget"
import {
  mockBalanceStatistics,
  mockCreditCards,
  mockExpensesCategories,
  mockTransactions
} from "@/features/dashboard/widgets/finance-mock"
import { MultiSeriesChartWidget } from "@/features/dashboard/widgets/MultiSeriesChartWidget"
import { TransactionListWidget } from "@/features/dashboard/widgets/TransactionListWidget"
import { FunnelChart, TrendChart } from "@/features/stats/charts"
import { MultiSeriesLineChart } from "@/features/stats/charts/MultiSeriesLineChart"
import type { DashboardWidgetVO } from "@/lib/api/rest/dashboard/dashboard"
import type { FunnelStage, TrendPoint } from "@/lib/api/rest/dashboard/stats"

import "react-grid-layout/css/styles.css"

/** 生成最近 N 天的模拟趋势数据 */
function mockTrend(days = 14, base = 100, variance = 30): TrendPoint[] {
  return Array.from({ length: days }, (_, i) => {
    const d = new Date()
    d.setDate(d.getDate() - (days - 1 - i))
    return {
      time: `${d.getMonth() + 1}/${d.getDate()}`,
      value: Math.round(base + Math.random() * variance - variance / 2)
    }
  })
}

const MOCK_TRENDS: Record<string, TrendPoint[]> = {
  dau: mockTrend(14, 800, 200),
  mau: mockTrend(6, 12000, 2000),
  api_calls: mockTrend(14, 50000, 10000),
  error_rate: mockTrend(14, 2, 1),
  avg_latency: mockTrend(14, 120, 40),
  revenue: mockTrend(14, 5000, 2000),
  token_usage: mockTrend(14, 200000, 50000),
  arpu: mockTrend(6, 28, 8),
  balance: mockTrend(14, 80000, 5000),
  income: mockTrend(14, 15000, 3000),
  expense: mockTrend(14, 9000, 2000)
}

const MOCK_FUNNEL: FunnelStage[] = [
  { name: "访问", value: 10000 },
  { name: "注册", value: 4200 },
  { name: "激活", value: 2800 },
  { name: "付费", value: 860 }
]

const ResponsiveGridLayout = WidthProvider(Responsive)

/** 静态 mock card，根据 widget 类型渲染 */
function MockWidget({ widget }: { widget: DashboardWidgetVO }) {
  const { title, config } = widget

  if (config.type === "finance") {
    switch (config.component) {
      case "overview":
        return <FinanceOverviewWidget />
      case "multi-series-chart":
        return <MultiSeriesChartWidget title={title} chart={mockBalanceStatistics} />
      case "expenses-category":
        return <ExpensesCategoryWidget title={title} series={mockExpensesCategories} />
      case "card-carousel":
        return (
          <div className="p-2">
            <CardCarouselWidget list={mockCreditCards} />
          </div>
        )
      case "transaction-list":
        return <TransactionListWidget title={title} tableData={mockTransactions} />
      default:
        return (
          <Card className="flex h-full items-center justify-center">
            <span className="text-muted-foreground text-sm">{title}</span>
          </Card>
        )
    }
  }

  if (config.type === "counter") {
    return (
      <Card className="h-full">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 font-medium text-sm">
            <TrendingUp className="h-4 w-4 text-muted-foreground" />
            {title}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="font-bold text-2xl">1,234</p>
          <p className="text-muted-foreground text-xs">模拟数据</p>
        </CardContent>
      </Card>
    )
  }

  if (config.type === "progress") {
    const current = Number(config.current) ?? 0
    const target = Number(config.target) ?? 100
    const percent = target > 0 ? Math.round((current / target) * 100) : 0
    return (
      <Card className="h-full">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 font-medium text-sm">
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

  if (config.type === "echarts") {
    const trendData = MOCK_TRENDS[config.metric ?? "dau"] ?? MOCK_TRENDS.dau
    const isFunnel = config.statsType === "funnel" || config.statsType === "retention"
    return (
      <Card className="flex h-full flex-col">
        <CardHeader className="pb-2">
          <CardTitle className="font-medium text-sm">{title}</CardTitle>
        </CardHeader>
        <CardContent className="min-h-0 flex-1 p-2">
          {isFunnel ? (
            <FunnelChart data={MOCK_FUNNEL} className="h-full w-full" />
          ) : (
            <TrendChart
              data={trendData}
              chartType={config.chartType ?? "line"}
              dataZoom={false}
              className="h-full w-full"
            />
          )}
        </CardContent>
      </Card>
    )
  }

  if (config.type === "shortcut") {
    return (
      <Card className="h-full">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 font-medium text-sm">
            <Zap className="h-4 w-4 text-muted-foreground" />
            {title}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground text-xs">快捷入口占位</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="flex h-full items-center justify-center">
      <span className="text-muted-foreground text-sm">{title}</span>
    </Card>
  )
}

export default function DashboardExamplePage() {
  const [activeKey, setActiveKey] = useState("banking")
  const preset = dashboardPresets.find((p) => p.key === activeKey) ?? dashboardPresets[0]

  const layouts = useMemo(() => {
    const lg: Layout[] = preset.widgets.map((w) => ({
      i: w.id,
      x: w.position.x,
      y: w.position.y,
      w: w.position.w,
      h: w.position.h,
      static: true
    }))
    return { lg }
  }, [preset])

  return (
    <div className="mx-auto max-w-7xl p-6">
      <h1 className="mb-1 font-bold text-2xl">Dashboard 示例</h1>
      <p className="mb-6 text-muted-foreground text-sm">
        react-grid-layout 仪表盘预设布局（只读 · 模拟数据）
      </p>

      {/* 预设切换 */}
      <div className="mb-6 flex gap-2">
        {dashboardPresets.map((p) => (
          <button
            key={p.key}
            type="button"
            onClick={() => setActiveKey(p.key)}
            className="rounded-lg border px-4 py-2 text-sm transition-colors hover:bg-accent data-[active=true]:border-primary data-[active=true]:bg-primary/10 data-[active=true]:font-medium"
            data-active={activeKey === p.key}
          >
            {p.name}
          </button>
        ))}
      </div>

      <div className="mb-4 flex items-center gap-2">
        <p className="text-muted-foreground text-sm">{preset.description}</p>
        <Badge variant="outline">刷新间隔 {preset.refreshInterval}s</Badge>
      </div>

      <ResponsiveGridLayout
        className="layout"
        layouts={layouts}
        breakpoints={{ lg: 1200, md: 996, sm: 768, xs: 480 }}
        cols={{ lg: 12, md: 9, sm: 6, xs: 3 }}
        rowHeight={80}
        isDraggable={false}
        isResizable={false}
      >
        {preset.widgets.map((widget) => (
          <div key={widget.id}>
            <MockWidget widget={widget} />
          </div>
        ))}
      </ResponsiveGridLayout>

      {/* 多系列折线图演示 */}
      <div className="mt-10">
        <h2 className="mb-1 font-semibold text-lg">MultiSeriesLineChart 演示</h2>
        <p className="mb-4 text-muted-foreground text-sm">多条折线同时对比，支持面积填充</p>
        <Card>
          <CardContent className="p-4">
            <MultiSeriesLineChart
              categories={MOCK_TRENDS.dau.map((p) => p.time)}
              series={[
                { name: "DAU", data: MOCK_TRENDS.dau.map((p) => p.value), area: true },
                {
                  name: "新增用户",
                  data: MOCK_TRENDS.dau.map((p) => Math.round(p.value * 0.3)),
                  area: false
                },
                { name: "活跃付费", data: MOCK_TRENDS.dau.map((p) => Math.round(p.value * 0.08)) }
              ]}
              yAxisName="人数"
              className="h-72 w-full"
            />
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
