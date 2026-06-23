/**
 * 拦截路由弹窗：从其他路由 push 进入 /settings/invite 时显示为弹窗，保留当前页面背景。
 *
 * <p>直接访问/刷新 /settings/invite 走 (workspace)/settings/invite/page.tsx，呈现完整页面。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useRouter } from "next/navigation"
import { useCallback } from "react"
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog"
import { InviteRewardView } from "@/features/invite/InviteRewardView"

export default function InviteInterceptModal() {
  const router = useRouter()

  // 关闭弹窗：调用 router.back() 回到来源页面
  const handleOpenChange = useCallback(
    (open: boolean) => {
      if (!open) router.back()
    },
    [router]
  )

  return (
    <Dialog open onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-2xl border-amber-500/30 bg-gradient-to-b from-amber-500/5 via-popover to-popover p-6 sm:max-w-2xl">
        <DialogTitle className="sr-only">邀请赚积分</DialogTitle>
        <InviteRewardView variant="dialog" />
      </DialogContent>
    </Dialog>
  )
}
