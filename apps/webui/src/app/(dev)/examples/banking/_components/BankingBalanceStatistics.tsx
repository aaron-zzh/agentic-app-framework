/**
 * BankingBalanceStatistics——多系列折线图（周/月/年切换，带渐变填充）
 */

"use client"

import { useMemo, useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { BaseChart, type EChartsOption } from "@/features/stats/charts/BaseChart"

interface SeriesData {
  name: string
  categories: string[]
  data: { name: string; data: number[] }[]
}

interface BankingBalanceStatisticsProps {
  title?: string
  subheader?: string
  chart: { series: SeriesData[] }
}

const COLORS = ["#6366f1", "#f97316", "#3b82f6"]

export function BankingBalanceStatistics({
  title,
  subheader,
  chart
}: BankingBalanceStatisticsProps) {
  const [selected, setSelected] = useState(chart.series[chart.series.length - 1].name)
  const current = chart.series.find((s) => s.name === selected) ?? chart.series[0]

  const option = useMemo<EChartsOption>(
    () => ({
      tooltip: { trigger: "axis" },
      legend: { bottom: 0, data: current.data.map((d) => d.name) },
      grid: { left: "3%", right: "4%", bottom: "15%", containLabel: true },
      xAxis: {
        type: "category",
        data: current.categories,
        boundaryGap: false
      },
      yAxis: { type: "value" },
      series: current.data.map((d, i) => ({
        name: d.name,
        type: "line",
        data: d.data,
        smooth: true,
        showSymbol: false,
        lineStyle: { color: COLORS[i], width: 2 },
        itemStyle: { color: COLORS[i] },
        areaStyle: {
          color: {
            type: "linear",
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              {
                offset: 0,
                color:
                  COLORS[i].replace(")", ", 0.3)").replace("rgb", "rgba") +
                  (COLORS[i].startsWith("#") ? "4d" : "")
              },
              {
                offset: 1,
                color: COLORS[i].startsWith("#")
                  ? `${COLORS[i]}00`
                  : COLORS[i].replace(")", ", 0)").replace("rgb", "rgba")
              }
            ]
          }
        }
      }))
    }),
    [current]
  )

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between pb-2">
        <div>
          <CardTitle className="text-base">{title}</CardTitle>
          {subheader && <p className="text-muted-foreground text-sm">{subheader}</p>}
        </div>
        <Select value={selected} onValueChange={(v) => v && setSelected(v)}>
          <SelectTrigger className="h-8 w-28 text-xs">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {chart.series.map((s) => (
              <SelectItem key={s.name} value={s.name} className="text-xs">
                {s.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </CardHeader>
      <CardContent>
        {/* 图例 */}
        <div className="mb-4 flex gap-6 px-1">
          {current.data.map((d, i) => (
            <div key={d.name} className="flex items-center gap-1.5">
              <span className="h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: COLORS[i] }} />
              <span className="text-muted-foreground text-xs">{d.name}</span>
            </div>
          ))}
        </div>
        <BaseChart option={option} className="h-[320px] w-full" />
      </CardContent>
    </Card>
  )
}
