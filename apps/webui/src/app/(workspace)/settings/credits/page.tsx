/**
 * 积分详情页——总余额 + 三大分组树形列表（会员/每周/奖励）+ tab 流水
 * @author AaronZZH
 */

"use client"

import { format } from "date-fns"
import { zhCN } from "date-fns/locale"
import { Crown, Gift } from "lucide-react"
import Link from "next/link"
import { Fragment, useEffect, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { RedeemCodeButton } from "@/features/billing/components/RedeemCodeButton"
import type { CreditTransactionVO } from "@/lib/api/rest/billing/credits"
import { paths } from "@/lib/constants/paths"
import { useDict } from "@/lib/hooks/use-dict"
import { useCurrentSubscription, useEntitlementQuotas } from "@/lib/queries/use-billing-plans"
import { useCreditGroups, useCreditTransactions } from "@/lib/queries/use-credits"
import { cn } from "@/lib/utils/cn"

// ─── 权益额度 ────────────────────────────────────────────────────────────────

function EntitlementQuotaSection() {
  const { data: quotas, isLoading } = useEntitlementQuotas()

  if (isLoading) return <Skeleton className="h-20 w-full" />

  const countable = (quotas ?? []).filter((q) => q.type === "COUNTABLE")
  const booleans = (quotas ?? []).filter((q) => q.type === "BOOLEAN")

  if (!quotas?.length) return null

  return (
    <div className="space-y-3">
      <p className="font-medium text-sm">权益额度</p>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {countable.map((q) => {
          const pct = q.total > 0 ? Math.round((q.used / q.total) * 100) : 0
          return (
            <div key={q.id} className="rounded-lg border p-4">
              <div className="mb-2 flex items-center justify-between">
                <span className="text-sm">{q.name ?? q.code}</span>
                <span className="text-muted-foreground text-xs tabular-nums">
                  {q.used.toLocaleString()} / {q.total.toLocaleString()}
                  {q.unit ? ` ${q.unit}` : ""}
                </span>
              </div>
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
                <div
                  className={cn(
                    "h-full rounded-full transition-all",
                    pct >= 90 ? "bg-destructive" : pct >= 70 ? "bg-amber-500" : "bg-primary"
                  )}
                  style={{ width: `${pct}%` }}
                />
              </div>
              {q.nextResetAt && (
                <p className="mt-1 text-muted-foreground text-xs">
                  {new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium" }).format(
                    new Date(q.nextResetAt)
                  )}{" "}
                  重置
                </p>
              )}
            </div>
          )
        })}
      </div>
      {booleans.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {booleans.map((q) => (
            <span
              key={q.id}
              className={cn(
                "rounded-full border px-3 py-1 text-xs",
                q.remain > 0
                  ? "border-emerald-300 bg-emerald-50 text-emerald-700 dark:border-emerald-700 dark:bg-emerald-950 dark:text-emerald-400"
                  : "bg-muted text-muted-foreground"
              )}
            >
              {q.remain > 0 ? "✓" : "✗"} {q.name ?? q.code}
            </span>
          ))}
        </div>
      )}
    </div>
  )
}

// ─── 会员状态 ────────────────────────────────────────────────────────────────

function SubscriptionBanner() {
  const { data: sub, isLoading } = useCurrentSubscription()
  const fmt = new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium" }).format
  const [mounted, setMounted] = useState(false)
  useEffect(() => setMounted(true), [])

  if (!mounted || isLoading) return <Skeleton className="h-16 w-full" />

  const isFree = !sub || sub.status !== "ACTIVE"
  return (
    <div
      className={cn(
        "flex items-center justify-between rounded-xl border px-5 py-4",
        isFree
          ? "bg-muted/50"
          : "border-amber-200 bg-amber-50/60 dark:border-amber-800 dark:bg-amber-900/20"
      )}
    >
      <div className="flex items-center gap-3">
        {isFree ? (
          <Gift className="size-7 text-muted-foreground" />
        ) : (
          <Crown className="size-7 text-amber-500" />
        )}
        <div>
          <div className="flex items-center gap-2">
            <span className="font-semibold">{isFree ? "免费版" : sub.planName}</span>
            {!isFree && (
              <Badge
                variant="outline"
                className="border-amber-400 text-amber-600 text-xs dark:border-amber-500 dark:text-amber-400"
              >
                {sub.status === "ACTIVE" ? "生效中" : sub.status}
              </Badge>
            )}
          </div>
          {!isFree && sub.endAt && (
            <p className="mt-0.5 text-muted-foreground text-xs">
              有效期至 {fmt(new Date(sub.endAt))}
            </p>
          )}
          {!isFree && !sub.endAt && (
            <p className="mt-0.5 text-muted-foreground text-xs">永久有效</p>
          )}
          {isFree && (
            <p className="mt-0.5 text-muted-foreground text-xs">升级套餐解锁更多积分与权益</p>
          )}
        </div>
      </div>
      {isFree && (
        <Button
          size="sm"
          nativeButton={false}
          render={<Link href={paths.workspace.settingsPricing} />}
          className="rounded-full bg-gradient-to-r from-amber-500 to-orange-500 text-white hover:opacity-90"
        >
          升级套餐
        </Button>
      )}
    </div>
  )
}

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

// ─── source / category 静态中文映射（字典未配时的兜底） ──────────────────────

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
            <p className="font-medium">
              {tx.remark || getSourceLabel(tx.source) || SOURCE_LABEL[tx.source] || tx.source}
            </p>
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
                {isEarn
                  ? (GROUP_LABEL[tx.category?.trim().toUpperCase() ?? ""] ?? tx.category)
                  : getCategoryLabel(tx.category) || tx.category}
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
      {/* 会员状态 */}
      <SubscriptionBanner />

      {/* 标题行 */}
      <div className="mt-6 flex items-center justify-between">
        <h1 className="font-semibold text-xl">积分详情</h1>
        <RedeemCodeButton />
      </div>

      {/* 余额公式 */}
      <BalanceFormula />

      {/* 权益额度 */}
      <EntitlementQuotaSection />

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
