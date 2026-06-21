"use client"

import QRCode from "qrcode"
import { useEffect, useRef, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { backendClient } from "@/lib/api/rest/backend-client"
import type { PayOrderVO } from "@/lib/api/rest/billing/plans"
import { restEndpoints } from "@/lib/api/rest/endpoints"
import { notify } from "@/lib/notification"
import { useSubscribe } from "@/lib/queries/use-billing-plans"

const IS_DEV = process.env.NODE_ENV === "development"

type Channel = "wx_native" | "alipay_qr" | "MOCK"

const CHANNELS: { value: Channel; label: string; icon: string; devOnly?: boolean }[] = [
  { value: "wx_native", label: "微信支付", icon: "💚" },
  { value: "alipay_qr", label: "支付宝", icon: "💙" },
  ...(IS_DEV ? [{ value: "MOCK" as Channel, label: "模拟（Dev）", icon: "🧪", devOnly: true }] : [])
]

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
    pollRef.current = setInterval(async () => {
      try {
        const res = await backendClient.get<PayOrderVO>(restEndpoints.pay.order(order.id))
        if (res.data.status === 10) {
          clearInterval(pollRef.current ?? undefined)
          onSuccess()
        } else if (res.data.status === 30) {
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
    <div className="flex flex-col items-center gap-4 py-6">
      <canvas ref={canvasRef} className="rounded-lg border" />
      <div className="text-center">
        <p className="font-medium text-sm">请用 {channelLabel} 扫码完成支付</p>
        <p className="mt-1 font-bold text-2xl">¥{(order.amount / 100).toFixed(0)}</p>
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

export interface SubscriptionPayDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 待订阅的套餐信息 */
  planCode: string
  planName: string
  price: number
  billingCycle: "monthly" | "yearly"
  onSuccess?: () => void
}

export function SubscriptionPayDialog({
  open,
  onOpenChange,
  planCode,
  planName,
  price,
  billingCycle,
  onSuccess
}: SubscriptionPayDialogProps) {
  const { mutate: subscribe, isPending } = useSubscribe()
  const [channel, setChannel] = useState<Channel>(IS_DEV ? "MOCK" : "wx_native")
  const [qrOrder, setQrOrder] = useState<PayOrderVO | null>(null)

  // 弹窗关闭时重置状态
  const handleOpenChange = (v: boolean) => {
    if (!v) setQrOrder(null)
    onOpenChange(v)
  }

  const handlePay = () => {
    subscribe(
      { planCode, billingCycle, channelCode: channel },
      {
        onSuccess: (data) => {
          if (!data || (data as unknown as number) > 0) {
            // 免费套餐直接激活 或 MOCK 同步成功（旧接口返回 recordId）
            notify.success("订阅成功！")
            onSuccess?.()
            onOpenChange(false)
          } else if (data.status === 10) {
            // 同步成功（PayOrderVO）
            notify.success("订阅成功！")
            onSuccess?.()
            onOpenChange(false)
          } else if (data.codeUrl) {
            setQrOrder(data)
          } else {
            notify.error("未获取到支付二维码，请检查渠道配置")
          }
        },
        onError: () => notify.error("订阅失败，请重试")
      }
    )
  }

  const handleQrSuccess = () => {
    notify.success("支付成功，订阅已激活！")
    onSuccess?.()
    onOpenChange(false)
    setQrOrder(null)
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="w-[400px] max-w-none!">
        <DialogHeader>
          <DialogTitle>订阅 {planName}</DialogTitle>
        </DialogHeader>

        {qrOrder ? (
          <QrStep
            order={qrOrder}
            channel={channel}
            onSuccess={handleQrSuccess}
            onCancel={() => setQrOrder(null)}
          />
        ) : (
          <div className="space-y-6 py-2">
            {/* 费用摘要 */}
            <div className="rounded-xl border bg-muted/30 px-5 py-4 text-center">
              <p className="mt-1 font-bold text-3xl">
                ¥{(price / 100).toFixed(0)}
                <span className="ml-1 font-normal text-base text-muted-foreground">
                  /{billingCycle === "yearly" ? "年" : "月"}
                </span>
              </p>
            </div>

            {/* 支付方式 */}
            <div>
              <p className="mb-2 text-muted-foreground text-xs">支付方式</p>
              <div className="flex gap-2">
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
                    {c.icon} {c.label}
                  </button>
                ))}
              </div>
            </div>

            <Button size="lg" className="w-full" disabled={isPending} onClick={handlePay}>
              {isPending ? "处理中..." : `确认支付 ¥${(price / 100).toFixed(0)}`}
            </Button>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
