"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import { notify } from "@/lib/notification"
import { useCreditPackages, usePurchaseCredits } from "@/lib/queries/use-billing-plans"
import type { CreditPackageVO } from "@/lib/api/rest/billing/plans"

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
}

/** 积分图标 */
function CreditIcon({ className }: { className?: string }) {
  return (
    <span className={className} aria-hidden>
      🪙
    </span>
  )
}

/** 单个套餐卡片 */
function PackageCard({
  pkg,
  selected,
  onClick
}: {
  pkg: CreditPackageVO
  selected: boolean
  onClick: () => void
}) {
  const totalCredits = pkg.credits + pkg.bonusCredits
  const bonusPct = pkg.bonusCredits > 0 ? Math.round((pkg.bonusCredits / pkg.credits) * 100) : 0

  return (
    <button
      type="button"
      onClick={onClick}
      className={[
        "relative rounded-xl border p-4 text-left transition-all",
        "hover:border-primary/60",
        selected
          ? "border-primary ring-2 ring-primary/40 bg-primary/5"
          : "border-border bg-card"
      ].join(" ")}
    >
      {bonusPct > 0 && (
        <span className="absolute -top-2 right-3 rounded-full bg-amber-500 px-2 py-0.5 text-[11px] font-semibold text-white">
          多 {bonusPct}%
        </span>
      )}
      {pkg.recommended && !bonusPct && (
        <span className="absolute -top-2 right-3 rounded-full bg-primary px-2 py-0.5 text-[11px] font-semibold text-primary-foreground">
          推荐
        </span>
      )}
      <div className="flex items-center gap-1.5">
        <span className="font-bold text-2xl">{totalCredits.toLocaleString()}</span>
        <CreditIcon />
      </div>
      <p className="mt-0.5 text-muted-foreground text-xs">积分</p>
      <p className="mt-3 text-right font-medium text-sm">
        ¥{(pkg.price / 100).toFixed(0)}
      </p>
    </button>
  )
}

export function CreditRechargeDialog({ open, onOpenChange }: Props) {
  const { data: packages, isLoading } = useCreditPackages()
  const { mutate: purchase, isPending } = usePurchaseCredits()
  const [selectedId, setSelectedId] = useState<string | null>(null)

  // 按 group 分组
  const groups = packages
    ? Array.from(new Set(packages.map((p) => p.group ?? "会员积分充值")))
    : []

  const selectedPkg = packages?.find((p) => p.id === selectedId)

  const handlePurchase = () => {
    if (!selectedId) {
      notify.error("请选择充值套餐")
      return
    }
    purchase(selectedId, {
      onSuccess: (data) => {
        notify.success(`订单已创建：${data.orderNo}`)
        onOpenChange(false)
      },
      onError: () => notify.error("购买失败，请重试")
    })
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl gap-0 p-0">
        <DialogHeader className="px-6 pt-6 pb-4">
          <DialogTitle className="text-xl">积分充值</DialogTitle>
        </DialogHeader>

        <div className="max-h-[70vh] overflow-y-auto px-6 pb-4">
          {isLoading ? (
            <div className="grid grid-cols-4 gap-3">
              {Array.from({ length: 8 }).map((_, i) => (
                <Skeleton key={`sk-${i}`} className="h-28 rounded-xl" />
              ))}
            </div>
          ) : !packages?.length ? (
            <p className="py-12 text-center text-muted-foreground">暂无可用套餐</p>
          ) : (
            <div className="space-y-6">
              {groups.map((group) => (
                <div key={group}>
                  <p className="mb-3 font-medium text-sm text-muted-foreground">{group}</p>
                  <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                    {packages
                      .filter((p) => (p.group ?? "会员积分充值") === group)
                      .map((pkg) => (
                        <PackageCard
                          key={pkg.id}
                          pkg={pkg}
                          selected={selectedId === pkg.id}
                          onClick={() => setSelectedId(pkg.id)}
                        />
                      ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 底部操作栏 */}
        <div className="flex items-center justify-between border-t px-6 py-4">
          <p className="text-muted-foreground text-xs">
            *充值积分有效期 2 年，支付后不退不换
          </p>
          <Button
            size="lg"
            className="min-w-32"
            disabled={!selectedId || isPending}
            onClick={handlePurchase}
          >
            {isPending
              ? "处理中..."
              : selectedPkg
                ? `去支付 ¥${(selectedPkg.price / 100).toFixed(0)}`
                : "去支付"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
