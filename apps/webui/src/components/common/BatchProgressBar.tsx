/**
 * BatchProgressBar——批量操作进度条组件
 * @author AaronZZH & Kiro
 *
 * 用法：
 * ```tsx
 * <BatchProgressBar progress={progress} onCancel={cancel} onClose={reset} />
 * ```
 */

"use client"

import type { BatchProgress } from "@/lib/hooks/use-batch-operation"

interface BatchProgressBarProps {
  progress: BatchProgress
  onCancel?: () => void
  onClose?: () => void
}

/** 批量操作进度条 */
export function BatchProgressBar({ progress, onCancel, onClose }: BatchProgressBarProps) {
  if (progress.status === "idle") return null

  const isActive = progress.status === "running"
  const isDone =
    progress.status === "completed" ||
    progress.status === "failed" ||
    progress.status === "cancelled"

  const statusLabel = {
    running: "进行中",
    completed: "已完成",
    failed: "失败",
    cancelled: "已取消",
    idle: ""
  }[progress.status]

  // 预计剩余时间（粗略估算）
  const estimatedSeconds =
    isActive && progress.percentage > 5
      ? Math.round(((100 - progress.percentage) / progress.percentage) * 3)
      : null

  return (
    <div className="fixed right-4 bottom-4 z-50 w-80 rounded-lg border bg-background p-4 shadow-lg">
      <div className="mb-2 flex items-center justify-between">
        <span className="font-medium text-sm">
          批量操作{statusLabel ? ` — ${statusLabel}` : ""}
        </span>
        {isDone && (
          <button
            type="button"
            className="text-muted-foreground text-xs hover:text-foreground"
            onClick={onClose}
          >
            关闭
          </button>
        )}
      </div>

      {/* 进度条 */}
      <div className="mb-2 h-2 w-full overflow-hidden rounded-full bg-muted">
        <div
          className={`h-full rounded-full transition-all duration-300 ${
            progress.status === "failed"
              ? "bg-destructive"
              : progress.status === "completed"
                ? "bg-green-500"
                : "bg-primary"
          }`}
          style={{ width: `${progress.percentage}%` }}
        />
      </div>

      <div className="flex items-center justify-between text-muted-foreground text-xs">
        <span>
          {progress.current} / {progress.total} 条（{progress.percentage}%）
        </span>
        {estimatedSeconds !== null && <span>预计剩余 {estimatedSeconds} 秒</span>}
      </div>

      {progress.errorMessage && (
        <p className="mt-1 text-destructive text-xs">{progress.errorMessage}</p>
      )}

      {isActive && onCancel && (
        <button
          type="button"
          className="mt-2 w-full rounded border px-3 py-1 text-sm hover:bg-accent"
          onClick={onCancel}
        >
          取消
        </button>
      )}
    </div>
  )
}
