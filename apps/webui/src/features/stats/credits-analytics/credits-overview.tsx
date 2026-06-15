/**
 * 积分消耗统计卡片——余额 / 充值 / 消耗 三张独立数字卡
 * 每张卡含迷你折线趋势
 * @author AaronZZH
 */

"use client"

import { TrendingDown, TrendingUp } from "lucide-react"
import { useMemo } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { BaseChart, type EChartsOption } from "@/features/stats/charts/BaseChart"
import { cn } from "@/lib/utils/cn"
import { MOCK_CREDITS_OVERVIEW, MOCK_TREND_DAILY } from "./_mock"

interface StatCardProps {
  label: string
  value: number
  unit?: string
  percent?: number
  chartData: number[]
  chartColor: string
}

function StatCard({ label, value, unit = "积分", percent, chartData, chartColor }: StatCardProps) {
  const option = useMemo<EChartsOption>(
    () => ({
      grid: { top: 4, bottom: 4, left: 4, right: 4 },
      xAxis: { type: "category", show: false, data: chartData.map((_, i) => i) },
      yAxis: { type: "value", show: false },
      series: [
        {
          type: "line",
          data: chartData,
          smooth: true,
          showSymbol: false,
          lineStyle: { color: chartColor, width: 2 },
          areaStyle: { color: chartColor, opacity: 0.15 }
        }
      ]
    }),
    [chartData, chartColor]
  )

  const isPos = percent !== undefined && percent > 0

  return (
    <Card>
      <CardHeader className="pt-4 pb-1">
        <CardTitle className="font-medium text-muted-foreground text-sm">{label}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 pb-4">
        <div>
          <span className="font-bold text-2xl tabular-nums">{value.toLocaleString()}</span>
          <span className="ml-1 text-muted-foreground text-xs">{unit}</span>
        </div>

        {percent !== undefined && (
          <div
            className={cn(
              "flex items-center gap-1 font-medium text-xs",
              isPos ? "text-emerald-600" : "text-red-500"
            )}
          >
            {isPos ? (
              <TrendingUp className="h-3.5 w-3.5" />
            ) : (
              <TrendingDown className="h-3.5 w-3.5" />
            )}
            {isPos ? "+" : ""}
            {percent}% 较上月
          </div>
        )}

        <BaseChart option={option} className="h-[56px] w-full" />
      </CardContent>
    </Card>
  )
}

/** 三张统计卡汇总 */
export function CreditsStatCards() {
  const dailyValues = MOCK_TREND_DAILY.map((d) => d.value)

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
      <StatCard
        label="当前余额"
        value={MOCK_CREDITS_OVERVIEW.balance}
        chartData={dailyValues.map((v) => Math.floor(v * 0.6))}
        chartColor="#3b82f6"
      />
      <StatCard
        label="本月消耗"
        value={MOCK_CREDITS_OVERVIEW.consumed}
        percent={MOCK_CREDITS_OVERVIEW.consumedPercent}
        chartData={dailyValues}
        chartColor="#f59e0b"
      />
      <StatCard
        label="本月充值"
        value={MOCK_CREDITS_OVERVIEW.recharged}
        percent={MOCK_CREDITS_OVERVIEW.rechargedPercent}
        chartData={dailyValues.map((v) => Math.floor(v * 1.5))}
        chartColor="#10b981"
      />
    </div>
  )
}
