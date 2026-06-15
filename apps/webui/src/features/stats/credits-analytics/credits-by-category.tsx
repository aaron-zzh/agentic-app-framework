/**
 * 积分消耗分布图——按服务/部门两维度切换的环形饼图
 * @author AaronZZH
 */

"use client"

import { useMemo, useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { BaseChart, type EChartsOption } from "@/features/stats/charts/BaseChart"
import { MOCK_BY_DEPT, MOCK_BY_SERVICE } from "./_mock"

const COLORS = ["#3b82f6", "#f59e0b", "#10b981", "#8b5cf6", "#ef4444", "#06b6d4"]

export function CreditsByCategory() {
  const [dimension, setDimension] = useState<"service" | "dept">("service")

  const data = dimension === "service" ? MOCK_BY_SERVICE : MOCK_BY_DEPT
  const total = data.reduce((sum, d) => sum + d.value, 0)

  const option = useMemo<EChartsOption>(
    () => ({
      tooltip: { trigger: "item", formatter: "{b}: {c} 积分 ({d}%)" },
      legend: { orient: "vertical", left: "left", top: "middle", textStyle: { fontSize: 12 } },
      series: [
        {
          type: "pie",
          radius: ["45%", "70%"],
          center: ["62%", "50%"],
          data: data.map((d, i) => ({ ...d, itemStyle: { color: COLORS[i % COLORS.length] } })),
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
      <CardHeader className="flex-row items-center justify-between space-y-0 pb-4">
        <div>
          <CardTitle>消耗分布</CardTitle>
          <p className="mt-1 text-muted-foreground text-sm">
            合计 <span className="font-semibold text-foreground">{total.toLocaleString()}</span>{" "}
            积分
          </p>
        </div>
        <Tabs value={dimension} onValueChange={(v) => setDimension(v as typeof dimension)}>
          <TabsList className="h-7">
            <TabsTrigger value="service" className="h-6 px-2 text-xs">
              按服务
            </TabsTrigger>
            <TabsTrigger value="dept" className="h-6 px-2 text-xs">
              按部门
            </TabsTrigger>
          </TabsList>
        </Tabs>
      </CardHeader>
      <CardContent>
        <BaseChart option={option} className="h-[260px] w-full" />
      </CardContent>
    </Card>
  )
}
