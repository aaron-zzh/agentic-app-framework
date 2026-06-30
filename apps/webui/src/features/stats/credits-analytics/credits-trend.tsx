/**
 * 积分消耗趋势图——支持日/月 + 柱状/折线切换
 * 数据来源：GET /api/stats/trend?metric=credit_cost
 * @author AaronZZH
 */

"use client"

import { useMemo, useState } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { BaseChart, type EChartsOption } from "@/features/stats/charts/BaseChart"
import { useCreditsTrend } from "@/lib/queries/use-credits-analytics"

const COLORS = ["#3b82f6", "#f59e0b", "#10b981"]

type Range = "monthly" | "daily"
type ChartType = "bar" | "line"

export function CreditsTrend() {
  const [range, setRange] = useState<Range>("monthly")
  const [chartType, setChartType] = useState<ChartType>("bar")

  const period = range === "daily" ? "day" : "month"
  const { data: trend, isLoading } = useCreditsTrend(period)

  const option = useMemo<EChartsOption>(() => {
    const categories = trend?.categories ?? []
    const series = trend?.series ?? []

    if (range === "daily") {
      const data = series[0]?.data ?? []
      return {
        tooltip: { trigger: "axis" },
        grid: { left: "3%", right: "4%", bottom: "14%", containLabel: true },
        xAxis: {
          type: "category",
          data: categories,
          axisLabel: { rotate: 45 }
        },
        yAxis: { type: "value", name: "积分" },
        dataZoom: [{ type: "slider", bottom: 0, height: 20 }],
        series: [
          {
            name: "日消耗",
            type: chartType,
            data,
            smooth: true,
            showSymbol: false,
            itemStyle: { color: COLORS[0] },
            areaStyle: chartType === "line" ? { color: COLORS[0], opacity: 0.12 } : undefined
          }
        ]
      }
    }

    return {
      tooltip: { trigger: "axis" },
      legend: { bottom: 0, data: series.map((s) => s.name) },
      grid: { left: "3%", right: "4%", bottom: "15%", containLabel: true },
      xAxis: { type: "category", data: categories },
      yAxis: { type: "value", name: "积分" },
      series: series.map((s, i) => ({
        name: s.name,
        type: chartType,
        stack: chartType === "bar" ? "total" : undefined,
        data: s.data,
        smooth: true,
        showSymbol: false,
        itemStyle: { color: COLORS[i % COLORS.length] },
        areaStyle:
          chartType === "line" ? { color: COLORS[i % COLORS.length], opacity: 0.1 } : undefined
      }))
    }
  }, [trend, range, chartType])

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0 pb-4">
        <div>
          <CardTitle>积分消耗趋势</CardTitle>
          <p className="mt-1 text-muted-foreground text-sm">按周期展示积分消耗走势</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex rounded-md border">
            <Button
              variant={chartType === "bar" ? "secondary" : "ghost"}
              size="sm"
              className="h-7 rounded-r-none px-2 text-xs"
              onClick={() => setChartType("bar")}
            >
              柱
            </Button>
            <Button
              variant={chartType === "line" ? "secondary" : "ghost"}
              size="sm"
              className="h-7 rounded-l-none border-l px-2 text-xs"
              onClick={() => setChartType("line")}
            >
              线
            </Button>
          </div>
          <Select value={range} onValueChange={(v) => setRange(v as Range)}>
            <SelectTrigger className="h-8 w-24 text-xs">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="monthly">按月</SelectItem>
              <SelectItem value="daily">近30天</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <Skeleton className="h-[320px] w-full" />
        ) : (
          <BaseChart option={option} className="h-[320px] w-full" />
        )}
      </CardContent>
    </Card>
  )
}
