/**
 * RedeemCodeGenerateButton——兑换码生成按钮（管理端）
 *
 * <p>支持两种码：积分码（CREDIT）和会员码（MEMBERSHIP），通过 Tab 切换。
 * - 数量=1：调用 /generate，弹出"明文展示对话框"（仅本次可见，提供复制）
 * - 数量>1：调用 /generate-batch?count=N，触发 xlsx 文件下载
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useQuery, useQueryClient } from "@tanstack/react-query"
import { Copy, Plus } from "lucide-react"
import { useId, useState } from "react"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { billingPlansApi } from "@/lib/api/rest/billing/plans"
import {
  type RedeemCodeBatchType,
  type RedeemCodeCreateDTO,
  redeemCodesApi
} from "@/lib/api/rest/billing/redeem-codes"
import { notify } from "@/lib/notification"
import { copyToClipboard } from "@/lib/utils/copy-to-clipboard"

const BATCH_TYPE_OPTIONS: { value: RedeemCodeBatchType; label: string }[] = [
  { value: "REWARD", label: "奖励积分（默认）" },
  { value: "SUBSCRIPTION", label: "会员积分" },
  { value: "TOPUP", label: "购买积分" },
  { value: "WEEKLY", label: "每周积分" },
  { value: "MANUAL", label: "额外赠送" }
]

interface CreditForm {
  creditAmount: string
  batchType: RedeemCodeBatchType
  expiresAt: string
  count: string
  remark: string
}

interface MembershipForm {
  planId: string
  expiresAt: string
  count: string
  remark: string
}

function nextMonthDateTimeLocal() {
  const d = new Date()
  d.setMonth(d.getMonth() + 1)
  return d.toISOString().slice(0, 16)
}

const INIT_CREDIT: CreditForm = {
  creditAmount: "100",
  batchType: "REWARD",
  expiresAt: nextMonthDateTimeLocal(),
  count: "1",
  remark: ""
}
const INIT_MEMBERSHIP: MembershipForm = { planId: "", expiresAt: "", count: "1", remark: "" }

function triggerDownload(filename: string, blob: Blob) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement("a")
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export function RedeemCodeGenerateButton() {
  const formId = useId()
  const queryClient = useQueryClient()

  const [open, setOpen] = useState(false)
  const [tab, setTab] = useState<"credit" | "membership">("credit")
  const [creditForm, setCreditForm] = useState<CreditForm>(INIT_CREDIT)
  const [membershipForm, setMembershipForm] = useState<MembershipForm>(INIT_MEMBERSHIP)
  const [submitting, setSubmitting] = useState(false)
  const [resultCode, setResultCode] = useState<string | null>(null)

  const { data: plans = [] } = useQuery({
    queryKey: ["billing", "plans"],
    queryFn: billingPlansApi.getPlans,
    enabled: open && tab === "membership"
  })

  function buildDto(): RedeemCodeCreateDTO | null {
    if (tab === "credit") {
      const creditAmount = Number(creditForm.creditAmount)
      if (!Number.isFinite(creditAmount) || creditAmount < 1) {
        notify.error("积分数量需 ≥ 1")
        return null
      }
      return {
        creditAmount,
        batchType: creditForm.batchType,
        type: "CREDIT",
        expiresAt: creditForm.expiresAt ? new Date(creditForm.expiresAt).toISOString() : null,
        remark: creditForm.remark.trim() || null
      }
    }
    if (!membershipForm.planId) {
      notify.error("请选择套餐")
      return null
    }
    return {
      creditAmount: 0,
      type: "MEMBERSHIP",
      planId: Number(membershipForm.planId),
      expiresAt: membershipForm.expiresAt ? new Date(membershipForm.expiresAt).toISOString() : null,
      remark: membershipForm.remark.trim() || null
    }
  }

  async function handleSubmit() {
    const dto = buildDto()
    if (!dto) return
    const count = Number(tab === "credit" ? creditForm.count : membershipForm.count)
    if (!Number.isFinite(count) || count < 1 || count > 500) {
      notify.error("生成数量需在 1-500 之间")
      return
    }
    setSubmitting(true)
    try {
      if (count === 1) {
        const code = await redeemCodesApi.generate(dto)
        setResultCode(code)
      } else {
        const { blob, filename } = await redeemCodesApi.generateBatch(dto, count)
        triggerDownload(filename, blob)
        notify.success(`已生成 ${count} 个兑换码，文件已下载`)
        handleClose()
      }
      queryClient.invalidateQueries({ queryKey: ["credit-redeem-code"] })
      queryClient.invalidateQueries({ queryKey: ["entity", "credit-redeem-code"] })
    } catch (e: unknown) {
      if (!(e instanceof Error)) notify.error("生成失败，请重试")
    } finally {
      setSubmitting(false)
    }
  }

  function handleClose() {
    setOpen(false)
    setCreditForm(INIT_CREDIT)
    setMembershipForm(INIT_MEMBERSHIP)
  }

  function handleCloseResult() {
    setResultCode(null)
    handleClose()
  }

  return (
    <>
      <Button
        size="sm"
        className="inline-flex h-8 items-center gap-1 px-3 font-medium text-sm"
        onClick={() => setOpen(true)}
      >
        <Plus className="size-3.5" />
        生成兑换码
      </Button>

      <Dialog
        open={open && !resultCode}
        onOpenChange={(v) => !submitting && (v ? setOpen(true) : handleClose())}
      >
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>生成兑换码</DialogTitle>
          </DialogHeader>

          <Tabs value={tab} onValueChange={(v) => setTab(v as "credit" | "membership")}>
            <TabsList className="w-full">
              <TabsTrigger value="credit" className="flex-1">
                积分码
              </TabsTrigger>
              <TabsTrigger value="membership" className="flex-1">
                会员码
              </TabsTrigger>
            </TabsList>

            <TabsContent value="credit" className="mt-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="col-span-2 space-y-2">
                  <Label htmlFor={`${formId}-amount`}>
                    积分数量 <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    id={`${formId}-amount`}
                    type="number"
                    min={1}
                    value={creditForm.creditAmount}
                    onChange={(e) => setCreditForm({ ...creditForm, creditAmount: e.target.value })}
                    placeholder="单个兑换码的积分数"
                  />
                </div>
                <div className="space-y-2">
                  <Label>积分类型</Label>
                  <Select
                    value={creditForm.batchType}
                    onValueChange={(v) =>
                      v && setCreditForm({ ...creditForm, batchType: v as RedeemCodeBatchType })
                    }
                  >
                    <SelectTrigger>
                      <SelectValue>
                        {BATCH_TYPE_OPTIONS.find((o) => o.value === creditForm.batchType)?.label}
                      </SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                      {BATCH_TYPE_OPTIONS.map((o) => (
                        <SelectItem key={o.value} value={o.value}>
                          {o.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor={`${formId}-credit-count`}>
                    生成数量 <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    id={`${formId}-credit-count`}
                    type="number"
                    min={1}
                    max={500}
                    value={creditForm.count}
                    onChange={(e) => setCreditForm({ ...creditForm, count: e.target.value })}
                    placeholder=">1 时下载 Excel"
                  />
                </div>
                <div className="col-span-2 space-y-2">
                  <Label>过期时间（默认 1 个月）</Label>
                  <Input
                    type="datetime-local"
                    value={creditForm.expiresAt}
                    onChange={(e) => setCreditForm({ ...creditForm, expiresAt: e.target.value })}
                  />
                </div>
                <div className="col-span-2 space-y-2">
                  <Label>备注</Label>
                  <Textarea
                    rows={2}
                    value={creditForm.remark}
                    onChange={(e) => setCreditForm({ ...creditForm, remark: e.target.value })}
                    placeholder="如「双 11 活动」"
                  />
                </div>
              </div>
            </TabsContent>

            <TabsContent value="membership" className="mt-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="col-span-2 space-y-2">
                  <Label>
                    套餐 <span className="text-destructive">*</span>
                  </Label>
                  <Select
                    value={membershipForm.planId}
                    onValueChange={(v) => v && setMembershipForm({ ...membershipForm, planId: v })}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="选择套餐">
                        {plans.find((p) => String(p.id) === membershipForm.planId)?.name}
                      </SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                      {plans.map((p) => (
                        <SelectItem key={p.id} value={String(p.id)}>
                          {p.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor={`${formId}-mem-count`}>
                    生成数量 <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    id={`${formId}-mem-count`}
                    type="number"
                    min={1}
                    max={500}
                    value={membershipForm.count}
                    onChange={(e) =>
                      setMembershipForm({ ...membershipForm, count: e.target.value })
                    }
                    placeholder=">1 时下载 Excel"
                  />
                </div>
                <div className="space-y-2">
                  <Label>过期时间（留空=永不过期）</Label>
                  <Input
                    type="datetime-local"
                    value={membershipForm.expiresAt}
                    onChange={(e) =>
                      setMembershipForm({ ...membershipForm, expiresAt: e.target.value })
                    }
                  />
                </div>
                <div className="col-span-2 space-y-2">
                  <Label>备注</Label>
                  <Textarea
                    rows={2}
                    value={membershipForm.remark}
                    onChange={(e) =>
                      setMembershipForm({ ...membershipForm, remark: e.target.value })
                    }
                    placeholder="如「年度活动赠送」"
                  />
                </div>
              </div>
            </TabsContent>
          </Tabs>

          <p className="rounded-md bg-amber-50 px-3 py-2 text-amber-700 text-xs dark:bg-amber-950/30 dark:text-amber-400">
            ⚠️ 兑换码仅本次可见，批量生成将下载 Excel，请妥善保存。
          </p>

          <DialogFooter>
            <Button variant="outline" onClick={handleClose} disabled={submitting}>
              取消
            </Button>
            <Button onClick={handleSubmit} disabled={submitting}>
              {submitting ? "生成中..." : "生成"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!resultCode} onOpenChange={(v) => !v && handleCloseResult()}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>兑换码已生成</DialogTitle>
          </DialogHeader>
          <p className="text-muted-foreground text-sm">
            请立即保存以下兑换码，关闭后无法再次查看。
          </p>
          <div className="flex items-center gap-2 rounded-md border bg-muted/40 px-3 py-2 font-mono text-sm">
            <span className="flex-1 break-all">{resultCode}</span>
            <Button
              size="sm"
              variant="ghost"
              onClick={async () => {
                if (resultCode) {
                  await copyToClipboard(resultCode)
                  notify.success("已复制兑换码")
                }
              }}
              className="shrink-0"
            >
              <Copy className="size-3.5" /> 复制
            </Button>
          </div>
          <DialogFooter>
            <Button onClick={handleCloseResult}>我已保存</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
