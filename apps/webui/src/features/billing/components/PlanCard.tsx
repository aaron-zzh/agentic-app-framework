/**
 * PlanCard——单个套餐卡片
 *
 * 视觉特性：
 *  - PLAN_THEME 控制边框/按钮渐变色，按 plan.code 取
 *  - 推荐套餐：ring + ribbon "🔥 最受欢迎"，默认推荐 PRO（外部 isRecommended 控制）
 *  - 当前订阅：CTA 显示"当前套餐"且 disabled
 *  - hover：阴影 + 微小 lift（-translate-y-1）
 *  - FREE 套餐：不论按月按年都显示 ¥0，按钮文案"开始使用"
 *  - 年付：实际折扣按 yearlyPrice/(price*12) 计算，不写死 8 折
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { CheckIcon, FlameIcon, SparklesIcon } from "lucide-react"
import { useMemo } from "react"
import { Button } from "@/components/ui/button"
import type { SubscriptionPlanVO } from "@/lib/api/rest/billing/plans"
import { cn } from "@/lib/utils/cn"
import {
  calcYearlyDiscount,
  effectiveMonthlyPrice,
  formatDiscount,
  formatYuan,
  parsePlanExt
} from "./pricing-utils"

/** 重置周期标签 */
const CYCLE_LABEL: Record<string, string> = {
  NONE: "",
  DAILY: "/天",
  MONTHLY: "/月",
  YEARLY: "/年"
}

/** 套餐主题色（按 code 取，兜底中性灰） */
interface PlanTheme {
  /** 卡片边框色（hover/recommend 时变实色） */
  border: string
  /** CTA 按钮色（推荐套餐用渐变） */
  cta: string
  /** 价格、徽标的强调色 */
  accent: string
}

const PLAN_THEME: Record<string, PlanTheme> = {
  FREE: {
    border: "border-border",
    cta: "",
    accent: "text-foreground"
  },
  PRO: {
    border: "border-amber-400/50 dark:border-amber-300/40",
    cta: "bg-gradient-to-r from-amber-500 to-orange-500 text-white hover:from-amber-500/90 hover:to-orange-500/90",
    accent: "text-amber-600 dark:text-amber-400"
  },
  TEAM: {
    border: "border-emerald-400/50 dark:border-emerald-300/40",
    cta: "bg-gradient-to-r from-emerald-500 to-teal-500 text-white hover:from-emerald-500/90 hover:to-teal-500/90",
    accent: "text-emerald-600 dark:text-emerald-400"
  },
  ENTERPRISE: {
    border: "border-rose-400/50 dark:border-rose-300/40",
    cta: "bg-gradient-to-r from-rose-500 to-pink-500 text-white hover:from-rose-500/90 hover:to-pink-500/90",
    accent: "text-rose-600 dark:text-rose-400"
  }
}

export interface PlanCardProps {
  plan: SubscriptionPlanVO
  billingCycle: "monthly" | "yearly"
  /** 是否标记为"最受欢迎"（一般 PRO 套餐） */
  isRecommended?: boolean
  /** 用户当前订阅的 planCode；与本卡片相同时，CTA 变"当前套餐" */
  currentPlanCode?: string | null
  onSubscribe: (code: string) => void
  isPending: boolean
}

export function PlanCard({
  plan,
  billingCycle,
  isRecommended = false,
  currentPlanCode = null,
  onSubscribe,
  isPending
}: PlanCardProps) {
  const theme = PLAN_THEME[plan.code] ?? PLAN_THEME.FREE
  const ext = useMemo(() => parsePlanExt(plan.ext), [plan.ext])

  const isFree = plan.code === "FREE" || (plan.price === 0 && plan.yearlyPrice === 0)
  const isCurrent = currentPlanCode === plan.code

  const discount = calcYearlyDiscount(plan)
  const showYearlyDiscount = billingCycle === "yearly" && !isFree && discount !== null

  // 相对"100 积分/元"基准的综合性价比折扣
  const monthlyEqPrice = effectiveMonthlyPrice(plan, billingCycle)
  const creditValueRatio =
    plan.price > 0 && plan.monthlyCredits > 0
      ? Math.round((monthlyEqPrice / (plan.monthlyCredits / 100)) * 10) / 10
      : null

  const ctaText = isCurrent ? "当前套餐" : isFree ? "开始使用" : "立即升级"
  const ctaDisabled = isCurrent || isFree || isPending

  const displayPrice = billingCycle === "yearly" ? plan.yearlyPrice : plan.price

  return (
    <div
      className={cn(
        "group/plan-card relative flex flex-col rounded-2xl border-2 bg-card p-6 transition-all duration-200",
        "hover:-translate-y-1 hover:shadow-xl",
        isRecommended ? "border-primary ring-2 ring-primary/20" : theme.border
      )}
    >
      {/* 推荐/当前套餐徽标：当前套餐优先 */}
      {isCurrent ? (
        <span className="absolute top-0 left-1/2 inline-flex -translate-x-1/2 items-center gap-1 rounded-full bg-foreground px-3 py-1 font-semibold text-background text-xs shadow-md ring-2 ring-background">
          <SparklesIcon className="size-3" strokeWidth={2.5} />
          当前套餐
        </span>
      ) : isRecommended ? (
        <span className="absolute top-0 left-1/2 inline-flex -translate-x-1/2 items-center gap-1 rounded-full bg-gradient-to-r from-amber-500 to-orange-500 px-3 py-1 font-semibold text-white text-xs shadow-md ring-2 ring-background">
          <FlameIcon className="size-3" strokeWidth={2.5} />
          最受欢迎
        </span>
      ) : null}

      {/* 年付折扣徽标（不与推荐冲突） */}
      {showYearlyDiscount && !isRecommended && !isCurrent && (
        <span className="absolute top-3 right-3 rounded-full bg-amber-500/15 px-2 py-0.5 font-semibold text-[11px] text-amber-600 ring-1 ring-amber-500/30 dark:text-amber-400">
          年付 {formatDiscount(discount)}
        </span>
      )}

      {/* 套餐名 + tagline */}
      <div className="mt-2 mb-4">
        <p className="font-semibold text-base">{plan.name}</p>
        {ext.tagline && <p className="mt-0.5 text-muted-foreground text-xs">{ext.tagline}</p>}
      </div>

      {/* 价格 */}
      <div className="mb-2">
        {isFree ? (
          <p className="font-bold text-4xl">¥0</p>
        ) : (
          <>
            <div className="flex items-end gap-1">
              <span className="font-bold text-4xl">¥{formatYuan(displayPrice)}</span>
              <span className="mb-1 text-muted-foreground text-sm">
                /{billingCycle === "yearly" ? "年" : "月"}
              </span>
            </div>
            {showYearlyDiscount && (
              <p className="text-muted-foreground text-xs line-through">
                原价 ¥{formatYuan(plan.price * 12)}/年
              </p>
            )}
            {!showYearlyDiscount && billingCycle === "yearly" && plan.yearlyPrice > 0 && (
              // 显示等效月费供参考
              <p className="text-muted-foreground text-xs">折合 ¥{formatYuan(monthlyEqPrice)}/月</p>
            )}
          </>
        )}
      </div>

      {/* 月度积分：始终渲染保持卡片高度一致 */}
      {plan.monthlyCredits > 0 ? (
        <p className="mb-5 inline-flex flex-wrap items-baseline gap-1.5 text-sm">
          <span className={cn("font-semibold", theme.accent)}>
            {plan.monthlyCredits.toLocaleString()} 🪙
          </span>
          <span className="text-muted-foreground">每月额度</span>
          {creditValueRatio !== null && creditValueRatio < 0.95 && (
            <span className="rounded-full bg-amber-500/15 px-1.5 py-0.5 font-medium text-[10px] text-amber-600 dark:text-amber-400">
              性价比 {creditValueRatio.toFixed(1)} 折
            </span>
          )}
        </p>
      ) : (
        <p className="mb-5 inline-flex items-baseline gap-1.5 text-sm">
          <span className="font-semibold">🪙</span>
          <span className="text-muted-foreground">按需购买</span>
        </p>
      )}

      {/* CTA */}
      <Button
        size="lg"
        className={cn(
          "w-full rounded-xl font-semibold transition-all",
          isCurrent && "bg-muted text-muted-foreground hover:bg-muted",
          !isCurrent && !isFree && theme.cta
        )}
        disabled={ctaDisabled}
        onClick={() => onSubscribe(plan.code)}
      >
        {ctaText}
      </Button>

      {/* 权益列表 */}
      {(plan.entitlements ?? []).length > 0 && (
        <div className="mt-6 space-y-2.5 border-t pt-5">
          {(plan.entitlements ?? []).map((ent) => (
            <div key={ent.code} className="flex items-start justify-between gap-2 text-sm">
              <span className="flex items-start gap-1.5 text-foreground/80">
                <CheckIcon
                  className="mt-0.5 size-3.5 shrink-0 text-emerald-600 dark:text-emerald-400"
                  strokeWidth={2.5}
                />
                <span>{ent.name}</span>
              </span>
              <span className="shrink-0 font-medium text-muted-foreground text-xs">
                {ent.type === "BOOLEAN"
                  ? "✓"
                  : ent.quota === -1
                    ? "无限"
                    : `${ent.quota.toLocaleString()}${ent.unit ?? ""}${CYCLE_LABEL[ent.resetCycle] ?? ""}`}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
