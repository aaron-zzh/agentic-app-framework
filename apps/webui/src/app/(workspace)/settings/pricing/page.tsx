"use client"

import { Check } from "lucide-react"
import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import type { SubscriptionPlanVO } from "@/lib/api/rest/billing/plans"
import { notify } from "@/lib/notification"
import { useSubscribe, useSubscriptionPlans } from "@/lib/queries/use-billing-plans"
import { useMemberFaq } from "@/lib/queries/use-system-config"

/** 重置周期标签 */
const CYCLE_LABEL: Record<string, string> = {
  NONE: "",
  DAILY: "/天",
  MONTHLY: "/月",
  YEARLY: "/年"
}

/** 套餐卡片颜色主题（按 code 顺序） */
const PLAN_ACCENT: Record<string, string> = {
  FREE: "border-border",
  PRO: "border-amber-500",
  TEAM: "border-green-500",
  ENTERPRISE: "border-red-500"
}

const PLAN_BTN: Record<string, string> = {
  FREE: "bg-secondary text-secondary-foreground hover:bg-secondary/80",
  PRO: "bg-gradient-to-r from-amber-400 to-orange-500 text-white hover:opacity-90",
  TEAM: "bg-gradient-to-r from-green-400 to-emerald-500 text-white hover:opacity-90",
  ENTERPRISE: "bg-gradient-to-r from-red-400 to-rose-500 text-white hover:opacity-90"
}

function fmt(fen: number) {
  return (fen / 100).toLocaleString("zh-CN", { minimumFractionDigits: 0 })
}

function PlanCard({
  plan,
  billingCycle,
  onSubscribe,
  isPending
}: {
  plan: SubscriptionPlanVO
  billingCycle: "monthly" | "yearly"
  onSubscribe: (code: string) => void
  isPending: boolean
}) {
  const price = billingCycle === "yearly" ? plan.yearlyPrice : plan.price
  const isFree = plan.price === 0
  const yearlyDiscount =
    plan.price > 0 ? Math.round((1 - plan.yearlyPrice / (plan.price * 12)) * 100) : 0

  return (
    <div
      className={[
        "relative flex flex-col rounded-2xl border-2 bg-card p-6 transition-shadow hover:shadow-lg",
        PLAN_ACCENT[plan.code] ?? "border-border"
      ].join(" ")}
    >
      {/* 折扣徽标 */}
      {billingCycle === "yearly" && yearlyDiscount > 0 && (
        <span className="absolute -top-3 right-4 rounded-full bg-amber-500 px-3 py-0.5 font-semibold text-white text-xs">
          积分 {(1 - yearlyDiscount / 100).toFixed(1)}折
        </span>
      )}

      {/* 套餐名 */}
      <div className="mb-4">
        <p className="font-semibold text-base text-muted-foreground">{plan.name}</p>
        {plan.ext &&
          (() => {
            try {
              const e = JSON.parse(plan.ext)
              return e.tagline ? (
                <p className="mt-0.5 text-muted-foreground/70 text-xs">{e.tagline}</p>
              ) : null
            } catch {
              return null
            }
          })()}
      </div>

      {/* 价格 */}
      <div className="mb-2">
        {isFree ? (
          <p className="font-bold text-4xl">¥0</p>
        ) : (
          <>
            <div className="flex items-end gap-1">
              <span className="font-bold text-4xl">¥{fmt(price)}</span>
              <span className="mb-1 text-muted-foreground text-sm">
                /{billingCycle === "yearly" ? "年" : "月"}
              </span>
            </div>
            {billingCycle === "yearly" && (
              <p className="text-muted-foreground text-xs line-through">
                原价 ¥{fmt(plan.price * 12)}/年
              </p>
            )}
          </>
        )}
      </div>

      {/* 月度积分 */}
      {plan.monthlyCredits > 0 && (
        <p className="mb-4 text-sm">
          <span className="font-semibold text-amber-500">
            {plan.monthlyCredits.toLocaleString()} 🪙
          </span>
          <span className="text-muted-foreground"> 每月到账</span>
        </p>
      )}

      {/* CTA 按钮 */}
      <Button
        className={["w-full rounded-xl font-semibold", PLAN_BTN[plan.code] ?? ""].join(" ")}
        disabled={isFree || isPending}
        onClick={() => onSubscribe(plan.code)}
      >
        {isFree ? "开始使用" : "升级"}
      </Button>

      {/* 权益列表 */}
      {(plan.entitlements ?? []).length > 0 && (
        <div className="mt-6 space-y-2 border-t pt-4">
          {(plan.entitlements ?? []).map((ent) => (
            <div key={ent.code} className="flex items-center justify-between text-sm">
              <span className="flex items-center gap-1.5 text-muted-foreground">
                <Check className="h-3.5 w-3.5 shrink-0 text-green-500" />
                {ent.name}
              </span>
              <span className="font-medium text-xs">
                {ent.type === "BOOLEAN"
                  ? "✓"
                  : ent.quota === -1
                    ? "无限"
                    : `${ent.quota.toLocaleString()}${ent.unit ?? ""}${CYCLE_LABEL[ent.resetCycle]}`}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default function PricingPage() {
  const [billingCycle, setBillingCycle] = useState<"monthly" | "yearly">("yearly")
  const [openFaq, setOpenFaq] = useState<number | null>(null)
  const { data: plans, isLoading } = useSubscriptionPlans()
  const { mutate: subscribe, isPending } = useSubscribe()
  const { faq: faqItems } = useMemberFaq()

  const handleSubscribe = (planCode: string) => {
    subscribe(
      { planCode, billingCycle },
      {
        onSuccess: (data) => notify.success(`订单已创建：${data.orderNo}`),
        onError: () => notify.error("订阅失败，请重试")
      }
    )
  }

  return (
    <div className="mx-auto max-w-6xl space-y-12 px-4 py-10">
      {/* 标题 */}
      <div className="text-center">
        <h1 className="font-bold text-3xl">选择适合您的套餐</h1>
        <p className="mt-2 text-muted-foreground">按年订阅最高可享受更多积分优惠</p>
      </div>

      {/* 按月/按年切换 */}
      <div className="flex justify-center">
        <div className="flex rounded-full border bg-muted p-1">
          <button
            type="button"
            className={[
              "rounded-full px-5 py-1.5 font-medium text-sm transition-colors",
              billingCycle === "monthly"
                ? "bg-background text-foreground shadow-sm"
                : "text-muted-foreground"
            ].join(" ")}
            onClick={() => setBillingCycle("monthly")}
          >
            按月订阅
          </button>
          <button
            type="button"
            className={[
              "relative rounded-full px-5 py-1.5 font-medium text-sm transition-colors",
              billingCycle === "yearly"
                ? "bg-background text-foreground shadow-sm"
                : "text-muted-foreground"
            ].join(" ")}
            onClick={() => setBillingCycle("yearly")}
          >
            按年订阅
            <Badge className="absolute -top-2 -right-2 scale-75 bg-amber-500 text-white">
              省更多
            </Badge>
          </button>
        </div>
      </div>

      {/* 套餐卡片 */}
      {isLoading ? (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={`sk-${i}`} className="h-96 rounded-2xl" />
          ))}
        </div>
      ) : !plans?.length ? (
        <p className="py-16 text-center text-muted-foreground">暂无可用套餐</p>
      ) : (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {plans.map((plan) => (
            <PlanCard
              key={plan.id}
              plan={plan}
              billingCycle={billingCycle}
              onSubscribe={handleSubscribe}
              isPending={isPending}
            />
          ))}
        </div>
      )}

      {/* FAQ */}
      <div className="mx-auto max-w-2xl">
        <h2 className="mb-6 text-center font-bold text-2xl">订阅与积分常见问题</h2>
        <div className="space-y-2">
          {faqItems.map((item, i) => (
            <div key={`faq-${i}`} className="rounded-xl border">
              <button
                type="button"
                className="flex w-full items-center justify-between px-5 py-4 text-left font-medium text-sm"
                onClick={() => setOpenFaq(openFaq === i ? null : i)}
              >
                <span>
                  <span className="mr-3 text-muted-foreground">{i + 1}</span>
                  {item.q}
                </span>
                <span className="ml-4 shrink-0 text-muted-foreground">
                  {openFaq === i ? "−" : "+"}
                </span>
              </button>
              {openFaq === i && (
                <p className="border-t px-5 py-4 text-muted-foreground text-sm">{item.a}</p>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
