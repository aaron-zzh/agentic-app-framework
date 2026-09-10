"use client"

import { CheckIcon, FlameIcon } from "lucide-react"
import { useMemo } from "react"
import { Button } from "@/components/ui/button"
import type { BillingCycle, SubscriptionPlanVO, SubscriptionSkuVO } from "@/lib/api/rest/billing"
import { cn } from "@/lib/utils/cn"
import { formatYuan, parsePlanExt } from "./pricing-utils"

const CYCLE_LABEL: Record<string, string> = {
  NONE: "",
  DAILY: "/天",
  MONTHLY: "/月",
  YEARLY: "/年"
}

const PRICE_CYCLE_LABEL: Record<BillingCycle, string> = {
  MONTH: "月",
  QUARTER: "季",
  YEAR: "年",
  PERPETUAL: "永久"
}

interface PlanTheme {
  border: string
  cta: string
  accent: string
}

const PLAN_THEME: Record<string, PlanTheme> = {
  FREE: { border: "border-border", cta: "", accent: "text-foreground" },
  PRO: {
    border: "border-amber-400/50 dark:border-amber-300/40",
    cta: "bg-gradient-to-r from-amber-500 to-orange-500 text-white",
    accent: "text-amber-600 dark:text-amber-400"
  },
  TEAM: {
    border: "border-emerald-400/50 dark:border-emerald-300/40",
    cta: "bg-gradient-to-r from-emerald-500 to-teal-500 text-white",
    accent: "text-emerald-600 dark:text-emerald-400"
  },
  ENTERPRISE: {
    border: "border-rose-400/50 dark:border-rose-300/40",
    cta: "bg-gradient-to-r from-rose-500 to-pink-500 text-white",
    accent: "text-rose-600 dark:text-rose-400"
  }
}

export interface PlanCardProps {
  plan: SubscriptionPlanVO
  billingCycle: Exclude<BillingCycle, "PERPETUAL">
  isRecommended?: boolean
  currentPlanCode?: string | null
  currentSkuCode?: string | null
  currentPlanSort?: number | null
  onSubscribe: (sku: SubscriptionSkuVO, plan: SubscriptionPlanVO) => void
  onDowngrade?: (sku: SubscriptionSkuVO, plan: SubscriptionPlanVO) => void
  isPending: boolean
}

export function PlanCard({
  plan,
  billingCycle,
  isRecommended = false,
  currentPlanCode = null,
  currentSkuCode = null,
  currentPlanSort = null,
  onSubscribe,
  onDowngrade,
  isPending
}: PlanCardProps) {
  const theme = PLAN_THEME[plan.code] ?? PLAN_THEME.FREE
  const ext = useMemo(() => parsePlanExt(plan.ext), [plan.ext])
  const isFree = plan.code === "FREE"
  const selectedSku = isFree
    ? plan.skus.find((sku) => sku.billingCycle === "PERPETUAL")
    : plan.skus.find((sku) => sku.billingCycle === billingCycle)
  const isCurrentPlan = currentPlanCode === plan.code
  const isCurrentSku = selectedSku?.skuCode === currentSkuCode
  const isSamePlanDifferentSku = isCurrentPlan && !isCurrentSku && !isFree
  const isLowerPlan = currentPlanSort !== null && plan.sort < currentPlanSort

  let ctaText = "立即订阅"
  if (!selectedSku) ctaText = "暂不可售"
  else if (isCurrentSku) ctaText = "续费当前套餐"
  else if (isSamePlanDifferentSku) ctaText = "暂不支持换周期"
  else if (isLowerPlan) ctaText = "周期结束后降级"
  else if (currentPlanSort !== null && plan.sort > currentPlanSort) ctaText = "立即升级"
  else if (isFree) ctaText = currentPlanCode === "FREE" ? "当前套餐" : "降级到免费版"

  const disabled =
    isPending || !selectedSku || isSamePlanDifferentSku || (isFree && currentPlanCode === "FREE")

  const handleAction = () => {
    if (!selectedSku) return
    if (isLowerPlan || (isFree && currentPlanCode !== "FREE")) {
      onDowngrade?.(selectedSku, plan)
      return
    }
    onSubscribe(selectedSku, plan)
  }

  return (
    <div
      className={cn(
        "group/plan-card relative flex flex-col rounded-2xl border-2 bg-card p-6 transition-all duration-200",
        "hover:-translate-y-1 hover:shadow-xl",
        isRecommended ? "border-primary ring-2 ring-primary/20" : theme.border
      )}
    >
      {isRecommended && (
        <span className="absolute top-2 left-1/2 inline-flex -translate-x-1/2 items-center gap-1 rounded-full bg-gradient-to-r from-amber-500 to-orange-500 px-3 py-1 font-semibold text-white text-xs shadow-md ring-2 ring-background">
          <FlameIcon className="size-3" strokeWidth={2.5} />
          最受欢迎
        </span>
      )}

      <div className="mt-2 mb-4">
        <p className="font-semibold text-base">{plan.name}</p>
        {ext.tagline && <p className="mt-0.5 text-muted-foreground text-xs">{ext.tagline}</p>}
      </div>

      <div className="mb-2">
        {isFree ? (
          <p className="font-bold text-4xl">¥0</p>
        ) : selectedSku ? (
          <>
            <div className="flex items-end gap-1">
              <span className="font-bold text-4xl">¥{formatYuan(selectedSku.price)}</span>
              <span className="mb-1 text-muted-foreground text-sm">
                /{PRICE_CYCLE_LABEL[selectedSku.billingCycle]}
              </span>
            </div>
            {selectedSku.marketPrice > selectedSku.price && (
              <p className="text-muted-foreground text-xs line-through">
                对比价 ¥{formatYuan(selectedSku.marketPrice)}
              </p>
            )}
          </>
        ) : (
          <p className="font-medium text-muted-foreground">当前周期暂不可售</p>
        )}
      </div>

      {plan.monthlyCredits > 0 ? (
        <p className="mb-5 inline-flex flex-wrap items-baseline gap-1.5 text-sm">
          <span className={cn("font-semibold", theme.accent)}>
            {plan.monthlyCredits.toLocaleString()} 🪙
          </span>
          <span className="text-muted-foreground">每月发放</span>
        </p>
      ) : (
        <p className="mb-5 text-muted-foreground text-sm">积分按需购买</p>
      )}

      <Button
        size="lg"
        className={cn("w-full rounded-xl font-semibold", !disabled && !isFree && theme.cta)}
        disabled={disabled}
        onClick={handleAction}
      >
        {ctaText}
      </Button>

      {(plan.entitlements ?? []).length > 0 && (
        <div className="mt-6 space-y-2.5 border-t pt-5">
          {plan.entitlements.map((entitlement) => (
            <div key={entitlement.code} className="flex items-start justify-between gap-2 text-sm">
              <span className="flex items-start gap-1.5 text-foreground/80">
                <CheckIcon className="mt-0.5 size-3.5 shrink-0 text-emerald-600" />
                <span>{entitlement.name}</span>
              </span>
              <span className="shrink-0 font-medium text-muted-foreground text-xs">
                {entitlement.type === "BOOLEAN"
                  ? "✓"
                  : entitlement.quota === -1
                    ? "无限"
                    : `${entitlement.quota.toLocaleString()}${entitlement.unit ?? ""}${CYCLE_LABEL[entitlement.resetCycle] ?? ""}`}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
