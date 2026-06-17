/**
 * RecoveryNotification——断点恢复通知横幅
 * 当后端发送 SESSION_RECOVERED 事件时展示，5 秒后自动消失或手动关闭
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { X } from "lucide-react"
import { useEffect } from "react"

interface RecoveryNotificationProps {
  taskCount: number
  onDismiss: () => void
}

export function RecoveryNotification({ taskCount, onDismiss }: RecoveryNotificationProps) {
  // 5 秒后自动消失
  useEffect(() => {
    const timer = setTimeout(onDismiss, 5000)
    return () => clearTimeout(timer)
  }, [onDismiss])

  return (
    <div className="flex items-center gap-2 rounded-md border border-blue-200 bg-blue-50 px-3 py-2 text-blue-800 text-sm dark:border-blue-800 dark:bg-blue-950 dark:text-blue-200">
      <span className="shrink-0">🔄</span>
      <span className="flex-1">之前的任务已恢复，继续执行中...（{taskCount} 个任务）</span>
      <button
        type="button"
        onClick={onDismiss}
        className="shrink-0 rounded p-0.5 hover:bg-blue-100 dark:hover:bg-blue-900"
        aria-label="关闭通知"
      >
        <X className="size-3.5" />
      </button>
    </div>
  )
}
