/**
 * 饼图组件——分布占比展示
 * @author AaronZZH & Kiro
 */

"use client"

import { PieChart as PieChartIcon } from "lucide-react"
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

  if (!data.length) {
    return (
      <div
        className={`flex flex-col items-center justify-center gap-2 text-muted-foreground ${className ?? "h-full min-h-[200px] w-full"}`}
      >
        <PieChartIcon className="h-10 w-10 opacity-20" />
        <span className="text-sm">暂无数据</span>
      </div>
    )
  }

  return <BaseChart option={option} className={className} />
}
