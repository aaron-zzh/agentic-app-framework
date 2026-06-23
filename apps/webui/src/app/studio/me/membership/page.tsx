/**
 * /studio/me/membership——会员套餐与模型收费（B3 重点）
 * - 上部：4 档套餐卡片
 * - 中部：模型能力 + 收费标准明细表（关键透明度功能）
 * - 下部：FAQ
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import {
  GlassCard,
  GlassCardBody,
  NeonChip,
  SectionHaze
} from "@/components/studio"
import { Skeleton } from "@/components/ui/skeleton"
import { BillingCycleToggle } from "@/features/billing/components/BillingCycleToggle"
import { PlanCard } from "@/features/billing/components/PlanCard"
import { PricingFAQ } from "@/features/billing/components/PricingFAQ"
import { useMemberFaq } from "@/lib/queries/use-system-config"
import Link from "next/link"
import { APP, CONTACT } from "@/lib/config"
import { LottieIcon } from "@/components/animate"
import { useCurrentSubscription, useSubscriptionPlans } from "@/lib/queries/use-billing-plans"

export default function StudioMeMembershipPage() {
  const [billingCycle, setBillingCycle] = useState<"monthly" | "yearly">("yearly")
  const { data: plans, isLoading: plansLoading } = useSubscriptionPlans()
  const { data: currentSub } = useCurrentSubscription()
  const { faq: faqItems } = useMemberFaq()

  const currentPlanCode = currentSub?.status === "ACTIVE" ? (currentSub.planCode ?? "FREE") : "FREE"

  return (
    <div className="relative mx-auto max-w-6xl p-6">
      <SectionHaze variant="soft" />
      <div className="relative space-y-8">
        {/* 标题 */}
        <div className="flex items-center gap-3">
          <LottieIcon name="premium" width={80} height={80} loop />
          <h1 className="font-semibold text-xl">会员套餐</h1>
          {currentSub?.status === "ACTIVE" && (
            <NeonChip tone="amber" size="sm">
              {currentSub.planName ?? currentPlanCode}
            </NeonChip>
          )}
        </div>

        {/* 计费周期切换 */}
        <BillingCycleToggle value={billingCycle} onChange={setBillingCycle} />

        {/* 套餐卡片 */}
        {plansLoading ? (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={`plan-sk-${i}`} className="h-[420px] rounded-2xl" />
            ))}
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {(plans ?? []).map((plan) => (
              <PlanCard
                key={plan.id}
                plan={plan}
                billingCycle={billingCycle}
                currentPlanCode={currentPlanCode}
                isRecommended={plan.code === "PRO"}
                onSubscribe={() => {}}
                isPending={false}
              />
            ))}
          </div>
        )}

        {/* FAQ */}
        {(faqItems ?? []).length > 0 && (
          <div className="space-y-3">
            <h2 className="font-medium text-base">常见问题</h2>
            <GlassCard glow="none">
              <GlassCardBody>
                <PricingFAQ items={faqItems ?? []} />
              </GlassCardBody>
            </GlassCard>
          </div>
        )}

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
      </div>
    </div>
  )
}
