/**
 * useUnsavedGuard——未保存修改离开确认
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useEffect, useState } from "react"

export interface UnsavedGuardState {
  /** 是否显示确认对话框 */
  showDialog: boolean
  /** 关闭对话框 */
  closeDialog: () => void
  /** 确认离开（放弃修改） */
  confirmLeave: () => void
  /** 尝试离开（有 dirty 时弹框，否则直接执行） */
  tryLeave: (onLeave: () => void) => void
}

export function useUnsavedGuard(isDirty: boolean, autosave = false): UnsavedGuardState {
  const [showDialog, setShowDialog] = useState(false)
  const [pendingLeave, setPendingLeave] = useState<(() => void) | null>(null)

  // 浏览器关闭/刷新拦截
  useEffect(() => {
    if (!isDirty || autosave) return
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault()
    }
    window.addEventListener("beforeunload", handler)
    return () => window.removeEventListener("beforeunload", handler)
  }, [isDirty, autosave])

  const tryLeave = useCallback(
    (onLeave: () => void) => {
      if (!isDirty || autosave) {
        onLeave()
        return
      }
      setPendingLeave(() => onLeave)
      setShowDialog(true)
    },
    [isDirty, autosave]
  )

  const confirmLeave = useCallback(() => {
    setShowDialog(false)
    pendingLeave?.()
    setPendingLeave(null)
  }, [pendingLeave])

  const closeDialog = useCallback(() => {
    setShowDialog(false)
    setPendingLeave(null)
  }, [])

  return { showDialog, closeDialog, confirmLeave, tryLeave }
}
