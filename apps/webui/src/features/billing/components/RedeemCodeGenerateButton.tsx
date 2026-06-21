/**
 * RedeemCodeGenerateButton——兑换码生成按钮（管理端）
 *
 * <p>对应实体 {@code credit-redeem-code} 的 "+ 生成兑换码" 入口。
 * 由于兑换码创建走特殊端点（{@code /generate} 与 {@code /generate-batch}），
 * 而非通用 CRUD 创建接口，因此在 EntityDef 中关闭 {@code access.create} 后，
 * 通过列表工具栏注册表注入此按钮。
 *
 * <p>支持两种模式：
 * - 数量=1：调用 /generate，弹出"明文展示对话框"（仅本次可见，提供复制）
 * - 数量>1：调用 /generate-batch?count=N，触发 xlsx 文件下载
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
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
import { Textarea } from "@/components/ui/textarea"
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

/** 表单状态 */
interface FormState {
  creditAmount: string
  batchType: RedeemCodeBatchType
  expiresAt: string
  count: string
  remark: string
}

const INITIAL_FORM: FormState = {
  creditAmount: "100",
  batchType: "REWARD",
  expiresAt: "",
  count: "1",
  remark: ""
}

/** 触发文件下载（用 a 标签 + objectURL） */
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
  const [form, setForm] = useState<FormState>(INITIAL_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [resultCode, setResultCode] = useState<string | null>(null)

  function buildDto(): RedeemCodeCreateDTO | null {
    const creditAmount = Number(form.creditAmount)
    if (!Number.isFinite(creditAmount) || creditAmount < 1) {
      notify.error("积分数量需 ≥ 1")
      return null
    }
    return {
      creditAmount,
      batchType: form.batchType,
      type: "CREDIT",
      // datetime-local 不带时区，后端按服务器时区解析；空串视作永不过期
      expiresAt: form.expiresAt ? new Date(form.expiresAt).toISOString() : null,
      remark: form.remark.trim() || null
    }
  }

  async function handleSubmit() {
    const dto = buildDto()
    if (!dto) return

    const count = Number(form.count)
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
        setOpen(false)
        setForm(INITIAL_FORM)
      }
      // 列表刷新
      queryClient.invalidateQueries({ queryKey: ["credit-redeem-code"] })
      queryClient.invalidateQueries({ queryKey: ["entity", "credit-redeem-code"] })
    } catch (e: unknown) {
      // 全局拦截器已弹 toast，这里仅兜底
      if (!(e instanceof Error)) notify.error("生成失败，请重试")
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCopyResult() {
    if (!resultCode) return
    await copyToClipboard(resultCode)
    notify.success("已复制兑换码")
  }

  function handleCloseResult() {
    setResultCode(null)
    setOpen(false)
    setForm(INITIAL_FORM)
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

      {/* 生成表单 Dialog（结果展示时隐藏，避免双层 Dialog） */}
      <Dialog open={open && !resultCode} onOpenChange={(v) => !submitting && setOpen(v)}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>生成兑换码</DialogTitle>
          </DialogHeader>

          <div className="grid grid-cols-2 gap-4">
            <div className="col-span-2 space-y-2">
              <Label htmlFor={`${formId}-amount`}>
                积分数量 <span className="text-destructive">*</span>
              </Label>
              <Input
                id={`${formId}-amount`}
                type="number"
                min={1}
                value={form.creditAmount}
                onChange={(e) => setForm({ ...form, creditAmount: e.target.value })}
                placeholder="单个兑换码的积分数"
              />
            </div>

            <div className="space-y-2">
              <Label>积分类型</Label>
              <Select
                value={form.batchType}
                onValueChange={(v) =>
                  v && setForm({ ...form, batchType: v as RedeemCodeBatchType })
                }
              >
                <SelectTrigger>
                  <SelectValue />
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
              <Label htmlFor={`${formId}-count`}>
                生成数量 <span className="text-destructive">*</span>
              </Label>
              <Input
                id={`${formId}-count`}
                type="number"
                min={1}
                max={500}
                value={form.count}
                onChange={(e) => setForm({ ...form, count: e.target.value })}
                placeholder="1-500，>1 时下载 Excel"
              />
            </div>

            <div className="col-span-2 space-y-2">
              <Label htmlFor={`${formId}-expires`}>过期时间（留空=永不过期）</Label>
              <Input
                id={`${formId}-expires`}
                type="datetime-local"
                value={form.expiresAt}
                onChange={(e) => setForm({ ...form, expiresAt: e.target.value })}
              />
            </div>

            <div className="col-span-2 space-y-2">
              <Label htmlFor={`${formId}-remark`}>备注</Label>
              <Textarea
                id={`${formId}-remark`}
                rows={2}
                value={form.remark}
                onChange={(e) => setForm({ ...form, remark: e.target.value })}
                placeholder="可记录用途，如「双 11 活动」"
              />
            </div>
          </div>

          <p className="rounded-md bg-amber-50 px-3 py-2 text-amber-700 text-xs dark:bg-amber-950/30 dark:text-amber-400">
            ⚠️ 兑换码明文仅本次响应可见，后端只持久化哈希。批量生成将下载 Excel，请妥善保存。
          </p>

          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)} disabled={submitting}>
              取消
            </Button>
            <Button onClick={handleSubmit} disabled={submitting}>
              {submitting ? "生成中..." : "生成"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 单条结果展示 Dialog */}
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
            <Button size="sm" variant="ghost" onClick={handleCopyResult} className="shrink-0">
              <Copy className="size-3.5" />
              复制
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
