/**
 * 留存率图表——折线图展示各日留存趋势
 * @author AaronZZH & Kiro
 */

"use client"

import { useMemo } from "react"
import { BaseChart, type EChartsOption } from "./BaseChart"
import type { RetentionData } from "@/lib/api/stats"

interface RetentionChartProps {
  data: RetentionData[]
  title?: string
  className?: string
}

export function RetentionChart({ data, title, className }: RetentionChartProps) {
  const option = useMemo<EChartsOption>(() => ({
    title: title ? { text: title, left: "center", textStyle: { fontSize: 14 } } : undefined,
    tooltip: { trigger: "axis", valueFormatter: (v) => `${v}%` },
    legend: { bottom: 0 },
    grid: { left: "3%", right: "4%", bottom: "12%", containLabel: true },
    xAxis: {
      type: "category",
      data: data.map((d) => d.date)
    },
    yAxis: { type: "value", axisLabel: { formatter: "{value}%" }, max: 100 },
    series: [
      { name: "次日留存", type: "line", data: data.map((d) => d.day1), smooth: true },
      { name: "3日留存", type: "line", data: data.map((d) => d.day3), smooth: true },
      { name: "7日留存", type: "line", data: data.map((d) => d.day7), smooth: true },
      { name: "14日留存", type: "line", data: data.map((d) => d.day14), smooth: true },
      { name: "30日留存", type: "line", data: data.map((d) => d.day30), smooth: true }
    ]
  }), [data, title])

  return <BaseChart option={option} className={className} />
}
