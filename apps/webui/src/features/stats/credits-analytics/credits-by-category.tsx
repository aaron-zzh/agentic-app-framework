/**
 * 积分消耗分布图——按分类的环形饼图
 * 数据来源：GET /api/stats/credits/by-category
 * @author AaronZZH
 */

"use client"

import { useMemo } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { BaseChart, type EChartsOption } from "@/features/stats/charts/BaseChart"
import { useCreditsByCategory } from "@/lib/queries/use-credits-analytics"

const COLORS = ["#3b82f6", "#f59e0b", "#10b981", "#8b5cf6", "#ef4444", "#06b6d4"]

export function CreditsByCategory() {
  const { data, isLoading } = useCreditsByCategory()

  const option = useMemo<EChartsOption>(
    () => ({
      tooltip: { trigger: "item", formatter: "{b}: {c} 积分 ({d}%)" },
      legend: { orient: "vertical", left: "left", top: "middle", textStyle: { fontSize: 12 } },
      series: [
        {
          type: "pie",
          radius: ["45%", "70%"],
          center: ["62%", "50%"],
          data: (data?.items ?? []).map((d, i) => ({
            ...d,
            itemStyle: { color: COLORS[i % COLORS.length] }
          })),
          label: { show: false },
          emphasis: {
            itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: "rgba(0,0,0,0.2)" }
          }
        }
      ]
    }),
    [data]
  )

  return (
    <Card>
      <CardHeader className="pb-4">
        <CardTitle>消耗分布</CardTitle>
        <p className="mt-1 text-muted-foreground text-sm">
          合计{" "}
          <span className="font-semibold text-foreground">
            {(data?.total ?? 0).toLocaleString()}
          </span>{" "}
          积分
        </p>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <Skeleton className="h-[260px] w-full" />
        ) : (
          <BaseChart option={option} className="h-[260px] w-full" />
        )}
      </CardContent>
    </Card>
  )
}
