/**
 * 积分详情页——总余额 + 三大分组树形列表（会员/每周/奖励）+ tab 流水
 * @author AaronZZH
 */

"use client"

import { format } from "date-fns"
import { zhCN } from "date-fns/locale"
import { useState } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import type { CreditTransactionVO } from "@/lib/api/rest/billing/credits"
import { creditsApi } from "@/lib/api/rest/billing/credits"
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

  if (isLoading) {
    return (
      <div className="grid grid-cols-4 gap-2 py-6">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={`sk-${i}`} className="h-14 w-full" />
        ))}
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
          {displayGroups.reduce((s, g) => s + g.remain, 0).toLocaleString()}
        </p>
      </div>
      {displayGroups.map((g, i) => (
        <>
          <span key={`op-${g.batchType}`} className="text-center text-muted-foreground text-xl">
            {i === 0 ? "=" : "+"}
          </span>
          <div key={g.batchType} className="text-center">
            <p className="flex items-center justify-center gap-1 text-muted-foreground text-sm">
              {GROUP_LABEL[g.batchType] ?? g.batchType}
              <InfoTip text={GROUP_TIP[g.batchType] ?? ""} />
            </p>
            <p className="mt-1 font-bold text-3xl tabular-nums">{g.remain.toLocaleString()}</p>
          </div>
        </>
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

const SOURCE_LABEL: Record<string, string> = {
  WEEKLY: "每周积分发放",
  SUBSCRIPTION: "订阅套餐积分",
  TOPUP: "购买积分",
  MANUAL: "人工赠送",
  REWARD: "奖励积分",
  register_gift: "注册赠送",
  chat: "对话消耗",
  image: "图像生成消耗",
  ENT_REFILL: "权益补充",
  "Weekly Credits refreshed": "每周积分刷新",
  "Weekly Credits expired": "每周积分过期"
}

function TxRow({ tx }: { tx: CreditTransactionVO }) {
  const isEarn = tx.amount > 0

  return (
    <>
      <div className="flex items-center justify-between py-4">
        <div className="flex-1">
          <p className="font-medium">{SOURCE_LABEL[tx.source] ?? tx.source}</p>
          <p className="mt-0.5 text-muted-foreground text-xs">
            {format(new Date(tx.createTime), "yyyy年MM月dd日 HH:mm", { locale: zhCN })}
          </p>
        </div>
        <span
          className={cn(
            "font-semibold tabular-nums",
            isEarn ? "text-emerald-500" : "text-orange-400"
          )}
        >
          {isEarn ? `+ ${tx.amount}` : `- ${Math.abs(tx.amount)}`}
        </span>
        <div className="ml-4 text-right">
          <p className="text-muted-foreground text-xs">余额</p>
          <p
            className={cn(
              "font-medium text-sm tabular-nums",
              tx.balanceAfter < 0 && "text-destructive"
            )}
          >
            {tx.balanceAfter.toLocaleString()}
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
      toast.success(`兑换成功，获得 ${amount} 积分`)
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
            输入兑换码，兑换成功后积分将自动到账。
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
