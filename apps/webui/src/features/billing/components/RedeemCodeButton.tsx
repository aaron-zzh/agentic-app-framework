/**
 * 兑换码按钮 + 弹窗——可在多处复用。
 * @author AaronZZH & Kiro
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { creditsApi } from "@/lib/api/rest/billing/credits"

interface RedeemCodeButtonProps {
  /** 触发按钮的自定义渲染，不传则用默认样式 */
  trigger?: React.ReactNode
}

export function RedeemCodeButton({ trigger }: RedeemCodeButtonProps) {
  const [open, setOpen] = useState(false)
  const [code, setCode] = useState("")
  const [loading, setLoading] = useState(false)
  const qc = useQueryClient()

  async function handleSubmit() {
    if (!code.trim()) return
    setLoading(true)
    try {
      const amount = await creditsApi.redeem(code.trim())
      toast.success(amount > 0 ? `兑换成功，获得 ${amount} 积分` : "兑换成功，会员已开通")
      setOpen(false)
      setCode("")
      qc.invalidateQueries({ queryKey: ["credits"] })
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "兑换失败，请检查兑换码")
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      {trigger ? (
        <button type="button" onClick={() => setOpen(true)} className="contents">
          {trigger}
        </button>
      ) : (
        <Button
          variant="outline"
          size="sm"
          className="border-amber-500 text-amber-500 hover:bg-amber-500/10"
          onClick={() => setOpen(true)}
        >
          兑换码
        </Button>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="p-8 sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="text-center">兑换码</DialogTitle>
          </DialogHeader>
          <p className="text-center text-muted-foreground text-sm">
            输入兑换码，可兑换积分或开通会员。
          </p>
          <div className="space-y-3 pt-2">
            <Input
              placeholder="请输入兑换码"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && handleSubmit()}
              className="text-center"
            />
            <Button className="w-full" onClick={handleSubmit} disabled={!code.trim() || loading}>
              {loading ? "兑换中..." : "提交"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}
