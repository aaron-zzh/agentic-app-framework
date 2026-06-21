/**
 * MultiSeriesChartWidget——多系列折线图（周期切换，支持渐变填充）
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
import { cn } from "@/lib/utils/cn"
import { WIDGET_CARD_CLASS } from "./_shared/styles"

export interface MultiSeriesData {
  name: string
  categories: string[]
  data: { name: string; data: number[] }[]
}

/** 后端 billing-multi-series 返回结构 */
export interface BillingTrendPoint {
  time: string
  value: number
}

export interface BillingMultiSeriesData {
  earn: BillingTrendPoint[]
  spend: BillingTrendPoint[]
}

interface MultiSeriesChartWidgetProps {
  title?: string
  subheader?: string
  chart?: { series: MultiSeriesData[] }
  /** billing 数据；提供时自动渲染为「获取 vs 消耗」双系列 */
  billing?: BillingMultiSeriesData
}

const COLORS = ["#6366f1", "#f97316", "#3b82f6"]
const BILLING_COLORS = ["#10b981", "#f97316"] // 绿色获取 / 橙色消耗

/** 把 billing 数据归一为内部 MultiSeriesData。 */
function billingToSeries(b: BillingMultiSeriesData): MultiSeriesData {
  const categories = b.earn.map((p) => p.time)
  return {
    name: "近 30 天",
    categories,
    data: [
      { name: "获取", data: b.earn.map((p) => Number(p.value) || 0) },
      { name: "消耗", data: b.spend.map((p) => Number(p.value) || 0) }
    ]
  }
}

export function MultiSeriesChartWidget({
  title,
  subheader,
  chart,
  billing
}: MultiSeriesChartWidgetProps) {
  // 优先 billing；否则用 chart.series；都没有则空
  const series = billing ? [billingToSeries(billing)] : (chart?.series ?? [])
  const palette = billing ? BILLING_COLORS : COLORS

  const [selected, setSelected] = useState(series[series.length - 1]?.name ?? "")
  const current = series.find((s) => s.name === selected) ?? series[0]

  const option = useMemo<EChartsOption>(
    () => ({
      tooltip: { trigger: "axis" },
      legend: { bottom: 0, data: current?.data.map((d) => d.name) ?? [] },
      grid: { left: "3%", right: "4%", bottom: "15%", containLabel: true },
      xAxis: { type: "category", data: current?.categories ?? [], boundaryGap: false },
      yAxis: { type: "value" },
      series:
        current?.data.map((d, i) => ({
          name: d.name,
          type: "line",
          data: d.data,
          smooth: true,
          showSymbol: false,
          lineStyle: { color: palette[i % palette.length], width: 2 },
          itemStyle: { color: palette[i % palette.length] },
          areaStyle: {
            color: {
              type: "linear",
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: `${palette[i % palette.length]}4d` },
                { offset: 1, color: `${palette[i % palette.length]}00` }
              ]
            }
          }
        })) ?? []
    }),
    [current, palette]
  )

  if (!current) {
    return (
      <Card className={cn(WIDGET_CARD_CLASS)}>
        <CardHeader>
          <CardTitle className="text-base">{title}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex h-32 items-center justify-center text-muted-foreground text-sm">
            暂无数据
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className={cn(WIDGET_CARD_CLASS)}>
      <CardHeader className="flex flex-row items-start justify-between pb-2">
        <div>
          <CardTitle className="text-base">{title}</CardTitle>
          {subheader && <p className="text-muted-foreground text-sm">{subheader}</p>}
        </div>
        {series.length > 1 && (
          <Select value={selected} onValueChange={(v) => v && setSelected(v)}>
            <SelectTrigger className="h-8 w-28 text-xs">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {series.map((s) => (
                <SelectItem key={s.name} value={s.name} className="text-xs">
                  {s.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      </CardHeader>
      <CardContent>
        <div className="mb-4 flex gap-6 px-1">
          {current.data.map((d, i) => (
            <div key={d.name} className="flex items-center gap-1.5">
              <span
                className="h-2.5 w-2.5 rounded-sm"
                style={{ backgroundColor: palette[i % palette.length] }}
              />
              <span className="text-muted-foreground text-xs">{d.name}</span>
            </div>
          ))}
        </div>
        <BaseChart option={option} className="h-[240px] w-full" />
      </CardContent>
    </Card>
  )
}
