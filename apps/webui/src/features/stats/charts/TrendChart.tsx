/**
 * 趋势图组件——折线图/柱状图，支持时间范围缩放
 * @author AaronZZH & Kiro
 */

"use client"

import { useMemo } from "react"
import type { TrendPoint } from "@/lib/api/rest/dashboard/stats"
import { BaseChart, type EChartsOption } from "./BaseChart"

interface TrendChartProps {
  data: TrendPoint[]
  chartType?: "line" | "bar"
  title?: string
  className?: string
  /** 是否显示数据缩放 */
  dataZoom?: boolean
}

export function TrendChart({
  data,
  chartType = "line",
  title,
  className,
  dataZoom = true
}: TrendChartProps) {
  const option = useMemo<EChartsOption>(
    () => ({
      title: title ? { text: title, left: "center", textStyle: { fontSize: 14 } } : undefined,
      tooltip: {
        trigger: "axis",
        axisPointer: { type: chartType === "bar" ? "shadow" : "cross" }
      },
      grid: { left: "3%", right: "4%", bottom: dataZoom ? "15%" : "3%", containLabel: true },
      xAxis: {
        type: "category",
        data: data.map((p) => p.time),
        axisLabel: { rotate: data.length > 15 ? 45 : 0 }
      },
      yAxis: { type: "value" },
      series: [
        {
          type: chartType,
          data: data.map((p) => p.value),
          smooth: chartType === "line",
          areaStyle: chartType === "line" ? { opacity: 0.15 } : undefined
        }
      ],
      dataZoom: dataZoom ? [{ type: "slider", bottom: 0 }] : undefined
    }),
    [data, chartType, title, dataZoom]
  )

  return <BaseChart option={option} className={className} />
}
