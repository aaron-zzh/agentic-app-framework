/**
 * 积分每日消耗折线面积图，积分中心与 Studio 首页共用。
 *
 * @example
 * const { data: points = [] } = useCreditSpendTrend(14)
 * <CreditSpendTrendChart points={points} className="h-44 w-full" />
 * @author AaronZZH & Kiro
 */

"use client"

import { useMemo } from "react"
import { BaseChart, type EChartsOption } from "@/features/stats/charts/BaseChart"
import type { CreditSpendTrendPoint } from "@/lib/api/rest/billing"

interface CreditSpendTrendChartProps {
  points: CreditSpendTrendPoint[]
  className?: string
}

export function CreditSpendTrendChart({ points, className }: CreditSpendTrendChartProps) {
  const option = useMemo<EChartsOption>(
    () => ({
      backgroundColor: "transparent",
      tooltip: {
        trigger: "axis",
        formatter: (params: unknown) => {
          const [first] = params as { name: string; value: number }[]
          return first ? `${first.name}<br/>消耗 ${first.value} 积分` : ""
        }
      },
      grid: { left: 40, right: 8, top: 8, bottom: 24 },
      xAxis: {
        type: "category",
        data: points.map((point) => point.date.slice(5)),
        axisLabel: { fontSize: 10 }
      },
      yAxis: {
        type: "value",
        axisLabel: { fontSize: 10 },
        splitLine: { lineStyle: { opacity: 0.2 } }
      },
      series: [
        {
          type: "line",
          smooth: true,
          symbol: "none",
          data: points.map((point) => point.amount),
          areaStyle: { opacity: 0.15 },
          lineStyle: { color: "#6366f1", width: 2 },
          itemStyle: { color: "#6366f1" }
        }
      ]
    }),
    [points]
  )

  return <BaseChart option={option} className={className} />
}
