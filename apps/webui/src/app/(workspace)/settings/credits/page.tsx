/**
 * 积分详情页——总余额 + 三大分组树形列表（会员/每周/奖励）+ tab 流水
 * @author AaronZZH
 */

"use client"

import { format } from "date-fns"
import { zhCN } from "date-fns/locale"
import { Fragment, useEffect, useState } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import type { CreditTransactionVO } from "@/lib/api/rest/billing/credits"
import { creditsApi } from "@/lib/api/rest/billing/credits"
import { useDict } from "@/lib/hooks/use-dict"
import { useCreditGroups, useCreditTransactions } from "@/lib/queries/use-credits"
import { cn } from "@/lib/utils/cn"

// ─── 积分树形列表 ─────────────────────────────────────────────────────────────

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

const DEFAULT_GROUPS = ["SUBSCRIPTION", "REWARD", "WEEKLY"]

function BalanceFormula() {
  const { data: groups, isLoading } = useCreditGroups()
  const [mounted, setMounted] = useState(false)
  useEffect(() => setMounted(true), [])

  if (!mounted || isLoading) {
    return (
      <div className="grid grid-cols-4 gap-2 py-6">
        <Skeleton className="h-14 w-full" />
        <Skeleton className="h-14 w-full" />
        <Skeleton className="h-14 w-full" />
        <Skeleton className="h-14 w-full" />
      </div>
    )
  }

  const remainMap = Object.fromEntries((groups ?? []).map((g) => [g.batchType, g.remain]))
  const displayGroups = DEFAULT_GROUPS.map((type) => ({
    batchType: type,
    remain: remainMap[type] ?? 0
  }))

  return (
    <div className="grid grid-cols-[1fr_auto_1fr_auto_1fr_auto_1fr] items-center gap-x-2 py-6">
      <div>
        <p className="text-muted-foreground text-sm">积分余额</p>
        <p className="mt-1 font-bold text-3xl tabular-nums">
          {(groups ?? []).reduce((s, g) => s + g.remain, 0).toLocaleString()}
        </p>
      </div>
      {displayGroups.map((g, i) => (
        <Fragment key={g.batchType}>
          <span className="text-center text-muted-foreground text-xl">{i === 0 ? "=" : "+"}</span>
          <div className="text-center">
            <p className="flex items-center justify-center gap-1 text-muted-foreground text-sm">
              {GROUP_LABEL[g.batchType] ?? g.batchType}
              <InfoTip text={GROUP_TIP[g.batchType] ?? ""} />
            </p>
            <p className="mt-1 font-bold text-3xl tabular-nums">{g.remain.toLocaleString()}</p>
          </div>
        </Fragment>
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

function TxRow({
  tx,
  getTypeLabel,
  getTypeColor,
  getSourceLabel,
  getCategoryLabel
}: {
  tx: CreditTransactionVO
  getTypeLabel: (v: string) => string
  getTypeColor: (v: string) => string
  getSourceLabel: (v: string) => string
  getCategoryLabel: (v: string) => string
}) {
  const isEarn = tx.type === "EARN"
  const typeLabel = getTypeLabel(tx.type) || tx.type
  const typeColor = getTypeColor(tx.type)

  return (
    <>
      <div className="flex items-center justify-between py-4">
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <p className="font-medium">{tx.remark || getSourceLabel(tx.source) || tx.source}</p>
            <span
              className={cn(
                "rounded px-1.5 py-0.5 text-xs",
                typeColor === "success" && "bg-emerald-100 text-emerald-700",
                typeColor === "danger" && "bg-orange-100 text-orange-700",
                typeColor === "warning" && "bg-yellow-100 text-yellow-700",
                typeColor === "info" && "bg-blue-100 text-blue-700",
                !["success", "danger", "warning", "info"].includes(typeColor) &&
                  "bg-muted text-muted-foreground"
              )}
            >
              {typeLabel}
            </span>
            {tx.category && (
              <span className="rounded bg-muted px-1.5 py-0.5 text-muted-foreground text-xs">
                {getCategoryLabel(tx.category) || tx.category}
              </span>
            )}
          </div>
          <p className="mt-0.5 text-muted-foreground text-xs">
            {format(new Date(tx.createTime), "yyyy年MM月dd日 HH:mm", { locale: zhCN })}
          </p>
        </div>
        <div className="text-right">
          <p
            className={cn(
              "font-semibold text-lg tabular-nums",
              isEarn ? "text-emerald-500" : "text-orange-400"
            )}
          >
            {isEarn ? `+${tx.amount}` : `-${Math.abs(tx.amount)}`}
          </p>
          <p
            className={cn(
              "mt-0.5 text-xs tabular-nums",
              tx.balanceAfter < 0 ? "text-destructive" : "text-muted-foreground"
            )}
          >
            余额 {tx.balanceAfter.toLocaleString()}
          </p>
        </div>
      </div>
      <Separator />
    </>
  )
}

// ─── 兑换码按钮 + 弹窗 ───────────────────────────────────────────────────────

function RedeemButton() {
  const [open, setOpen] = useState(false)
  const [code, setCode] = useState("")
  const [loading, setLoading] = useState(false)

  async function handleSubmit() {
    if (!code.trim()) return
    setLoading(true)
    try {
      const amount = await creditsApi.redeem(code.trim())
      toast.success(amount > 0 ? `兑换成功，获得 ${amount} 积分` : "兑换成功，会员已开通")
      setOpen(false)
      setCode("")
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "兑换失败，请检查兑换码")
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <Button
        variant="outline"
        size="sm"
        className="border-amber-500 text-amber-500 hover:bg-amber-500/10"
        onClick={() => setOpen(true)}
      >
        兑换码
      </Button>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="p-8 sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="text-center">兑换码</DialogTitle>
          </DialogHeader>
          <p className="text-center text-muted-foreground text-sm">
            输入兑换码，可兑换积分或开通会员。
          </p>
          <div className="space-y-3 pt-2">
            <Input
              placeholder="请输入兑换码"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && handleSubmit()}
              className="text-center"
            />
            <Button className="w-full" onClick={handleSubmit} disabled={!code.trim() || loading}>
              {loading ? "兑换中..." : "提交"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}

// ─── 主页面 ──────────────────────────────────────────────────────────────────

type TabValue = "all" | "spend" | "earn"

export default function CreditsPage() {
  const [tab, setTab] = useState<TabValue>("all")
  const [page, setPage] = useState(0)
  const { data: txPage, isLoading: txLoading } = useCreditTransactions(page)
  const { getLabel: getTypeLabel, getColor: getTypeColor } = useDict("credit_transaction_type")
  const { getLabel: getSourceLabel } = useDict("credit_transaction_source")
  const { getLabel: getCategoryLabel } = useDict("credit_transaction_category")

  const transactions = txPage?.list ?? []
  const filtered = transactions.filter((tx) => {
    if (tab === "earn") return tx.type === "EARN"
    if (tab === "spend") return tx.type === "SPEND"
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
          filtered.map((tx) => (
            <TxRow
              key={tx.id}
              tx={tx}
              getTypeLabel={getTypeLabel}
              getTypeColor={getTypeColor}
              getSourceLabel={getSourceLabel}
              getCategoryLabel={getCategoryLabel}
            />
          ))
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
