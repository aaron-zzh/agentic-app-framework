/**
 * LottieDialog——带 Lottie 动画图标的通用弹窗
 *
 * 封装自 examples/lottie/page.tsx 的 DialogDemo 模式。
 * 顶部 Lottie 动画 + 标题 + 描述 + 操作按钮。
 *
 * @example
 * <LottieDialog
 *   open={open}
 *   onOpenChange={setOpen}
 *   icon="warning"
 *   title="联系客服充值"
 *   description="请扫码联系客服完成充值"
 *   confirmText="好的"
 * />
 */

"use client"

import { LottieIcon } from "@/components/animate/LottieIcon"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"

interface LottieDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Lottie 动画文件名（/assets/icons/lottie/{icon}.json） */
  icon: string
  /** 是否循环播放动画，默认 false */
  loop?: boolean
  title: string
  description?: React.ReactNode
  confirmText?: string
  cancelText?: string
  onConfirm?: () => void
  /** 图标尺寸，默认 80 */
  iconSize?: number
}

export function LottieDialog({
  open,
  onOpenChange,
  icon,
  loop = false,
  title,
  description,
  confirmText = "好的",
  cancelText,
  onConfirm,
  iconSize = 80
}: LottieDialogProps) {
  function handleConfirm() {
    onConfirm?.()
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xs text-center">
        <DialogHeader className="items-center">
          <LottieIcon name={icon} width={iconSize} height={iconSize} loop={loop} />
          <DialogTitle>{title}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        <DialogFooter className="gap-2 sm:justify-center">
          {cancelText && (
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              {cancelText}
            </Button>
          )}
          <Button onClick={handleConfirm}>{confirmText}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
