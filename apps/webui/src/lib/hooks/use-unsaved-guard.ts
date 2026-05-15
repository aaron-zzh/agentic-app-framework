/**
 * useUnsavedGuard——未保存修改离开确认
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useEffect } from "react"

/** 离开确认 Hook */
export function useUnsavedGuard(isDirty: boolean, autosave = false) {
  // 浏览器关闭/刷新拦截
  useEffect(() => {
    if (!isDirty || autosave) return

    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault()
    }
    window.addEventListener("beforeunload", handler)
    return () => window.removeEventListener("beforeunload", handler)
  }, [isDirty, autosave])

  /** 路由跳转前调用，返回 true 允许离开 */
  const confirmLeave = useCallback((): boolean => {
    if (!isDirty || autosave) return true
    return window.confirm("有未保存的修改，确定离开吗？")
  }, [isDirty, autosave])

  return { confirmLeave }
}
