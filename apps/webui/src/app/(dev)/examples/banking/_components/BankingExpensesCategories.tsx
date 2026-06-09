/**
 * BankingExpensesCategories——支出分类极坐标饼图
 */

"use client"

import {
  Coffee,
  Dumbbell,
  Fuel,
  Gamepad2,
  HeartPulse,
  ShoppingCart,
  Smartphone,
  Utensils
} from "lucide-react"
import { useMemo } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { BaseChart, type EChartsOption } from "@/features/stats/charts/BaseChart"

interface CategoryItem {
  label: string
  value: number
  icon: string
}

interface BankingExpensesCategoriesProps {
  title?: string
  chart: { series: CategoryItem[] }
}

const COLORS = [
  "#6366f1",
  "#ef4444",
  "#3b82f6",
  "#f97316",
  "#0ea5e9",
  "#22c55e",
  "#8b5cf6",
  "#eab308"
]

const ICON_MAP: Record<string, React.ReactNode> = {
  "gamepad-2": <Gamepad2 className="h-4 w-4" />,
  fuel: <Fuel className="h-4 w-4" />,
  utensils: <Utensils className="h-4 w-4" />,
  coffee: <Coffee className="h-4 w-4" />,
  smartphone: <Smartphone className="h-4 w-4" />,
  "heart-pulse": <HeartPulse className="h-4 w-4" />,
  dumbbell: <Dumbbell className="h-4 w-4" />,
  "shopping-cart": <ShoppingCart className="h-4 w-4" />
}

export function BankingExpensesCategories({ title, chart }: BankingExpensesCategoriesProps) {
  const option = useMemo<EChartsOption>(
    () => ({
      tooltip: { trigger: "item", formatter: "{b}: {c}%" },
      series: [
        {
          type: "pie",
          radius: ["30%", "70%"],
          center: ["50%", "50%"],
          roseType: "area",
          itemStyle: { borderRadius: 4 },
          label: { show: false },
          data: chart.series.map((item, i) => ({
            name: item.label,
            value: item.value,
            itemStyle: { color: COLORS[i % COLORS.length] }
          }))
        }
      ]
    }),
    [chart.series]
  )

  const total = chart.series.reduce((s, i) => s + i.value, 0)

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex flex-col items-center gap-6 md:flex-row">
          <BaseChart option={option} className="h-60 w-60 shrink-0" />
          <div className="grid flex-1 grid-cols-2 gap-3">
            {chart.series.map((item, i) => (
              <div key={item.label} className="flex items-center gap-2">
                <span
                  className="flex h-7 w-7 items-center justify-center rounded-full"
                  style={{
                    backgroundColor: `${COLORS[i % COLORS.length]}20`,
                    color: COLORS[i % COLORS.length]
                  }}
                >
                  {ICON_MAP[item.icon]}
                </span>
                <div className="min-w-0">
                  <p className="truncate font-medium text-sm">{item.label}</p>
                  <p className="text-muted-foreground text-xs">${item.value}</p>
                </div>
                <span
                  className="ml-auto h-2 w-2 shrink-0 rounded-full"
                  style={{ backgroundColor: COLORS[i % COLORS.length] }}
                />
              </div>
            ))}
          </div>
        </div>

        <Separator className="my-4 border-dashed" />

        <div className="grid grid-cols-2 text-center">
          <div className="py-2">
            <p className="text-muted-foreground text-xs">Categories</p>
            <p className="font-bold text-xl">{chart.series.length}</p>
          </div>
          <div className="border-l py-2">
            <p className="text-muted-foreground text-xs">Total spent</p>
            <p className="font-bold text-xl">${total.toLocaleString()}</p>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
