/**
 * ECharts 基础包装组件——响应式容器 + 自动 resize
 * @author AaronZZH & Kiro
 */

"use client"

import type { EChartsOption } from "echarts"
import { BarChart, FunnelChart, GaugeChart, LineChart, PieChart } from "echarts/charts"
import {
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  TitleComponent,
  ToolboxComponent,
  TooltipComponent
} from "echarts/components"
import * as echarts from "echarts/core"
import { CanvasRenderer } from "echarts/renderers"
import { useEffect, useRef } from "react"

/** 注册 ECharts 模块（按需引入，减小 bundle） */
echarts.use([
  BarChart,
  LineChart,
  PieChart,
  FunnelChart,
  GaugeChart,
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  DataZoomComponent,
  ToolboxComponent,
  CanvasRenderer
])

export type { EChartsOption }

interface BaseChartProps {
  option: EChartsOption
  className?: string
  /** 主题：light | dark */
  theme?: string
}

/**
 * ECharts 基础渲染组件
 * - 自动监听容器尺寸变化（ResizeObserver）
 * - option 变更时增量更新
 */
export function BaseChart({ option, className, theme }: BaseChartProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<echarts.ECharts | null>(null)

  useEffect(() => {
    if (!containerRef.current) return

    const chart = echarts.init(containerRef.current, theme)
    chartRef.current = chart

    const ro = new ResizeObserver(() => {
      chart.resize()
    })
    ro.observe(containerRef.current)

    return () => {
      ro.disconnect()
      chart.dispose()
      chartRef.current = null
    }
  }, [theme])

  useEffect(() => {
    if (chartRef.current) {
      chartRef.current.setOption(option, { notMerge: true })
    }
  }, [option])

  return <div ref={containerRef} className={className ?? "h-full min-h-[200px] w-full"} />
}
