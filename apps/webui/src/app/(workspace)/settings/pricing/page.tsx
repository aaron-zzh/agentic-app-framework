/**
 * 套餐定价页（设置 → 订阅与积分）
 *
 * 组成：
 *  - Hero 标题 + TrustSignals
 *  - 按月/按年切换（BillingCycleToggle）
 *  - 4 列套餐卡片网格（PlanCard）——PRO 默认推荐，标记当前订阅
 *  - 完整功能对比表（PlanCompareTable，移动端隐藏）
 *  - FAQ Accordion + 免责声明
 *
 * 数据：
 *  - useSubscriptionPlans / useCurrentSubscription / useSubscribe
 *  - useMemberFaq 读 system config 的 FAQ 列表
 *
 * @author AaronZZH & Kiro
 */

"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useState } from "react"
import { Skeleton } from "@/components/ui/skeleton"
import { BillingCycleToggle } from "@/features/billing/components/BillingCycleToggle"
import { PlanCard } from "@/features/billing/components/PlanCard"
import { PlanCompareTable } from "@/features/billing/components/PlanCompareTable"
import { PricingFAQ } from "@/features/billing/components/PricingFAQ"
import { TrustSignals } from "@/features/billing/components/TrustSignals"
import type { SubscriptionPlanVO, SubscriptionSkuVO } from "@/lib/api/rest/billing"
import { useCurrentSubscription, useSubscriptionPlans } from "@/lib/api/rest/billing"
import { useMemberFaq } from "@/lib/api/rest/system"
import { APP, CONTACT } from "@/lib/config"

/** 默认推荐套餐 code，可从 plan.ext.recommended 后续改成数据驱动 */
const RECOMMENDED_PLAN_CODE = "PRO"

export default function PricingPage() {
  const router = useRouter()
  const [billingCycle, setBillingCycle] = useState<"MONTH" | "QUARTER" | "YEAR">("YEAR")
  const { data: plans, isLoading } = useSubscriptionPlans()
  const { data: currentSub } = useCurrentSubscription()
  const { faq: faqItems } = useMemberFaq()
  const currentPlanCode = currentSub?.status === "ACTIVE" ? (currentSub.planCode ?? "FREE") : "FREE"
  const currentPlan = plans?.find((plan) => plan.code === currentPlanCode) ?? null

  function handleSubscribe(_sku: SubscriptionSkuVO, _plan: SubscriptionPlanVO) {
    router.push("/studio/me/membership")
  }

  return (
    <div className="mx-auto max-w-6xl space-y-12 px-4 py-10 sm:py-14">
      {/* Hero */}
      <header className="space-y-5 text-center">
        <h1 className="font-bold text-3xl tracking-tight sm:text-4xl">选择适合您的套餐</h1>
        <p className="mx-auto max-w-xl text-base text-muted-foreground">
          月付、季付、年付均为独立明示价格；购买、升级、续费和降级请前往会员中心。
        </p>
        <TrustSignals />
      </header>

      <BillingCycleToggle value={billingCycle} onChange={setBillingCycle} />

      {/* 套餐卡片 */}
      {isLoading ? (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={`pricing-skeleton-${i}`} className="h-[480px] rounded-2xl" />
          ))}
        </div>
      ) : !plans?.length ? (
        <p className="py-16 text-center text-muted-foreground">暂无可用套餐</p>
      ) : (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 xl:grid-cols-4">
          {plans.map((plan) => (
            <PlanCard
              key={plan.id}
              plan={plan}
              billingCycle={billingCycle}
              isRecommended={plan.code === RECOMMENDED_PLAN_CODE}
              currentPlanCode={currentPlanCode}
              currentSkuCode={currentSub?.skuCode}
              currentPlanSort={currentPlan?.sort ?? null}
              onSubscribe={handleSubscribe}
              onDowngrade={handleSubscribe}
              isPending={false}
            />
          ))}
        </div>
      )}

      {/* 功能对比表（仅桌面） */}
      {!isLoading && plans && plans.length > 1 && (
        <section className="space-y-4">
          <h2 className="text-center font-bold text-2xl">所有功能对比</h2>
          <p className="text-center text-muted-foreground text-sm">
            完整对比各套餐权益，帮助您做出选择
          </p>
          <PlanCompareTable plans={plans} recommendedCode={RECOMMENDED_PLAN_CODE} />
        </section>
      )}

      {/* FAQ + 免责声明 */}
      <section className="mx-auto max-w-3xl space-y-8">
        <div className="space-y-2 text-center">
          <h2 className="font-bold text-2xl">订阅与积分常见问题</h2>
          <p className="text-muted-foreground text-sm">仍有疑问？欢迎通过下方渠道联系我们</p>
        </div>

        <PricingFAQ items={faqItems} />

        <div className="space-y-3 rounded-xl border bg-muted/30 px-5 py-2">
          <h3 className="font-semibold text-base">免责声明与联系方式</h3>
          <p className="text-[12px] text-muted-foreground leading-relaxed">
            如有关于<span className="font-medium text-foreground">订阅或积分</span>
            的问题，欢迎通过邮箱{" "}
            <Link
              href={`mailto:${CONTACT.email}`}
              className="font-medium text-primary hover:underline"
            >
              {CONTACT.email}
            </Link>{" "}
            或微信 <span className="font-medium text-foreground">{CONTACT.wechatId}</span>{" "}
            联系我们，也可前往{" "}
            <Link href="/contact" className="font-medium text-primary hover:underline">
              联系我们
            </Link>{" "}
            页面留言。
          </p>
          <p className="text-[12px] text-muted-foreground leading-relaxed">
            {APP.name} 会根据产品优化与用户体验需要，不断调整功能、价格、订阅方案及积分政策。
            上述内容仅供参考，可能会在提前通知或不提前通知的情况下进行变更。
            如出现争议或不一致情况，以{" "}
            <Link href="/terms" className="font-medium text-primary hover:underline">
              服务条款
            </Link>
            、
            <Link href="/privacy" className="font-medium text-primary hover:underline">
              隐私政策
            </Link>
            、系统记录与实际账单数据为准。
          </p>
        </div>
      </section>
    </div>
  )
}
