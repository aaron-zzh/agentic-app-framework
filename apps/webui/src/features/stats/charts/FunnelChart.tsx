/**
 * 漏斗图组件——行为分析漏斗
 * @author AaronZZH & Kiro
 */

"use client"

import { useMemo } from "react"
import { BaseChart, type EChartsOption } from "./BaseChart"
import type { FunnelStage } from "@/lib/api/stats"

interface FunnelChartProps {
  data: FunnelStage[]
  title?: string
  className?: string
}

export function FunnelChart({ data, title, className }: FunnelChartProps) {
  const option = useMemo<EChartsOption>(() => ({
    title: title ? { text: title, left: "center", textStyle: { fontSize: 14 } } : undefined,
    tooltip: { trigger: "item", formatter: "{b}: {c} ({d}%)" },
    series: [{
      type: "funnel",
      left: "10%",
      width: "80%",
      top: title ? 40 : 10,
      bottom: 10,
      sort: "descending",
      gap: 2,
      label: { show: true, position: "inside", formatter: "{b}\n{c}" },
      data: data.map((s) => ({ name: s.name, value: s.value }))
    }]
  }), [data, title])

  return <BaseChart option={option} className={className} />
}
