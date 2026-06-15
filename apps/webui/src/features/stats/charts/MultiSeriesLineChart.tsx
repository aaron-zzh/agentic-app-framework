/**
 * 多系列折线图——多条折线对比，支持面积填充
 * @author AaronZZH
 */

"use client"

import { useMemo } from "react"
import { BaseChart, type EChartsOption } from "./BaseChart"

export interface LineSeriesItem {
  name: string
  data: number[]
  /** 是否显示面积填充 */
  area?: boolean
}

interface MultiSeriesLineChartProps {
  /** X 轴分类 */
  categories: string[]
  series: LineSeriesItem[]
  title?: string
  /** Y 轴名称 */
  yAxisName?: string
  className?: string
  /** 是否显示数据缩放滑块 */
  dataZoom?: boolean
}

export function MultiSeriesLineChart({
  categories,
  series,
  title,
  yAxisName,
  className,
  dataZoom = false
}: MultiSeriesLineChartProps) {
  const option = useMemo<EChartsOption>(
    () => ({
      ...(title ? { title: { text: title, left: "center", textStyle: { fontSize: 14 } } } : {}),
      tooltip: { trigger: "axis" },
      legend: { bottom: 0, data: series.map((s) => s.name) },
      grid: {
        left: "3%",
        right: "4%",
        bottom: series.length > 1 || dataZoom ? "15%" : "3%",
        containLabel: true
      },
      xAxis: {
        type: "category",
        data: categories,
        axisLabel: { rotate: categories.length > 12 ? 45 : 0 }
      },
      yAxis: { type: "value", name: yAxisName },
      series: series.map((s) => ({
        name: s.name,
        type: "line",
        data: s.data,
        smooth: true,
        showSymbol: false,
        areaStyle: s.area ? { opacity: 0.12 } : undefined
      })),
      ...(dataZoom ? { dataZoom: [{ type: "slider", bottom: 0, height: 20 }] } : {})
    }),
    [categories, series, title, yAxisName, dataZoom]
  )

  return <BaseChart option={option} className={className ?? "h-full min-h-[200px] w-full"} />
}
