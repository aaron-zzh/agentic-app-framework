/**
 * Sparkline——纯 SVG 迷你折线图，用于 counter / kpi 卡片右下角的趋势走势。
 *
 * <p>无依赖、零运行时开销（生成静态 SVG path）。后端 widget data 没有 sparkline
 * 字段时不渲染，避免空区域。
 *
 * @author AaronZZH &amp; Kiro
 */

"use client"

interface SparklineProps {
  /** 数值序列；少于 2 个点不渲染 */
  data?: number[] | null
  /** 描边色 CSS 值（如 "rgb(5 150 105)"） */
  color: string
  /** 视图宽（px），默认 88 */
  width?: number
  /** 视图高（px），默认 32 */
  height?: number
  /** 描边粗细（px），默认 2 */
  strokeWidth?: number
}

export function Sparkline({
  data,
  color,
  width = 88,
  height = 32,
  strokeWidth = 2
}: SparklineProps) {
  if (!data || data.length < 2) return null

  const max = Math.max(...data)
  const min = Math.min(...data)
  const range = max - min || 1
  const stepX = width / (data.length - 1)

  const points = data
    .map((v, i) => {
      const x = i * stepX
      const y = height - ((v - min) / range) * height
      return `${x.toFixed(2)},${y.toFixed(2)}`
    })
    .join(" ")

  return (
    <svg
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      className="shrink-0 overflow-visible"
      aria-hidden="true"
    >
      <polyline
        points={points}
        fill="none"
        stroke={color}
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
