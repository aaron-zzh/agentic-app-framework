/**
 * ConfirmDialog——通用确认弹窗
 * @example
 * <ConfirmDialog
 *   open={open}
 *   onOpenChange={setOpen}
 *   title="删除项目"
 *   description={`确定要删除「${name}」吗？此操作不可撤销。`}
 *   onConfirm={handleDelete}
 * />
 */

"use client"

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from "@/components/ui/alert-dialog"

interface ConfirmDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  confirmText?: string
  cancelText?: string
  variant?: "destructive" | "default"
  onConfirm: () => void
}

export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmText = "确认",
  cancelText = "取消",
  variant = "default",
  onConfirm
}: ConfirmDialogProps) {
  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="shadow-lg ring-0">
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          {description && (
            <AlertDialogDescription className="py-4 text-sm">{description}</AlertDialogDescription>
          )}
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel onClick={() => onOpenChange(false)}>{cancelText}</AlertDialogCancel>
          <AlertDialogAction
            onClick={() => {
              onConfirm()
              onOpenChange(false)
            }}
            className={
              variant === "destructive"
                ? "bg-destructive text-white hover:bg-destructive/90"
                : undefined
            }
          >
            {confirmText}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
