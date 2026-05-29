/**
 * 饼图组件——分布占比展示
 * @author AaronZZH & Kiro
 */

"use client"

import { useMemo } from "react"
import { BaseChart, type EChartsOption } from "./BaseChart"

interface PieDataItem {
  name: string
  value: number
}

interface PieChartProps {
  data: PieDataItem[]
  title?: string
  className?: string
  /** 是否环形图 */
  donut?: boolean
}

export function PieChart({ data, title, className, donut = false }: PieChartProps) {
  const option = useMemo<EChartsOption>(
    () => ({
      title: title ? { text: title, left: "center", textStyle: { fontSize: 14 } } : undefined,
      tooltip: { trigger: "item", formatter: "{b}: {c} ({d}%)" },
      legend: { orient: "vertical", left: "left", top: "middle" },
      series: [
        {
          type: "pie",
          radius: donut ? ["40%", "70%"] : "70%",
          center: ["60%", "50%"],
          data,
          emphasis: {
            itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: "rgba(0,0,0,0.2)" }
          }
        }
      ]
    }),
    [data, title, donut]
  )

  return <BaseChart option={option} className={className} />
}
