"use client"

import { useQueryClient } from "@tanstack/react-query"
import Image from "next/image"
import QRCode from "qrcode"
import { useEffect, useRef, useState } from "react"
import { LottieDialog } from "@/components/common/LottieDialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { buildApiUrl } from "@/lib/api/config"
import { backendApi } from "@/lib/api/rest/backend-client"
import type { PayOrderVO } from "@/lib/api/rest/billing/plans"
import { restEndpoints } from "@/lib/api/rest/endpoints"
import { notify } from "@/lib/notification"
import { useSubscribe } from "@/lib/queries/use-billing-plans"
import { invalidateCreditQueries } from "@/lib/queries/use-credits"

const IS_DEV = process.env.NODE_ENV === "development"

type Channel = "wx_native" | "alipay_pc" | "alipay_wap" | "alipay_qr" | "MOCK" | "contact_service"

const CHANNELS: {
  value: Channel
  label: string
  icon?: string
  iconUrl?: string
  devOnly?: boolean
  /** 仅移动端展示（手机浏览器整页跳转支付，不适用于 PC） */
  mobileOnly?: boolean
  /** 仅 PC 端展示（整页跳转，移动端用 alipay_wap 整页跳转替代） */
  desktopOnly?: boolean
  /** 微信内置浏览器会拦截跳转支付宝域名，此渠道在微信内不可用，需用手机网站支付兜底 */
  hiddenInWechat?: boolean
  /**
   * 默认隐藏——当面付（扫码支付）需支付宝开放平台单独签约权限，未签约前调用会报
   * ACQ.ACCESS_FORBIDDEN。签约完成后移除此标记即可恢复展示。
   */
  hidden?: boolean
}[] = [
  {
    value: "wx_native",
    label: "微信支付",
    iconUrl: "/assets/brand/wechatpay.png",
    // 临时隐藏，默认展示支付宝支付；恢复时移除此标记即可
    hidden: true
  },
  {
    value: "alipay_pc",
    label: "支付宝",
    iconUrl: "/assets/brand/alipay.png",
    desktopOnly: true,
    hiddenInWechat: true
  },
  {
    value: "alipay_wap",
    label: "支付宝",
    iconUrl: "/assets/brand/alipay.png",
    mobileOnly: true
  },
  {
    value: "alipay_qr",
    label: "支付宝",
    iconUrl: "/assets/brand/alipay.png",
    desktopOnly: true,
    hidden: true
  },
  { value: "contact_service", label: "联系客服", icon: "💬" },
  ...(IS_DEV ? [{ value: "MOCK" as Channel, label: "模拟（Dev）", icon: "🧪", devOnly: true }] : [])
]

/** 微信内置浏览器会拦截跳转到支付宝域名，需检测 UA 并降级为扫码支付 */
function isWechatBrowser(): boolean {
  if (typeof navigator === "undefined") return false
  return /MicroMessenger/i.test(navigator.userAgent)
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

  useEffect(() => {
    if (canvasRef.current && order.codeUrl) {
      QRCode.toCanvas(canvasRef.current, order.codeUrl, { width: 200, margin: 2 })
    }
  }, [order.codeUrl])

  usePayOrderPolling(order.id, onSuccess, onCancel)

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

/**
 * 支付宝页面跳转类支付（电脑网站支付/手机网站支付）等待步骤。
 *
 * <p>跳转链接已在新标签页打开，不替换当前页面；当前页面轮询订单状态感知支付完成
 * （后端 PayOrderSyncTask 每 30 秒主动查单兜底，无需等待异步回调）。
 */
function WaitingRedirectStep({
  order,
  onSuccess,
  onCancel
}: {
  order: PayOrderVO
  onSuccess: () => void
  onCancel: () => void
}) {
  usePayOrderPolling(order.id, onSuccess, onCancel)

  return (
    <div className="flex flex-col items-center gap-4 py-6">
      <p className="text-center font-medium text-sm">
        已在新标签页打开支付宝收银台，完成支付后本页面将自动更新
      </p>
      <p className="mt-1 font-bold text-2xl">¥{(order.amount / 100).toFixed(0)}</p>
      <Badge variant="outline" className="mt-2 animate-pulse text-xs">
        等待支付…
      </Badge>
      <p className="text-[11px] text-muted-foreground">订单号：{order.merchantOrderNo}</p>
      <Button variant="ghost" size="sm" onClick={onCancel}>
        取消
      </Button>
    </div>
  )
}

/** 每 2 秒轮询一次支付单状态，成功/关闭时触发对应回调；页面从后台切回前台时立即查一次兜底 */
function usePayOrderPolling(orderId: number, onSuccess: () => void, onCancel: () => void) {
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    const checkOnce = async () => {
      try {
        const res = await backendApi.get<PayOrderVO>(restEndpoints.pay.order(orderId))
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
    }

    pollRef.current = setInterval(checkOnce, 2000)

    // 浏览器对后台标签页的定时器限流/暂停会导致轮询失效（尤其手机网站支付新标签页跳转场景），
    // 页面重新可见时（用户从支付宝页面切回）立即补查一次，不等下一个周期
    const onVisible = () => {
      if (document.visibilityState === "visible") checkOnce()
    }
    document.addEventListener("visibilitychange", onVisible)

    return () => {
      clearInterval(pollRef.current ?? undefined)
      document.removeEventListener("visibilitychange", onVisible)
    }
  }, [orderId, onSuccess, onCancel])
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
  const qc = useQueryClient()
  const { mutate: subscribe, isPending } = useSubscribe()
  const [isMobile, setIsMobile] = useState(false)
  const [isWechat, setIsWechat] = useState(false)
  const [channel, setChannel] = useState<Channel>(IS_DEV ? "MOCK" : "alipay_pc")
  const [qrOrder, setQrOrder] = useState<PayOrderVO | null>(null)
  const [contactOpen, setContactOpen] = useState(false)

  // 检测移动端 + 微信内置浏览器：支付宝渠道按设备互斥展示（PC → 电脑网站支付；移动端 → 手机网站支付整页跳转）
  useEffect(() => {
    const check = () => setIsMobile(window.innerWidth < 768)
    check()
    window.addEventListener("resize", check)
    setIsWechat(isWechatBrowser())
    return () => window.removeEventListener("resize", check)
  }, [])

  const visibleChannels = CHANNELS.filter((c) => {
    if (c.hidden) return false
    if (c.hiddenInWechat && isWechat) return false
    if (c.desktopOnly && isMobile) return false
    if (c.mobileOnly && !isMobile) return false
    return true
  })

  // 移动端默认渠道为电脑网站支付时不可见，自动切换到当前设备可见的第一个渠道（微信支付临时隐藏期间默认支付宝）
  useEffect(() => {
    if (IS_DEV) return
    if (!visibleChannels.some((c) => c.value === channel)) {
      const fallback = visibleChannels[0]?.value
      if (fallback) setChannel(fallback)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isMobile, isWechat])


  // 弹窗关闭时重置状态
  const handleOpenChange = (v: boolean) => {
    if (!v) setQrOrder(null)
    onOpenChange(v)
  }

  const handlePay = () => {
    // 联系客服渠道，直接弹窗提示
    if (channel === "contact_service") {
      setContactOpen(true)
      return
    }
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
          } else if ((channel === "alipay_wap" || channel === "alipay_pc") && data.codeUrl) {
            // 手机网站支付/电脑网站支付：新标签页打开跳转链接，不替换当前页面，
            // 当前页面轮询订单状态感知支付完成。codeUrl 为后端相对路径，需拼上后端 origin。
            window.open(buildApiUrl(data.codeUrl), "_blank")
            setQrOrder(data)
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
    invalidateCreditQueries(qc)
    qc.invalidateQueries({ queryKey: ["billing", "subscription", "current"] })
    qc.invalidateQueries({ queryKey: ["billing", "entitlement", "quotas"] })
    onSuccess?.()
    onOpenChange(false)
    setQrOrder(null)
  }

  return (
    <>
      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent className="w-[400px] max-w-none!">
          <DialogHeader>
            <DialogTitle>订阅 {planName}</DialogTitle>
          </DialogHeader>

          {qrOrder ? (
            channel === "alipay_wap" || channel === "alipay_pc" ? (
              <WaitingRedirectStep
                order={qrOrder}
                onSuccess={handleQrSuccess}
                onCancel={() => setQrOrder(null)}
              />
            ) : (
              <QrStep
                order={qrOrder}
                channel={channel}
                onSuccess={handleQrSuccess}
                onCancel={() => setQrOrder(null)}
              />
            )
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
                  {visibleChannels.map((c) => (
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
                        <Image src={c.iconUrl} alt="" width={16} height={16} className="h-4 w-4" />
                      ) : (
                        c.icon
                      )}{" "}
                      {c.label}
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

      <LottieDialog
        open={contactOpen}
        onOpenChange={setContactOpen}
        icon="warning"
        loop
        title="联系客服充值"
        description="在线支付即将开放，请扫码联系客服完成充值，客服将在 5 分钟内为您处理。"
        confirmText="好的，我知道了"
      />
    </>
  )
}
