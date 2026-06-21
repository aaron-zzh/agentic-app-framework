/**
 * FinanceOverviewWidget——积分总览（接 useWidgetData 真实数据）。
 *
 * <p>展示当前积分余额 + 30 天获取/消耗 tabs + 渐变折线图。
 * 操作按钮替换为：兑换码 / 充值 / 邀请赚积分。
 *
 * @author AaronZZH &amp; Kiro
 */

"use client"

import { Gift, Info, Plus, Ticket, TrendingDown, TrendingUp } from "lucide-react"
import Link from "next/link"
import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { RedeemCodeButton } from "@/features/billing/components/RedeemCodeButton"
import { BaseChart, type EChartsOption } from "@/features/stats/charts/BaseChart"
import { cn } from "@/lib/utils/cn"
import { WIDGET_CARD_CLASS } from "./_shared/styles"

export interface BillingTrendPoint {
  time: string
  value: number
}

export interface BillingOverviewData {
  /** 当前总积分（balance + frozen） */
  balance: number
  /** 30 天累计获取 */
  monthEarn: number
  /** 30 天累计消耗 */
  monthSpend: number
  /** 30 天每日获取时序 */
  earnTrend: BillingTrendPoint[]
  /** 30 天每日消耗时序 */
  spendTrend: BillingTrendPoint[]
}

interface FinanceOverviewWidgetProps {
  data?: BillingOverviewData
}

const EMPTY_DATA: BillingOverviewData = {
  balance: 0,
  monthEarn: 0,
  monthSpend: 0,
  earnTrend: [],
  spendTrend: []
}

function formatCredit(value: number): string {
  return `${new Intl.NumberFormat("zh-CN").format(value)} 积分`
}

function GradientLineChart({ data, color }: { data: BillingTrendPoint[]; color: string }) {
  const option: EChartsOption = {
    tooltip: { trigger: "axis", axisPointer: { type: "cross" } },
    grid: { left: "3%", right: "4%", bottom: "3%", containLabel: true },
    xAxis: { type: "category", data: data.map((p) => p.time), boundaryGap: false },
    yAxis: { type: "value" },
    series: [
      {
        type: "line",
        data: data.map((p) => p.value),
        smooth: true,
        showSymbol: false,
        areaStyle: {
          color: {
            type: "linear",
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: `${color}66` },
              { offset: 1, color: `${color}00` }
            ]
          }
        },
        lineStyle: { color, width: 2 },
        itemStyle: { color }
      }
    ]
  }
  return <BaseChart option={option} className="h-[220px] w-full" />
}

export function FinanceOverviewWidget({ data }: FinanceOverviewWidgetProps) {
  const [activeTab, setActiveTab] = useState<"earn" | "spend">("earn")
  const d = data ?? EMPTY_DATA
  const earnPct =
    d.monthEarn > 0 && d.monthSpend > 0
      ? Number((((d.monthEarn - d.monthSpend) / d.monthSpend) * 100).toFixed(1))
      : 0
  const spendPct =
    d.monthEarn > 0 && d.monthSpend > 0
      ? Number((((d.monthSpend - d.monthEarn) / d.monthEarn) * 100).toFixed(1))
      : 0

  const tabs = [
    {
      value: "earn" as const,
      label: "本月获取",
      icon: TrendingUp,
      iconBg: "bg-emerald-100 text-emerald-700",
      total: d.monthEarn,
      percent: earnPct,
      data: d.earnTrend,
      color: "#059669"
    },
    {
      value: "spend" as const,
      label: "本月消耗",
      icon: TrendingDown,
      iconBg: "bg-orange-100 text-orange-700",
      total: d.monthSpend,
      percent: spendPct,
      data: d.spendTrend,
      color: "#ea580c"
    }
  ]

  return (
    <Card className={cn(WIDGET_CARD_CLASS)}>
      <CardContent className="p-6">
        <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="mb-1 flex items-center gap-1 text-muted-foreground text-sm">
              当前积分余额
              <Info className="h-3.5 w-3.5" />
            </div>
            <p className="font-bold text-3xl">{formatCredit(d.balance)}</p>
          </div>
          <div className="flex flex-wrap gap-3">
            <RedeemCodeButton
              trigger={
                <Button variant="secondary" className="gap-1.5">
                  <Ticket className="h-4 w-4" /> 兑换码
                </Button>
              }
            />
            <Button
              variant="secondary"
              className="gap-1.5"
              nativeButton={false}
              render={<Link href="/settings/pricing" />}
            >
              <Plus className="h-4 w-4" /> 充值
            </Button>
            <Button
              variant="secondary"
              className="gap-1.5"
              nativeButton={false}
              render={<Link href="/settings/invite" />}
            >
              <Gift className="h-4 w-4" /> 邀请
            </Button>
          </div>
        </div>

        <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as "earn" | "spend")}>
          <TabsList className="mb-6 h-20! w-full rounded-2xl p-1.5">
            {tabs.map((tab) => {
              const Icon = tab.icon
              return (
                <TabsTrigger key={tab.value} value={tab.value} className="flex-1 rounded-xl">
                  <div className="flex w-full items-center gap-3 text-left">
                    <div
                      className={cn(
                        "hidden h-9 w-9 shrink-0 items-center justify-center rounded-full sm:flex",
                        tab.iconBg
                      )}
                    >
                      <Icon className="h-4 w-4" />
                    </div>
                    <div>
                      <p className="mb-0.5 text-muted-foreground text-xs">{tab.label}</p>
                      <p className="font-bold text-xl">{formatCredit(tab.total)}</p>
                    </div>
                    {tab.percent !== 0 && (
                      <Badge
                        variant={tab.percent >= 0 ? "default" : "destructive"}
                        className="ml-auto text-xs"
                      >
                        {tab.percent > 0 ? "+" : ""}
                        {tab.percent}%
                      </Badge>
                    )}
                  </div>
                </TabsTrigger>
              )
            })}
          </TabsList>
          {tabs.map((tab) => (
            <TabsContent key={tab.value} value={tab.value}>
              <GradientLineChart data={tab.data} color={tab.color} />
            </TabsContent>
          ))}
        </Tabs>
      </CardContent>
    </Card>
  )
}
