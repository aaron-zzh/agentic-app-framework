/**
 * 支付结果落地页——支付宝网页跳转类支付（alipay_pc/alipay_wap）returnUrl 跳转回来的落地页。
 * @author AaronZZH & Kiro
 *
 * returnUrl 跳转本身不可信（用户可能中途取消/网络中断/伪造参数），
 * 页面职责仅为展示过渡态提示，真正的支付结果以订单状态查询（后端异步回调驱动）为准。
 */

"use client"

import { CheckCircle2, Loader2, XCircle } from "lucide-react"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { backendApi } from "@/lib/api/rest/backend-client"
import type { BizOrderType, PayOrderVO } from "@/lib/api/rest/billing/plans"
import { BIZ_ORDER_TYPE } from "@/lib/api/rest/billing/plans"
import { restEndpoints } from "@/lib/api/rest/endpoints"
import { paths } from "@/lib/constants/paths"

type QueryState = "loading" | "success" | "pending" | "failed"

export default function PayResultPage() {
  const searchParams = useSearchParams()
  // 支付宝 returnUrl 跳转自动携带 out_trade_no（即后端 merchantOrderNo），非数据库自增 ID
  const merchantOrderNo = searchParams.get("out_trade_no")
  const [state, setState] = useState<QueryState>("loading")
  const [bizOrderType, setBizOrderType] = useState<BizOrderType | undefined>()

  useEffect(() => {
    if (!merchantOrderNo) {
      setState("failed")
      return
    }
    let cancelled = false
    // returnUrl 跳转到达时支付宝异步回调可能尚未处理完成，短轮询几次确认最终状态
    let attempts = 0
    const poll = async () => {
      try {
        const order = await backendApi.get<PayOrderVO>(
          restEndpoints.pay.orderByMerchantOrderNo(merchantOrderNo)
        )
        if (cancelled) return
        setBizOrderType(order.bizOrderType)
        if (order.status === 10) {
          setState("success")
          return
        }
        if (order.status === 30) {
          setState("failed")
          return
        }
        attempts += 1
        if (attempts >= 5) {
          setState("pending")
          return
        }
        setTimeout(poll, 2000)
      } catch {
        if (!cancelled) setState("failed")
      }
    }
    poll()
    return () => {
      cancelled = true
    }
  }, [merchantOrderNo])

  const successMessage =
    bizOrderType === BIZ_ORDER_TYPE.SUBSCRIPTION
      ? "订阅已激活，可返回继续使用"
      : bizOrderType === BIZ_ORDER_TYPE.CREDIT_PACKAGE || bizOrderType === BIZ_ORDER_TYPE.RECHARGE
        ? "积分已到账，可返回继续使用"
        : "订单已完成，可返回继续使用"

  // 按业务类型跳回对应功能页，而非笼统的首页；未知类型兜底到工作台欢迎页
  const backHref =
    bizOrderType === BIZ_ORDER_TYPE.SUBSCRIPTION
      ? paths.studio.mePricing
      : bizOrderType === BIZ_ORDER_TYPE.CREDIT_PACKAGE || bizOrderType === BIZ_ORDER_TYPE.RECHARGE
        ? paths.studio.meCredits
        : paths.studio.welcome

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 px-4">
      {state === "loading" && (
        <>
          <Loader2 className="size-10 animate-spin text-muted-foreground" />
          <p className="text-muted-foreground text-sm">正在确认支付结果…</p>
        </>
      )}
      {state === "success" && (
        <>
          <CheckCircle2 className="size-10 text-green-500" />
          <p className="font-medium text-lg">支付成功</p>
          <p className="text-muted-foreground text-sm">{successMessage}</p>
        </>
      )}
      {state === "pending" && (
        <>
          <Loader2 className="size-10 text-muted-foreground" />
          <p className="font-medium text-lg">支付结果确认中</p>
          <p className="text-muted-foreground text-sm">
            如已完成支付，稍后将自动到账；如有疑问请联系客服
          </p>
        </>
      )}
      {state === "failed" && (
        <>
          <XCircle className="size-10 text-destructive" />
          <p className="font-medium text-lg">未查询到支付结果</p>
          <p className="text-muted-foreground text-sm">请返回重新发起支付，或联系客服处理</p>
        </>
      )}
      <Button className="mt-4" nativeButton={false} render={<Link href={backHref} />}>
        {state === "success" ? "返回继续使用" : "返回工作台"}
      </Button>
    </div>
  )
}
