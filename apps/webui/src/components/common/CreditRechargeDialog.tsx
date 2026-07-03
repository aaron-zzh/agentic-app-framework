"use client"

import { useQueryClient } from "@tanstack/react-query"
import Image from "next/image"
import QRCode from "qrcode"
import { useEffect, useRef, useState } from "react"
import { LottieDialog } from "@/components/common/LottieDialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import { backendApi } from "@/lib/api/rest/backend-client"
import type { CreditPackageVO, PayOrderVO } from "@/lib/api/rest/billing/plans"
import { restEndpoints } from "@/lib/api/rest/endpoints"
import { notify } from "@/lib/notification"
import { useCreditPackages, usePurchaseCredits } from "@/lib/queries/use-billing-plans"
import { invalidateCreditQueries } from "@/lib/queries/use-credits"

const IS_DEV = process.env.NODE_ENV === "development"

type Channel = "wx_native" | "alipay_qr" | "MOCK" | "contact_service"

const CHANNELS: {
  value: Channel
  label: string
  icon?: string
  iconUrl?: string
  devOnly?: boolean
}[] = [
  { value: "wx_native", label: "微信支付", iconUrl: "/assets/brand/wechatpay.png" },
  { value: "alipay_qr", label: "支付宝", iconUrl: "/assets/brand/alipay.png" },
  { value: "contact_service", label: "联系客服", icon: "💬" },
  ...(IS_DEV ? [{ value: "MOCK" as Channel, label: "模拟（Dev）", icon: "🧪", devOnly: true }] : [])
]

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: () => void
}

function CreditIcon({ className }: { className?: string }) {
  return (
    <span className={className} aria-hidden>
      🪙
    </span>
  )
}

function PackageCard({
  pkg,
  selected,
  onClick
}: {
  pkg: CreditPackageVO
  selected: boolean
  onClick: () => void
}) {
  const total = pkg.credits + pkg.bonusCredits
  const bonusPct = pkg.bonusCredits > 0 ? Math.round((pkg.bonusCredits / pkg.credits) * 100) : 0
  return (
    <button
      type="button"
      onClick={onClick}
      className={[
        "relative w-full rounded-xl border p-4 text-left transition-all hover:border-primary/60",
        selected ? "border-primary bg-primary/5 ring-2 ring-primary/40" : "border-border bg-card"
      ].join(" ")}
    >
      {bonusPct > 0 && (
        <span className="absolute -top-2 right-3 rounded-full bg-amber-500 px-2 py-0.5 font-semibold text-[11px] text-white">
          多 {bonusPct}%
        </span>
      )}
      {pkg.recommended && !bonusPct && (
        <span className="absolute -top-2 right-3 rounded-full bg-primary px-2 py-0.5 font-semibold text-[11px] text-primary-foreground">
          推荐
        </span>
      )}
      <div className="flex items-center gap-1.5">
        <span className="font-bold text-2xl">{total.toLocaleString()}</span>
        <CreditIcon />
      </div>
      <p className="mt-0.5 text-muted-foreground text-xs">积分</p>
      <p className="mt-3 text-right font-medium text-sm">¥{(pkg.price / 100).toFixed(0)}</p>
    </button>
  )
}

/** 二维码展示 + 轮询支付状态 */
function QrStep({
  order,
  channel,
  onSuccess,
  onCancel
}: {
  order: PayOrderVO
  channel: Channel
  onSuccess: () => void
  onCancel: () => void
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    if (canvasRef.current && order.codeUrl) {
      QRCode.toCanvas(canvasRef.current, order.codeUrl, { width: 200, margin: 2 })
    }
    // 每2秒轮询订单状态
    pollRef.current = setInterval(async () => {
      try {
        const res = await backendApi.get<PayOrderVO>(restEndpoints.pay.order(order.id))
        if (res.status === 10) {
          clearInterval(pollRef.current ?? undefined)
          onSuccess()
        } else if (res.status === 30) {
          clearInterval(pollRef.current ?? undefined)
          notify.error("订单已关闭，请重新发起支付")
          onCancel()
        }
      } catch {
        /* 网络错误静默处理 */
      }
    }, 2000)
    return () => clearInterval(pollRef.current ?? undefined)
  }, [order.id, order.codeUrl, onSuccess, onCancel])

  const channelLabel = CHANNELS.find((c) => c.value === channel)?.label ?? channel

  return (
    <div className="flex flex-col items-center gap-4 py-4">
      <canvas ref={canvasRef} className="rounded-lg border" />
      <div className="text-center">
        <p className="font-medium text-sm">请用 {channelLabel} 扫码完成支付</p>
        <p className="mt-1 font-bold text-xl">¥{(order.amount / 100).toFixed(0)}</p>
        <Badge variant="outline" className="mt-2 animate-pulse text-xs">
          等待支付…
        </Badge>
      </div>
      <p className="text-[11px] text-muted-foreground">订单号：{order.merchantOrderNo}</p>
      <Button variant="ghost" size="sm" onClick={onCancel}>
        取消
      </Button>
    </div>
  )
}

export function CreditRechargeDialog({ open, onOpenChange, onSuccess }: Props) {
  const qc = useQueryClient()
  const { data: packages, isLoading } = useCreditPackages()
  const { mutate: purchase, isPending } = usePurchaseCredits()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [channel, setChannel] = useState<Channel>(IS_DEV ? "MOCK" : "wx_native")
  const [qrOrder, setQrOrder] = useState<PayOrderVO | null>(null)
  const [contactOpen, setContactOpen] = useState(false)

  const selectedPkg = packages?.find((p) => p.id === selectedId)

  const handlePurchase = () => {
    if (!selectedId) {
      notify.error("请选择充值套餐")
      return
    }
    // 联系客服渠道，直接弹窗提示
    if (channel === "contact_service") {
      setContactOpen(true)
      return
    }
    purchase(
      { packageId: selectedId, channelCode: channel },
      {
        onSuccess: (data) => {
          if (data.status === 10) {
            notify.success("充值成功！")
            onSuccess?.()
            onOpenChange(false)
          } else if (data.codeUrl) {
            setQrOrder(data)
          } else {
            notify.error("未获取到支付二维码，请检查渠道配置")
          }
        },
        onError: () => notify.error("购买失败，请重试")
      }
    )
  }

  const handleQrSuccess = () => {
    notify.success("支付成功，积分已到账！")
    invalidateCreditQueries(qc)
    onSuccess?.()
    onOpenChange(false)
    setQrOrder(null)
  }

  const handleQrCancel = () => setQrOrder(null)

  return (
    <>
      <Dialog
        open={open}
        onOpenChange={(v) => {
          if (!v) setQrOrder(null)
          onOpenChange(v)
        }}
      >
        <DialogContent className="flex max-h-[90vh] w-full max-w-[calc(100%-2rem)] flex-col gap-0 p-0 md:max-w-[640px]">
          <DialogHeader className="px-4 pt-6 pb-4 sm:px-6">
            <DialogTitle className="text-xl">积分充值</DialogTitle>
          </DialogHeader>

          <div className="flex-1 overflow-y-auto px-4 pt-2 pb-4 sm:px-6">
            {qrOrder ? (
              <QrStep
                order={qrOrder}
                channel={channel}
                onSuccess={handleQrSuccess}
                onCancel={handleQrCancel}
              />
            ) : isLoading ? (
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                {Array.from({ length: 8 }).map((_, i) => (
                  <Skeleton key={`sk-${i}`} className="h-28 rounded-xl" />
                ))}
              </div>
            ) : !packages?.length ? (
              <p className="py-12 text-center text-muted-foreground">暂无可用套餐</p>
            ) : (
              <div className="space-y-6">
                {/* 套餐列表 */}
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                  {packages.map((pkg) => (
                    <PackageCard
                      key={pkg.id}
                      pkg={pkg}
                      selected={selectedId === pkg.id}
                      onClick={() => setSelectedId(pkg.id)}
                    />
                  ))}
                </div>

                {/* 渠道选择 */}
                <div>
                  <p className="mb-2 text-muted-foreground text-xs">支付方式</p>
                  <div className="flex flex-wrap gap-2">
                    {CHANNELS.map((c) => (
                      <button
                        key={c.value}
                        type="button"
                        onClick={() => setChannel(c.value)}
                        className={[
                          "flex items-center gap-1.5 rounded-lg border px-4 py-2 text-sm transition-all",
                          channel === c.value
                            ? "border-primary bg-primary/5 font-medium text-primary"
                            : "border-border hover:border-primary/40",
                          c.devOnly ? "text-amber-600" : ""
                        ].join(" ")}
                      >
                        {c.iconUrl ? (
                          <Image
                            src={c.iconUrl}
                            alt=""
                            width={16}
                            height={16}
                            className="h-4 w-4"
                          />
                        ) : (
                          c.icon
                        )}{" "}
                        {c.label}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </div>

          {!qrOrder && (
            <div className="flex flex-col gap-3 border-t px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-0 sm:px-6">
              <p className="text-muted-foreground text-xs">*充值积分有效期 2 年，支付后不退不换</p>
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
          )}
        </DialogContent>
      </Dialog>

      <LottieDialog
        open={contactOpen}
        onOpenChange={setContactOpen}
        icon="warning"
        loop
        title="联系客服充值"
        description="请扫码联系客服完成充值，客服将在 5 分钟内为您处理。"
        confirmText="好的，我知道了"
      />
    </>
  )
}
