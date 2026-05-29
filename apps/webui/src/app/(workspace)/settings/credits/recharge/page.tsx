/**
 * 积分充值页面——选择套餐或自定义金额，确认充值
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { notify } from "@/lib/notification"
import { useCreateRecharge, useTokenRules } from "@/lib/queries/use-credits"

export default function RechargePage() {
  const { data: rules, isLoading } = useTokenRules()
  const { mutate: recharge, isPending } = useCreateRecharge()
  const [selectedRuleId, setSelectedRuleId] = useState<string | null>(null)
  const [customAmount, setCustomAmount] = useState("")

  /** 当前选中的充值金额 */
  const activeAmount = selectedRuleId
    ? rules?.find((r) => r.id === selectedRuleId)?.creditAmount ?? 0
    : Number(customAmount) || 0

  const handleSelectRule = (ruleId: string) => {
    setSelectedRuleId(ruleId)
    setCustomAmount("")
  }

  const handleCustomInput = (value: string) => {
    setCustomAmount(value)
    setSelectedRuleId(null)
  }

  const handleRecharge = () => {
    if (activeAmount <= 0) {
      notify.error("请选择充值套餐或输入有效金额")
      return
    }
    recharge(activeAmount, {
      onSuccess: (data) => {
        notify.success(`充值订单已创建，订单号：${data.orderNo}`)
      },
      onError: () => notify.error("充值失败，请重试")
    })
  }

  if (isLoading) {
    return (
      <div className="space-y-4 p-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6 p-6">
      {/* 页面标题 */}
      <div>
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="sm" asChild>
            <Link href="/settings/credits">← 返回</Link>
          </Button>
          <h1 className="font-semibold text-2xl">积分充值</h1>
        </div>
        <p className="mt-1 text-muted-foreground text-sm">选择充值套餐或输入自定义金额</p>
      </div>

      {/* 充值套餐 */}
      <Card>
        <CardHeader>
          <CardTitle>充值套餐</CardTitle>
          <CardDescription>选择预设套餐享受更优兑换比例</CardDescription>
        </CardHeader>
        <CardContent>
          {!rules?.length ? (
            <p className="py-4 text-center text-muted-foreground text-sm">暂无可用套餐</p>
          ) : (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {rules
                .filter((r) => r.status === "ENABLED")
                .map((rule) => (
                  <button
                    key={rule.id}
                    type="button"
                    className={`rounded-lg border p-4 text-left transition-colors hover:border-primary ${
                      selectedRuleId === rule.id ? "border-primary bg-primary/5 ring-1 ring-primary" : ""
                    }`}
                    onClick={() => handleSelectRule(rule.id)}
                  >
                    <p className="font-medium">{rule.name}</p>
                    <p className="mt-1 font-bold text-lg">{rule.creditAmount} 积分</p>
                    <p className="text-muted-foreground text-sm">
                      可兑换 {rule.tokenAmount} Token
                    </p>
                  </button>
                ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* 自定义金额 */}
      <Card>
        <CardHeader>
          <CardTitle>自定义金额</CardTitle>
          <CardDescription>输入任意充值积分数量</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-end gap-4">
            <div className="flex-1 space-y-2">
              <Label htmlFor="custom-amount">充值积分数</Label>
              <Input
                id="custom-amount"
                type="number"
                min={1}
                placeholder="输入积分数量"
                value={customAmount}
                onChange={(e) => handleCustomInput(e.target.value)}
              />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* 确认充值 */}
      <Card>
        <CardContent className="flex items-center justify-between pt-6">
          <div>
            <p className="text-muted-foreground text-sm">充值金额</p>
            <p className="font-bold text-2xl">{activeAmount} 积分</p>
          </div>
          <Button size="lg" disabled={activeAmount <= 0 || isPending} onClick={handleRecharge}>
            {isPending ? "处理中..." : "确认充值"}
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
