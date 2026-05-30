/**
 * recharts 类型声明存根
 * recharts 尚未安装，此文件提供最小类型声明避免编译错误
 */
declare module "recharts" {
  import type { ComponentType, ReactNode } from "react"

  interface ChartProps {
    data?: Record<string, unknown>[]
    width?: number
    height?: number
    children?: ReactNode
    className?: string
  }

  export const BarChart: ComponentType<ChartProps>
  export const LineChart: ComponentType<ChartProps>
  export const AreaChart: ComponentType<ChartProps>
  export const PieChart: ComponentType<ChartProps>
  export const ResponsiveContainer: ComponentType<{ width?: string | number; height?: string | number; children?: ReactNode }>
  export const Bar: ComponentType<{ dataKey?: string; fill?: string; name?: string; type?: string; [key: string]: unknown }>
  export const Line: ComponentType<{ dataKey?: string; stroke?: string; name?: string; type?: string; [key: string]: unknown }>
  export const Area: ComponentType<{ dataKey?: string; fill?: string; stroke?: string; name?: string; type?: string; [key: string]: unknown }>
  export const XAxis: ComponentType<{ dataKey?: string; [key: string]: unknown }>
  export const YAxis: ComponentType<{ [key: string]: unknown }>
  export const CartesianGrid: ComponentType<{ strokeDasharray?: string; [key: string]: unknown }>
  export const Tooltip: ComponentType<{ [key: string]: unknown }>
  export const Legend: ComponentType<{ [key: string]: unknown }>
  export const Cell: ComponentType<{ fill?: string; [key: string]: unknown }>
  export const Pie: ComponentType<{ data?: Record<string, unknown>[]; dataKey?: string; cx?: string; cy?: string; outerRadius?: number; fill?: string; label?: boolean; children?: ReactNode; [key: string]: unknown }>
}
