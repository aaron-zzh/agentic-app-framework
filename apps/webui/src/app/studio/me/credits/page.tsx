/**
 * /studio/me/credits——积分中心（迁移自 workspace/settings/credits）
 * Studio 风格：DataCapsule + GlassCard，不跳出外壳
 * @author AaronZZH & Kiro
 */

"use client"

import { format } from "date-fns"
import { zhCN } from "date-fns/locale"
import { Fragment, useEffect, useState } from "react"
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
import { CreditRechargeDialog } from "@/components/common/CreditRechargeDialog"
import { RedeemCodeButton } from "@/features/billing/components/RedeemCodeButton"
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

  const { data: balance, isLoading: balLoading } = useCreditBalance()
  const { data: groups, isLoading: grpLoading } = useCreditGroups()
  const [tab, setTab] = useState<TabValue>("all")
  const [page, setPage] = useState(0)
  const { data: txPage, isLoading: txLoading } = useCreditTransactions(page)
  const { getLabel: getTypeLabel, getColor: getTypeColor } = useDict("credit_transaction_type")
  const { getLabel: getSourceLabel } = useDict("credit_transaction_source")

  const transactions = txPage?.list ?? []
  const filtered = transactions.filter((tx) => {
    if (tab === "earn") return tx.type === "EARN"
    if (tab === "spend") return tx.type === "SPEND"
    return true
  })

  const totalBalance = mounted ? (groups ?? []).reduce((s, g) => s + g.remain, 0) : 0
  const frozen = mounted ? (balance?.frozen ?? 0) : 0
  const totalEarned = mounted ? (balance?.totalEarned ?? 0) : 0
  const totalSpent = mounted ? (balance?.totalSpent ?? 0) : 0

  return (
    <div className="relative mx-auto max-w-3xl p-6">
      <SectionHaze variant="soft" />
      <div className="relative space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="font-semibold text-xl">积分中心</h1>
          <RedeemCodeButton />
        </div>

        {/* 数据胶囊 */}
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <div className="relative">
            <DataCapsule
              label="可用余额"
              value={mounted ? totalBalance.toLocaleString() : "—"}
              loading={grpLoading}
              tone="amber"
            />
            <div className="absolute top-2 right-2">
              <GlowButton tone="violet" size="sm" onClick={() => setRechargeOpen(true)}>充值</GlowButton>
            </div>
          </div>
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
            ) : filtered.length === 0 ? (
              <p className="py-8 text-center text-muted-foreground text-sm">暂无记录</p>
            ) : (
              filtered.map((tx) => {
                const isEarn = tx.type === "EARN"
                const typeLabel = getTypeLabel(tx.type) || tx.type
                const typeColor = getTypeColor(tx.type)
                return (
                  <Fragment key={tx.id}>
                    <div className="flex items-center justify-between py-3">
                      <div className="flex-1">
                        <div className="flex items-center gap-2">
                          <p className="font-medium text-sm">
                            {tx.remark ||
                              getSourceLabel(tx.source) ||
                              SOURCE_LABEL[tx.source] ||
                              SOURCE_LABEL[tx.source?.toLowerCase()] ||
                              "积分变动"}
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
                        {isEarn ? `+${tx.amount}` : `-${Math.abs(tx.amount)}`}
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
