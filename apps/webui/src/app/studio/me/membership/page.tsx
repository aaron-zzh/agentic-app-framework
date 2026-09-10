"use client"

import Link from "next/link"
import { useMemo, useState } from "react"
import { LottieIcon } from "@/components/animate"
import { GlassCard, GlassCardBody, NeonChip, SectionHaze } from "@/components/studio"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { BillingCycleToggle } from "@/features/billing/components/BillingCycleToggle"
import { PlanCard } from "@/features/billing/components/PlanCard"
import { PricingFAQ } from "@/features/billing/components/PricingFAQ"
import { SubscriptionPayDialog } from "@/features/billing/components/SubscriptionPayDialog"
import type { BillingCycle, SubscriptionPlanVO, SubscriptionSkuVO } from "@/lib/api/rest/billing"
import {
  useCancelPendingDowngrade,
  useCancelSubscription,
  useCurrentSubscription,
  useDowngradeSubscription,
  useSubscriptionPlans
} from "@/lib/api/rest/billing"
import { useMemberFaq } from "@/lib/api/rest/system"
import { APP, CONTACT } from "@/lib/config"
import { notify } from "@/lib/notification"

type SaleCycle = Exclude<BillingCycle, "PERPETUAL">
type PaymentAction = "subscribe" | "upgrade" | "renew"

interface SelectedPayment {
  plan: SubscriptionPlanVO
  sku: SubscriptionSkuVO
  action: PaymentAction
}

type ConfirmationState =
  | { type: "downgrade"; plan: SubscriptionPlanVO; sku: SubscriptionSkuVO }
  | { type: "cancel" }
  | { type: "cancelPending" }
  | null

const CYCLE_TEXT: Record<BillingCycle, string> = {
  MONTH: "月付",
  QUARTER: "季付",
  YEAR: "年付",
  PERPETUAL: "免费"
}

function formatDate(value: string | null): string {
  if (!value) return "长期有效"
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value))
}

export default function StudioMeMembershipPage() {
  const [billingCycle, setBillingCycle] = useState<SaleCycle>("YEAR")
  const [selectedPayment, setSelectedPayment] = useState<SelectedPayment | null>(null)
  const [confirmation, setConfirmation] = useState<ConfirmationState>(null)
  const { data: plans, isLoading: plansLoading } = useSubscriptionPlans()
  const { data: currentSub } = useCurrentSubscription()
  const cancelSubscription = useCancelSubscription()
  const downgradeSubscription = useDowngradeSubscription()
  const cancelPending = useCancelPendingDowngrade()
  const { faq: faqItems } = useMemberFaq()

  const currentPlan = useMemo(
    () => plans?.find((plan) => plan.code === currentSub?.planCode) ?? null,
    [plans, currentSub?.planCode]
  )
  const currentPlanCode = currentSub?.status === "ACTIVE" ? (currentSub.planCode ?? "FREE") : "FREE"
  const actionPending =
    cancelSubscription.isPending || downgradeSubscription.isPending || cancelPending.isPending
  const confirmationPending =
    confirmation?.type === "downgrade"
      ? downgradeSubscription.isPending
      : confirmation?.type === "cancel"
        ? cancelSubscription.isPending
        : confirmation?.type === "cancelPending"
          ? cancelPending.isPending
          : false

  const handleSubscribe = (sku: SubscriptionSkuVO, plan: SubscriptionPlanVO) => {
    const action: PaymentAction =
      currentSub?.skuCode === sku.skuCode
        ? "renew"
        : currentPlan && plan.sort > currentPlan.sort
          ? "upgrade"
          : "subscribe"
    setSelectedPayment({ plan, sku, action })
  }

  const handleDowngrade = (sku: SubscriptionSkuVO, plan: SubscriptionPlanVO) => {
    setConfirmation({ type: "downgrade", plan, sku })
  }

  const handleCancel = () => {
    setConfirmation({ type: "cancel" })
  }

  const handleCancelPending = () => {
    setConfirmation({ type: "cancelPending" })
  }

  const handleConfirmAction = () => {
    if (!confirmation) return

    if (confirmation.type === "downgrade") {
      downgradeSubscription.mutate(confirmation.sku.skuCode, {
        onSuccess: () => {
          setConfirmation(null)
          notify.success("降级申请已保存，将在当前已付周期结束后处理")
        },
        onError: () => notify.error("降级申请失败，请重试")
      })
      return
    }

    if (confirmation.type === "cancel") {
      cancelSubscription.mutate(undefined, {
        onSuccess: () => {
          setConfirmation(null)
          notify.success("已取消，到期前仍可继续使用或手动续费")
        },
        onError: () => notify.error("取消失败，请重试")
      })
      return
    }

    cancelPending.mutate(undefined, {
      onSuccess: () => {
        setConfirmation(null)
        notify.success("已撤销降级申请")
      },
      onError: () => notify.error("撤销失败，请重试")
    })
  }

  return (
    <div className="relative mx-auto max-w-6xl p-6">
      <SectionHaze variant="soft" />
      <div className="relative space-y-8">
        <div className="flex items-center gap-3">
          <LottieIcon name="premium" width={80} height={80} loop />
          <h1 className="font-semibold text-xl">会员套餐</h1>
          {currentSub?.status === "ACTIVE" && (
            <NeonChip tone="amber" size="sm">
              {currentSub.planName ?? currentPlanCode}
            </NeonChip>
          )}
        </div>

        {currentSub?.status === "ACTIVE" && (
          <GlassCard glow="none">
            <GlassCardBody className="space-y-4">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <h2 className="font-semibold">当前订阅</h2>
                  <p className="mt-1 text-muted-foreground text-sm">
                    {currentSub.planName} ·{" "}
                    {currentSub.billingCycle ? CYCLE_TEXT[currentSub.billingCycle] : ""}
                  </p>
                  <p className="text-muted-foreground text-sm">
                    {formatDate(currentSub.startAt)} — {formatDate(currentSub.endAt)}
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  {currentSub.planCode !== "FREE" && !currentSub.cancelledAt && (
                    <Button variant="outline" disabled={actionPending} onClick={handleCancel}>
                      取消订阅
                    </Button>
                  )}
                  {currentSub.pendingSkuCode && (
                    <Button
                      variant="outline"
                      disabled={actionPending}
                      onClick={handleCancelPending}
                    >
                      撤销降级
                    </Button>
                  )}
                </div>
              </div>
              {currentSub.cancelledAt && (
                <p className="rounded-lg bg-amber-500/10 px-3 py-2 text-amber-700 text-sm dark:text-amber-300">
                  已设置在当前周期到期后结束付费套餐。当前权益保留至 {formatDate(currentSub.endAt)}
                  ，仍可手动续费当前 SKU。
                </p>
              )}
              {currentSub.pendingSkuCode && (
                <p className="rounded-lg bg-blue-500/10 px-3 py-2 text-blue-700 text-sm dark:text-blue-300">
                  已预约降级至 {currentSub.pendingPlanName}（
                  {currentSub.pendingBillingCycle ? CYCLE_TEXT[currentSub.pendingBillingCycle] : ""}
                  ）。 付费目标不会自动扣款，未主动支付不能开通。
                </p>
              )}
            </GlassCardBody>
          </GlassCard>
        )}

        <BillingCycleToggle value={billingCycle} onChange={setBillingCycle} />

        {plansLoading ? (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={`plan-sk-${index}`} className="h-[420px] rounded-2xl" />
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
                currentSkuCode={currentSub?.skuCode}
                currentPlanSort={currentPlan?.sort ?? null}
                isRecommended={plan.code === "PRO"}
                onSubscribe={handleSubscribe}
                onDowngrade={handleDowngrade}
                isPending={actionPending}
              />
            ))}
          </div>
        )}

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
            如有关于订阅或积分的问题，可通过邮箱{" "}
            <Link
              href={`mailto:${CONTACT.email}`}
              className="font-medium text-primary hover:underline"
            >
              {CONTACT.email}
            </Link>{" "}
            或微信 <span className="font-medium text-foreground">{CONTACT.wechatId}</span>{" "}
            联系我们。
          </p>
          <p className="text-[12px] text-muted-foreground leading-relaxed">
            {APP.name} 会根据产品优化调整功能、价格、订阅方案及积分政策，以系统记录与实际账单为准。
          </p>
        </div>
      </div>

      {selectedPayment && (
        <SubscriptionPayDialog
          open
          onOpenChange={(open) => {
            if (!open) setSelectedPayment(null)
          }}
          skuCode={selectedPayment.sku.skuCode}
          planName={`${selectedPayment.plan.name}（${CYCLE_TEXT[selectedPayment.sku.billingCycle]}）`}
          price={selectedPayment.sku.price}
          billingCycle={selectedPayment.sku.billingCycle}
          action={selectedPayment.action}
          onSuccess={() => setSelectedPayment(null)}
        />
      )}

      <AlertDialog
        open={confirmation !== null}
        onOpenChange={(open) => {
          if (!open && !confirmationPending) setConfirmation(null)
        }}
      >
        <AlertDialogContent aria-busy={confirmationPending}>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {confirmation?.type === "downgrade"
                ? confirmation.sku.billingCycle === "PERPETUAL"
                  ? "确认降级到免费套餐？"
                  : `确认降级至 ${confirmation.plan.name} · ${CYCLE_TEXT[confirmation.sku.billingCycle]}？`
                : confirmation?.type === "cancel"
                  ? "确认取消当前订阅？"
                  : "撤销待生效降级？"}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {confirmation?.type === "downgrade"
                ? confirmation.sku.billingCycle === "PERPETUAL"
                  ? `当前权益保留至 ${formatDate(currentSub?.endAt ?? null)}，到期后切换至免费套餐，不产生费用。`
                  : `仅记录到期后的目标，不立即切换、不收费。当前权益保留至 ${formatDate(currentSub?.endAt ?? null)}。到期不会发起扣款；如需继续使用该规格，请在到期后手动购买，否则将回到免费套餐。`
                : confirmation?.type === "cancel"
                  ? `当前权益保留至 ${formatDate(currentSub?.endAt ?? null)}，本次操作不退款。到期前仍可续费当前规格；到期未续费将回到免费套餐。`
                  : `将移除降级至 ${currentSub?.pendingPlanName ?? "目标套餐"}${
                      currentSub?.pendingBillingCycle
                        ? ` · ${CYCLE_TEXT[currentSub.pendingBillingCycle]}`
                        : ""
                    } 的安排，当前套餐与到期时间保持不变。`}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={confirmationPending} onClick={() => setConfirmation(null)}>
              {confirmation?.type === "cancel"
                ? "暂不取消"
                : confirmation?.type === "cancelPending"
                  ? "保留安排"
                  : "暂不降级"}
            </AlertDialogCancel>
            <AlertDialogAction disabled={confirmationPending} onClick={handleConfirmAction}>
              {confirmationPending
                ? confirmation?.type === "cancel"
                  ? "取消中…"
                  : confirmation?.type === "cancelPending"
                    ? "撤销中…"
                    : "降级中…"
                : confirmation?.type === "cancel"
                  ? "确认取消"
                  : confirmation?.type === "cancelPending"
                    ? "确认撤销"
                    : "确认降级"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
