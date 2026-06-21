/**
 * 提现申请页 /settings/withdraw
 *
 * 流程：检查手机号 → 弹窗绑定（未绑时）→ 填写申请 → 提交 → 查看历史。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { format } from "date-fns"
import { zhCN } from "date-fns/locale"
import Link from "next/link"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import {
  useApplyWithdraw,
  useMyBalance,
  useMyWithdraws,
  type WithdrawType
} from "@/lib/api/rest/brokerage/withdraw"
import { useProfile } from "@/lib/api/rest/user/profile"
import { notify } from "@/lib/notification"
import { cn } from "@/lib/utils/cn"

const withdrawSchema = z.object({
  amount: z.number().min(100, "最低提现 1 元（100 分）"),
  type: z.enum(["WECHAT", "ALIPAY", "BANK"] as const),
  accountName: z.string().min(2, "请输入收款人姓名"),
  accountNo: z.string().min(5, "请输入收款账号")
})

type WithdrawForm = z.infer<typeof withdrawSchema>

const TYPE_LABELS: Record<WithdrawType, string> = {
  WECHAT: "微信",
  ALIPAY: "支付宝",
  BANK: "银行卡"
}

const STATUS_LABELS: Record<string, { label: string; color: string }> = {
  PENDING: { label: "待审核", color: "text-amber-500" },
  APPROVED: { label: "已通过", color: "text-blue-500" },
  REJECTED: { label: "已拒绝", color: "text-destructive" },
  TRANSFERRED: { label: "已转账", color: "text-emerald-500" }
}

export default function WithdrawPage() {
  const { data: profile, isLoading: profileLoading } = useProfile()
  const { data: balance, isLoading: balanceLoading } = useMyBalance()
  const { data: historyPage, isLoading: histLoading } = useMyWithdraws()
  const applyWithdraw = useApplyWithdraw()

  const hasPhone = !profileLoading && !!profile?.phone

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting }
  } = useForm<WithdrawForm>({
    resolver: zodResolver(withdrawSchema),
    defaultValues: { type: "ALIPAY", accountName: "", accountNo: "", amount: 100 }
  })

  const onSubmit = handleSubmit(async (data) => {
    try {
      await applyWithdraw.mutateAsync(data)
      notify.success("提现申请已提交，等待审核")
    } catch (e) {
      notify.error(e instanceof Error ? e.message : "申请失败，请重试")
    }
  })

  return (
    <div className="mx-auto max-w-3xl px-8 py-6">
      <h1 className="mb-6 font-semibold text-xl">佣金提现</h1>

      {/* 余额 */}
      <Card className="mb-6">
        <CardContent className="py-5">
          <p className="text-muted-foreground text-sm">可用佣金余额</p>
          {balanceLoading ? (
            <Skeleton className="mt-2 h-8 w-32" />
          ) : (
            <p className="mt-1 font-bold text-3xl tabular-nums">
              ¥{((balance ?? 0) / 100).toFixed(2)}
            </p>
          )}
        </CardContent>
      </Card>

      {/* 未绑手机拦截 */}
      {!profileLoading && !hasPhone && (
        <div className="mb-6 rounded-xl border border-amber-500/40 bg-amber-500/10 px-5 py-4">
          <p className="font-medium text-amber-600">请先绑定手机号</p>
          <p className="mt-1 text-amber-500/80 text-sm">
            根据合规要求，申请提现前需要绑定手机号完成实名验证。
          </p>
          <Link
            href="/settings/security"
            className={cn(
              buttonVariants({ variant: "outline", size: "sm" }),
              "mt-3 border-amber-500/50 text-amber-600"
            )}
          >
            去绑定手机号
          </Link>
        </div>
      )}

      {/* 申请表单 */}
      <Card className={cn(!hasPhone && "pointer-events-none opacity-50")}>
        <CardHeader>
          <CardTitle className="text-base">申请提现</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <Label>提现金额（分，1 元 = 100 分）</Label>
              <Input
                type="number"
                min={100}
                placeholder="最低 100 分（¥1.00）"
                {...register("amount", { valueAsNumber: true })}
                aria-invalid={!!errors.amount}
              />
              {errors.amount && <p className="text-destructive text-xs">{errors.amount.message}</p>}
            </div>

            <div className="space-y-1.5">
              <Label>提现方式</Label>
              <Select
                defaultValue="ALIPAY"
                onValueChange={(v) => setValue("type", v as WithdrawType)}
              >
                <SelectTrigger>
                  <SelectValue placeholder="选择提现方式" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="WECHAT">微信</SelectItem>
                  <SelectItem value="ALIPAY">支付宝</SelectItem>
                  <SelectItem value="BANK">银行卡</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label>收款人姓名</Label>
              <Input
                placeholder="真实姓名"
                {...register("accountName")}
                aria-invalid={!!errors.accountName}
              />
              {errors.accountName && (
                <p className="text-destructive text-xs">{errors.accountName.message}</p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label>收款账号</Label>
              <Input
                placeholder="微信/支付宝账号或银行卡号"
                {...register("accountNo")}
                aria-invalid={!!errors.accountNo}
              />
              {errors.accountNo && (
                <p className="text-destructive text-xs">{errors.accountNo.message}</p>
              )}
            </div>

            <div className="flex justify-end pt-2">
              <Button type="submit" disabled={isSubmitting || applyWithdraw.isPending || !hasPhone}>
                {isSubmitting || applyWithdraw.isPending ? "提交中..." : "申请提现"}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {/* 提现历史 */}
      <div className="mt-8">
        <h2 className="mb-4 font-medium">提现历史</h2>
        <Separator className="mb-4" />
        {histLoading ? (
          <div className="space-y-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={`wsk-${i}`} className="h-14 w-full" />
            ))}
          </div>
        ) : (historyPage?.list ?? []).length === 0 ? (
          <p className="py-8 text-center text-muted-foreground text-sm">暂无提现记录</p>
        ) : (
          <div className="divide-y">
            {(historyPage?.list ?? []).map((w) => {
              const s = STATUS_LABELS[w.status] ?? { label: w.status, color: "" }
              return (
                <div key={w.id} className="flex items-center justify-between py-4">
                  <div>
                    <p className="font-medium">
                      {TYPE_LABELS[w.type] ?? w.type} — {w.accountName}
                    </p>
                    <p className="mt-0.5 text-muted-foreground text-xs">
                      {format(new Date(w.createTime), "yyyy年MM月dd日 HH:mm", { locale: zhCN })}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="font-semibold text-lg tabular-nums">
                      ¥{(w.amount / 100).toFixed(2)}
                    </p>
                    <p className={cn("text-xs", s.color)}>{s.label}</p>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
