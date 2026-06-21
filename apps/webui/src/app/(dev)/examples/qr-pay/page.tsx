"use client"

import QRCode from "qrcode"
import { useCallback, useEffect, useRef, useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { backendClient } from "@/lib/api/rest/backend-client"
import { restEndpoints } from "@/lib/api/rest/endpoints"

// ==================== 类型 ====================

type Channel = "wx_native" | "alipay_qr" | "MOCK"

interface PayOrderVO {
  id: number
  merchantOrderNo: string
  amount: number
  status: number // 0=待支付 10=成功 30=已关闭
  channelCode: string
  codeUrl?: string
}

// ==================== 常量 ====================

const IS_DEV = process.env.NODE_ENV === "development"

const CHANNELS: { value: Channel; label: string; icon: string; devOnly?: boolean }[] = [
  { value: "wx_native", label: "微信支付", icon: "💚" },
  { value: "alipay_qr", label: "支付宝", icon: "💙" },
  ...(IS_DEV
    ? [{ value: "MOCK" as Channel, label: "模拟支付（Dev）", devOnly: true, icon: "🧪" }]
    : [])
]

const AMOUNTS = [100, 500, 1000, 5000]

// ==================== 子组件 ====================

function QrCanvas({ url }: { url: string }) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  useEffect(() => {
    if (!canvasRef.current || !url) return
    QRCode.toCanvas(canvasRef.current, url, { width: 200, margin: 2 })
  }, [url])
  return <canvas ref={canvasRef} className="rounded-lg border" />
}

// ==================== 主页面 ====================

export default function QrPayPage() {
  const [channel, setChannel] = useState<Channel>("wx_native")
  const [amount, setAmount] = useState(500)
  const [order, setOrder] = useState<PayOrderVO | null>(null)
  const [phase, setPhase] = useState<"idle" | "loading" | "qr" | "success" | "error">("idle")
  const [error, setError] = useState("")
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const stopPoll = useCallback(() => {
    if (pollRef.current) clearInterval(pollRef.current)
  }, [])

  const pollStatus = useCallback(
    (orderId: number) => {
      stopPoll()
      pollRef.current = setInterval(async () => {
        try {
          const res = await backendClient.get<PayOrderVO>(restEndpoints.pay.order(orderId))
          if (res.data.status === 10) {
            stopPoll()
            setOrder(res.data)
            setPhase("success")
          } else if (res.data.status === 30) {
            stopPoll()
            setPhase("error")
            setError("订单已关闭")
          }
        } catch {
          // 网络错误静默处理，继续轮询
        }
      }, 2000)
    },
    [stopPoll]
  )

  const handlePay = async () => {
    setPhase("loading")
    setError("")
    try {
      const orderNo = `QR${Date.now()}`
      const res = await backendClient.post<PayOrderVO>(restEndpoints.pay.orders, {
        merchantOrderNo: orderNo,
        subject: "扫码支付示例",
        amount,
        channelCode: channel,
        userId: 1 // 实际应从 auth context 取
      })
      setOrder(res.data)

      if (channel === "MOCK") {
        // Mock 直接成功
        setPhase("success")
      } else if (res.data.codeUrl) {
        setPhase("qr")
        pollStatus(res.data.id)
      } else {
        setPhase("error")
        setError("未获取到支付二维码，请检查渠道配置")
      }
    } catch (e: unknown) {
      setPhase("error")
      setError(e instanceof Error ? e.message : "下单失败，请重试")
    }
  }

  const handleReset = () => {
    stopPoll()
    setOrder(null)
    setPhase("idle")
    setError("")
  }

  useEffect(() => () => stopPoll(), [stopPoll])

  const selectedChannel = CHANNELS.find((c) => c.value === channel) ?? CHANNELS[0]

  return (
    <PageContainer>
      <div className="mx-auto max-w-md">
        <h1 className="mb-2 font-bold text-2xl">扫码支付示例</h1>
        <p className="mb-6 text-muted-foreground text-sm">
          微信/支付宝扫码支付，含渠道切换、二维码渲染、状态轮询。
          {IS_DEV && <span className="ml-1 text-amber-500">（Dev 模式下可用模拟支付）</span>}
        </p>

        <Card>
          <CardHeader className="pb-4">
            <CardTitle className="text-base">选择支付方式</CardTitle>
            <CardDescription>扫码支付，无需 JSSDK</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            {/* 渠道切换 */}
            <div className="flex gap-2 rounded-xl bg-muted p-1">
              {CHANNELS.map((c) => (
                <button
                  key={c.value}
                  type="button"
                  disabled={phase !== "idle" && phase !== "success"}
                  onClick={() => {
                    setChannel(c.value)
                    handleReset()
                  }}
                  className={[
                    "flex flex-1 items-center justify-center gap-1.5 rounded-lg py-2.5 text-sm transition-all",
                    channel === c.value
                      ? "bg-foreground font-medium text-background shadow"
                      : "text-muted-foreground hover:bg-muted/80",
                    c.devOnly ? "text-amber-600" : ""
                  ].join(" ")}
                >
                  {c.icon} {c.label}
                </button>
              ))}
            </div>

            {/* 金额选择 */}
            <div>
              <p className="mb-2 text-muted-foreground text-xs">选择金额</p>
              <div className="grid grid-cols-4 gap-2">
                {AMOUNTS.map((a) => (
                  <button
                    key={a}
                    type="button"
                    disabled={phase !== "idle"}
                    onClick={() => setAmount(a)}
                    className={[
                      "rounded-lg border py-2 text-center text-sm transition-all",
                      amount === a
                        ? "border-primary bg-primary/5 font-semibold text-primary"
                        : "border-border hover:border-primary/40 disabled:opacity-50"
                    ].join(" ")}
                  >
                    ¥{(a / 100).toFixed(0)}
                  </button>
                ))}
              </div>
            </div>

            <Separator />

            {/* 支付区域 */}
            {phase === "idle" && (
              <div className="flex flex-col items-center gap-4 py-4">
                <p className="text-muted-foreground text-sm">
                  {selectedChannel.icon} 使用 {selectedChannel.label} 支付
                  <span className="ml-2 font-bold text-foreground text-lg">
                    ¥{(amount / 100).toFixed(0)}
                  </span>
                </p>
                <Button className="w-full" size="lg" onClick={handlePay}>
                  生成支付二维码
                </Button>
              </div>
            )}

            {phase === "loading" && (
              <div className="flex flex-col items-center gap-3 py-8">
                <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
                <p className="text-muted-foreground text-sm">正在创建订单…</p>
              </div>
            )}

            {phase === "qr" && order?.codeUrl && (
              <div className="flex flex-col items-center gap-4 py-2">
                <QrCanvas url={order.codeUrl} />
                <div className="text-center">
                  <p className="font-medium text-sm">
                    {selectedChannel.icon} 请用 {selectedChannel.label} 扫码支付
                  </p>
                  <p className="mt-1 font-bold text-xl">¥{(amount / 100).toFixed(0)}</p>
                  <Badge variant="outline" className="mt-2 animate-pulse text-xs">
                    等待扫码中…（每 2 秒轮询）
                  </Badge>
                </div>
                <p className="text-[11px] text-muted-foreground">订单号：{order.merchantOrderNo}</p>
                <Button variant="ghost" size="sm" onClick={handleReset}>
                  取消
                </Button>
              </div>
            )}

            {phase === "success" && (
              <div className="flex flex-col items-center gap-3 py-6">
                <div className="flex h-16 w-16 items-center justify-center rounded-full bg-green-100 text-4xl">
                  ✅
                </div>
                <p className="font-semibold text-green-600 text-lg">支付成功</p>
                <p className="text-muted-foreground text-sm">
                  {selectedChannel.label} · ¥{(amount / 100).toFixed(0)}
                </p>
                {order && (
                  <p className="text-muted-foreground text-xs">订单号：{order.merchantOrderNo}</p>
                )}
                <Button variant="outline" className="mt-2" onClick={handleReset}>
                  再来一笔
                </Button>
              </div>
            )}

            {phase === "error" && (
              <div className="flex flex-col items-center gap-3 py-6">
                <div className="text-4xl">❌</div>
                <p className="font-medium text-destructive">{error || "支付失败"}</p>
                <Button variant="outline" onClick={handleReset}>
                  重试
                </Button>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </PageContainer>
  )
}
