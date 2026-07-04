/**
 * /studio/me/credits——积分中心（迁移自 workspace/settings/credits）
 * Studio 风格：DataCapsule + GlassCard，不跳出外壳
 * @author AaronZZH & Kiro
 */

"use client"

import { format } from "date-fns"
import { zhCN } from "date-fns/locale"
import { RefreshCw } from "lucide-react"
import { Fragment, useEffect, useMemo, useState } from "react"
import { CreditRechargeDialog } from "@/components/common/CreditRechargeDialog"
import {
  DataCapsule,
  GlassCard,
  GlassCardBody,
  GlassCardHeader,
  GlassCardTitle,
  GlowButton,
  SectionHaze
} from "@/components/studio"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { RedeemCodeButton } from "@/features/billing/components/RedeemCodeButton"
import { ExpensesCategoryWidget } from "@/features/dashboard/widgets/ExpensesCategoryWidget"
import { BaseChart, type EChartsOption } from "@/features/stats/charts/BaseChart"
import { useDict } from "@/lib/hooks/use-dict"
import { useCreditBalance, useCreditGroups, useCreditTransactions } from "@/lib/queries/use-credits"
import { cn } from "@/lib/utils/cn"

const SOURCE_LABEL: Record<string, string> = {
  manual: "人工赠送",
  register_gift: "注册赠送",
  REGISTER_GIFT: "注册赠送",
  subscription_activate: "订阅激活",
  SUBSCRIPTION_ACTIVATE: "订阅激活",
  subscription_renew: "订阅续期",
  SUBSCRIPTION_RENEW: "订阅续期",
  weekly_reset: "每周刷新",
  WEEKLY_RESET: "每周刷新",
  invite_reward: "邀请奖励",
  INVITE_REWARD: "邀请奖励",
  topup: "充值",
  TOPUP: "充值",
  redeem: "兑换码",
  REDEEM: "兑换码",
  ai_call: "AI 调用",
  AI_CALL: "AI 调用",
  refund: "退款",
  REFUND: "退款",
  expire: "过期清零",
  EXPIRE: "过期清零"
}

type TabValue = "all" | "spend" | "earn"

export default function StudioMeCreditsPage() {
  const [mounted, setMounted] = useState(false)
  useEffect(() => setMounted(true), [])
  const [rechargeOpen, setRechargeOpen] = useState(false)

  const {
    data: balance,
    isLoading: balLoading,
    refetch: refetchBalance,
    isFetching: balFetching
  } = useCreditBalance()
  const {
    data: groups,
    isLoading: grpLoading,
    refetch: refetchGroups,
    isFetching: grpFetching
  } = useCreditGroups()
  const [refreshing, setRefreshing] = useState(false)
  const [tab, setTab] = useState<TabValue>("all")
  const [page, setPage] = useState(0)
  const { data: txPage, isLoading: txLoading } = useCreditTransactions(page)
  // 拉取更多流水用于图表聚合（100 条，不影响分页流水）
  const { data: chartTxPage } = useCreditTransactions(0, 100)
  const { getLabel: getTypeLabel, getColor: getTypeColor } = useDict("credit_transaction_type")
  const { getLabel: getSourceLabel } = useDict("credit_transaction_source")

  // 手动刷新余额——兜底移动端刷新页面/切后台等场景下数据未自动更新
  const handleRefreshBalance = async () => {
    setRefreshing(true)
    await Promise.all([refetchBalance(), refetchGroups()])
    setRefreshing(false)
  }

  // 近 14 天消耗趋势（从 chartTxPage 聚合）
  const trendOption = useMemo<EChartsOption>(() => {
    const spendTxs = (chartTxPage?.list ?? []).filter((tx) => tx.type === "SPEND")
    const dayMap = new Map<string, number>()
    for (const tx of spendTxs) {
      const day = tx.createTime.slice(0, 10)
      dayMap.set(day, (dayMap.get(day) ?? 0) + Math.abs(tx.amount))
    }
    const days = Array.from({ length: 14 }, (_, i) => {
      const d = new Date()
      d.setDate(d.getDate() - 13 + i)
      return d.toISOString().slice(0, 10)
    })
    return {
      tooltip: {
        trigger: "axis",
        formatter: (p: unknown) => {
          const params = p as { name: string; value: number }[]
          return `${params[0].name}<br/>消耗 ${params[0].value} 积分`
        }
      },
      grid: { left: 40, right: 8, top: 8, bottom: 24 },
      xAxis: { type: "category", data: days.map((d) => d.slice(5)), axisLabel: { fontSize: 10 } },
      yAxis: {
        type: "value",
        axisLabel: { fontSize: 10 },
        splitLine: { lineStyle: { opacity: 0.2 } }
      },
      series: [
        {
          type: "line",
          smooth: true,
          symbol: "none",
          data: days.map((d) => dayMap.get(d) ?? 0),
          areaStyle: { opacity: 0.15 },
          lineStyle: { color: "#6366f1", width: 2 },
          itemStyle: { color: "#6366f1" }
        }
      ]
    }
  }, [chartTxPage])

  // 消费分类（bizType 聚合）
  const spendCategories = useMemo(() => {
    const catMap = new Map<string, number>()
    for (const tx of (chartTxPage?.list ?? []).filter((t) => t.type === "SPEND")) {
      const key = tx.category ?? "other"
      catMap.set(key, (catMap.get(key) ?? 0) + Math.abs(tx.amount))
    }
    return Array.from(catMap.entries())
      .map(([biz_type, total]) => ({ biz_type, total }))
      .sort((a, b) => b.total - a.total)
  }, [chartTxPage])

  const transactions = txPage?.list ?? []
  const filtered = transactions.filter((tx) => {
    if (tab === "earn") return tx.type === "EARN"
    if (tab === "spend") return tx.type === "SPEND"
    return true
  })

  // 按 source + type + 日期 聚合，合并多条同类流水
  const aggregated = (() => {
    const map = new Map<string, { tx: (typeof filtered)[0]; count: number; total: number }>()
    for (const tx of filtered) {
      const day = tx.createTime.slice(0, 10)
      const isChat = (tx.category ?? "").toLowerCase() === "chat"
      const key = isChat ? `chat|SPEND|${day}` : tx.id
      const existing = map.get(key)
      if (existing) {
        existing.count += 1
        existing.total += tx.amount
      } else {
        map.set(key, { tx, count: 1, total: tx.amount })
      }
    }
    return Array.from(map.values())
  })()

  const totalBalance = mounted ? (groups ?? []).reduce((s, g) => s + g.remain, 0) : 0
  const frozen = mounted ? (balance?.frozen ?? 0) : 0
  const totalEarned = mounted ? (balance?.totalEarned ?? 0) : 0
  const totalSpent = mounted ? (balance?.totalSpent ?? 0) : 0

  return (
    <div className="relative mx-auto max-w-6xl p-6">
      <SectionHaze variant="soft" />
      <div className="relative space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="font-semibold text-xl">积分中心</h1>
          <RedeemCodeButton />
        </div>

        {/* 数据胶囊 */}
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <DataCapsule
            label="可用余额"
            value={mounted ? totalBalance.toLocaleString() : "—"}
            loading={grpLoading}
            tone="amber"
            action={
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  onClick={handleRefreshBalance}
                  disabled={refreshing || balFetching || grpFetching}
                  aria-label="刷新积分余额"
                  className="rounded p-1 text-muted-foreground transition-colors hover:bg-foreground/[0.06] hover:text-foreground disabled:opacity-50"
                >
                  <RefreshCw
                    className={cn(
                      "size-3.5",
                      (refreshing || balFetching || grpFetching) && "animate-spin"
                    )}
                  />
                </button>
                <GlowButton tone="violet" size="sm" onClick={() => setRechargeOpen(true)}>
                  充值
                </GlowButton>
              </div>
            }
          />
          <DataCapsule
            label="冻结积分"
            value={mounted ? frozen.toLocaleString() : "—"}
            loading={balLoading}
            tone="default"
          />
          <DataCapsule
            label="总获取"
            value={mounted ? totalEarned.toLocaleString() : "—"}
            loading={balLoading}
            tone="emerald"
          />
          <DataCapsule
            label="总消耗"
            value={mounted ? totalSpent.toLocaleString() : "—"}
            loading={balLoading}
            tone="violet"
          />
        </div>

        {/* 消耗趋势 + 分类 */}
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <GlassCard glow="none">
            <GlassCardHeader>
              <GlassCardTitle>近 14 天消耗趋势</GlassCardTitle>
            </GlassCardHeader>
            <GlassCardBody>
              <BaseChart option={trendOption} className="h-44 w-full" />
            </GlassCardBody>
          </GlassCard>
          <ExpensesCategoryWidget categories={spendCategories} />
        </div>

        {/* 流水 */}
        <GlassCard glow="none">
          <GlassCardHeader>
            <GlassCardTitle>积分流水</GlassCardTitle>
          </GlassCardHeader>
          <GlassCardBody className="space-y-4">
            <Tabs value={tab} onValueChange={(v) => setTab(v as TabValue)}>
              <TabsList className="w-full">
                <TabsTrigger value="all" className="flex-1">
                  全部
                </TabsTrigger>
                <TabsTrigger value="spend" className="flex-1">
                  消耗
                </TabsTrigger>
                <TabsTrigger value="earn" className="flex-1">
                  获得
                </TabsTrigger>
              </TabsList>
            </Tabs>

            {txLoading ? (
              <div className="space-y-3">
                {Array.from({ length: 4 }).map((_, i) => (
                  <Skeleton key={`tx-${i}`} className="h-12" />
                ))}
              </div>
            ) : aggregated.length === 0 ? (
              <p className="py-8 text-center text-muted-foreground text-sm">暂无记录</p>
            ) : (
              aggregated.map(({ tx, count, total }) => {
                const isEarn = tx.type === "EARN"
                const typeLabel = getTypeLabel(tx.type) || tx.type
                const typeColor = getTypeColor(tx.type)
                const isChat = (tx.category ?? "").toLowerCase() === "chat"
                const sourceName = isChat
                  ? "AI 对话"
                  : tx.remark ||
                    getSourceLabel(tx.source) ||
                    SOURCE_LABEL[tx.source] ||
                    SOURCE_LABEL[tx.source?.toLowerCase()] ||
                    "积分变动"
                return (
                  <Fragment key={`${isChat ? `chat|${tx.createTime.slice(0, 10)}` : tx.id}`}>
                    <div className="flex items-center justify-between py-3">
                      <div className="flex-1">
                        <div className="flex items-center gap-2">
                          <p className="font-medium text-sm">
                            {sourceName}
                            {count > 1 && (
                              <span className="ml-1 text-muted-foreground text-xs">× {count}</span>
                            )}
                          </p>
                          <span
                            className={cn(
                              "rounded px-1.5 py-0.5 text-xs",
                              typeColor === "success" && "bg-emerald-500/10 text-emerald-400",
                              typeColor === "danger" && "bg-orange-500/10 text-orange-400",
                              !["success", "danger"].includes(typeColor) &&
                                "bg-muted text-muted-foreground"
                            )}
                          >
                            {typeLabel}
                          </span>
                        </div>
                        <p className="mt-0.5 text-muted-foreground text-xs">
                          {format(new Date(tx.createTime), "yyyy年MM月dd日 HH:mm", {
                            locale: zhCN
                          })}
                        </p>
                      </div>
                      <p
                        className={cn(
                          "font-semibold tabular-nums",
                          isEarn ? "text-emerald-400" : "text-orange-400"
                        )}
                      >
                        {isEarn ? `+${total}` : `-${Math.abs(total)}`}
                      </p>
                    </div>
                    <Separator className="opacity-40" />
                  </Fragment>
                )
              })
            )}

            {(txPage?.total ?? 0) > 20 && (
              <div className="flex justify-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage((p) => p - 1)}
                >
                  上一页
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={(page + 1) * 20 >= (txPage?.total ?? 0)}
                  onClick={() => setPage((p) => p + 1)}
                >
                  下一页
                </Button>
              </div>
            )}
          </GlassCardBody>
        </GlassCard>
      </div>
      <CreditRechargeDialog open={rechargeOpen} onOpenChange={setRechargeOpen} />
    </div>
  )
}
