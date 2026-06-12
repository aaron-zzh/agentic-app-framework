/**
 * 积分详情页——余额公式（总额 = 会员积分 + 奖励积分 + 每周积分）+ tab 流水 + 可展开记录
 * @author Kiro
 */

"use client"

import { Gift } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import type { CreditTransactionVO } from "@/lib/api/rest/billing/credits"
import { useCreditGroups, useCreditTransactions } from "@/lib/queries/use-credits"
import { cn } from "@/lib/utils/cn"

// ─── 余额公式区 ──────────────────────────────────────────────────────────────

const GROUP_LABEL: Record<string, string> = {
  SUBSCRIPTION: "会员积分",
  TOPUP: "充值积分",
  WEEKLY: "每周积分",
  REWARD: "奖励积分",
  MANUAL: "赠送积分"
}

const GROUP_TIP: Record<string, string> = {
  SUBSCRIPTION: "通过订阅会员获得",
  TOPUP: "通过充值获得",
  WEEKLY: "每周自动刷新，到期清零",
  REWARD: "邀请好友或完成任务获得",
  MANUAL: "人工赠送"
}

function BalanceFormula() {
  const { data: groups, isLoading } = useCreditGroups()
  const total = groups?.reduce((sum, g) => sum + g.remain, 0) ?? 0
  const displayGroups = groups?.length
    ? groups
    : [
        { batchType: "SUBSCRIPTION", remain: 0 },
        { batchType: "REWARD", remain: 0 },
        { batchType: "WEEKLY", remain: 0 }
      ]

  if (isLoading) {
    return (
      <div className="grid grid-cols-4 gap-2 py-6">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={`sk-${i}`} className="h-14 w-full" />
        ))}
      </div>
    )
  }

  return (
    <div className="grid grid-cols-4 items-end gap-2 py-6">
      <div>
        <p className="text-muted-foreground text-sm">积分余额</p>
        <p className="mt-1 font-bold text-3xl tabular-nums">{total.toLocaleString()}</p>
      </div>

      {displayGroups.slice(0, 3).map((g, i) => (
        <div key={g.batchType} className="flex flex-col items-center gap-1">
          <span className="text-muted-foreground text-xl">{i === 0 ? "=" : "+"}</span>
          <div>
            <p className="flex items-center gap-1 text-muted-foreground text-sm">
              {GROUP_LABEL[g.batchType] ?? g.batchType}
              <InfoTip text={GROUP_TIP[g.batchType] ?? ""} />
            </p>
            <p className="mt-1 font-bold text-3xl tabular-nums">{g.remain.toLocaleString()}</p>
          </div>
        </div>
      ))}
    </div>
  )
}

function InfoTip({ text }: { text: string }) {
  return (
    <span
      className="inline-flex size-4 cursor-default items-center justify-center rounded-full border text-[10px] text-muted-foreground"
      title={text}
    >
      i
    </span>
  )
}

// ─── 流水条目 ────────────────────────────────────────────────────────────────

function TxRow({ tx }: { tx: CreditTransactionVO }) {
  const isEarn = tx.amount > 0

  return (
    <>
      <div className="flex items-center justify-between py-4">
        <div className="flex-1">
          <p className="font-medium">{tx.source}</p>
          <p className="mt-0.5 text-muted-foreground text-xs">{tx.createTime}</p>
        </div>
        <span
          className={cn(
            "font-semibold tabular-nums",
            isEarn ? "text-emerald-500" : "text-orange-400"
          )}
        >
          {isEarn ? `+ ${tx.amount}` : `- ${Math.abs(tx.amount)}`}
        </span>
      </div>
      <Separator />
    </>
  )
}

// ─── 兑换码按钮 ──────────────────────────────────────────────────────────────

function RedeemButton() {
  return (
    <Button
      variant="outline"
      size="sm"
      className="gap-1.5 border-amber-500 text-amber-500 hover:bg-amber-500/10"
    >
      <Gift className="size-4" />
      兑换码
    </Button>
  )
}

// ─── 主页面 ──────────────────────────────────────────────────────────────────

type TabValue = "all" | "spend" | "earn"

export default function CreditsPage() {
  const [tab, setTab] = useState<TabValue>("all")
  const [page, setPage] = useState(0)
  const { data: txPage, isLoading: txLoading } = useCreditTransactions(page)

  const transactions = txPage?.list ?? []
  const filtered = transactions.filter((tx) => {
    if (tab === "earn") return tx.amount > 0
    if (tab === "spend") return tx.amount < 0
    return true
  })

  return (
    <div className="mx-auto max-w-3xl px-8 py-6">
      {/* 标题行 */}
      <div className="flex items-center justify-between">
        <h1 className="font-semibold text-xl">积分详情</h1>
        <RedeemButton />
      </div>

      {/* 余额公式 */}
      <BalanceFormula />

      <Separator />

      {/* Tab */}
      <div className="py-4">
        <Tabs value={tab} onValueChange={(v) => setTab(v as TabValue)}>
          <TabsList className="w-full">
            <TabsTrigger value="all" className="flex-1">
              全部
            </TabsTrigger>
            <TabsTrigger value="spend" className="flex-1">
              已消耗
            </TabsTrigger>
            <TabsTrigger value="earn" className="flex-1">
              已获得
            </TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      {/* 流水列表 */}
      <div>
        {txLoading ? (
          <div className="space-y-4">
            {Array.from({ length: 5 }).map((_, i) => (
              <Skeleton key={`tx-sk-${i}`} className="h-14 w-full" />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <p className="py-12 text-center text-muted-foreground text-sm">暂无记录</p>
        ) : (
          filtered.map((tx) => <TxRow key={tx.id} tx={tx} />)
        )}
      </div>

      {/* 分页 */}
      {(txPage?.total ?? 0) > 20 && (
        <div className="flex justify-center gap-2 pt-4">
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
    </div>
  )
}
