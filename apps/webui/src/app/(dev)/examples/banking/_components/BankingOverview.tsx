/**
 * BankingOverview——总余额 + 收入/支出 tabs + 折线图
 */

"use client"

import { ArrowDownLeft, ArrowUpRight, Info, Plus, TrendingDown, TrendingUp } from "lucide-react"
import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { BaseChart, type EChartsOption } from "@/features/stats/charts/BaseChart"
import type { TrendPoint } from "@/lib/api/rest/dashboard/stats"

// 渐变折线图（替代 TrendChart，支持渐变填充）
function GradientLineChart({ data }: { data: TrendPoint[] }) {
  const option: EChartsOption = {
    tooltip: { trigger: "axis", axisPointer: { type: "cross" } },
    grid: { left: "3%", right: "4%", bottom: "3%", containLabel: true },
    xAxis: { type: "category", data: data.map((p) => p.time), boundaryGap: false },
    yAxis: { type: "value" },
    series: [
      {
        type: "line",
        data: data.map((p) => p.value),
        smooth: true,
        showSymbol: false,
        areaStyle: {
          color: {
            type: "linear",
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: "rgba(99,102,241,0.4)" },
              { offset: 1, color: "rgba(99,102,241,0)" }
            ]
          }
        },
        lineStyle: { color: "rgb(99,102,241)", width: 2 },
        itemStyle: { color: "rgb(99,102,241)" }
      }
    ]
  }
  return <BaseChart option={option} className="h-[270px] w-full" />
}

const TABS = [
  {
    value: "income",
    label: "Income",
    percent: 8.2,
    total: 9990,
    data: [5, 31, 33, 50, 99, 76, 72, 76, 89]
  },
  {
    value: "expenses",
    label: "Expenses",
    percent: -6.6,
    total: 1989,
    data: [10, 41, 35, 51, 49, 62, 69, 91, 148]
  }
]

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep"]

function formatCurrency(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value)
}

export function BankingOverview() {
  const [activeTab, setActiveTab] = useState("income")
  const current = TABS.find((t) => t.value === activeTab) ?? TABS[0]

  const chartData: TrendPoint[] = MONTHS.map((m, i) => ({ time: m, value: current.data[i] }))

  return (
    <Card>
      <CardContent className="p-6">
        {/* 总余额 + 操作按钮 */}
        <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="mb-1 flex items-center gap-1 text-muted-foreground text-sm">
              Total balance
              <Info className="h-3.5 w-3.5" />
            </div>
            <p className="font-bold text-3xl">{formatCurrency(49990)}</p>
          </div>
          <div className="flex gap-3">
            <Button variant="secondary" className="gap-1.5">
              <ArrowUpRight className="h-4 w-4" /> Send
            </Button>
            <Button variant="secondary" className="gap-1.5">
              <Plus className="h-4 w-4" /> Add card
            </Button>
            <Button variant="secondary" className="gap-1.5">
              <ArrowDownLeft className="h-4 w-4" /> Request
            </Button>
          </div>
        </div>

        {/* Tabs */}
        <Tabs value={activeTab} onValueChange={setActiveTab}>
          <TabsList className="mb-6 h-20! w-full rounded-2xl p-1.5">
            {TABS.map((tab) => (
              <TabsTrigger key={tab.value} value={tab.value} className="flex-1 rounded-xl">
                <div className="flex w-full items-center gap-3 text-left">
                  <div
                    className={`hidden h-9 w-9 shrink-0 items-center justify-center rounded-full sm:flex ${tab.value === "income" ? "bg-primary/10 text-primary" : "bg-orange-100 text-orange-600"}`}
                  >
                    {tab.value === "income" ? (
                      <TrendingUp className="h-4 w-4" />
                    ) : (
                      <TrendingDown className="h-4 w-4" />
                    )}
                  </div>
                  <div>
                    <p className="mb-0.5 text-muted-foreground text-xs">{tab.label}</p>
                    <p className="font-bold text-xl">{formatCurrency(tab.total)}</p>
                  </div>
                  <Badge
                    variant={tab.percent >= 0 ? "default" : "destructive"}
                    className="ml-auto text-xs"
                  >
                    {tab.percent > 0 ? "+" : ""}
                    {tab.percent}%
                  </Badge>
                </div>
              </TabsTrigger>
            ))}
          </TabsList>

          {TABS.map((tab) => (
            <TabsContent key={tab.value} value={tab.value}>
              <GradientLineChart data={chartData} />
            </TabsContent>
          ))}
        </Tabs>
      </CardContent>
    </Card>
  )
}
