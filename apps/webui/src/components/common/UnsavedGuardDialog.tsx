/**
 * UnsavedGuardDialog——未保存修改确认对话框
 * @author AaronZZH & Kiro
 */

"use client"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"

interface UnsavedGuardDialogProps {
  open: boolean
  onSaveAndLeave?: () => void
  onDiscardAndLeave: () => void
  onCancel: () => void
}

export function UnsavedGuardDialog({
  open,
  onSaveAndLeave,
  onDiscardAndLeave,
  onCancel
}: UnsavedGuardDialogProps) {
  return (
    <Dialog open={open} onOpenChange={(v) => !v && onCancel()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>有未保存的修改</DialogTitle>
          <DialogDescription>离开此页面将丢失未保存的内容。</DialogDescription>
        </DialogHeader>
        <DialogFooter className="flex-col gap-2 sm:flex-row">
          {onSaveAndLeave && <Button onClick={onSaveAndLeave}>保存并离开</Button>}
          <Button variant="destructive" onClick={onDiscardAndLeave}>
            放弃修改
          </Button>
          <Button variant="outline" onClick={onCancel}>
            取消
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
