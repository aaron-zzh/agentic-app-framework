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

import Image from "next/image"
import Link from "next/link"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import { BillingCycleToggle } from "@/features/billing/components/BillingCycleToggle"
import { PlanCard } from "@/features/billing/components/PlanCard"
import { PlanCompareTable } from "@/features/billing/components/PlanCompareTable"
import { PricingFAQ } from "@/features/billing/components/PricingFAQ"
import { TrustSignals } from "@/features/billing/components/TrustSignals"
import { APP, CONTACT } from "@/lib/config"
import { useCurrentSubscription, useSubscriptionPlans } from "@/lib/queries/use-billing-plans"
import { useMemberFaq, useWechatQrImage } from "@/lib/queries/use-system-config"

/** 默认推荐套餐 code，可从 plan.ext.recommended 后续改成数据驱动 */
const RECOMMENDED_PLAN_CODE = "PRO"

export default function PricingPage() {
  const [billingCycle, setBillingCycle] = useState<"monthly" | "yearly">("yearly")
  const { data: plans, isLoading } = useSubscriptionPlans()
  const { data: currentSub } = useCurrentSubscription()
  const { faq: faqItems } = useMemberFaq()
  const { data: wechatQrImage } = useWechatQrImage()

  const currentPlanCode = currentSub?.status === "ACTIVE" ? (currentSub.planCode ?? "FREE") : "FREE"

  const [contactOpen, setContactOpen] = useState(false)

  function handleSubscribe(_planCode: string) {
    setContactOpen(true)
  }

  return (
    <div className="mx-auto max-w-6xl space-y-12 px-4 py-10 sm:py-14">
      {/* Hero */}
      <header className="space-y-5 text-center">
        <h1 className="font-bold text-3xl tracking-tight sm:text-4xl">选择适合您的套餐</h1>
        <p className="mx-auto max-w-xl text-base text-muted-foreground">
          按年订阅享受更多积分优惠，随时升级或降级，灵活满足您的成长需要。
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
              onSubscribe={handleSubscribe}
              isPending={false}
            />
          ))}
        </div>
      )}

      {/* 联系客服弹窗 */}
      <Dialog open={contactOpen} onOpenChange={setContactOpen}>
        <DialogContent className="w-[360px] max-w-none!">
          <DialogHeader>
            <DialogTitle>联系客服开通</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-2 text-muted-foreground text-sm">
            <p>套餐订阅功能正在接入中，请联系客服人工开通。</p>
            {wechatQrImage && (
              <div className="flex justify-center">
                <Image
                  src={wechatQrImage}
                  alt="微信客服二维码"
                  width={160}
                  height={160}
                  className="rounded-lg border object-contain"
                  unoptimized
                />
              </div>
            )}
            <div className="space-y-2 rounded-xl border bg-muted/30 px-4 py-3">
              <p>
                微信：<span className="font-medium text-foreground">{CONTACT.wechatId}</span>
              </p>
              <p>
                邮箱：
                <Link
                  href={`mailto:${CONTACT.email}`}
                  className="font-medium text-primary hover:underline"
                >
                  {CONTACT.email}
                </Link>
              </p>
            </div>
          </div>
          <Button className="w-full" onClick={() => setContactOpen(false)}>
            知道了
          </Button>
        </DialogContent>
      </Dialog>

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
