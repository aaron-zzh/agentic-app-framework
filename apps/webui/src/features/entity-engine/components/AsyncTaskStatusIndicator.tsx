"use client"

import { LoaderCircleIcon } from "lucide-react"
import { useEffect } from "react"

import { Badge } from "@/components/ui/badge"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"

export type AsyncTaskStatus =
  | "PENDING"
  | "QUEUED"
  | "RUNNING"
  | "RETRY_WAIT"
  | "SUCCEEDED"
  | "FAILED"

interface AsyncTaskStatusIndicatorProps {
  label: string
  status: AsyncTaskStatus
  result?: string
  lastError?: string
  onClose: () => void
}

const AUTO_CLOSE_DELAY = 2_000

const STATUS_META: Record<
  AsyncTaskStatus,
  { label: string; variant: "default" | "secondary" | "destructive" | "outline" }
> = {
  PENDING: { label: "等待提交", variant: "secondary" },
  QUEUED: { label: "排队中", variant: "secondary" },
  RUNNING: { label: "执行中", variant: "default" },
  RETRY_WAIT: { label: "等待重试", variant: "outline" },
  SUCCEEDED: { label: "已完成", variant: "default" },
  FAILED: { label: "失败", variant: "destructive" }
}

export function AsyncTaskStatusIndicator({
  label,
  status,
  result,
  lastError,
  onClose
}: AsyncTaskStatusIndicatorProps) {
  const meta = STATUS_META[status]
  const detail = status === "FAILED" ? lastError : status === "SUCCEEDED" ? result : undefined
  const terminal = status === "SUCCEEDED" || status === "FAILED"

  useEffect(() => {
    if (!terminal) return
    const timer = window.setTimeout(onClose, AUTO_CLOSE_DELAY)
    return () => window.clearTimeout(timer)
  }, [onClose, terminal])

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent showCloseButton>
        <DialogHeader>
          <DialogTitle>{label}</DialogTitle>
          <DialogDescription>后台任务状态会自动更新。</DialogDescription>
        </DialogHeader>
        <div className="flex items-center gap-2">
          <Badge variant={meta.variant} className="gap-1">
            {status === "RUNNING" && <LoaderCircleIcon className="size-3 animate-spin" />}
            {meta.label}
          </Badge>
          {detail && <span className="max-w-64 truncate text-muted-foreground">{detail}</span>}
        </div>
      </DialogContent>
    </Dialog>
  )
}